#!/bin/bash

# kill all running catalog instances
ps -ef | grep "pygmy\.com\.order\." | while read -r line ; do
    pid=$( echo $line | cut -d " " -f 2 )
    kill -9 $pid 2>/dev/null
done
