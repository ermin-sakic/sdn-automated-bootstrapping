#!/bin/bash

# Use this script to change boot virtuwind feature
# without recompiling the whole project again

# Procedure:
# 1. STOP Karaf
# 2. RUN this script, e.g. ./change_virtuwind_boot_feature.sh stand -> for standard
# 			   ./change_virtuwind_boot_feature.sh alter -> for alternative 
# 3. START Karaf again

KARAF_HOME=distribution/opendaylight-karaf/target/assembly
echo "KARAF_HOME folder: $KARAF_HOME"

# Deletes cached data from the previous runs
if [ "$(ls -A  $KARAF_HOME/data)" ]; then
      sudo -S rm -r $KARAF_HOME/data/*
      echo -e "\nCached data from $KARAF_HOME/data/ cleaned."
else
      echo "There is no any cached data to clean."
fi



# Specifies which virtuwind feature to run during Karaf bootup
if [ $1 == "stand" ]; then
	VIRTUWIND_FEATURE="virtuwind-intra"
elif [ $1 == "alter" ]; then	
	VIRTUWIND_FEATURE="virtuwind-intra-alternative"
else
	echo "The only available choices are stand and alter"
	exit 1
fi


# Finds current config
# TODO: Add better pattern matching
CURRENT=$(grep -h "virtuwind-intra*" "$KARAF_HOME/etc/org.apache.karaf.features.cfg" | awk -F'[ ,]' '{print $10}')

# Changes Karaf boot feature config

if [ "$CURRENT" == "virtuwind-intra" ]; then
	sed -i "s/virtuwind-intra/$VIRTUWIND_FEATURE/g" "$KARAF_HOME/etc/org.apache.karaf.features.cfg" 
else
	sed -i "s/virtuwind-intra-alternative/$VIRTUWIND_FEATURE/g" "$KARAF_HOME/etc/org.apache.karaf.features.cfg" 
fi

echo "Bootup virtuwind feature used: $VIRTUWIND_FEATURE"
echo "Now run one of the exec_karaf scripts to start the Karaf"
