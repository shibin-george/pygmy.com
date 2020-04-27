#!/bin/bash

UI_IP="172.18.0.22"
CATALOG_IP1="172.18.0.23"
CATALOG_IP2="172.18.0.24"
ORDER_IP1="172.18.0.25"
ORDER_IP2="172.18.0.26"

# set classpath
CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')

# run user-interface
cd bin-docker/
java -cp $CP:. userinterface.UserInterface $UI_IP userinterface/UserInterface.class
