#!/bin/bash

SW_NUM=$1

for i in $(seq 1 $SW_NUM);
do
	echo "sw_$i -> $(sudo docker exec -u root sw_$i date +"%T.%N")" &
done	
