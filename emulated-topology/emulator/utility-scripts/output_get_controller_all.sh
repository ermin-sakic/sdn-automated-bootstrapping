#!/bin/bash

SW_NUM=$1
for (( i=1; i<=$SW_NUM; i++)); 
do
	echo "sw_$i -> $(./doc_exec.sh sw_$i "ovs-vsctl get-controller br100")" 
done
