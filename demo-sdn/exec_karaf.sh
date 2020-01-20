#!/bin/bash

#########################################
# Starts OpenDaylight's Karaf Container #
#########################################
# Clean arp table 
sudo ip -s -s neigh flush all
##################
sudo ./clean_up.sh
sudo ./distribution/opendaylight-karaf/target/assembly/bin/karaf debug
sudo ./clean_up.sh
