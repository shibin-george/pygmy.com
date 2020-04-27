#!/bin/bash

#docker build uiserverdocker -t uiserver
#docker build orderserverdocker -t orderserver
#docker build catalogserverdocker -t catalogserver

# pull the docker images
docker pull shibingeorge/pygmy.com:orderserver
docker pull shibingeorge/pygmy.com:catalogserver
docker pull shibingeorge/pygmy.com:uiserver
docker pull shibingeorge/pygmy.com:userinterface

# create subnet mask
docker network rm mynet
docker network create --subnet=172.18.0.0/16 mynet

# assign IP addresses within this subnet
UI_IP="172.18.0.22"
CATALOG_IP1="172.18.0.23"
CATALOG_IP2="172.18.0.24"
ORDER_IP1="172.18.0.25"
ORDER_IP2="172.18.0.26"

docker run --net mynet --ip $UI_IP -d shibingeorge/pygmy.com:uiserver
sleep 3
docker run --net mynet --ip $CATALOG_IP1 -d shibingeorge/pygmy.com:catalogserver
sleep 3
docker run --net mynet --ip $CATALOG_IP2 -d shibingeorge/pygmy.com:catalogserver
sleep 3
docker run --net mynet --ip $ORDER_IP1 -d shibingeorge/pygmy.com:orderserver
sleep 3
docker run --net mynet --ip $ORDER_IP2 -d shibingeorge/pygmy.com:orderserver
sleep 3
docker run --net mynet -it shibingeorge/pygmy.com:userinterface
sleep 3
