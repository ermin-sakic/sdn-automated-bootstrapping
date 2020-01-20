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
SW_NUM=16

# Edit the network topology here
# horizontal connections
$SW_CONN_UTIL sw_1 sw_2 veth12-a veth12-b
$SW_CONN_UTIL sw_2 sw_3 veth23-a veth23-b
$SW_CONN_UTIL sw_3 sw_4 veth34-a veth34-b
$SW_CONN_UTIL sw_5 sw_6 veth56-a veth56-b
$SW_CONN_UTIL sw_6 sw_7 veth67-a veth67-b
$SW_CONN_UTIL sw_7 sw_8 veth78-a veth78-b
$SW_CONN_UTIL sw_9 sw_10 veth9-10-a veth9-10-b
$SW_CONN_UTIL sw_10 sw_11 veth10-11-a veth10-11-b
$SW_CONN_UTIL sw_11 sw_12 veth11-12-a veth11-12-b
$SW_CONN_UTIL sw_13 sw_14 veth13-14-a veth13-14-b
$SW_CONN_UTIL sw_14 sw_15 veth14-15-a veth14-15-b
$SW_CONN_UTIL sw_15 sw_16 veth15-16-a veth15-16-b
# vertical connections
$SW_CONN_UTIL sw_1 sw_5 veth15-a veth15-b
$SW_CONN_UTIL sw_2 sw_6 veth26-a veth26-b
$SW_CONN_UTIL sw_3 sw_7 veth37-a veth37-b
$SW_CONN_UTIL sw_4 sw_8 veth48-a veth48-b
$SW_CONN_UTIL sw_5 sw_9 veth59-a veth59-b
$SW_CONN_UTIL sw_6 sw_10 veth6-10-a veth6-10-b
$SW_CONN_UTIL sw_7 sw_11 veth7-11-a veth7-11-b
$SW_CONN_UTIL sw_8 sw_12 veth8-12-a veth8-12-b
$SW_CONN_UTIL sw_9 sw_13 veth9-13-a veth9-13-b
$SW_CONN_UTIL sw_10 sw_14 veth10-14-a veth10-14-b
$SW_CONN_UTIL sw_11 sw_15 veth11-15-a veth11-15-b
$SW_CONN_UTIL sw_12 sw_16 veth12-16-a veth12-16-b



# TODO: Add hosts later 

