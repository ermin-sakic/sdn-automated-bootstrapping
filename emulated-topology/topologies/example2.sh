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
SW_NUM=7

# Edit the network topology here
$SW_CONN_UTIL sw_1 sw_2 veth12-a veth12-b
$SW_CONN_UTIL sw_1 sw_3 veth13-a veth13-b
$SW_CONN_UTIL sw_2 sw_3 veth23-a veth23-b
$SW_CONN_UTIL sw_2 sw_4 veth24-a veth24-b
$SW_CONN_UTIL sw_2 sw_5 veth25-a veth25-b
$SW_CONN_UTIL sw_3 sw_6 veth36-a veth36-b
$SW_CONN_UTIL sw_3 sw_7 veth37-a veth37-b
$SW_CONN_UTIL sw_4 sw_5 veth45-a veth45-b
$SW_CONN_UTIL sw_5 sw_6 veth56-a veth56-b
$SW_CONN_UTIL sw_6 sw_7 veth67-a veth67-b


# TODO: Add hosts later 

