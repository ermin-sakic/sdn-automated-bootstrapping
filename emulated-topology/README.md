VirtuWind Network Emulator (OpenFlow) (SDN Network Emulator - Extension)
===============================

This emulator is the extension of the emulator that can be found in the following folders:
* emulated-topo-esxi-setup
* emulated-topo-local

The purpose of this extension is to allow easier configuration of the emulated networks that are mainly used for testing (demonstrating) 
different SDN bootstrapping schemes. 

#### Prerequisites
  * Ensure you are running a 64-bit host, Docker is not supporting 32-bit hosts
  * Install docker from native repository as per official tutorial:
    https://docs.docker.com/engine/installation/linux/ubuntulinux/
  * Install openvswitch-vswitch on the host. Host kernel modules are required for vswitch related kernel calls ran in containers:

 ```bash
    	sudo apt-get install openvswitch-switch
 ```
    
#### Operation

##### Start
The starting point of this emulator is the `emulate_network.sh`. In other words, in order to emulate the network you just
need to run this script. 

##### Configuration
Prior to running the emulator, the first step would be to configure your setup.
We provide a single configuration file `config` where you can configure the emulator according to your needs.
In the following, the brief description of each configurable variable in the config file is given:

* **TARGET** - here you configure whether you are going to run your emulator on the *REMOTE* or *LOCAL* setup. Under the *REMOTE* 
 setup we asssume executing the bootstrapping environment on the VMWare ESXI hypervisor, i.e. having different VMs for each 
 SDN controller and a separate VM for an emulator. The VMs are interconnected and configured via ESXI admin interface.
 On the other hand, *LOCAL* execution assumes running everything on a single Linux PC. 
 This differentiation is necessary since the mechanisms of interconnecting SDN controllers with the emulator differ 
 depending on where you want to instantiate your setup.
 
* **BOOTSTRAPPING** - here you choose one of the currently two available bootstrapping schemes.
 The valid values that you can use to populate this variable are *STAND* and *ALTER*, referring to the standard 
 (with RSTP) and alternative (no RSTP) bootstrapping schemes respectively. Based on the configured value, the 
 ovs-vswitches will be booted with the different initial setup.
 
* **CON_NUM** - is the variable where you can configure how many SDN controllers in the emulated network you want to have.
 This variable, as well as  *CON_POSITION* are used to configure additional ports on some switches for the in-band control
 of the emulated network.

* **CON_POSITION** - is the bash array variable where you define on which switches the SDN controllers should be connected.
 Numbers that you put here correspond to the respective switch number in the chosen topology. To see where each switch is
 positioned in the selected topology, please consult the topology images that are provided in the topology folder.
 
* **CON_IP_START** - is the variable that is valid only if you select LOCAL setup. Here you can specify the starting IP
 address of the SDN controllers. The specified address is assigned to the first controller. If you have specified more than 
 one SDN controller, then all subsequent controllers will be assigned with the IP addresses in the incremental fashion 
 (e.g. SDNC-1 -> 10.10.0.10; SDNC-2 -> 10.10.0.11; ...).
 
* **AKKA_GOSSIP_PORT** - is the variable valid only if you select the alternative approach. It is used to configure tc policers
that prevent initial broadcast storms. It represents the excpected TCP port for the inter-controller traffic. 
 
* **TOPOLOGY** - here you specify the topology that you want to emulate. Check topology folder to see what topologies are 
 currently available. If you want to add your own topology, please have a look at the `example.sh` topology, where you can
 find some additional information on how to organize your new topology file.
 
##### Stop

 To stop the emulator and clean the setup, run the following command:
 ```bash
 sudo ./clean_up.sh
 ```

#### Additional information

Initial execution of the `emulate_network.sh` may take some time, because Docker needs to download some images and
packages initially.

##### STANDARD BOOTSTRAPPING SCHEME

The Docker instances are configured to run:
  * OpenvSwitch software switch (OpenFlow 1.3)
  * LLDPD implementation of LLDP agent with agentx extensions to support writing and partial configuration of net-snmp
  * SSH server
  * DHCP Client
  
OVS is by default configured to execute dhclient on br100 (bridge 100) and to run in standalone mode with enabled STP/RSTP, hence, acting as a standard L2 switch.
Default user:password for SSH connections to Docker instances is admin:admin. 

##### ALTERNATIVE BOOTSTRAPPING SCHEME

The Docker instances are configured to run:
  * OpenvSwitch software switch (OpenFlow 1.3)
  * SSH server
  * DHCP Client
  
OVS is by default configured to execute dhclient on br100 (bridge 100) and to run in secure mode with disabled STP/RSTP. OVS is precofigured with the set of 
initial OpenFlow rules that enable proper operation in secure mode and without STP/RSTP enabled.
Default user:password for SSH connections to Docker instances is admin:admin. 

##### LOCAL

For LOCAL testing we use separate network namespaces for each setup component. Each SDN controller is started in the `ODL-Controller-SDNC-x`
network namespace, where `x` denotes the controller order number. Then these namespaces are connected with the desired  OVS-Switches
through virtual interfaces. For more information on how to handle controllers in separate namespaces, please see the README
 file in the demo-sdn folder.
 
##### REMOTE

For REMOTE testing see the setup on VMWare ESXi hypervisor.   

##### Useful utility scripts and commands

In the *emulator/utility-scripts* folder you can find many scripts that normally have a self-explanatory name or provide
a brief description on how to use them inside the scripts themselves. These scripts provide various ways to 
monitor the running emulator.

