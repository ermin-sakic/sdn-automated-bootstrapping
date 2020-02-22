#!/bin/bash
######################################################################
#       Filename: restore_initial_OF_rules.sh                        #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 25, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: restore_initial_OF_rules.sh
#
#   Description: 
#
#   For some reason the secure-mode configured ovs-switches flush a flow table,
#   i.e. preinstalled OF rules, when they establish a connection to the 
#   controller for the first time. This behaviour is unwanted and leads
#   to the problems of broken OF connections.
#
#   The concrete reason could not be found. Ovs log files do not provide any 
#   valuable information regarding this problem.
#   The OF traffic, analyzed via Wireshark, does not show that ODL sends 
#   a flow mod message to do this either. Thus, this script is deployed on 
#   switches in order to track when a switch establishes a connection to the 
#   controller and then it restores deleted initial rules.
#	
######################################################################

LOG_FILE="/restore_initial_OF_rules.log"

# redirect stdout and stderr to the log file
exec 1>$LOG_FILE 2>&1

CONDITION=$(ovs-vsctl show | grep "is_connected: true")

while [[ -z "$CONDITION" ]]; do
	echo "Switch not connected to the controller." 
	sleep .2
	CONDITION=$(ovs-vsctl show | grep "is_connected: true")
done

echo "Switch is connected to the controller."
echo "Restoring initial OF rules..."
for (( i = 0; i < 5; i++ )); do
	 ovs-ofctl add-flows br100 /initial_OF_rules_one_controller_setup --protocol=OpenFlow13
	 sleep .5
	 echo "Attempt number $i"
done
echo "Restoring done!"


