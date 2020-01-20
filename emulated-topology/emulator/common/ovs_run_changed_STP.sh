#!/bin/bash
######################################################################
#       Filename: ovs_run_changed_STP.sh                             #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Aug 23, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: ovs_run_changed_STP.sh
#
#   Description: 
#
#   OVS stratup script 
######################################################################
cd /ovs

export DB_SOCK=/usr/local/var/run/openvswitch/db.sock
mkdir -p /usr/local/var/run/openvswitch
export PATH=$PATH:/usr/local/share/openvswitch/scripts

# Run Ovs
ovsdb-server --remote=punix:/usr/local/var/run/openvswitch/db.sock \
	    --remote=db:Open_vSwitch,Open_vSwitch,manager_options \
		--private-key=db:Open_vSwitch,SSL,private_key \
		--certificate=db:Open_vSwitch,SSL,certificate \
		--bootstrap-ca-cert=db:Open_vSwitch,SSL,ca_cert \
		--pidfile --detach --log-file

ovs-vsctl --no-wait init
ovs-vswitchd --pidfile --detach


                                                        
