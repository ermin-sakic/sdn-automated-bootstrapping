#!/bin/bash 

######################################################################
#       Filename: arp_broadcast_manager.sh                           #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Jun 05, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: arp_broadcast_manager.sh
#
#   Description: 
#
#   Periodically installs and deletes OF rules related to the initial
#	ARP broadcasting issues when C->C ARP requests are created and 
#	later S->C requests due to the multi-controller environment
#
######################################################################

LOG_FILE="/arp_broadcast_manager.log"

# redirect stdout and stderr to the log file
exec 1>$LOG_FILE 2>&1

echo "Initial start of the script"

while [[ true ]]; do
	echo "Rule installed"
	ovs-ofctl -O OpenFlow13 add-flow br100 priority=9,arp,arp_op=1,actions=normal
	sleep 0.2
	echo "Rule removed"
	ovs-ofctl -O OpenFlow13 --strict del-flows br100 priority=9,arp,arp_op=1
	sleep 0.1
done


