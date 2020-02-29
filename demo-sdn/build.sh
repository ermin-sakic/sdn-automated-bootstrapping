#!/bin/bash
sudo mvn clean
mvn clean install -DskipTests -Dcheckstyle.skip=true
