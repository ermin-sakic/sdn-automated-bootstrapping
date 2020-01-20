#!/bin/bash

######################################################################
#       Filename: attach_controllers_remote.sh                       #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 16, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: attach_controllers_remote.sh
#
#   Description: 
#
#   Connects SDN controllers' VMs to the emulator VM 
#
#	Currently supports max 3 controllers.
#	TODO: extend to arbitrary number of controllers 
#	(see ESXI setup) via SSH
#
######################################################################

VM_INTERFACES=(ens160 ens224 ens256) # for more controllers extend this array
CON_NUM=$1
CON_POSITION=$2

echo "Attaching SDN controllers REMOTE to switches: ${CON_POSITION[@]}"
for (( i = 0; i < $CON_NUM; i++ )); do
	echo "Attaching emulator interface ${VM_INTERFACES[$i]} (SDNC-$((i+1))) to sw_${CON_POSITION[$i]}"
	./emulator/remote/add_phys_interface.sh sw_${CON_POSITION[$i]} ${VM_INTERFACES[$i]}	
done

echo ""
echo "Controllers attached to the emulator"
