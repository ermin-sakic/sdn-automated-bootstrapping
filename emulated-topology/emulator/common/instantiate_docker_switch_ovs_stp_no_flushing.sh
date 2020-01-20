#!/bin/bash
######################################################################
#       Filename: instantiate_docker_switch_ovs_stp_no_flushing.sh   #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Aug 22, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: instantiate_docker_switch_ovs_stp_no_flushing.sh
#
#   Description: 
#
#   Instantiates docker switch with OVS built from source with changed
#   STP beahavior

######################################################################

######### VARIABLEs ############

SWITCH_NUM="$1"

################################

# Build images if executing first time
sudo docker build -t switch_ovs_changed_stp_basis -f ./emulator/docker-files/SwitchDockerFileSTPFlushingDisabledInOVSBasis ./emulator/common
sudo docker build -t switch_ovs_changed_stp -f ./emulator/docker-files/SwitchDockerFileSTPFlushingDisabledInOVS ./emulator/common


# Start switch containers with static persistent mac-addresses (required for ODL's SNMP wiring based on a static file) 
# - if OpenFlow assumed, no SNMP or static MAC setting for management interface required
switch_name=sw_$SWITCH_NUM
switch_port=$((1660+$SWITCH_NUM))
hex_switch_mac_id=`printf "%x\n" $((SWITCH_NUM))` 
echo "Instantiating basic docker container for sw_$SWITCH_NUM."
echo $hex_switch_mac_id
hex2dec=$((16#$hex_switch_mac_id))
if [[ $hex2dec -gt 15 ]]; then	
 	docker run --network none --mac-address="02:42:ac:11:00:$hex_switch_mac_id" -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix --name $switch_name --privileged -d switch_ovs_changed_stp tail -f /dev/null # bind ssh port to a host port like this -p $switch_port:1161g
else
	docker run --network none --mac-address="02:42:ac:11:00:0$hex_switch_mac_id" -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix --name $switch_name --privileged -d switch_ovs_changed_stp tail -f /dev/null # bind ssh port to a host port like this -p $switch_port:1161g
fi

