echo -e "\n\n\n===========================================|ATTENTION PLEASE|===========================================\n"
echo -e "For deploying the lab2 assignment across servers, ssh-key pairs need to be set up.\n\n"
echo -e "Setting up ssh-key pairs now..."
echo -e "You will be asked for passphrase when generating the RSA private key."
echo -e "\nDO NOT ENTER a passphrase. Just proceed with empty passphrase!"
echo -e "\nDO NOT change the location of the keys from the default values!"
echo -e "\nIF prompted to confirm whether to overwrite the key, please press y.\n\n"
ssh-keygen -t rsa -b 4096

echo -e "\n\n\n===========================================|ATTENTION PLEASE|===========================================\n"
echo -e "\n\nNow copying the ssh public key to remote server..."
echo -e "You will be asked for your passwords about 3 times.\n\n"
ssh-copy-id $USER@elnux1.cs.umass.edu
ssh-copy-id $USER@elnux3.cs.umass.edu
ssh-copy-id $USER@elnux7.cs.umass.edu


echo -e "\n\n\n===========================================|ATTENTION PLEASE|===========================================\n"
echo -e "\n\nRun this script only ONCE.\n\n"
