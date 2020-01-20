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
#	This script creates a symmetric grid topology based on the
#	dimension size provided in the GRID_DIMENSION variable
#	e.g. for a 3x3 grid set GRID_DIMENSION=3 
# 
######################################################################

# Connect switches utility (do not change this line)
SW_CONN_UTIL=./emulator/common/connect_sw.sh

# Grid dimension
GRID_DIMENSION=2

# Here define the number of switches that your topology should have
SW_NUM=4


for (( i = 0; i < $GRID_DIMENSION; i++ )); do
	for (( j = 1; j < $GRID_DIMENSION; j++ )); do
		index1=$(($GRID_DIMENSION*$i + $j))
		index2=$(($GRID_DIMENSION*$i + $j + 1))
		# Horizontal connections
		echo "$index1 $index2"
		$SW_CONN_UTIL sw_$index1 sw_$index2 veth$index1-$index2-a veth$index1-$index2-b
	done
done

for (( i = 0; i < $(($GRID_DIMENSION - 1)); i++ )); do
	for (( j = 1; j <= $GRID_DIMENSION; j++ )); do
		index1=$(($GRID_DIMENSION*i + j))
		index2=$(($GRID_DIMENSION*i + j + $GRID_DIMENSION))
		# Vertical connections
		echo "$index1 $index2"
		$SW_CONN_UTIL sw_$index1 sw_$index2 veth$index1-$index2-a veth$index1-$index2-b
	done
done

# For switch extensions manually add a switch and links here
# A new switch has to be instantiated manually as well
# Create the virtual interfaces and veth tubes (links)
#sudo ip link add veth_test1 type veth peer name veth_test2
#sudo ip link set veth_test1 netns sw_1
#sudo ip link set veth_test2 netns sw_4
#sudo docker exec -u root sw1 ifconfig veth_test1 up
#sudo docker exec -u root sw4 ifconfig veth_test2 up



# TODO: Add hosts later 

