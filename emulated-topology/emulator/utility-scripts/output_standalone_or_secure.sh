#!/bin/bash

for i in $(seq 1 $1); 
do
	echo "Fail mode from sw_$i extracted:"
	./doc_exec.sh sw_$i "ovs-vsctl show" | grep fail_mode 
done
