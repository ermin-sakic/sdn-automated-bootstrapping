#!/bin/bash
######################################################################
#       Filename: measure-flow-table-size.sh                         #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Jul 12, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: measure-flow-table-size.sh
#
#   Description: 
#
#   Used do extract the flow table size of a switch after the 
#   bootstrapping has been done.
#
######################################################################

LOG_FILE="/measure-flow-table-size.log"
MEASUREMENT_RECORD="/measurement_record"
TABLE_RECORD="/flow_table"

# Redirect stdout and stderr to the log file
exec 1>$LOG_FILE 2>&1

# Extracting the entire flow table
FLOW_TABLE=$(ovs-ofctl dump-flows br100 -O OpenFlow13)
echo "Flow table extracted"
echo "Flow table logging into $FLOW_TABLE" >> $TABLE_RECORD

# Measuring flow table size
FLOW_TABLE_SIZE=$(ovs-ofctl dump-flows br100 -O OpenFlow13 | grep duration -c)
echo "Flow table size extracted -> $FLOW_TABLE_SIZE"
echo "Flow table size -> $FLOW_TABLE_SIZE" >> $MEASUREMENT_RECORD

