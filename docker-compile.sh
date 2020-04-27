#!/bin/bash

CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')
rm -rf bin-docker/ && mkdir bin-docker/
javac -cp $CP -d bin-docker/ $(find . -name "*.java")
