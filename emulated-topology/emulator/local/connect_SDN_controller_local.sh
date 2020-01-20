#!/bin/bash

######################################################################
#       Filename: connect_SDN_controller_local.sh                    #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 16, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: connect_SDN_controller_local.sh
#
#   Description: 
#
#   The script creates a new network namespace (ns) for a controller,
#	creates a new veth pair in the new ns, and connects the desired
#	switch with the provided SDN controller.
#
######################################################################

# switch name to which the ODL controller is going to be connected
sw_name=$1
controller_num=$2
controller_ip=$3

echo "Connecting ODL Controller SDNC-$controller_num with switch  $sw_name"
# Checks if the controller network namespace exists
if ip netns list | grep -q "ODL-Controller-SDNC-$controller_num"; then
	echo "Network namespace ODL-Controller-SDNC-$controller_num already exists!"
	#sudo ip netns delete "ODL-Controller-SDNC-$controller_num"	
	#echo "ODL-Controller-SDNC-$controller_num netns deleted!"

else
	echo "Creates a new network namespace ODL-Controller-SDNC-$controller_num"
	# Creates a new netns for the SDN controller
	sudo ip netns add "ODL-Controller-SDNC-$controller_num"
fi

# Creates a new veth pair
sudo ip link add "veth-SDNC-$controller_num-c" type veth peer name "veth-SDNC-$controller_num-s"
# Puts one veth to the ODL controller netns
sudo ip link set "veth-SDNC-$controller_num-c" netns "ODL-Controller-SDNC-$controller_num"

# Puts the other veth to the switch netns
# Creates netns symbolic link for sw manipulation using ip link
container_id_sw=`docker inspect -f '{{.Id}}' $sw_name`
pid_sw=`docker inspect -f '{{.State.Pid}}' $container_id_sw`
sudo mkdir -p /var/run/netns/
if [ ! -f /var/run/netns/$container_id_sw ]; then
	 sudo ln -s /proc/$pid_sw/ns/net /var/run/netns/$container_id_sw
fi
sudo ip link set "veth-SDNC-$controller_num-s" netns $container_id_sw
# Processing and preparing a controller IP address 
IP_ADDR_SDNC=$(echo "$controller_ip" | awk -v controller_num="$controller_num" -F "." '{$4 = $4 + controller_num; $4--; print $1"."$2"."$3"."$4}') 
# Assigning IP address to the controller veth interface
sudo ip netns exec "ODL-Controller-SDNC-$controller_num" ip addr add "$IP_ADDR_SDNC/24" dev "veth-SDNC-$controller_num-c"
echo "Controller IP address $IP_ADDR_SDNC assigned to veth-SDNC-$controller_num-c"
# Bringing up ODL controller interface
echo "Bringing up interface veth-SDNC-$controller_num-c in the controller ns"
sudo ip netns exec "ODL-Controller-SDNC-$controller_num" ip link set dev "veth-SDNC-$controller_num-c" up
echo "Bringing up local interface of the controller ns"
sudo ip netns exec "ODL-Controller-SDNC-$controller_num" ip link set dev lo up
# Bringing up switch interface
echo "Bringing up switch interface veth-SDNC-$controller_num-s"
sudo docker exec -u root $container_id_sw ip link set "veth-SDNC-$controller_num-s" up
# Adds a new port in the specific bridge
sudo docker exec -u root $container_id_sw ovs-vsctl add-port br100 "veth-SDNC-$controller_num-s"
echo "Virtual interface veth-SDNC-$controller_num-s added to the br100"
