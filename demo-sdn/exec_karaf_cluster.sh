#!/bin/bash

sudo pkill -9 arping

# Clear arp table
sudo ip -s -s neigh flush all 

# If freshly compiled it needs this folder in order to put config files
mkdir -p ./distribution/opendaylight-karaf/target/assembly/etc/opendaylight/karaf

# Push remote DHCP and SETUP configs
#MODE="STANDARD"
MODE="ALTERNATIVE"

mkdir -p ./distribution/opendaylight-karaf/target/assembly/etc/opendaylight/karaf

./clean_previous_sessions.sh
./push_log_conf.sh

if [[ $MODE == "STANDARD" ]]; then
		./change_virtuwind_boot_feature.sh stand
		./push_standard_conf_REMOTE_CLUSTER.sh
else
		./change_virtuwind_boot_feature.sh alter
		./push_alternative_conf_REMOTE_CLUSTER.sh
fi


# Runs the cluster config script to initialize the akka configs
./config_cluster.sh $1

#########################################
# Starts OpenDaylight's Karaf Container #
#########################################
sudo ./distribution/opendaylight-karaf/target/assembly/bin/karaf debug
