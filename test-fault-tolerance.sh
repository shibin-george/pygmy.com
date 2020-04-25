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

echo "Crashing one of catalog-server replicas.."
./crash-catalog-server.sh

echo -e "Clearing cached responses from UIServer..\n"
curl -s -X POST http://$UI_IP:35650/invalidate/rpcdummies > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/impstudent > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/67720min > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/xenart177 > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/project3 > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/pioneer > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/whytheory > /dev/null

cd bin/

# run tests!

echo "Checking search/lookup/buy endpoints are still up and running.."
# test-case #1: thorough sanity tests on /search and /lookup endpoints
java -ea -cp $CP:. test.TestRunner $CATALOG_IP $UI_IP 6

# test-case #2: thorough sanity tests on /multibuy and /update endpoints
java -ea -cp $CP:. test.TestRunner $CATALOG_IP $UI_IP 7

echo "Restarting the stopped catalog-server replica.."
cd ../
./recover-catalog-server.sh
cd bin/

echo -e "Waiting for some time to ensure that restarted replica is added to load-balancer..\n"
sleep 20

echo -e "Clearing cached responses from UIServer again..\n"
curl -s -X POST http://$UI_IP:35650/invalidate/rpcdummies > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/impstudent > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/67720min > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/xenart177 > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/project3 > /dev/null 
curl -s -X POST http://$UI_IP:35650/invalidate/pioneer > /dev/null
curl -s -X POST http://$UI_IP:35650/invalidate/whytheory > /dev/null

echo "Checking search/lookup/buy are redirected to the restarted replica.."
# test-case #1: thorough sanity tests on /search and /lookup endpoints
java -ea -cp $CP:. test.TestRunner $CATALOG_IP $UI_IP 8

# test-case #2: thorough sanity tests on /multibuy and /update endpoints
java -ea -cp $CP:. test.TestRunner $CATALOG_IP $UI_IP 9
