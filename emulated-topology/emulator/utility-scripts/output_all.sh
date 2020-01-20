#!/bin/bash

echo "Cleaning old logs!"
rm -r flows-from-switches
echo "Creating new log folder!"
mkdir flows-from-switches
for i in $(seq 1 $1); 
do
	./doc_exec.sh sw_$i "ovs-appctl bridge/dump-flows br100" >> flows-from-switches/sw$i.log
	echo "Flow rules from sw_$i extracted."
done
