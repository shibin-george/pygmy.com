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

if [[ ${@: -1} == "clean" ]]
then
  CLEAN="clean"
fi

chmod +x *.sh && ./compile.sh

# set classpath
CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')

PWD=`pwd`

# UI server
ssh $USER@$UI_SERVER "cd $PWD && ./run-ui-server.sh $CLEAN"
ssh $USER@$UI_SERVER "cd $PWD && ps -ef > ps.ui"
if grep -q "pygmy.com.ui.UIServer" ps.ui;
then
  echo "UI Server is running on $UI_SERVER @ $UI_IP !"
else
  echo "UI Server is not running! Check this before proceeding!!"
fi

# Catalog server replica 1
ssh $USER@$CATALOG_SERVER1 "cd $PWD && ./run-catalog-server.sh $UI_IP 1 $CLEAN"
ssh $USER@$CATALOG_SERVER1 "cd $PWD && ps -ef > ps.cat"
if grep -q "pygmy.com.catalog.CatalogServer" ps.cat;
then
  echo "Catalog Server is running on $CATALOG_SERVER1 @ $CATALOG_IP1 !"
else 
  echo "Catalog Server in not running! Check this before proceeding!!"
fi

# Catalog server replica 2
ssh $USER@$CATALOG_SERVER2 "cd $PWD && ./run-catalog-server.sh $UI_IP 2 $CLEAN"
ssh $USER@$CATALOG_SERVER2 "cd $PWD && ps -ef > ps.cat"
if grep -q "pygmy.com.catalog.CatalogServer" ps.cat;
then
  echo "Catalog Server is running on $CATALOG_SERVER2 @ $CATALOG_IP2 !"
else
  echo "Catalog Server in not running! Check this before proceeding!!"
fi

# Order server replica 1
ssh $USER@$ORDER_SERVER1 "cd $PWD && ./run-order-server.sh $UI_IP 1 $CLEAN"
ssh $USER@$ORDER_SERVER1 "cd $PWD && ps -ef > ps.ord"
if grep -q "pygmy.com.order.OrderServer" ps.ord;
then
  echo "Order Server is running on $ORDER_SERVER1 @ $ORDER_IP1 !"
else
  echo "Order Server is not running! Check this before proceeding!!"
fi

# Order server replica 2
ssh $USER@$ORDER_SERVER2 "cd $PWD && ./run-order-server.sh $UI_IP 2 $CLEAN"
ssh $USER@$ORDER_SERVER2 "cd $PWD && ps -ef > ps.ord"
if grep -q "pygmy.com.order.OrderServer" ps.ord;
then
  echo "Order Server is running on $ORDER_SERVER2 @ $ORDER_IP2 !"
else
  echo "Order Server is not running! Check this before proceeding!!"
fi
