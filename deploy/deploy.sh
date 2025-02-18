#!/bin/bash

# Install Java and MongoDB
sudo apt update
sudo apt install -y openjdk-11-jdk mongodb

# Configure MongoDB for low memory
sudo tee /etc/mongodb.conf << EOF
storage:
  engine: wiredTiger
  wiredTiger:
    engineConfig:
      cacheSizeGB: 0.25
systemLog:
  destination: file
  path: /var/log/mongodb/mongodb.log
  logAppend: true
EOF

# Start MongoDB
sudo systemctl start mongodb
sudo systemctl enable mongodb

# Create Steve directory
sudo mkdir -p /opt/steve
sudo chown -R ubuntu:ubuntu /opt/steve

# Create Steve service with memory optimization
sudo tee /etc/systemd/system/steve.service << EOF
[Unit]
Description=Steve OCPP Server
After=network.target mongodb.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/steve
# Memory optimization for t2.micro
Environment="JAVA_OPTS=-Xms128m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ExecStart=java \$JAVA_OPTS -jar steve.jar
Restart=always

[Install]
WantedBy=multi-user.target
EOF

# Create swap file for extra memory
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Reload systemd
sudo systemctl daemon-reload

# Start Steve
sudo systemctl enable steve
sudo systemctl start steve
