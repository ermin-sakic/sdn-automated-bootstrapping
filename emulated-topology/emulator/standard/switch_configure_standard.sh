#!/bin/bash
######################################################################
#       Filename: switch_configure_standard.sh                       #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 16, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: switch_configure_standard.sh
#
#   Description: 
#
#   This script configures switches to be ready for the standard 
#	(RSTP) bootstrapping scheme.
#
######################################################################

# Switch name (number)
sw_var=$1

container_id=`docker inspect -f '{{.Id}}' sw_$sw_var`

# Start ovs built form source (ONLY USE TO RUN YOUR OVS VERSION)
#echo "Set up openvswitch built from source"
#sudo docker exec -u root $container_id /ovs_STP_changed_run.sh

# Start network services : OVS, LLDPD, SNMPD ...
echo "Restarting openvswitch-switch"
sudo docker exec -u root $container_id service openvswitch-switch restart
echo "Starting lldpd"
sudo docker exec -u root $container_id lldpd -x & 


# Start manually swith if using your own version of OVS
#sudo docker exec -u root $container_id ./ovs_run_changed_STP.sh
# Add bridge 100 set to standalone
echo "Adding OF bridge" 
sudo docker exec -u root $container_id ovs-vsctl add-br br100
echo "Setting OF bridge to standalone mode"
sudo docker exec -u root $container_id ovs-vsctl set-fail-mode br100 standalone 
# Use OpenFlow 1.3
echo "Setting OF version to 1.3"
sudo docker exec -u root $container_id ovs-vsctl set bridge br100 protocols=OpenFlow13
# At the beginning use ovs hidden flow rules
echo "Keep OF hidden rules enabled"
sudo docker exec -u root $container_id ovs-vsctl set bridge br100 other-config:disable-in-band=false  
#sudo docker exec -u root $container_id ovs-vsctl set bridge br100 other-config:disable-in-band=true  

# Enable STP initially to block broadcast storms
echo "Enable RSTP/STP"
sudo docker exec -u root $container_id ovs-vsctl set Bridge br100 rstp_enable=true
#sudo docker exec -u root $container_id ovs-vsctl set Bridge br100 rstp_enable=false
#sudo docker exec -u root $container_id ovs-vsctl set Bridge br100 stp_enable=true
#other_config:stp_enable=true

# Add virtual interfaces to OVS bridges
for intname in $(sudo docker exec -u root $container_id ls /sys/class/net); do
   
	case $intname in *veth*)
		intnameclean=$(echo $intname|tr -d '\r')
		echo "Virtual interface $intname added to br100"
		sudo docker exec -u root $container_id ovs-vsctl add-port br100 $intnameclean
	
		# Create 8 queues per port, each limited to 1Gbps throughput
		sudo docker exec -u root $container_id ovs-vsctl set Port $intnameclean qos=@newq -- --id=@newq create qos type=linux-htb other-config:max-rate=100000000 queues:0=@q0 queues:1=@q1 queues:2=@q2 queues:3=@q3 queues:4=@q4 queues:5=@q5 queues:6=@q6 queues:7=@q7 -- --id=@q0 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=0 -- --id=@q2 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=2 -- --id=@q7 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=7 -- --id=@q1 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=1 -- --id=@q3 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=3 -- --id=@q4 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=4  -- --id=@q5 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=5 -- --id=@q6 create queue other-config:min-rate=0 other-config:max-rate=100000000 other-config:priority=6 
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
sudo docker cp ./emulator/common/measure-standard.sh $container_id:/
sudo docker exec -u root -d $container_id /measure-standard.sh

