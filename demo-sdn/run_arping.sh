#!/bin/bash

# Usage: arping -s <IPv4 address> -I <ifname> target IPv4 address
nohup arping -s $1 -I $2 10.50.0.100 &
