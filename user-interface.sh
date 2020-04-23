#!/bin/bash

UI_IP="128.119.243.168"
CATALOG_IP1="128.119.243.175"
CATALOG_IP2="128.119.243.147"
ORDER_IP1="128.119.243.175"
ORDER_IP2="128.119.243.168"

# set classpath
CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')

cd bin/

# run user-interface
java -cp $CP:. userinterface.UserInterface $UI_IP userinterface/UserInterface.class
