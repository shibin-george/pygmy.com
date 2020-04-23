#!/bin/bash

UI_IP="128.119.243.168"
CATALOG_IP="128.119.243.175"
ORDER_IP="128.119.243.175"

# set classpath
CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')

cd bin/

# run tests!

# test-case #1: thorough sanity tests on /search and /lookup endpoints
java -ea -cp $CP:. test.TestRunner $CATALOG_IP $UI_IP 1

# test-case #2: thorough sanity tests on /multibuy and /update endpoints
java -ea -cp $CP:. test.TestRunner $CATALOG_IP $UI_IP 2

# test-case #3: consecutive buy requests of same item assuming enough stock
java -ea -cp $CP:. test.TestRunner $CATALOG_IP $UI_IP 3

# test-case #4: consecutive buy requests of same item while checking out-of-stock behavior
java -ea -cp $CP:. test.TestRunner $CATALOG_IP $UI_IP 4

# test-case #5: 3 concurrent threads buying the same should see consistent stock
java -ea -cp $CP:. test.TestRunner $CATALOG_IP $UI_IP 5
