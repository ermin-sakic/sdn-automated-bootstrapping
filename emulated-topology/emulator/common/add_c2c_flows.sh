#!/bin/bash
while true; 
do
	ovs-ofctl add-flow br100 table=0,priority=100,ip,nw_src=$1,nw_dst=$2,actions=NORMAL --protocol=OpenFlow13
	ovs-ofctl add-flow br100 table=0,priority=100,ip,nw_src=$2,nw_dst=$1,actions=NORMAL --protocol=OpenFlow13

	ovs-ofctl add-flow br100 table=0,priority=100,ip,nw_src=$1,nw_dst=$3,actions=NORMAL --protocol=OpenFlow13

	ovs-ofctl add-flow br100 table=0,priority=100,ip,nw_src=$3,nw_dst=$1,actions=NORMAL --protocol=OpenFlow13

	ovs-ofctl add-flow br100 table=0,priority=100,ip,nw_src=$2,nw_dst=$3,actions=NORMAL --protocol=OpenFlow13

	ovs-ofctl add-flow br100 table=0,priority=100,ip,nw_src=$3,nw_dst=$2,actions=NORMAL --protocol=OpenFlow13

	sleep 0.25;
done
