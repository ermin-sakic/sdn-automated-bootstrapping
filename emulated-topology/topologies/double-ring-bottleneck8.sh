#!/bin/bash

######################################################################
#       Filename: double-ring-bottleneck_template.sh                 #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Aug 13, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: double-ring-bottleneck_template.sh
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
#	This script creates a double-ring-bottleneck topology of size SW_NUM
#	e.g. for a 11 switch double-ring-bottleneck set SW_NUM=11
#	additionally you can specify HORIZONTAL_SIZE and VERTICAL_SIZE
#	of the topology (SW_NUM should always be HORIZONTAL_SIZE*VERTICAL_SIZE + 2)
# 
######################################################################

# Connect switches utility (do not change this line)
SW_CONN_UTIL=./emulator/common/connect_sw.sh

# Here define the number of switches that your topology should have
SW_NUM=8

HORIZONTAL_SIZE=3
VERTICAL_SIZE=2


# CONNECTING HORIZONTAL LINE TOP
for (( i = 2; i <= $HORIZONTAL_SIZE; i++)); do
	$SW_CONN_UTIL sw_$i sw_$(($i+1)) veth$i-$(($i+1))-a veth$i-$(($i+1))-b
	echo "TOPOLOGY: sw_$i sw_$(($i+1)) veth$i-$(($i+1))-a veth$i-$(($i+1))-b"
done

# CONNECTING ROOT TO EDGES OF THE HORIZONTAL LINE TOP
$SW_CONN_UTIL sw_1 sw_2 veth1-2-a veth1-2-b
echo "TOPOLOGY:sw_1 sw_2 veth1-2-a veth1-2-b"
$SW_CONN_UTIL sw_1 sw_$(( $HORIZONTAL_SIZE + 1 )) veth1-$(( $HORIZONTAL_SIZE + 1 ))-a veth1-$(( $HORIZONTAL_SIZE + 1 ))-b
echo "TOPOLOGY:sw_1 sw_$(( $HORIZONTAL_SIZE + 1 )) veth1-$(( $HORIZONTAL_SIZE + 1 ))-a veth1-$(( $HORIZONTAL_SIZE + 1 ))-b"

# INDEX OF THE FIRST SWITCH AFTER THE HORIZONTAL LINE
NEXT_SWITCH_INDEX=$(( $HORIZONTAL_SIZE + 2 ))

# CONNECTING VERTICAL LINES
echo "TOPOLOGY:Connecting vertical lines"
for (( i = 2; i <= $(( $HORIZONTAL_SIZE + 1 )); i++ )); do
	$SW_CONN_UTIL sw_$i sw_$NEXT_SWITCH_INDEX veth$i-$NEXT_SWITCH_INDEX-a veth$i-$NEXT_SWITCH_INDEX-b
	echo "TOPOLOGY:sw_$i sw_$NEXT_SWITCH_INDEX veth$i-$NEXT_SWITCH_INDEX-a veth$i-$NEXT_SWITCH_INDEX-b"
	for (( j = 1; j < $(( $VERTICAL_SIZE - 1 )); j++ )); do
		$SW_CONN_UTIL sw_$NEXT_SWITCH_INDEX sw_$(( $NEXT_SWITCH_INDEX + 1 )) veth$NEXT_SWITCH_INDEX-$(( $NEXT_SWITCH_INDEX + 1 ))-a  veth$NEXT_SWITCH_INDEX-$(( $NEXT_SWITCH_INDEX + 1 ))-b
		echo "TOPOLOGY:sw_$NEXT_SWITCH_INDEX sw_$(( $NEXT_SWITCH_INDEX + 1 )) veth$NEXT_SWITCH_INDEX-$(( $NEXT_SWITCH_INDEX + 1 ))-a  veth$NEXT_SWITCH_INDEX-$(( $NEXT_SWITCH_INDEX + 1 ))-b"
		NEXT_SWITCH_INDEX=$(( $NEXT_SWITCH_INDEX + 1 ))
	done
	NEXT_SWITCH_INDEX=$(( $NEXT_SWITCH_INDEX + 1 ))
	echo "TOPOLOGY:NEXT_SWITCH_INDEX-> $NEXT_SWITCH_INDEX"
done

# Index of the bottom root
BOTTOM_ROOT_INDEX=$NEXT_SWITCH_INDEX
# Index of the last switch prior bottom root
NEXT_SWITCH_INDEX=$(( $NEXT_SWITCH_INDEX - 1 ))
echo "TOPOLOGY:Connecting horizontal lines"
# CONNECTING HORIZONTAL LINE BOTTOM
for (( i = 1; i < $HORIZONTAL_SIZE; i++)); do
	$SW_CONN_UTIL sw_$(( $NEXT_SWITCH_INDEX - $VERTICAL_SIZE + 1)) sw_$NEXT_SWITCH_INDEX veth$(( $NEXT_SWITCH_INDEX - $VERTICAL_SIZE + 1))-$NEXT_SWITCH_INDEX-a veth$(( $NEXT_SWITCH_INDEX - $VERTICAL_SIZE + 1 ))-$NEXT_SWITCH_INDEX-b
	echo "TOPOLOGY:sw_$(( $NEXT_SWITCH_INDEX - $VERTICAL_SIZE + 1)) sw_$NEXT_SWITCH_INDEX veth$(( $NEXT_SWITCH_INDEX - $VERTICAL_SIZE + 1))-$NEXT_SWITCH_INDEX-a veth$(( $NEXT_SWITCH_INDEX - $VERTICAL_SIZE + 1 ))-$NEXT_SWITCH_INDEX-b"
	echo "TOPOLOGY:Connecting horizontal lines bottom -> sw_$(( $NEXT_SWITCH_INDEX - $VERTICAL_SIZE + 1)) sw_$NEXT_SWITCH_INDEX"
	NEXT_SWITCH_INDEX=$(( $NEXT_SWITCH_INDEX - $VERTICAL_SIZE + 1 ))
done

# CONNECTING VERTICAL LINE BOTTOM ROOT
$SW_CONN_UTIL sw_$(( $BOTTOM_ROOT_INDEX - 1 )) sw_$BOTTOM_ROOT_INDEX veth$(( $BOTTOM_ROOT_INDEX - 1))-$BOTTOM_ROOT_INDEX-a veth$(( $BOTTOM_ROOT_INDEX - 1))-$BOTTOM_ROOT_INDEX-b
echo "TOPOLOGY:Connecting vertical line bottom root: sw_$(( $BOTTOM_ROOT_INDEX - 1 )) sw_$BOTTOM_ROOT_INDEX veth$(( $BOTTOM_ROOT_INDEX - 1))-$BOTTOM_ROOT_INDEX-a veth$(( $BOTTOM_ROOT_INDEX - 1))-$BOTTOM_ROOT_INDEX-b"
$SW_CONN_UTIL sw_$(( $NEXT_SWITCH_INDEX )) sw_$BOTTOM_ROOT_INDEX veth$NEXT_SWITCH_INDEX-$BOTTOM_ROOT_INDEX-a veth$NEXT_SWITCH_INDEX-$BOTTOM_ROOT_INDEX-b
echo "TOPOLOGY:Connecting vertical line bottom root: sw_$(( $NEXT_SWITCH_INDEX )) sw_$BOTTOM_ROOT_INDEX veth$NEXT_SWITCH_INDEX-$BOTTOM_ROOT_INDEX-a veth$NEXT_SWITCH_INDEX-$BOTTOM_ROOT_INDEX-b"

# TODO: Add hosts later 

