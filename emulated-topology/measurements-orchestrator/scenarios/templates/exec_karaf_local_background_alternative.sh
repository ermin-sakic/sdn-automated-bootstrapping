#!/bin/bash

#########################################
# Starts OpenDaylight's Karaf Container #
#########################################
#MODE="STANDARD"
MODE="ALTERNATIVE"


# If freshly compiled it needs this folder in order to put config files
mkdir -p ./distribution/opendaylight-karaf/target/assembly/etc/opendaylight/karaf

./clean_previous_sessions.sh
./push_log_conf.sh

if [[ $MODE == "STANDARD" ]]; then
	./change_virtuwind_boot_feature.sh stand
	./push_standard_conf_LOCAL.sh
else
	./change_virtuwind_boot_feature.sh alter
	./push_alternative_conf_LOCAL.sh
fi

sudo ip netns exec ODL-Controller-SDNC-1 ./distribution/opendaylight-karaf/target/assembly/bin/start 
#sudo ./clean_up.sh
