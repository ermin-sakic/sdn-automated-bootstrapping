#!/bin/bash

######################################################################
#       Filename: 2childtree.sh                                      #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 16, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: 2childtree.sh
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
SW_NUM=15

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
$SW_CONN_UTIL sw_4 sw_8 veth48-a veth48-b
$SW_CONN_UTIL sw_4 sw_9 veth49-a veth49-b
$SW_CONN_UTIL sw_8 sw_9 veth89-a veth89-b
$SW_CONN_UTIL sw_9 sw_10 veth9-10-a veth9-10-b
$SW_CONN_UTIL sw_5 sw_10 veth5-10-a veth5-10-b
$SW_CONN_UTIL sw_10 sw_11 veth10-11-a veth10-11-b
$SW_CONN_UTIL sw_5 sw_11 veth5-11-a veth5-11-b
$SW_CONN_UTIL sw_11 sw_12 veth11-12-a veth11-12-b
$SW_CONN_UTIL sw_6 sw_12 veth6-12-a veth6-12-b
$SW_CONN_UTIL sw_12 sw_13 veth12-13-a veth12-13-b
$SW_CONN_UTIL sw_6 sw_13 veth6-13-a veth6-13-b
$SW_CONN_UTIL sw_13 sw_14 veth13-14-a veth13-14-b
$SW_CONN_UTIL sw_7 sw_14 veth7-14-a veth7-14-b
$SW_CONN_UTIL sw_14 sw_15 veth14-15-a veth14-15-b
$SW_CONN_UTIL sw_7 sw_15 veth7-15-a veth7-15-b


# TODO: Add hosts later 

