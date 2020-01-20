#!/bin/bash

######################################################################
#       Filename: emulate_network.sh                                 #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 13, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: emulate_network.sh
#
#   Description: 
#
#   This is an orchestration script for the VirtuWind SDN emulator.
#	Therefore this script represents an entry/starting point of the
#	network emulation.
#
#	Before running this script first configure your emulator in the
#	config file which is located in the same folder as this script.
#
#	Network emulator is started by simply executing this orchestrator
#	script:
#
#	./emulate_network.sh
#
######################################################################

# Loading configuration
source config

echo ""
echo "You configured the emulator as follows:"
echo ""
echo "################################################################"

if [[ "$TARGET" = "LOCAL" || "$TARGET" = "REMOTE" ]]; then
	echo "Emulation should be done: ${TARGET}"
else
	echo "Invalid TARGET value!!!"
	exit 1
fi

if [[ "$BOOTSTRAPPING" = "STANDARD" || "$BOOTSTRAPPING" = "ALTERNATIVE" ]]; then
	echo "Intended bootstrapping scheme: ${BOOTSTRAPPING}"
else
	echo "Invalid BOOTSTRAPPING value!!!"
	exit 1
fi

if [[ "$CON_NUM" > 0 && "$((CON_NUM%2))" = 1 ]]; then
	echo "Network should be orchestrated with ${CON_NUM} controller(s)."
else
	echo "Invalid CON_NUM value!!!"
	exit 1
fi

if [[ -f "topologies/${TOPOLOGY}.sh"  ]]; then
	echo "Emulated topology: ${TOPOLOGY}"
else
	echo "Selected topology does not exist!!!"
	exit 1
fi

# Some constraints on controller positions
TOPOLOGY_SIZE_SW=$(awk '/^SW_NUM=/ {print}' "topologies/${TOPOLOGY}.sh" | awk -F "=" 'NR==1{print $2}')
#TOPOLOGY_SIZE_SW=$(grep "SW_NUM=" "topologies/${TOPOLOGY}.sh" | awk -F "=" '/^SW_NUM=/NR==1{print $2}')
#echo "$TOPOLOGY_SIZE_SW"
#echo "${#CON_POSITION[@]}"
# Finds the largest switchindex in the controller positions
LARGEST_SW_CON_POS=$(echo "${CON_POSITION[@]}" | xargs -n1 | sort -nr | awk 'NR==1{print}')
#echo $LARGEST_SW_CON_POS
SMALLEST_SW_CON_POS=$(echo "${CON_POSITION[@]}" | xargs -n1 | sort -n | awk 'NR==1{print}')
#echo $SMALLEST_SW_CON_POS
UNIQUE_POS=$(echo "${CON_POSITION[@]}" | xargs -n1 | sort -n | wc -l)
#echo "$UNIQUE_POS"

if [[ "${#CON_POSITION[@]}" = "$CON_NUM" \
&& "$LARGEST_SW_CON_POS" -le "$TOPOLOGY_SIZE_SW" \
&& "$SMALLEST_SW_CON_POS" > 0 \
&& "$UNIQUE_POS" = "$CON_NUM" ]]; then
	echo "Position of the controllers in the ${TOPOLOGY} topology: ${CON_POSITION[*]}"
else
	echo "Invalid controller positions!!!"
	exit 1
fi

echo "################################################################"
echo ""


# Based on the config file orchestrating emulator setup
TOPOLOGY_TO_RUN="topologies/${TOPOLOGY}.sh"

# Hugepages setup for DPDK 
#source ./emulator/common/host_setup.sh

# Instantiating basic docker switch containers
if [[ "$BOOTSTRAPPING" = "STANDARD" ]]; then
	for (( i = 1; i <= $TOPOLOGY_SIZE_SW; i++ )); do
		echo "Instantiating docker container for switch sw_$i"
		source ./emulator/common/instantiate_docker_switch.sh $i
		#source ./emulator/common/instantiate_docker_switch_ovs_stp_no_flushing.sh $i
		echo ""
	done
elif [[ "$BOOTSTRAPPING" = "ALTERNATIVE" ]]; then
	for (( i = 1; i <= $TOPOLOGY_SIZE_SW; i++ )); do
		echo "Instantiating docker container for switch sw_$i"
		#source ./emulator/common/instantiate_docker_switch_dpdk.sh $i
		source ./emulator/common/instantiate_docker_switch.sh $i
		echo ""
	done
fi

# Create topology
source "./$TOPOLOGY_TO_RUN"

# Based on a configured bootstrapping scheme 
# choose a different ovs-switch config

if [[ "$BOOTSTRAPPING" = "STANDARD" ]]; then
	for (( i = 1; i <= $TOPOLOGY_SIZE_SW; i++ )); do
		echo ""
		echo "Switch sw_$i is being configured for the standard bootstrapping scheme."
		source ./emulator/standard/switch_configure_standard.sh $i
		echo "Switch sw_$i is configured for the standard bootstrapping scheme."
		echo ""
	done
elif [[ "$BOOTSTRAPPING" = "ALTERNATIVE" ]]; then
	for (( i = 1; i <= $TOPOLOGY_SIZE_SW; i++ )); do
		echo ""
		echo "Switch sw_$i is being configured for the alternative bootstrapping scheme."
		source ./emulator/alternative/switch_configure_alternative.sh $i "$CON_NUM" "$AKKA_GOSSIP_PORT"
		echo "Switch sw_$i is configured for the alternative bootstrapping scheme."
		echo ""
	done
	# Running script that controls initial ARP broadcast rules
#	cd ./emulator/alternative
#	for (( i = 1; i <= $TOPOLOGY_SIZE_SW; i++ )); do
#		echo "Starting ARP broadcast manager script on the sw_$i"
#		./arp_broadcast_manager_push_and_start.sh $i
#	done
#	cd -
fi


# Based on a configured execution destination
# connect ODL accordingly


if [[ "$TARGET" = "REMOTE" ]]; then
	echo "Controllers expected to be started REMOTE-ly on separate ESXI VMs"
	source ./emulator/remote/attach_controllers_remote.sh $CON_NUM $CON_POSITION 
	echo ""
elif [[ "$TARGET" = "LOCAL" ]]; then
	echo "Controllers expected to be started LOCAL-ly in separate network namespaces."
	source ./emulator/local/attach_controllers_local.sh $CON_NUM $CON_POSITION $CON_IP_START
	echo ""
	
fi

# TODO: Make this also configurable #
#for (( j = 1; j <= $TOPOLOGY_SIZE_SW; j++ )); do
#	echo "Configuring logging for sw_$j:"
#    ./emulator/common/setup_logging.sh sw_$j
#done
