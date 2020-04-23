#!/bin/bash

CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')
rm -rf bin/ && mkdir bin/
javac -cp $CP -d bin/ $(find . -name "*.java")
