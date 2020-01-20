#!/bin/bash
#####
# Default execution script executed following a successful Docker Image instantiation. Starts all the daemons in configurations required for our setup.
####

# Start the Netconf-Testtool agent
#java -Xmx1G -XX:MaxPermSize=256M -jar /root/netconf.jar --device-count 1 --starting-port 18300 --schemas-dir /root/yang

# Configure a new user and default password
#net-snmp-config --create-snmpv3-user -A MD5 -a "passphrase" nagios

# Start the LLDPD daemon together with AgentX (exports the configuration updates via AgentX protocol to a master agent implemented by Net-SNMP)
#lldpd -x &

# Start the SNMP daemon
#/usr/sbin/snmpd -f @
#service snmpd restart

echo "admin" | sudo -kS /periodicExecScript.sh 10.10.0.101 10.10.0.102 10.10.0.103
