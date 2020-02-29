#!/bin/bash

# Adds the C2C flows during the bootstrapping phase, needs to be triggered externally
nohup /add_c2c_flows.sh $1 $2 $3 > /dev/null 2>&1 &
