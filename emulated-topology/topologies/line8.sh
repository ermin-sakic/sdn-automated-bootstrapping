#!/bin/bash

######################################################################
#       Filename: line_dynamic_template.sh                           #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 16, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: line_dynamic_template.sh
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
#	This script creates a line topology of size SW_NUM
#	e.g. for a 5 switch line set SW_NUM=5
# 
######################################################################

# Connect switches utility (do not change this line)
SW_CONN_UTIL=./emulator/common/connect_sw.sh

# Here define the number of switches that your topology should have
SW_NUM=8

for (( i = 1; i < $SW_NUM; i++ )); do
	$SW_CONN_UTIL sw_$i sw_$(($i+1)) veth$i-$(($i+1))-a veth$i-$(($i+1))-b
done


# TODO: Add hosts later 

