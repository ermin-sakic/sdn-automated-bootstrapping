#!/bin/bash

# Executes script close_karaf_faster.sh remotely on selected controllers

SCRIPT_PATH=/home/ermin/Desktop/demo-sdn-alternative
PASSWORD="openflow"
for controller in "$@"
do
	echo "Killing Karaf on $controller"
	ssh ermin@$controller "echo $PASSWORD | sudo -S $SCRIPT_PATH/close_karaf_faster.sh" 
done
