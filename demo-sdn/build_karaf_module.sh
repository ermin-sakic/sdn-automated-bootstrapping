#!/bin/bash

# This scripts tries to speed up building of the ODL Karaf container
# First argument is the module that you want to build
# Second argument is the feature loaded by Karaf during bootup phase  automatically
# Example call:
# ./build_karaf_module.sh bootstrapping-manager-setup virtuwind-intra-alternative

# sudo password on remote hosts
PASSWORD="openflow"

KARAF_HOME=distribution/opendaylight-karaf/target/assembly
echo "KARAF_HOME folder: $KARAF_HOME"

# Builds a module that has been modified 
# Copies the newly build jar file to the default maven repository 
# folder of this Karaf instance
# NOTE: This does not change ~/.m2/repository (full build does)
if [ -z $1 ]; then
	echo "Please specify a module that you want to build!"
	exit 1
fi
echo "Building module: $1"
# -DaddInstallRepositorypath currently buggy
#mvn -pl $1 -am -DaddInstallRepositoryPath=$KARAF_HOME/system clean install
mvn -pl $1 -am clean install

# Copy snapshots to local repository
if [ $1 == "bootstrapping-manager-setup" ]; then
	echo "Copying bootstrapping-manager-setup to local repository."
	cp bootstrapping-manager-setup/config/src/main/resources/initial/149-bootstrapping-manager-setup.xml $KARAF_HOME/system/eu/virtuwind/bootstrappingmanager-setup-config/1.0.0-SNAPSHOT/bootstrappingmanager-setup-config-1.0.0-SNAPSHOT-config.xml
	cp bootstrapping-manager-setup/implementation/target/bootstrappingmanager-setup-impl-1.0.0-SNAPSHOT.jar $KARAF_HOME/system/eu/virtuwind/bootstrappingmanager-setup-impl/1.0.0-SNAPSHOT/bootstrappingmanager-setup-impl-1.0.0-SNAPSHOT.jar 

elif [ $1 == "bootstrapping-manager-dhcp" ]; then
	echo "Copying bootstrapping-manager-dhcp to local repository."
	cp bootstrapping-manager-dhcp/model/target/bootstrappingmanager-dhcp-model-1.0.0-SNAPSHOT.jar $KARAF_HOME/system/eu/virtuwind/bootstrappingmanager-dhcp-model/1.0.0-SNAPSHOT/bootstrappingmanager-dhcp-model-1.0.0-SNAPSHOT.jar
	cp bootstrapping-manager-dhcp/config/src/main/resources/initial/150-bootstrapping-manager-dhcp.xml $KARAF_HOME/system/eu/virtuwind/bootstrappingmanager-dhcp-config/1.0.0-SNAPSHOT/bootstrappingmanager-dhcp-config-1.0.0-SNAPSHOT-config.xml
	cp bootstrapping-manager-dhcp/implementation/target/bootstrappingmanager-dhcp-impl-1.0.0-SNAPSHOT.jar $KARAF_HOME/system/eu/virtuwind/bootstrappingmanager-dhcp-impl/1.0.0-SNAPSHOT/bootstrappingmanager-dhcp-impl-1.0.0-SNAPSHOT.jar

elif [ $1 == "bootstrapping-manager-alternative-setup" ]; then
	echo "Copying bootstrapping-manager-alternative-setup to local repository."
        cp bootstrapping-manager-alternative-setup/config/src/main/resources/initial/149-bootstrapping-manager-alternative-setup.xml $KARAF_HOME/system/eu/virtuwind/bootstrappingmanager-alternative-setup-config/1.0.0-SNAPSHOT/bootstrappingmanager-alternative-setup-config-1.0.0-SNAPSHOT-config.xml
        cp bootstrapping-manager-alternative-setup/implementation/target/bootstrappingmanager-alternative-setup-impl-1.0.0-SNAPSHOT.jar $KARAF_HOME/system/eu/virtuwind/bootstrappingmanager-alternative-setup-impl/1.0.0-SNAPSHOT/bootstrappingmanager-alternative-setup-impl-1.0.0-SNAPSHOT.jar

elif [ $1 == "bootstrapping-manager-alternative-dhcp" ]; then
	echo "Copying bootstrapping-manager-alternative-dhcp to local repository."
        cp bootstrapping-manager-alternative-dhcp/model/target/bootstrappingmanager-alternative-dhcp-model-1.0.0-SNAPSHOT.jar $KARAF_HOME/system/eu/virtuwind/bootstrappingmanager-alternative-dhcp-model/1.0.0-SNAPSHOT/bootstrappingmanager-alternative-dhcp-model-1.0.0-SNAPSHOT.jar
        cp bootstrapping-manager-alternative-dhcp/config/src/main/resources/initial/150-bootstrapping-manager-alternative-dhcp.xml $KARAF_HOME/system/eu/virtuwind/bootstrappingmanager-alternative-dhcp-config/1.0.0-SNAPSHOT/bootstrappingmanager-alternative-dhcp-config-1.0.0-SNAPSHOT-config.xml
        cp bootstrapping-manager-alternative-dhcp/implementation/target/bootstrappingmanager-alternative-dhcp-impl-1.0.0-SNAPSHOT.jar $KARAF_HOME/system/eu/virtuwind/bootstrappingmanager-alternative-dhcp-impl/1.0.0-SNAPSHOT/bootstrappingmanager-alternative-dhcp-impl-1.0.0-SNAPSHOT.jar

else
	echo "The script currently does not support building $1 module!"
	exit 1
fi



# Deletes cached data from the previous runs
if [ "$(ls -A  $KARAF_HOME/data)" ]; then	
	echo "$PASSWORD" | sudo -S rm -r $KARAF_HOME/data/*
	echo -e "\nCached data from $KARAF_HOME/data/ cleaned."
else
	echo "There is no any cached data to clean."
fi

# Specifies which virtuwind feature to run during Karaf bootup
# default is virtuwind-intra; alternative is virtuwind-intra-alternative
VIRTUWIND_FEATURE=${2:-"virtuwind-intra"}


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
