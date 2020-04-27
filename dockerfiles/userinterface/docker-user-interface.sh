#!/bin/bash

UI_IP="172.18.0.22"
CATALOG_IP1="172.18.0.23"
CATALOG_IP2="172.18.0.24"
ORDER_IP1="172.18.0.25"
ORDER_IP2="172.18.0.26"

# run user-interface
cd /
java -jar /UserInterface.jar $UI_IP