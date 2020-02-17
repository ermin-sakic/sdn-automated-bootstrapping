#!/bin/bash
######################################################################
#       Filename: measure-alternative.sh                             #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Mai 29, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: measure-alternative.sh
#
#   Description: 
#
#   It does the measurements on the switches that are configured to be 
#   bootstrapped via the alternative approach
#
######################################################################

LOG_FILE="/measure-alternative.log"
MEASUREMENT_RECORD="/measurement_record"

# redirect stdout and stderr to the log file
exec 1>$LOG_FILE 2>&1

# measurement type
echo "ALTERNATIVE" >> $MEASUREMENT_RECORD

timestamp=$(date "+%Y-%m-%d %H:%M:%S")
echo "Starting the script at $timestamp"
echo $timestamp >> $MEASUREMENT_RECORD

DHCP_PERIOD=1 # in seconds
PERIOD=0.1 # in seconds
IP_ASSIGNED=false
INITIAL_OF_RULES_PHASEI_INSTALLED=false
INITIAL_OF_RULES_PHASEII_INSTALLED=false
RESILIENT_OF_RULES_INSTALLED=false

while [ "$IP_ASSIGNED" = false ]; do
	echo "IP not configured for br100"
	ip="$(ip a | grep br100 -A 1 | awk '/net/ {print $2}')"	
	if [ ! -z "$ip" ]; then
		timestamp=$(date +"%T.%3N")
		echo "IP assigned -> $timestamp" >> $MEASUREMENT_RECORD
		IP_ASSIGNED=true
	fi
	sleep $DHCP_PERIOD;
done

echo "IP assigned to br100"

echo "Waiting for the initial OF rules of Phase I to be installed"
# Find out when the last rule from the Phase I has been installed
# ARP Broadcasting not for LOCAL or ROOT 
NUMBER_OF_INITIAL_OF_RULES=1
while [[ "$INITIAL_OF_RULES_PHASEI_INSTALLED" = false ]]; do
	number_of_initial_rules=$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep priority=100,arp,arp_spa=10.10.0.10[0-9],arp_tpa=10.10.0.0/24 -c)
	if [[ "$number_of_initial_rules" -eq "$NUMBER_OF_INITIAL_OF_RULES" ]]; then
		timestamp=$(date +"%T.%3N")
		echo "Initial OF Phase I installed -> $timestamp" >> $MEASUREMENT_RECORD
		INITIAL_OF_RULES_PHASEI_INSTALLED=true

	fi
done
echo "Initial OF rules Phase I installed"

echo "Waiting for the initial OF rules of Phase II to be installed"
# Find out when the last rule from the Phase II has been installed
# Setting ARP flows used for controller discovery. Overwriting rules from the Phase I.
# Number of rules depends on the number of ports that are part of the tree on a switch
# but since we do not targes ms it is fine to say simply >= 1
NUMBER_OF_INITIAL_OF_RULES=1
while [[ "$INITIAL_OF_RULES_PHASEII_INSTALLED" = false ]]; do
	number_of_initial_rules=$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep priority=99,arp,in_port -c)
	if [[ "$number_of_initial_rules" -ge "$NUMBER_OF_INITIAL_OF_RULES" ]]; then
		timestamp=$(date +"%T.%3N")
		echo "Initial OF Phase II installed -> $timestamp" >> $MEASUREMENT_RECORD
		INITIAL_OF_RULES_PHASEII_INSTALLED=true

	fi
done
echo "Initial OF rules Phase II installed"

echo "Resilience is being examined"
controllersip=($(ovs-vsctl show | awk '/Controller/ {print $2}' | awk -F":" '{print $2}'))
switchip=$(ip a | grep br100 -A 1 | awk '/inet/ {print $2}' | awk -F"/" '{print $1}')
# Create array of size #controllers; initialize it with false
controllersCheck=()
for (( i = 0; i < ${#controllersip[@]}; i++ )); do
	controllersCheck+=(false)
done
while [ "$RESILIENT_OF_RULES_INSTALLED" = false ]; do
	# USE THIS FOR MULTI CONTROLLER CASE
#	# Check rules for each C-S pair; based on OF LOCAL rule since it is installed as the last one
#	for (( i = 0; i < ${#controllersip[@]}; i++ )); do
#		if [[ ${controllersCheck[$i]} == false ]]; then
			#resilience_done=$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep priority=201,tcp,nw_src=${controllersip[$i]},nw_dst=$switchip,tp_src=6633)
			resilience_done=$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep priority=201,tcp,nw_dst=$switchip,tp_src=6633)
			if [ ! -z "$resilience_done" ]; then
				timestamp=$(date +"%T.%3N")
				if [[ ! -z $(echo $resilience_done | grep -w "cookie=0xaaaaa") && ${controllersCheck[0]} == false ]]; then
	
					echo "Resilience installed for 10.10.0.10 -> $timestamp" >> $MEASUREMENT_RECORD
					controllersCheck[0]=true

				elif [[ ! -z $(echo $resilience_done | grep -w "cookie=0xbbbbb") && ${controllersCheck[1]} == false ]]; then
					
					echo "Resilience installed for 10.10.0.11 -> $timestamp" >> $MEASUREMENT_RECORD
					controllersCheck[1]=true

				elif [[ ! -z $(echo $resilience_done | grep -w "cookie=0xccccc") && ${controllersCheck[2]} == false ]]; then
					
					echo "Resilience installed for 10.10.0.12 -> $timestamp" >> $MEASUREMENT_RECORD
					controllersCheck[2]=true
				fi
			fi
#		fi
		# check if all c-s pairs have resilient rules installed
		# simply check if all elements of controllersCheck are true
		if [[ "${controllersCheck[@]}" =~ ^(true )*true$ ]]; then
			RESILIENT_OF_RULES_INSTALLED=true
		fi
#	done
	
		# USE THIS FOR ONE CONTROLLER CASE
		# new rule necessary after aggregation OF has been introduced  
		#resilience_done=$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep priority=201,tcp,nw_dst=$switchip,tp_src=6633)
		#if [ ! -z "$resilience_done" ]; then
		#	timestamp=$(date +"%T.%3N")
		#	echo "$resilience_done"
		#	echo "Resilience installed for ${controllersip[$i]} -> $timestamp" >> $MEASUREMENT_RECORD
		#	RESILIENT_OF_RULES_INSTALLED=true
		#fi
		sleep $PERIOD;
done

echo "Resilience done"

echo "Exiting the script"
