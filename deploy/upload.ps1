# PowerShell script to upload Steve to EC2
$EC2_IP = "3.107.10.202"
$KEY_PATH = "c:\Users\erici\Downloads\steve-key.pem"
$STEVE_PATH = "c:\Users\erici\Desktop\steve-master"

# Create remote directories
$remoteCmd = "mkdir -p /opt/steve"
$process = Start-Process -FilePath "powershell" -ArgumentList "-Command", "ssh -i `"$KEY_PATH`" -o StrictHostKeyChecking=no ubuntu@$EC2_IP `"$remoteCmd`""

# Upload Steve files
$uploadCmd = "scp -i `"$KEY_PATH`" -r `"$STEVE_PATH\target\steve.jar`" ubuntu@${EC2_IP}:/opt/steve/"
$process = Start-Process -FilePath "powershell" -ArgumentList "-Command", $uploadCmd

# Upload deployment script
$uploadDeployCmd = "scp -i `"$KEY_PATH`" -r `"$STEVE_PATH\deploy\deploy.sh`" ubuntu@${EC2_IP}:~/"
$process = Start-Process -FilePath "powershell" -ArgumentList "-Command", $uploadDeployCmd

Write-Host "Files uploaded successfully! Next steps:"
Write-Host "1. SSH to your instance: ssh -i $KEY_PATH ubuntu@$EC2_IP"
Write-Host "2. Run the deployment script: chmod +x deploy.sh && ./deploy.sh"
