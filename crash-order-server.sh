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

# stop jetty server
curl -m 5 -s -X GET http://$ORDER_IP2:35660/stop > /dev/null

ssh $USER@$ORDER_SERVER2 "cd $PWD && ./kill-order-server.sh"
echo -e "Crashed Order Server running @ $ORDER_SERVER2 \n"
sleep 5
