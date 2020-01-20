#!/bin/bash
######################################################################
#       Filename: setup_logging.sh                                   #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 25, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: setup_logging.sh
#
#   Description: 
#
#   Here you can set up logging levels for different  ovs components
#   and for different logging sources
#
######################################################################

SWITCH_ID=$1

OVS_COMPONENTS=(dpctl dpif in_band lldp lldpd lldpd_structs odp_util vswitchd)
OF_STUFF=$(sudo docker exec -u root $SWITCH_ID ovs-appctl vlog/list | awk '/ofp/ {print $1}') 

LOGGING_LEVEL="dbg"

for i in ${OVS_COMPONENTS[*]}; do
	echo ""
	printf "Changing %s logging level of syslog to %s \n" $i $LOGGING_LEVEL
	sudo docker exec -u root $SWITCH_ID ovs-appctl vlog/set $i:syslog:$LOGGING_LEVEL
	printf "Changing %s logging level of file to %s \n" $i $LOGGING_LEVEL
	sudo docker exec -u root $SWITCH_ID ovs-appctl vlog/set $i:file:$LOGGING_LEVEL
done
for i in ${OF_STUFF[*]}; do
	echo ""
	printf "Changing %s logging level of syslog to %s \n" $i $LOGGING_LEVEL
	sudo docker exec -u root $SWITCH_ID ovs-appctl vlog/set $i:syslog:$LOGGING_LEVEL
	printf "Changing %s logging level of file to %s \n" $i $LOGGING_LEVEL
    sudo docker exec -u root $SWITCH_ID ovs-appctl vlog/set $i:file:$LOGGING_LEVEL
done
