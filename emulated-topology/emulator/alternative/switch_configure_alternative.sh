#!/bin/bash
######################################################################
#       Filename: switch_configure_alternative.sh                    #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 17, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: switch_configure_alternative.sh
#
#   Description: 
#
#   This script configures switches to be ready for the alternative (no RSTP) bootstrapping scheme. 
######################################################################

# Switch name (number)
sw_var=$1

# Akka gossip tcp port
akka_gossip_tcp_port=$3

# Create a transparent network namespace entry in /var/run/netns for allowing configuration using "ip link"

# Start network services : OVS, LLDPD, SNMPD ...
container_id=`docker inspect -f '{{.Id}}' sw_$sw_var`
#echo "Restarting openvswitch-switch"
sudo docker exec -u root $container_id service openvswitch-switch restart
#echo "Starting lldpd"
#sudo docker exec -u root $container_id lldpd -x & 

# Initial setup of the OVS
#sudo docker exec -u root $container_id ./ovs_run.sh
## Add bridge 100 set to secure
echo "Adding OF bridge" 
sudo docker exec -u root $container_id ovs-vsctl add-br br100
# Secure fail mode configured
echo "Setting OF bridge to secure mode"
sudo docker exec -u root $container_id ovs-vsctl set-fail-mode br100 secure 
# Use OpenFlow 1.3
echo "Setting OF version to 1.3"
sudo docker exec -u root $container_id ovs-vsctl set bridge br100 protocols=OpenFlow13
# At the beginning use ovs hidden flow rules
#echo "Keep OF hidden rules enabled"
echo "OF hidden rules disabled"
sudo docker exec -u root $container_id ovs-vsctl set bridge br100 other-config:disable-in-band=true 

# Disable STP initially 
echo "Disable RSTP"
sudo docker exec -u root $container_id ovs-vsctl set Bridge br100 rstp_enable=false
#sudo docker exec -u root $container_id ovs-vsctl set Bridge br100 rstp_enable=true
#other_config:stp_enable=true

# Add virtual interfaces to OVS bridges
for intname in $(sudo docker exec -u root $container_id ls /sys/class/net); do
   
	case $intname in *veth*)
		intnameclean=$(echo $intname|tr -d '\r')
		echo "Virtual interface $intname added to br100"
		#port_mac=$(sudo docker exec "$container_id" ip a | grep -A 1 "$intnameclean" | awk '/ether/ {print $2}')
		#sudo docker exec -u root $container_id ovs-vsctl add-port br100 $intnameclean -- set Interface $intnameclean type=dpdk options:dpdk-devargs="class=eth,mac=$port_mac"
		#sudo docker exec -u root $container_id ovs-vsctl add-port br100 $intnameclean -- set Interface $intnameclean type=dpdk options:dpdk-devargs=eth_afpacketl$intnameclean
		sudo docker exec -u root $container_id ovs-vsctl add-port br100 $intnameclean 
	
		# Create 2 queues per port
		sudo docker exec -u root $container_id ovs-vsctl set Port $intnameclean qos=@newq -- --id=@newq create qos type=linux-htb other-config:max-rate=1000000000 queues:0=@q0  queues:1=@q1 queues:2=@q2  -- --id=@q0 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=0  -- --id=@q1 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=1 -- --id=@q2 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=2 

		# Configure tc policer for arp traffic
		# Add ingress queue discipline
		sudo docker exec -u root $container_id tc qdisc add dev $intnameclean handle ffff: ingress
		# Set tc policer to allow only ARP request per second on each ingress qdisc
		# Mimicing meters
		#sudo docker exec -u root $container_id tc filter add dev $intnameclean parent ffff: protocol arp u32 match u32 0x00000001 0x0000ffff at 4 police rate 0.5kbit burst 65b drop flowid :1
		sudo docker exec -u root $container_id tc filter add dev $intnameclean parent ffff: protocol arp u32 match u32 0x00000001 0x0000ffff at 4 police rate 1.5kbit burst 65b drop flowid :1
		# Set the same policer at each egress qdisc
		#sudo docker exec -u root $container_id tc filter add dev $intnameclean parent 1: protocol arp u32 match u32 0x00000001 0x0000ffff at 4 police rate 0.5kbit burst 65b drop flowid :1
		sudo docker exec -u root $container_id tc filter add dev $intnameclean parent 1: protocol arp u32 match u32 0x00000001 0x0000ffff at 4 police rate 1.5kbit burst 65b drop flowid :1
 		# Limit ARP replies also that coul create a storm 
                 #sudo docker exec -u root $container_id tc filter add dev $intnameclean parent ffff: protocol arp u32 match u32 0x00000002 0x0000ffff at 4 police rate 0.5kbit burst 65b drop flowid :1
                 sudo docker exec -u root $container_id tc filter add dev $intnameclean parent ffff: protocol arp u32 match u32 0x00000002 0x0000ffff at 4 police rate 1.5kbit burst 65b drop flowid :1
                 #sudo docker exec -u root $container_id tc filter add dev $intnameclean parent 1: protocol arp u32 match u32 0x00000002 0x0000ffff at 4 police rate 0.5kbit burst 65b drop flowid :1
                 sudo docker exec -u root $container_id tc filter add dev $intnameclean parent 1: protocol arp u32 match u32 0x00000002 0x0000ffff at 4 police rate 1.5kbit burst 65b drop flowid :1
                 # Limit TCP SYN and SYN ACK that initially can create storm effects
		 # Processing of the gossip port
		 akka_dst_port_match_filter=$(printf "0x0000%04x" "$akka_gossip_tcp_port")
		 echo "TCP SYN filtering on akka dst port $akka_dst_port_match_filter"
		 akka_src_port_match_filter=$(printf "0x%04x0000" "$akka_gossip_tcp_port")
		 echo "TCP SYN filtering on akka src port $akka_src_port_match_filter"
                 # SYN packet matching
                 sudo docker exec -u root $container_id tc filter add dev $intnameclean parent ffff: protocol ip u32 match u32 0x45000000 0xff000000 at 0 match u32 0x00060000 0x00ff0000 at 8 match u32 $akka_dst_port_match_filter 0x0000ffff at 20 match u32 0x00020000 0x000f0000 at 32 police rate 70kbit burst 800b drop flowid :1
                 # SYN ACK matching
                 sudo docker exec -u root $container_id tc filter add dev $intnameclean parent ffff: protocol ip u32 match u32 0x45000000 0xff000000 at 0 match u32 0x00060000 0x00ff0000 at 8  match u32 $akka_src_port_match_filter 0xffff0000 at 20 match u32 0x00120000 0x00ff0000 at 32 police rate 70kbit burst 800b drop flowid :1
                 # SYN packet matching
                 sudo docker exec -u root $container_id tc filter add dev $intnameclean parent 1: protocol ip u32 match u32 0x45000000 0xff000000 at 0 match u32 0x00060000 0x00ff0000 at 8  match u32 $akka_dst_port_match_filter 0x0000ffff at 20 match u32 0x00020000 0x000f0000 at 32 police rate 70kbit burst 800b drop flowid :1
                 # SYN ACK matching
                 sudo docker exec -u root $container_id tc filter add dev $intnameclean parent 1: protocol ip u32 match u32 0x45000000 0xff000000 at 0 match u32 0x00060000 0x00ff0000 at 8 match u32 $akka_src_port_match_filter 0xffff0000 at 20 match u32 0x00120000 0x00ff0000 at 32 police rate 70kbit burst 800b drop flowid :1

	esac
done

size=${#sw_var}
hex_switch_mac_id=`printf "%x\n" $sw_var` 
 
# Give a persistent datapath name to switch
hex2dec=$((16#$hex_switch_mac_id))
if [[ $hex2dec -gt 15 ]]; then 
	sudo docker exec -u root $container_id ovs-vsctl set bridge br100 other-config:datapath-id=00000000000000$hex_switch_mac_id
else
	sudo docker exec -u root $container_id ovs-vsctl set bridge br100 other-config:datapath-id=000000000000000$hex_switch_mac_id
fi

CON_NUM=$2
# PREINSTALLING INITIAL OF RULES

# Extracting br100 MAC address
br100_MAC=$(sudo docker exec -u root $container_id ip a | grep -A 1 br100 | awk '/link/ {print $2}') 
printf "br100 MAC address: %s\n" "$br100_MAC"
echo "Initial OF rules are being installed"
if [[ "$2" > 1 ]]; then
	# Embedd br100 MAC in the template file (does not modify the original)
	sed "s/BR100_MAC/$br100_MAC/g" ./emulator/alternative/initial_OF_rules_multiple_controllers_setup.template > ./emulator/alternative/initial_OF_rules_multiple_controllers_setup
	# Copy initial rules in the docker container
	sudo docker cp ./emulator/alternative/initial_OF_rules_multiple_controllers_setup $container_id:/
	# Create meters for the initial ARP C->C traffic
	#sudo docker exec -u root $container_id ovs-ofctl add-meter br100 meter=1,pktps,stats,bands=type=drop,rate=1 --protocol=OpenFlow13 
	#sudo docker exec -u root $container_id ovs-ofctl add-meter br100 meter=2,pktps,stats,bands=type=drop,rate=1 --protocol=OpenFlow13
	# Add initial flow rules
	sudo docker exec -u root $container_id ovs-ofctl add-flows br100 /initial_OF_rules_multiple_controllers_setup --protocol=OpenFlow13 
	# Copy periodic script in the docker container
	sudo docker cp ./emulator/alternative/periodic_initial_OF_rules_restoration_multiple_controllers.sh $container_id:/
	# Add initial flow rules periodically
	sudo docker exec -u root -d $container_id /periodic_initial_OF_rules_restoration_multiple_controllers.sh 
else
	# Embedd br100 MAC in the template file (does not modify the original)
	sed "s/BR100_MAC/$br100_MAC/g" ./emulator/alternative/initial_OF_rules_one_controller_setup.template > ./emulator/alternative/initial_OF_rules_one_controller_setup
	# Copy initial rules in the docker container
	sudo docker cp ./emulator/alternative/initial_OF_rules_one_controller_setup $container_id:/
	# Add initial flow rules
	#sudo docker exec -u root $container_id ovs-ofctl add-meter br100 meter=1,pktps,stats,bands=type=drop,rate=1 --protocol=OpenFlow13 
	sudo docker exec -u root $container_id ovs-ofctl add-flows br100 /initial_OF_rules_one_controller_setup --protocol=OpenFlow13 
	# Copy periodic script in the docker container
	sudo docker cp ./emulator/alternative/periodic_initial_OF_rules_restoration_one_controller.sh $container_id:/
	# Add initial flow rules periodically
	sudo docker exec -u root -d $container_id /periodic_initial_OF_rules_restoration_one_controller.sh  
fi

# Setting up restore initial rules script
echo "Restore script setup"
sudo docker cp ./emulator/alternative/restore_initial_OF_rules.sh $container_id:/
sudo docker exec -u root -d $container_id /restore_initial_OF_rules.sh

# If needed, set controller IP for the bridge
# sudo docker exec -u root $container_id ifconfig br100 10.10.0.$(($sw_var+1))
# sudo docker exec -u root $container_id ovs-vsctl set-controller br100 tcp:10.10.0.101:6633 tcp:10.10.0.102:6633 tcp:10.10.0.103:6633

# Initialize some host services
#for ho_var in $(seq 1 $no_ho)
#do
#  echo "Host initiating Host services"
#  container_id=`docker inspect -f '{{.Id}}' ho_$ho_var`
#  sudo docker exec -u root $container_id lldpd -x &
#  #sudo docker exec -u root $container_id service snmpd restart
#done

# Announce container IDs
container_id=`docker inspect -f '{{.Id}}' sw_$sw_var`
echo "Switch $sw_var has Docker ID: $container_id"
# for one controller unnecessary
#sudo docker exec -u root $container_id /periodicExecScript.sh 10.10.0.101 10.10.0.102 10.10.0.103

#for ho_var in $(seq 1 $no_ho)
#do
# container_id=`docker inspect -f '{{.Id}}' ho_$ho_var`
# echo "Host $ho_var has Docker ID: $container_id"
#done

# Starting dhclient on every instance
container_id=`docker inspect -f '{{.Id}}' sw_$sw_var`
# for some reason dhclient cannot be executed from /sbin
sudo docker exec -u root $container_id cp /sbin/dhclient /usr/sbin
sudo docker exec -u root $container_id /usr/sbin/dhclient br100 &
echo "dhclient started on br100"
#sudo docker exec -u root $container_id dhclient br100 &
sudo docker exec -u root $container_id service ssh restart &
echo "SSH service restarted"

# Measuring scripts
sudo docker cp ./emulator/common/measure-flow-table-size.sh $container_id:/
sudo docker cp ./emulator/common/measure-alternative.sh $container_id:/
sudo docker exec -u root -d $container_id /measure-alternative.sh

