#!/bin/bash

UI_SERVER="elnux3.cs.umass.edu"
CATALOG_SERVER1="elnux7.cs.umass.edu"
CATALOG_SERVER2="elnux1.cs.umass.edu"
ORDER_SERVER1="elnux7.cs.umass.edu"
ORDER_SERVER2="elnux3.cs.umass.edu"

UI_IP="128.119.243.168"
CATALOG_IP1="128.119.243.175"
CATALOG_IP2="128.119.243.147"
ORDER_IP1="128.119.243.175"
ORDER_IP2="128.119.243.168"

ssh $USER@$CATALOG_SERVER2 "cd $PWD && ./kill-catalog-server.sh"
echo -e "Crashed Catalog Server running @ $CATALOG_SERVER2\n"
