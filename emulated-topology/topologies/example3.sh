#!/bin/bash

######################################################################
#       Filename: example3.sh                                        #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  May 9, 2018                                           #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: example3.sh
#
#   Description: 
#
#       An example of how the topology file should look like.
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
	SW_NUM=10

# Edit the network topology here
$SW_CONN_UTIL sw_1 sw_2 veth12-a veth12-b
$SW_CONN_UTIL sw_1 sw_3 veth13-a veth13-b
$SW_CONN_UTIL sw_1 sw_4 veth14-a veth14-b
$SW_CONN_UTIL sw_2 sw_3 veth23-a veth23-b
$SW_CONN_UTIL sw_2 sw_4 veth24-a veth24-b
$SW_CONN_UTIL sw_3 sw_4 veth34-a veth34-b
$SW_CONN_UTIL sw_1 sw_5 veth15-a veth15-b
$SW_CONN_UTIL sw_2 sw_6 veth26-a veth26-b
$SW_CONN_UTIL sw_4 sw_7 veth47-a veth47-b
$SW_CONN_UTIL sw_3 sw_8 veth38-a veth38-b
$SW_CONN_UTIL sw_5 sw_6 veth56-a veth56-b
$SW_CONN_UTIL sw_6 sw_7 veth67-a veth67-b
$SW_CONN_UTIL sw_7 sw_8 veth78-a veth78-b
$SW_CONN_UTIL sw_8 sw_5 veth85-a veth85-b
$SW_CONN_UTIL sw_5 sw_9 veth59-a veth59-b
$SW_CONN_UTIL sw_8 sw_10 veth810-a veth810-b


# TODO: Add hosts later 


