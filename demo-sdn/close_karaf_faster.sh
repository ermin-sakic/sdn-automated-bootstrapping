#!/bin/bash

# Use Ctrl+Z to kill ODL Karaf container
# then run this program to really kill
# the process
echo -e "\nProcesses killed:\n"
ps -aux | grep  "karaf" | grep -v close_karaf_faster | grep -v grep
KARAF_PIDS=$(ps -aux | grep  "karaf" | grep -v close_karaf_faster | grep -v grep | awk '{print $2}')
if [ "$KARAF_PIDS" ]; then
	echo -e "Killing \n $KARAF_PIDS"
	echo $KARAF_PIDS | xargs kill -9
else
	echo "No Karaf processes running!"
fi
#ps -aux | grep karaf | awk '(NR>=3 && N<=4){print $2}' 
#ps -aux | grep karaf | awk '(NR>=3 && N<=4){print $2}' | xargs kill -9
