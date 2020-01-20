#!/bin/bash
######################################################################
#       Filename: orchestrator.sh                                    #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Mai 31, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: orchestrator.sh
#
#   Description: 
#
#   In this script you specify which scenarios you want to be measured
#   This is the script that only needs to be run!
#   
#
######################################################################

LOG_FILE="log"
function instantiate() {
	./measurement.sh $1 2>&1 | tee -a $LOG_FILE
}


# remove log from the previous session
rm $LOG_FILE

#instantiate configs/conf51.sh
#instantiate configs/conf53.sh
#instantiate configs/conf55.sh
#instantiate configs/conf57.sh
#instantiate configs/conf6.sh
#instantiate configs/conf7.sh
#instantiate configs/conf8.sh
#instantiate configs/conf9.sh
#instantiate configs/conf10.sh
#instantiate configs/conf11.sh
#instantiate configs/conf12.sh
#instantiate configs/conf5.sh
#./measurement.sh configs/conf1.sh
#./measurement.sh configs/conf2.sh
#./measurement.sh configs/conf3.sh

for (( i = 22; i <= 24; i++ )); do
	instantiate configs/conf$i.sh
done

for (( i = 9; i <= 12; i++ )); do
	instantiate configs/conf$i.sh
done
