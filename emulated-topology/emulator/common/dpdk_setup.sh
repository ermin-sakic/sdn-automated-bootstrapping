######################################################################
#       Filename: dpdk_setup.sh                              	     #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Mai 25, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: dpdk_setup.sh 
#
#   Description: 
#
#   This script downloads and builds DPDK and ovs-vswitch with DPDK
#	enabled.
#	It follows the procedure described here: 
#	http://docs.openvswitch.org/en/latest/intro/install/dpdk/
#
######################################################################

# Download the DPDK sources, extract the file and set DPDK_DIR
cd /usr/src/
wget http://fast.dpdk.org/rel/dpdk-17.11.2.tar.xz
tar xf dpdk-17.11.2.tar.xz
export DPDK_DIR=/usr/src/dpdk-stable-17.11.2
cd $DPDK_DIR

# Configure and install DPDK
export DPDK_TARGET=x86_64-native-linuxapp-gcc
export DPDK_BUILD=$DPDK_DIR/$DPDK_TARGET
make install T=$DPDK_TARGET DESTDIR=install




