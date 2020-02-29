#!/bin/bash

######################################################################
#       Filename: attach_controllers_local.sh                        #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 16, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: attach_controllers_local.sh
#
#   Description: 
#
#   Connects each controller to the desired switches via the helping
#   script connect_SDN_controller_local.sh
#
######################################################################

# Since we are sourcing the script this variables are not necessary 
# but for better readability it is nice to have them here
CON_NUM=$1
CON_POSITION=$2
CON_IP_START=$3

echo "Attaching SDN controllers LOCAL to switches: ${CON_POSITION[@]}"

for (( i = 0; i < $CON_NUM; i++ )); do
	echo "Attaching SDNC-$((i+1)) to sw_${CON_POSITION[$i]}"
	./emulator/local/connect_SDN_controller_local.sh "sw_${CON_POSITION[$i]}" $((i+1)) $CON_IP_START
done

echo "Controllers attached to the emulator"


