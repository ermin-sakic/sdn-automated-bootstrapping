#!/bin/bash

######################################################################
#       Filename: dump_all_flow_tables.sh                            #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Apr 17, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: dump_all_flow_tables.sh
#
#   Description: 
#
#   Dumps flow tables from the specified number of switches in 
#   the flow-tables folder.
#
######################################################################

FOLDER="flow-tables"
if [ ! -d "$FOLDER" ]; then
	mkdir $FOLDER
fi

for (( i = 1; i <= $1; i++ )); do
	sudo docker exec -u root "sw_$i" ovs-ofctl dump-flows br100 --protocol=OpenFlow13 > $FOLDER/"sw_$i.log"
done
