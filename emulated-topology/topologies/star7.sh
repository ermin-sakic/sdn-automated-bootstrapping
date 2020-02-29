#!/bin/bash

######################################################################
#       Filename: star_dynamic_template.sh                           #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 16, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: star_dynamic_template.sh
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
#	This script creates a star topology with (SW_NUM - 1) petals (leaves),
#  	e.g. for a 5 petal star set SW_NUM=6	
# 
######################################################################

# Connect switches utility (do not change this line)
SW_CONN_UTIL=./emulator/common/connect_sw.sh

# Here define the number of switches that your topology should have
SW_NUM=7

# Edit the network topology here
for (( i = 2; i <= SW_NUM; i++ )); do
	$SW_CONN_UTIL sw_1 sw_$i veth1-$i-a veth1-$i-b
done

# TODO: Add hosts later 

