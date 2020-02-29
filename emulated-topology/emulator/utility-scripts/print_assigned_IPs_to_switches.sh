#!/bin/bash

SW_NUM=$1

for i in $(seq 1 $SW_NUM);
do
	echo "sw_$i -> $(sudo docker exec -u root sw_$i ip a | grep br100 | awk 'NR==2{print $2}') $(sudo docker exec -u root sw_$i ip a | grep br100 -A 1 |  awk 'NR==2{print $2}')"
done	
