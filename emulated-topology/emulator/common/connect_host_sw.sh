#!/bin/bash

sw_name=$1
veth_sw=$2
veth_host=$3

# Create the virtual interfaces and veth tubes (links)
sudo ip link add $veth_sw type veth peer name $veth_host

# Create netns symbolic link for sw1 manipulation using ip link
container_id_sw=`docker inspect -f '{{.Id}}' $sw_name`
pid_sw=`docker inspect -f '{{.State.Pid}}' $container_id_sw`
sudo mkdir -p /var/run/netns/

echo $container_id_sw
echo $pid_sw

if [ ! -f /var/run/netns/$container_id_sw ]; then
 sudo ln -s /proc/$pid_sw/ns/net /var/run/netns/$container_id_sw
fi

sudo ip link set $veth_sw netns $container_id_sw
sudo ifconfig $veth_host 10.10.0.23

# SW - Bring the interfaces up and configure an IP address for L3 connectivity (testing)
sudo docker exec -u root $container_id_sw ifconfig $veth_sw up
#sudo docker exec -u root $container_id_sw1 ifconfig $veth_sw1 10.0.0.${veth_sw1//[A-Z]/}
