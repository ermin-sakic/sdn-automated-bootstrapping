#!/bin/bash
sw_id=`sudo docker ps | grep $1 | cut -d ' ' --f 1`
echo $sw_iecho $sw_id

sudo docker exec -u root $sw_id ovs-vsctl set bridge br100 other-config:disable-in-band=true  
