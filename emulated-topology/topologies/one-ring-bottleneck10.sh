#!/bin/bash

######################################################################
#       Filename: one-ring-bottleneck_template.sh                    #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 16, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: one-ring-bottleneck_template.sh
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
#	This script creates a one-ring-bottleneck topology of size SW_NUM
#	e.g. for a 5 switch one-ring-bottleneck set SW_NUM=5
#	additionally you can specify HORIZONTAL_SIZE and VERTICAL_SIZE
#	of the topology (SW_NUM should always be HORIZONTAL_SIZE*VERTICAL_SIZE + 1)
# 
######################################################################

# Connect switches utility (do not change this line)
SW_CONN_UTIL=./emulator/common/connect_sw.sh

# Here define the number of switches that your topology should have
SW_NUM=10

HORIZONTAL_SIZE=3
VERTICAL_SIZE=3


# CONNECTING HORIZONTAL LINE
for (( i = 2; i <= $HORIZONTAL_SIZE; i++)); do
	$SW_CONN_UTIL sw_$i sw_$(($i+1)) veth$i-$(($i+1))-a veth$i-$(($i+1))-b
done

# CONNECTING ROOT TO EDGES OF THE HORIZONTAL LINE
$SW_CONN_UTIL sw_1 sw_2 veth1-2-a veth1-2-b
$SW_CONN_UTIL sw_1 sw_$(( $HORIZONTAL_SIZE + 1 )) veth1-$(( $HORIZONTAL_SIZE + 1 ))-a veth1-$(( $HORIZONTAL_SIZE + 1 ))-b

# INDEX OF THE FIRST SWITCH AFTER THE HORIZONTAL LINE
NEXT_SWITCH_INDEX=$(( $HORIZONTAL_SIZE + 2 ))

# CONNECTING VERTICAL LINES
for (( i = 2; i <= $(( $HORIZONTAL_SIZE + 1 )); i++ )); do
	$SW_CONN_UTIL sw_$i sw_$NEXT_SWITCH_INDEX veth$i-$NEXT_SWITCH_INDEX-a veth$i-$NEXT_SWITCH_INDEX-b
	for (( j = 1; j < $(( $VERTICAL_SIZE - 1 )); j++ )); do
		$SW_CONN_UTIL sw_$NEXT_SWITCH_INDEX sw_$(( $NEXT_SWITCH_INDEX + 1 )) veth$NEXT_SWITCH_INDEX-$(( $NEXT_SWITCH_INDEX + 1 ))-a  veth$NEXT_SWITCH_INDEX-$(( $NEXT_SWITCH_INDEX + 1 ))-b
		NEXT_SWITCH_INDEX=$(( $NEXT_SWITCH_INDEX + 1 ))
	done
	NEXT_SWITCH_INDEX=$(( $NEXT_SWITCH_INDEX + 1 ))
done


# TODO: Add hosts later 

