#!/bin/bash

echo "Cleaning old logs!"
rm -r in-band-state
echo "Creating new log folder!"
mkdir in-band-state
SW_NUM=$1
for (( i=1; i<=$SW_NUM; i++)); 
do
	./doc_exec.sh sw_$i "ovs-vsctl get Bridge br100 other-config:disable-in-band" >> in-band-state/sw$i.log
	echo "In-band state from sw_$i extracted."
	cat ./in-band-state/sw$i.log
done
