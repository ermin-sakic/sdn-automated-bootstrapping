#!/bin/bash

echo "Cleaning old logs!"
rm -r flows-from-switches-sorted
echo "Creating new log folder!"
mkdir flows-from-switches-sorted
for i in $(seq 1 $1); 
do
	./doc_exec.sh sw_$i "ovs-appctl bridge/dump-flows br100" | awk -F "=|," '!/table_id/{print $8, $0}' | sort -nr -k1 | awk '{$1 = ""; print $0}' >> flows-from-switches-sorted/sw$i.log
	echo "Flow rules from sw_$i extracted."
done


