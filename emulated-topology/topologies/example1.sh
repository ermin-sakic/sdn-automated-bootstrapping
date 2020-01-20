#!/bin/bash

######################################################################
#       Filename: example.sh                                         #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 16, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: example.sh
#
#   Description: 
#
#   An example of how the topology file should look like.
#
#	For switches always use the following naming convention:
#	sw_num - where num should always be in range [1, SW_NUM]
#
#	For virtual ethernet interfaces names should always contain 
#	"veth" substring, since some scripts rely on names being in 
#	in this format.
# 
######################################################################

# Connect switches utility (do not change this line)
SW_CONN_UTIL=./emulator/common/connect_sw.sh

# Here define the number of switches that your topology should have
SW_NUM=4

# Edit the network topology here
$SW_CONN_UTIL sw_1 sw_2 veth12-a veth12-b
$SW_CONN_UTIL sw_2 sw_3 veth23-a veth23-b
$SW_CONN_UTIL sw_1 sw_3 veth13-a veth13-b
$SW_CONN_UTIL sw_3 sw_4 veth34-a veth34-b
$SW_CONN_UTIL sw_1 sw_4 veth14-a veth14-b
$SW_CONN_UTIL sw_2 sw_4 veth24-a veth24-b


# TODO: Add hosts later 

