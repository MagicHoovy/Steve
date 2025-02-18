# Steve Deployment Instructions

1. Create EC2 Instance:
   - Ubuntu Server 22.04 LTS
   - t2.micro (Free tier eligible)
   - Open ports: 22 (SSH), 8080 (Steve HTTP), 27017 (MongoDB)

2. Upload files:
   ```bash
   scp -i your-key.pem steve.jar ubuntu@your-ec2-ip:/opt/steve/
   scp -i your-key.pem deploy.sh ubuntu@your-ec2-ip:~/
   ```

3. SSH to instance:
   ```bash
   ssh -i your-key.pem ubuntu@your-ec2-ip
   ```

4. Run deployment:
   ```bash
   chmod +x deploy.sh
   ./deploy.sh
   ```

5. Check status:
   ```bash
   sudo systemctl status steve
   sudo systemctl status mongodb
   ```

6. View logs:
   ```bash
   journalctl -u steve -f
   ```

7. Monitor memory usage:
   ```bash
   free -m
   top
   ```

Note: The deployment script includes optimizations for t2.micro:
- Reduced Java heap size (512MB max)
- Optimized MongoDB cache (250MB)
- Added 2GB swap file for memory management
- Using G1GC garbage collector for better memory efficiency
