#!/bin/bash

#docker build uiserverdocker -t uiserver
#docker build orderserverdocker -t orderserver
#docker build catalogserverdocker -t catalogserver

# pull the docker images
docker pull shibingeorge/pygmy.com:orderserver
docker pull shibingeorge/pygmy.com:catalogserver
docker pull shibingeorge/pygmy.com:uiserver

docker network create --subnet=172.18.0.0/16 mynet123

UI_IP="172.18.0.22"
CATALOG_IP1="172.18.0.23"
CATALOG_IP2="172.18.0.24"
ORDER_IP1="172.18.0.25"
ORDER_IP2="172.18.0.26"

docker run --net mynet123 --ip $UI_IP -d shibingeorge/pygmy.com:uiserver
docker run --net mynet123 --ip $CATALOG_IP1 -d shibingeorge/pygmy.com:catalogserver
docker run --net mynet123 --ip $CATALOG_IP2 -d shibingeorge/pygmy.com:catalogserver
docker run --net mynet123 --ip $ORDER_IP1 -d shibingeorge/pygmy.com:orderserver
docker run --net mynet123 --ip $ORDER_IP2 -d shibingeorge/pygmy.com:orderserver