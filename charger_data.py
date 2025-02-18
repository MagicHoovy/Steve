import subprocess
import json
from pymongo import MongoClient
from datetime import datetime
import logging
import time
import signal
import sys
from logging.handlers import RotatingFileHandler

# Configure logging with file output
log_formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
log_file = 'charger_data.log'
file_handler = RotatingFileHandler(log_file, maxBytes=5*1024*1024, backupCount=5)
file_handler.setFormatter(log_formatter)

console_handler = logging.StreamHandler()
console_handler.setFormatter(log_formatter)

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)
logger.addHandler(file_handler)
logger.addHandler(console_handler)

# MongoDB connection settings
MONGO_URI = "mongodb+srv://IoTadmin:admin888@maincluster.xh201.mongodb.net/"
MONGO_DB = "TestDatabase"
MONGO_COLLECTION = "charger"

# Global flag for graceful shutdown
running = True

def signal_handler(signum, frame):
    """Handle shutdown signals"""
    global running
    logger.info("Shutdown signal received. Cleaning up...")
    running = False

def validate_data(data):
    """Validate the data structure"""
    required_fields = ['chargeBoxId', 'connectorId', 'connectorStatus']
    
    if not isinstance(data, dict):
        logger.error("Data is not a dictionary")
        return False
        
    for field in required_fields:
        if field not in data:
            logger.error(f"Missing required field: {field}")
            return False
            
    # Validate meter values if present
    if 'meterValues' in data and 'values' in data['meterValues']:
        values = data['meterValues']['values']
        logger.info(f"Meter Values - Energy: {values.get('energy.active.import.register', {}).get('value')} Wh, "
                   f"Temperature: {values.get('temperature', {}).get('value')}°C, "
                   f"Current: {values.get('current.import', {}).get('value')}A, "
                   f"Power: {values.get('power.active.import', {}).get('value')}W, "
                   f"Voltage: {values.get('voltage', {}).get('value')}V")
    
    return True

def fetch_data_with_curl(charger_id):
    """Fetch data using curl command"""
    curl_command = [
        'curl',
        '-s',  # silent mode
        '-u', 'admin:12345',  # basic auth
        '-H', 'STEVE-API-KEY: 12345',
        f'http://192.168.4.2:8080/steve/api/v1/transactions/charger/{charger_id}/latest'
    ]
    
    try:
        # Execute curl command
        result = subprocess.run(curl_command, capture_output=True, text=True)
        
        if result.returncode == 0 and result.stdout:
            data = json.loads(result.stdout)
            if validate_data(data):
                return data
            return None
        else:
            logger.error(f"Curl command failed. Exit code: {result.returncode}")
            logger.error(f"Stderr: {result.stderr}")
            return None
            
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON response: {e}")
        return None
    except Exception as e:
        logger.error(f"Error executing curl command: {e}")
        return None

def store_in_mongodb(data):
    """Store data in MongoDB"""
    client = None
    try:
        # MongoDB connection
        client = MongoClient(MONGO_URI)
        db = client[MONGO_DB]
        collection = db[MONGO_COLLECTION]
        
        # Add metadata
        data['_updated_at'] = datetime.utcnow()
        
        # Store previous values for change detection
        previous = collection.find_one({'chargeBoxId': data['chargeBoxId']})
        
        # Upsert the document
        result = collection.update_one(
            {'chargeBoxId': data['chargeBoxId']},  # match by charger ID
            {'$set': data},
            upsert=True
        )
        
        if result.modified_count > 0:
            # Log changes if previous data exists
            if previous:
                log_changes(previous, data)
            logger.info(f"Updated data for charger {data['chargeBoxId']}")
        elif result.upserted_id:
            logger.info(f"Inserted new data for charger {data['chargeBoxId']}")
            
        return True
        
    except Exception as e:
        logger.error(f"MongoDB error: {e}")
        return False
    finally:
        if client:
            client.close()

def log_changes(old_data, new_data):
    """Log important changes in charger data"""
    if old_data.get('connectorStatus') != new_data.get('connectorStatus'):
        logger.info(f"Status changed: {old_data.get('connectorStatus')} -> {new_data.get('connectorStatus')}")
    
    if 'meterValues' in old_data and 'meterValues' in new_data:
        old_values = old_data['meterValues'].get('values', {})
        new_values = new_data['meterValues'].get('values', {})
        
        # Check energy consumption
        old_energy = float(old_values.get('energy.active.import.register', {}).get('value', 0))
        new_energy = float(new_values.get('energy.active.import.register', {}).get('value', 0))
        if new_energy > old_energy:
            logger.info(f"Energy consumption increased: {new_energy - old_energy} Wh")

def main():
    # Register signal handlers for graceful shutdown
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # Configuration
    charger_id = 'CDJ940009'  # Replace with your charger ID
    interval = 10  # seconds
    
    logger.info(f"Starting data collection for charger {charger_id}")
    logger.info(f"Data will be collected every {interval} seconds")
    logger.info("Press Ctrl+C to stop")
    logger.info(f"Logging to file: {log_file}")
    
    error_count = 0
    max_errors = 3  # Maximum consecutive errors before backing off
    
    while running:
        try:
            # Fetch data
            data = fetch_data_with_curl(charger_id)
            
            if data:
                logger.info(f"Data fetched successfully for charger {charger_id}")
                if store_in_mongodb(data):
                    logger.info("Data stored in MongoDB successfully")
                    error_count = 0  # Reset error count on success
                else:
                    error_count += 1
            else:
                error_count += 1
                logger.error("Failed to fetch data")
            
            # Back off if too many errors
            if error_count >= max_errors:
                logger.warning(f"Too many errors ({error_count}). Waiting 30 seconds before retry...")
                time.sleep(30)
                error_count = 0
            else:
                time.sleep(interval)
                
        except Exception as e:
            logger.error(f"Unexpected error: {e}")
            error_count += 1
            time.sleep(interval)
    
    logger.info("Shutdown complete")

if __name__ == "__main__":
    main()
