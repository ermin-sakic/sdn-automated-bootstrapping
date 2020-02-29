#!/bin/bash
######################################################################
#       Filename: configure_arping_local_remote.sh                   #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 17, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: configure_arping_local_remote.sh
#
#   Description: 
#
#   Use this script to configure controllers' self discovery 
#   for LOCAL or REMOTE testing
#
#   Valid script arguments are LOCAL or REMOTE
#
######################################################################


UNCOMMENTED_LOCAL="<arping-path>sudo ip netns exec ODL-Controller-SDNC-1 /home/ermin/run_arping.sh</arping-path>"
COMMENTED_LOCAL='<!-- <arping-path>sudo ip netns exec ODL-Controller-SDNC-1 /home/ermin/run_arping.sh</arping-path> -->'
UNCOMMENTED_REMOTE="<arping-path>/home/ermin/run_arping.sh</arping-path>"
COMMENTED_REMOTE='<!-- <arping-path>/home/ermin/run_arping.sh</arping-path> -->'

DESIRED=$1
CONF_PATH_STAND=./distribution/opendaylight-karaf/target/assembly/etc/opendaylight/karaf/149-bootstrapping-manager-setup.xml 
CONF_PATH_ALTER=./distribution/opendaylight-karaf/target/assembly/etc/opendaylight/karaf/149-bootstrapping-manager-alternative-setup.xml 

REMOTE_IS_COMMENTED_STAND=$(grep "$COMMENTED_REMOTE" "$CONF_PATH_STAND")
LOCAL_IS_COMMENTED_STAND=$(grep "$COMMENTED_LOCAL" "$CONF_PATH_STAND")
REMOTE_IS_COMMENTED_ALTER=$(grep "$COMMENTED_REMOTE" "$CONF_PATH_ALTER")
LOCAL_IS_COMMENTED_ALTER=$(grep "$COMMENTED_LOCAL" "$CONF_PATH_ALTER")

# Configuration for the standard bootstrapping scheme
echo "Configuring the standard bootstrapping scheme"
if [[ "$DESIRED" = "LOCAL" ]]; then
	if [[ $REMOTE_IS_COMMENTED_STAND ]]; then
		echo "Already good configured XML for REMOTE"	
	else
		#sed -i "33 s|$UNCOMMENTED_REMOTE|$COMMENTED_REMOTE|" "$CONF_PATH_STAND"
		sed -i " s|$UNCOMMENTED_REMOTE|$COMMENTED_REMOTE|" "$CONF_PATH_STAND"
	fi
	if [[ "$LOCAL_IS_COMMENTED_STAND" ]]; then
		echo "Uncommenting LOCAL"
		#sed -i "31 s|$COMMENTED_LOCAL|$UNCOMMENTED_LOCAL|" "$CONF_PATH_STAND"
		sed -i "s|$COMMENTED_LOCAL|$UNCOMMENTED_LOCAL|" "$CONF_PATH_STAND"
	fi
elif [[ "$DESIRED" = "REMOTE" ]]; then
	if [[ $LOCAL_IS_COMMENTED_STAND ]]; then
		echo "Already good defined XML"	
	else
		#sed -i "31 s|$UNCOMMENTED_LOCAL|$COMMENTED_LOCAL|" "$CONF_PATH_STAND"
		sed -i "s|$UNCOMMENTED_LOCAL|$COMMENTED_LOCAL|" "$CONF_PATH_STAND"
	fi
	if [[ "$REMOTE_IS_COMMENTED_STAND" ]]; then
		echo "Uncommenting REMOTE"
		#sed -i "33 s|$COMMENTED_REMOTE|$UNCOMMENTED_REMOTE|" "$CONF_PATH_STAND"
		sed -i "s|$COMMENTED_REMOTE|$UNCOMMENTED_REMOTE|" "$CONF_PATH_STAND"
	fi
fi

# Configuration for the alternative bootstrapping scheme
echo "Configuring the alternative bootstrapping scheme"
if [[ "$DESIRED" = "LOCAL" ]]; then
	if [[ $REMOTE_IS_COMMENTED_ALTER ]]; then
		echo "Already good configured XML for REMOTE"	
	else
		#sed -i "33 s|$UNCOMMENTED_REMOTE|$COMMENTED_REMOTE|" "$CONF_PATH_ALTER"
		sed -i " s|$UNCOMMENTED_REMOTE|$COMMENTED_REMOTE|" "$CONF_PATH_ALTER"
	fi
	if [[ "$LOCAL_IS_COMMENTED_ALTER" ]]; then
		echo "Uncommenting LOCAL"
		#sed -i "31 s|$COMMENTED_LOCAL|$UNCOMMENTED_LOCAL|" "$CONF_PATH_ALTER"
		sed -i "s|$COMMENTED_LOCAL|$UNCOMMENTED_LOCAL|" "$CONF_PATH_ALTER"
	fi
elif [[ "$DESIRED" = "REMOTE" ]]; then
	if [[ $LOCAL_IS_COMMENTED_ALTER ]]; then
		echo "Already good defined XML"	
	else
		#sed -i "31 s|$UNCOMMENTED_LOCAL|$COMMENTED_LOCAL|" "$CONF_PATH_ALTER"
		sed -i "s|$UNCOMMENTED_LOCAL|$COMMENTED_LOCAL|" "$CONF_PATH_ALTER"
	fi
	if [[ "$REMOTE_IS_COMMENTED_ALTER" ]]; then
		echo "Uncommenting REMOTE"
		#sed -i "33 s|$COMMENTED_REMOTE|$UNCOMMENTED_REMOTE|" "$CONF_PATH_ALTER"
		sed -i "s|$COMMENTED_REMOTE|$UNCOMMENTED_REMOTE|" "$CONF_PATH_ALTER"
	fi
fi
