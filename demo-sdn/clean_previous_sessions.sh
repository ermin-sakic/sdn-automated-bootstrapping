#!/bin/bash

# Cleaning data folder form the previous session

rm -r distribution/opendaylight-karaf/target/assembly/data/*

# Cleaning cluster configuration data (necessary when first executing cluster and then one controller)

rm -r distribution/opendaylight-karaf/target/assembly/configuration/*
