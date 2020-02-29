#!/bin/bash

SW_NUM=$1

for i in $(seq 1 $SW_NUM);
do
	echo "sw_$i MAC table ->"
   	echo "$(sudo docker exec -u root sw_$i ovs-appctl fdb/show br100)"
	echo ""
done	
