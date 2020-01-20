######################################################################
#       Filename: host_setup.sh                                      #
#                                                                    #
#       Author: Mirza Avdic                                          #
#       Date:  Mai 28, 2018                                          #
#                                                                    #
######################################################################  

######################################################################
#
#   Filename: host_setup.sh
#
#   Description: 
#
#   Execute this script to setup virtual memory hugepages on the host
#	computer, i.e. where the Docker switch containers will be 
#	instantiated.
#
######################################################################

# Building necessary docker images if not already built
sudo docker build -t dpdk_img -f ./emulator/docker-files/DPDKDockerFile ./emulator/common
sudo docker build -t ovs_dpdk_img -f ./emulator/docker-files/Ovs-Switch-DPDKInit-Dockerfile ./emulator/common

echo "Configuring hugepages in /etc/sysctl.conf"
if grep -q "vm.nr_hugepages=2048" /etc/sysctl.conf; then
	echo "ALready Configured"
else
	sudo echo "# Hugepages setup" >> /etc/sysctl.conf
	sudo echo "vm.nr_hugepages=2048" >> /etc/sysctl.conf
fi

# Reload variables without reseting the machine
echo "Reloading variables in sysctl"
sudo sysctl --system

# Mount the hugepages, if not already mounted by default
echo "Mounting hugepages"
sudo umount -a -t hugetlbfs
sudo mount -t hugetlbfs none /dev/hugepages``
