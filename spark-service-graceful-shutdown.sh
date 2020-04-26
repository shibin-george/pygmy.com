#!/bin/bash

UI_IP="128.119.243.168"
CATALOG_IP1="128.119.243.175"
CATALOG_IP2="128.119.243.147"
ORDER_IP1="128.119.243.175"
ORDER_IP2="128.119.243.168"

curl -s -X GET http://$UI_IP:35650/stop > /dev/null
curl -s -X GET http://$CATALOG_IP1:35640/stop > /dev/null
curl -s -X GET http://$CATALOG_IP2:35640/stop > /dev/null
curl -s -X GET http://$ORDER_IP1:35660/stop > /dev/null
curl -s -X GET http://$ORDER_IP2:35660/stop > /dev/null

