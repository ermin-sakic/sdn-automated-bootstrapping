#!/bin/bash

for i in $(seq 1 $1); 
do
	flow_table_size=$(cat ./flows-from-switches-sorted/sw$i.log | grep -c duration)
	echo "sw_$i -> $flow_table_size"
done


