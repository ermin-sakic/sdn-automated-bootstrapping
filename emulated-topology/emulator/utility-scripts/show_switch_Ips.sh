#!/bin/bash

######################################################################
#       Filename: show_switch_Ips.sh                                 #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 20, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: show_switch_Ips.sh
#
#   Description: 
#
#   Prints out assigned switch IP addresses
#
######################################################################

SW_NO=$1

for (( i = 1; i < $SW_NO+1; i++ )); do
	echo "sw_$i"
	sudo docker exec sw_$i ip a | grep -A 2 br100 | awk '/inet/ {print $2}'
done
