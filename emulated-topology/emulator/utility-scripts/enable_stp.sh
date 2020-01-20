 #!/bin/bash

######### VARIABLEs ############

# Number of switches in total
no_sw=13

################################

# Start containers with static persistent mac-addresses (required for ODL's SNMP wiring based on a static file) - if OpenFlow assumed, no SNMP or static MAC setting for management interface required
for sw_var in $(seq 1 $no_sw)
do
  container_id=`docker inspect -f '{{.Id}}' sw_$sw_var`
  # Enable STP
  sudo docker exec -u root -it $container_id ovs-vsctl set Bridge br100 rstp_enable=true
done
:
