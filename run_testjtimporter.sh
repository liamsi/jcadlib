#!/bin/bash

ROOTDIR=`dirname "${0}"`

export CLASSPATH=${ROOTDIR}/dist/jcadlib-all.jar
echo CLASSPATH = $CLASSPATH

java -Djava.awt.headless=true TestJTImporter "$@"
