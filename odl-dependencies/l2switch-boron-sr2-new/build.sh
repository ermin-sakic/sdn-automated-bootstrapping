#!/bin/bash

mvn clean install -DskipTests -Dcheckstyle.skip=true -Dmaven.javadoc.skip=true verify
