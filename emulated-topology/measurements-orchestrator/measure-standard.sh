#!/bin/bash
######################################################################
#       Filename: measure-standard.sh                                #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Mai 29, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: measure-standard.sh
#
#   Description: 
#
#   It does the measurements on the switches that are configured to be 
#   bootstrapped via the standard approach
#   
######################################################################

LOG_FILE="/measure-standard.log"
MEASUREMENT_RECORD="/measurement_record"

# redirect stdout and stderr to the log file
exec 1>$LOG_FILE 2>&1

# measurement type
echo "STANDARD" >> $MEASUREMENT_RECORD

timestamp=$(date "+%Y-%m-%d %H:%M:%S")
echo "Starting the script at $timestamp"
echo $timestamp >> $MEASUREMENT_RECORD

DHCP_PERIOD=1 # in seconds
PERIOD=0.1 # in seconds
IP_ASSIGNED=false
INITIAL_OF_RULES_INSTALLED=false
RESILIENT_OF_RULES_INSTALLED=false

while [ "$IP_ASSIGNED" = false ]; do
	echo "IP not configured for br100"
	ip="$(ip a | grep br100 -A 1 | awk '/net/ {print $2}')"	
	if [ ! -z "$ip" ]; then
		timestamp=$(date +"%T.%3N")
		echo "IP assigned -> $timestamp" >> $MEASUREMENT_RECORD
		#date +"%T.%3N" >> $MEASUREMENT_RECORD
		IP_ASSIGNED=true
	fi
	sleep $DHCP_PERIOD;
done

echo "IP assigned to br100"

echo "Waiting for the initial OF rules to be installed"
# Find out when the last rule from the InitialFlowWriter has been installed
# that is the rule for any unmatched arp traffic that has priority 5
NUMBER_OF_INITIAL_OF_RULES=1
while [[ "$INITIAL_OF_RULES_INSTALLED" = false ]]; do
	number_of_initial_rules=$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep -e priority=5 | grep arp -c)
	if [[ "$number_of_initial_rules" -eq "$NUMBER_OF_INITIAL_OF_RULES" ]]; then
		timestamp=$(date +"%T.%3N")
		echo "Initial OF installed -> $timestamp" >> $MEASUREMENT_RECORD
		INITIAL_OF_RULES_INSTALLED=true

	fi
done
echo "Initial OF rules installed"

#while [ "$RESILIENT_OF_RULES_INSTALLED" = false ]; do
#	resilience_done="$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep 201 | grep arp | grep LOCAL)"	
#	if [ ! -z "$resilience_done" ]; then
#		timestamp=$(date +"%T.%3N")
#		echo "Resilience installed -> $timestamp" >> $MEASUREMENT_RECORD
#		RESILIENT_OF_RULES_INSTALLED=true
#	fi
#	sleep $PERIOD;
#done

echo "Resilience is being examined"
controllersip=($(ovs-vsctl show | awk '/Controller/ {print $2}' | awk -F":" '{print $2}'))
echo "Controllers provided to switch: $controllersip"
switchip=$(ip a | grep br100 -A 1 | awk '/inet/ {print $2}' | awk -F"/" '{print $1}')
echo "Switch IP: $switchip"
# Create array of size #controllers; initialize it with false
controllersCheck=()
for (( i = 0; i < ${#controllersip[@]}; i++ )); do
	controllersCheck+=(false)
done
while [ "$RESILIENT_OF_RULES_INSTALLED" = false ]; do
	# Check rules for each C-S pair; based on OF LOCAL rule since it is installed as the last one
	for (( i = 0; i < ${#controllersip[@]}; i++ )); do
		if [[ ${controllersCheck[$i]} == false ]]; then
			#resilience_done=$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep -i priority=200,tcp,nw_src=${controllersip[$i]},nw_dst=$switchip,tp_src=6633)
			#resilience_done=$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep -i priority=201,tcp,nw_dst=$switchip,tp_src=6633)
			resilience_done=$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep -i priority=99,arp)
			echo "Resilience done: $resilience_done"
			if [ ! -z "$resilience_done" ]; then
				timestamp=$(date +"%T.%3N")
				echo "Resilience installed for ${controllersip[$i]} -> $timestamp" >> $MEASUREMENT_RECORD
				controllersCheck[$i]=true
			fi
		fi
		# check if all c-s pairs have resilient rules installed
		# simply check if all elements of controllersCheck are true
		if [[ "${controllersCheck[@]}" =~ ^(true )*true$ ]]; then
			RESILIENT_OF_RULES_INSTALLED=true
		fi
	done
	sleep $PERIOD;
done
echo "Resilience done"

echo "Exiting the script"
