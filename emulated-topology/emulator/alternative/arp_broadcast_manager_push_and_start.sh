#!/bin/bash

######################################################################
#       Filename: arp_broadcast_manager_push_and_start.sh            #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Jun 05, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: arp_broadcast_manager_push_and_start.sh
#
#   Description: 
#
#   Starts a script in a switch that periodically installs and deletes
#	OF ARP rules related to the initial broadcasting problems
#
######################################################################

# Copy script to the docker container
sudo docker cp ./arp_broadcast_manager.sh sw_$1:/
# Start the copied script
sudo docker exec -u root -d sw_$1 /arp_broadcast_manager.sh


