#!/bin/bash

# Usage: arping -S <IPv4 address> -i <ifname> target IPv4 address
nohup arping -S $1 -i $2 10.50.0.100 &
