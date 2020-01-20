#!/bin/bash

sw_name=$1
phys_eth=$2

# Create netns symbolic link for sw manipulation using ip link
container_id_sw=`docker inspect -f '{{.Id}}' $sw_name`
pid_sw=`docker inspect -f '{{.State.Pid}}' $container_id_sw`
sudo mkdir -p /var/run/netns/

echo "Container switch ID:"
echo $container_id_sw
echo "Switch PID:"
echo $pid_sw

if [ ! -f /var/run/netns/$container_id_sw ]; then
 sudo ln -s /proc/$pid_sw/ns/net /var/run/netns/$container_id_sw
fi

# Extracting br100 MAC address
br100_MAC=$(sudo docker exec -u root $container_id_sw ip a | grep -A 1 br100 | awk '/link/ {print $2}')

# Moving phys_eth to the switch ns
sudo ip link set $phys_eth netns $container_id_sw

# SW - Bring the interfaces up and configure an IP address for L3 connectivity (testing)
sudo docker exec -u root $container_id_sw ifconfig $phys_eth up

# SW - Add the physical interface to OVS bridge
sudo docker exec -u root $container_id_sw ovs-vsctl add-port br100 $phys_eth

# Restore br100 MAC (for some reason it changes to the MAC of phys_eth)
sudo docker exec -u root $container_id_sw ovs-vsctl set bridge br100 other-config:hwaddr=\"$br100_MAC\"
