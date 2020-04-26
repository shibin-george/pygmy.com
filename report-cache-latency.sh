#!/bin/bash

UI_IP="128.119.243.168"
CATALOG_IP="128.119.243.175"
ORDER_IP="128.119.243.175"

chmod +x *.sh 

if [ ! -f bin/test/TestRunner.class ]; then
    ./compile.sh
fi

# set classpath
CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')

cd bin/
java -ea -cp $CP:. test.TestRunner $CATALOG_IP $UI_IP 10