#!/bin/bash

UI_IP="128.119.243.168"

curl -m 5 -s -X POST http://$UI_IP:35650/invalidate/rpcdummies > /dev/null
curl -m 5 -s -X POST http://$UI_IP:35650/invalidate/impstudent > /dev/null
curl -m 5 -s -X POST http://$UI_IP:35650/invalidate/67720min > /dev/null
curl -m 5 -s -X POST http://$UI_IP:35650/invalidate/xenart177 > /dev/null
curl -m 5 -s -X POST http://$UI_IP:35650/invalidate/project3 > /dev/null
curl -m 5 -s -X POST http://$UI_IP:35650/invalidate/pioneer > /dev/null
curl -m 5 -s -X POST http://$UI_IP:35650/invalidate/whytheory > /dev/null
