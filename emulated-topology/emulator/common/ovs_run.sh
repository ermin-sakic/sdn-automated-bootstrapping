#!/bin/bash
######################################################################
#       Filename: ovs_run.sh                                         #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Mai 25, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: ovs_run.sh
#
#   Description: 
#
#   Used to start ovs components with dpdk init enabled 
######################################################################

export DB_SOCK=/usr/local/var/run/openvswitch/db.sock
mkdir -p /usr/local/var/run/openvswitch
export PATH=$PATH:/usr/local/share/openvswitch/scripts

ovsdb-server --remote=punix:/usr/local/var/run/openvswitch/db.sock \
	    --remote=db:Open_vSwitch,Open_vSwitch,manager_options \
		--private-key=db:Open_vSwitch,SSL,private_key \
		--certificate=db:Open_vSwitch,SSL,certificate \
		--bootstrap-ca-cert=db:Open_vSwitch,SSL,ca_cert \
		--pidfile --detach --log-file


ovs-vsctl --no-wait set Open_vSwitch . other_config:dpdk-socket-mem="1024"
ovs-vsctl --no-wait set Open_vSwitch . other_config:dpdk-init=true
#ovs-vsctl --no-wait set Open_vSwitch . external-ids:hostname=$(hostname)
ovs-ctl --no-ovsdb-server --db-sock="$DB_SOCK" start

ovs-vsctl add-br br100
ovs-vsctl set bridge br100 datapath_type=netdev



                                                        
