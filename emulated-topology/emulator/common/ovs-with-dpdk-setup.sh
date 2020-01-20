######################################################################
#       Filename: ovs-with-dpdk-setup.sh                             #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Mai 25, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: ovs-with-dpdk-setup.sh
#
#   Description: 
#
#   This script downloads and builds DPDK and ovs-vswitch with DPDK
#	enabled.
#	It follows the procedure described here: 
#	http://docs.openvswitch.org/en/latest/intro/install/dpdk/
#
######################################################################

OVS_VERSION="2.9.1"
DPDK_BUILD="/usr/src/dpdk-stable-17.11.2/x86_64-native-linuxapp-gcc"

# Install a necessary Python package if needed
pip install six

# Download and build ovs-vswitch source
cd /home/admin 
mkdir ovs-vswitch-src
cd ovs-vswitch-src
wget http://openvswitch.org/releases/openvswitch-$OVS_VERSION.tar.gz
tar xf openvswitch-$OVS_VERSION.tar.gz
export OVS_DIR=openvswitch-$OVS_VERSION
cd $OVS_DIR
./boot.sh
./configure --with-dpdk=$DPDK_BUILD
make
make install



# Setup ovs

mkdir -p /usr/local/etc/openvswitch

ovsdb-tool create /usr/local/etc/openvswitch/conf.db \
	    vswitchd/vswitch.ovsschema

