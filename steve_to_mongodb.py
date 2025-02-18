import requests
import pymongo
from datetime import datetime
import time
import logging
from typing import Optional, Dict, Any
import json

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class SteveToMongoDB:
    def __init__(
        self,
        steve_url: str,
        steve_username: str,
        steve_password: str,
        steve_api_key: str,
        mongo_uri: str,
        mongo_db: str,
        mongo_collection: str,
        charger_ids: list[str]
    ):
        self.steve_url = steve_url.rstrip('/')
        self.steve_auth = (steve_username, steve_password)
        self.steve_headers = {'STEVE-API-KEY': steve_api_key}
        self.charger_ids = charger_ids

        # Initialize MongoDB connection
        try:
            self.mongo_client = pymongo.MongoClient(mongo_uri)
            self.db = self.mongo_client[mongo_db]
            self.collection = self.db[mongo_collection]
            # Create indexes
            self.collection.create_index([("chargeBoxId", 1), ("timestamp", -1)])
            self.collection.create_index([("id", 1)], unique=True)
            logger.info("Successfully connected to MongoDB")
        except Exception as e:
            logger.error(f"Failed to connect to MongoDB: {e}")
            raise

    def fetch_latest_transaction(self, charger_id: str) -> Optional[Dict[str, Any]]:
        """Fetch latest transaction data for a specific charger from Steve API."""
        try:
            url = f"{self.steve_url}/api/v1/transactions/charger/{charger_id}/latest"
            response = requests.get(
                url,
                auth=self.steve_auth,
                headers=self.steve_headers,
                timeout=10
            )
            
            if response.status_code == 200:
                return response.json()
            elif response.status_code == 404:
                logger.warning(f"No transactions found for charger {charger_id}")
                return None
            else:
                logger.error(f"Failed to fetch data for charger {charger_id}. Status code: {response.status_code}")
                return None
                
        except requests.exceptions.RequestException as e:
            logger.error(f"Error fetching data for charger {charger_id}: {e}")
            return None

    def store_transaction(self, transaction_data: Dict[str, Any]) -> bool:
        """Store transaction data in MongoDB with upsert."""
        try:
            # Add metadata
            transaction_data['_updated_at'] = datetime.utcnow()
            
            # Use the transaction ID as the unique identifier
            result = self.collection.update_one(
                {'id': transaction_data['id']},
                {'$set': transaction_data},
                upsert=True
            )
            
            if result.modified_count > 0:
                logger.info(f"Updated transaction {transaction_data['id']} for charger {transaction_data['chargeBoxId']}")
            elif result.upserted_id:
                logger.info(f"Inserted new transaction {transaction_data['id']} for charger {transaction_data['chargeBoxId']}")
                
            return True
            
        except Exception as e:
            logger.error(f"Error storing transaction data: {e}")
            return False

    def run(self, interval_seconds: int = 60):
        """Main loop to continuously fetch and store data."""
        logger.info("Starting Steve to MongoDB sync")
        
        while True:
            for charger_id in self.charger_ids:
                try:
                    # Fetch latest transaction
                    transaction_data = self.fetch_latest_transaction(charger_id)
                    
                    if transaction_data:
                        # Store in MongoDB
                        self.store_transaction(transaction_data)
                    
                except Exception as e:
                    logger.error(f"Error processing charger {charger_id}: {e}")
                    continue
                    
            # Wait for next interval
            time.sleep(interval_seconds)

def main():
    # Configuration
    config = {
        'steve_url': 'http://192.168.4.2:8080/steve',
        'steve_username': 'admin',
        'steve_password': '12345',
        'steve_api_key': '12345',
        'mongo_uri': 'mongodb://localhost:27017/',
        'mongo_db': 'steve_ocpp',
        'mongo_collection': 'transactions',
        'charger_ids': ['CDJ940009'],  # Add more charger IDs as needed
        'sync_interval': 60  # seconds
    }
    
    try:
        syncer = SteveToMongoDB(
            steve_url=config['steve_url'],
            steve_username=config['steve_username'],
            steve_password=config['steve_password'],
            steve_api_key=config['steve_api_key'],
            mongo_uri=config['mongo_uri'],
            mongo_db=config['mongo_db'],
            mongo_collection=config['mongo_collection'],
            charger_ids=config['charger_ids']
        )
        
        # Start the sync process
        syncer.run(interval_seconds=config['sync_interval'])
        
    except KeyboardInterrupt:
        logger.info("Stopping Steve to MongoDB sync")
    except Exception as e:
        logger.error(f"Application error: {e}")
        raise

if __name__ == "__main__":
    main()
