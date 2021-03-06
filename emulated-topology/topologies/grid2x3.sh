#!/bin/bash

######################################################################
#       Filename: grid_dynamic.sh                                    #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Jul 13, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: grid_dynamic.sh
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
#	This script creates a grid topology based on the
#	dimension size provided in the GRID_WIDTH and GRID_HEIGHT variables
#	e.g. for a 3x3 grid set GRID_WIDTH=3 GRID_HEIGHT=3 
# 
######################################################################

# Connect switches utility (do not change this line)
SW_CONN_UTIL=./emulator/common/connect_sw.sh

# Grid dimension
GRID_WIDTH=2
GRID_HEIGHT=3

# Here define the number of switches that your topology should have
SW_NUM=6

# HORIZONTAL CONNECTIONS
for (( i = 0; i < $GRID_HEIGHT; i++ )); do
	for (( j = 1; j < $GRID_WIDTH; j++ )); do
		index1=$(($GRID_WIDTH*$i + $j))
		index2=$(($GRID_WIDTH*$i + $j + 1))
		# Horizontal connections
		echo "$index1 $index2"
		$SW_CONN_UTIL sw_$index1 sw_$index2 veth$index1-$index2-a veth$index1-$index2-b
	done
done

# VERTICAL CONNECTIONS
for (( i = 0; i < $(($GRID_HEIGHT - 1)); i++ )); do
	for (( j = 1; j <= $GRID_WIDTH; j++ )); do
		index1=$(($GRID_WIDTH*i + j))
		index2=$(($GRID_WIDTH*i + j + $GRID_WIDTH))
		# Vertical connections
		echo "$index1 $index2"
		$SW_CONN_UTIL sw_$index1 sw_$index2 veth$index1-$index2-a veth$index1-$index2-b
	done
done


# TODO: Add hosts later 

