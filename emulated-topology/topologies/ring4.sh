#!/bin/bash

######################################################################
#       Filename: ring_dynamic_template.sh                           #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Jul 13, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: ring_dynamic_template.sh
#
#   Description: 
#
#   This script creates a ring topology based on the dimension size,
#   i.e. SW_NUM
#   e.g. for a 5 switch ring set SW_NUM=5
######################################################################

# Connect switches utility (do not change this line)
SW_CONN_UTIL=./emulator/common/connect_sw.sh

# Here define the number of switches that your topology should have
SW_NUM=4

for (( i = 1; i < $SW_NUM; i++ )); do
	$SW_CONN_UTIL sw_$i sw_$(($i + 1)) veth$i-$(($i + 1))-a veth$i-$(($i + 1))-b
	#echo "$i $(($i+1))"
done
$SW_CONN_UTIL sw_1 sw_$SW_NUM veth1-$SW_NUM-a veth1-$SW_NUM-b


