#!/bin/bash

sw1_name=$1
sw2_name=$2
veth_sw1=$3
veth_sw2=$4

# Create the virtual interfaces and veth tubes (links)
sudo ip link add $veth_sw1 type veth peer name $veth_sw2

# Create netns symbolic link for sw1 manipulation using ip link
container_id_sw1=`docker inspect -f '{{.Id}}' $sw1_name`
pid_sw1=`docker inspect -f '{{.State.Pid}}' $container_id_sw1`
sudo mkdir -p /var/run/netns/

echo $container_id_sw1
echo $pid_sw1

if [ ! -f /var/run/netns/$container_id_sw1 ]; then
 sudo ln -s /proc/$pid_sw1/ns/net /var/run/netns/$container_id_sw1
fi

if [ ! -f /var/run/netns/$sw1_name ]; then
 # more user friendly netns name for wireshark traffic inspection on switches
 sudo ln -s /proc/$pid_sw1/ns/net /var/run/netns/$sw1_name
fi

# Create netns symbolic link for sw2 manipulation using ip link
container_id_sw2=`docker inspect -f '{{.Id}}' $sw2_name`
pid_sw2=`docker inspect -f '{{.State.Pid}}' $container_id_sw2`
sudo mkdir -p /var/run/netns/

if [ ! -f /var/run/netns/$container_id_sw2 ]; then
 sudo ln -s /proc/$pid_sw2/ns/net /var/run/netns/$container_id_sw2
fi

if [ ! -f /var/run/netns/$sw2_name ]; then
 # more user friendly netns name for wireshark traffic inspection on switches
 sudo ln -s /proc/$pid_sw2/ns/net /var/run/netns/$sw2_name
fi

sudo ip link set $veth_sw1 netns $container_id_sw1
sudo ip link set $veth_sw2 netns $container_id_sw2

# SW1 - Bring the interfaces up and configure an IP address for L3 connectivity (testing)
sudo docker exec -u root $container_id_sw1 ifconfig $veth_sw1 up
#sudo docker exec -u root $container_id_sw1 ifconfig $veth_sw1 10.0.0.${veth_sw1//[A-Z]/}

# SW2 - Bring the interfaces up and configure an IP address for L3 connectivity (testing)
sudo docker exec -u root $container_id_sw2 ifconfig $veth_sw2 up

if [[ $sw1_name == *"ho"* ]]; 
then 
	echo "Host identified, assigning an IP to interface"
	sudo docker exec -u root $container_id_sw1 ifconfig $veth_sw1 10.0.0.${veth_sw1//[A-Z]/}
fi
