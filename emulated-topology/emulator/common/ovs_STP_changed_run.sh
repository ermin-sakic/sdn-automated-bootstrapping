#!/bin/bash
######################################################################
#       Filename: ovs_STP_changed_run.sh                             #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Aug 22, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: ovs_STP_changed_run.sh
#
#   Description: 
#
#   installs and initalizes ovs in the containers
#
######################################################################

cd /ovs

# Install ovs from the mounted folder
./boot.sh
./configure --with-linux=/lib/modules/`uname -r`/build
make
make install
make modules_install

mkdir -p /usr/local/etc/openvswitch
# Create Ovs database
sudo ovsdb-tool create /usr/local/etc/openvswitch/conf.db vswitchd/vswitch.ovsschema

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

sudo ovs-vsctl --no-wait init
sudo ovs-vswitchd --pidfile --detach


                                                        
