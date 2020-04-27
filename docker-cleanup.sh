#!/bin/bash

# remove container
docker ps -a | grep "uiserver" | awk '{print $1}' | xargs docker stop
docker ps -a | grep "uiserver" | awk '{print $1}' | xargs docker rm
docker ps -a | grep "orderserver" | awk '{print $1}' | xargs docker stop
docker ps -a | grep "orderserver" | awk '{print $1}' | xargs docker rm
docker ps -a | grep "catalogserver" | awk '{print $1}' | xargs docker stop
docker ps -a | grep "catalogserver" | awk '{print $1}' | xargs docker rm
docker ps -a | grep "userinterface" | awk '{print $1}' | xargs docker stop
docker ps -a | grep "userinterface" | awk '{print $1}' | xargs docker rm

# remove images
docker images | grep "uiserver" | awk '{print $3}' | xargs docker rmi -f
docker images | grep "orderserver" | awk '{print $3}' | xargs docker rmi -f
docker images | grep "catalogserver" | awk '{print $3}' | xargs docker rmi -f
docker images | grep "userinterface" | awk '{print $3}' | xargs docker rmi -f