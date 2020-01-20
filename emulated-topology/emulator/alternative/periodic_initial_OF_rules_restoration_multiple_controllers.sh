#!/bin/bash
######################################################################
#       Filename: periodic_initial_OF_rules_restoration.sh           #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Mai 03, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: periodic_initial_OF_rules_restoration.sh
#
#   Description: 
#
#   For some reason when in-band pre-disabled switches flush their 
#	pre-installed table and for that reason they hinder the bootstrapping
#
#	Therefore we install the rules in a periodic fashion in order to
#	prevent any unexpected connection breaks prior a full bootstrapped
#	state.
#	
######################################################################


LOG_FILE="/periodic_initial_OF_rules_restoration_multiple_controllers.log"

# redirect stdout and stderr to the log file
exec 1>$LOG_FILE 2>&1


while true; do
	 echo "Woke up, do it again."
	 ovs-ofctl add-flows br100 /initial_OF_rules_multiple_controllers_setup --protocol=OpenFlow13
	 sleep 1.5;
done
