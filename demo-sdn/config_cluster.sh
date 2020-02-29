#!/bin/bash

# Create the path
mkdir -p ./distribution/opendaylight-karaf/target/assembly/configuration/initial/

# Put all member specific cluster-configuration scripts in the right place 
sudo cp ./cluster_scripts/akka.conf_$1 ./distribution/opendaylight-karaf/target/assembly/configuration/initial/akka.conf
sudo cp ./cluster_scripts/modules.conf ./distribution/opendaylight-karaf/target/assembly/configuration/initial/modules.conf
sudo cp ./cluster_scripts/module-shards.conf ./distribution/opendaylight-karaf/target/assembly/configuration/initial/module-shards.conf

