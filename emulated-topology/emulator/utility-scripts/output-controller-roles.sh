#!/bin/bash

SW_NUM=$1
for (( i=1; i<=$SW_NUM; i++)); do
	res=$(./doc_exec.sh sw_$i "ovs-vsctl list Controller")
	# double quotes preserve formating, e.g. new lines
	#echo "$res"
	targets=($(echo "$res" | grep "target" | awk -F": " '{print $2}'))
	roles=($(echo "$res" | grep "role" | awk -F": " '{print $2}'))
	echo "Switch: sw_$i"
	for (( j = 0; j < ${#targets[@]}; j++ )); do
		echo "${targets[$j]}"
		echo "${roles[$j]}"
	done
done
