# Build & Run

## Build without tests or style checks
<code>mvn clean install -DskipTests -Dcheckstyle.skip=true</code>

## Build only changed part 
<code>mvn install -pl <module-name> -am</code>

# Exec distro
Before executing one of the following described *exec* scripts, enter the script and comment/uncomment the desired bootstrapping scheme. 
### Remote one controller
`./exec_karaf_remote.sh ` or
`./exec_karaf_remote_background.sh `
### Remote multiple controllers
`./exec_karaf_cluster.sh <cluster-member-number>` or
`./exec_karaf_cluster_background.sh <cluster-member-number>`
### Local one controller
`./exec_karaf_local.sh ` or
`./exec_karaf_local_background.sh `
### Local multiple controllers (still not tested)
`./exec_karaf_cluster_local.sh <cluster-member-number>` 

# Additional information

#### How to switch between a current implementation and an alternative approach

##### Without building Karaf again
Just comment/uncomment the wanted scheme in the *exec* scripts as mentioned above. The *exec* scripts run internally the custom bash scripts shown below.

For configuring which Karaf feature regarding the bootstrapping scheme you want to use, use the following commands:

###### Standard bootstrapping scheme
```bash
sudo ./change_virtuwind_boot_feature.sh stand
```

###### Alternative bootstrapping scheme
```bash
sudo ./change_virtuwind_boot_feature.sh alter
```

##### With building Karaf again

To load desired features during the Karaf boot time comment/uncomment the folowing lines in *distribution/opendaylight-karaf/pom.xml* :

```xml
<!--<karaf.localFeature>virtuwind-intra</karaf.localFeature>-->
<karaf.localFeature>virtuwind-intra-alternative</karaf.localFeature>
```

Build the project again!

You can avoid boot time deployment (comment both previously shown lines) and do hot deployment using standard Karaf commands:

* `feature:install virtuwind-intra`
* `feature:install virtuwind-intra-alternative`

To uninstall some features use:

* `feature:uninstall <feature-name>`

#### Information regarding testing on the local computer:

For local tests run `./exec_karaf_local.sh`.
The  ODL controller will be executed on the host computer.
The controller is started in a separate network namespace (ODL-Controller-SDNC-1 for just one controller).
Run all programs related to the controller in the controller network namespace (currently ODL-Controller-SDNC-1).
Useful commands: 
* `xhost +` - grant all users/namespaces access to X Window System Server
* `sudo ip netns exec ODL-Controller-SDNC-1 postman &` - for sending REST requests to ODL 
* `sudo ip netns exec ODL-Controller-SDNC-1 wireshark &` - for monitoring traffic on the controller interface (veth-SDNC-1-c)
* `sudo ip netns exec ODL-Controller-SDNC-1 firefox -P -no-remote &` - for DLUX services for instance

#### Controller Self-Discovery

* *Manually*: `sudo ip netns exec ODL-Controller-SDNC-1 ~/run_arping.sh 10.10.0.101 veth-SDNC-1-c` - used only for debugging purposes

*  *Automatic*: Configure arping path in the *bootstrapping-manager-alternative-setup* config XML file like this: `<arping-path>sudo ip netns exec ODL-Controller-SDNC-1 /home/ermin/run_arping.sh</arping-path>`
You can use the available script `configure_arping_local_remote.sh` to automatically toggle the arping configuration between the local and the remote setup, if you have both present in your XML file:

    *  **LOCAL**: `configure_arping_local_remote.sh LOCAL`
    *  **REMOTE**: `configure_arping_local_remote.sh REMOTE`

#### Template configuration XML files
The following XML files are used by *exec* scripts as default config files for different execution environments and scenarios: 
* 149-bootstrapping-manager-alternative-setup-LOCAL.xml           
* 149-bootstrapping-manager-alternative-setup-REMOTE-CLUSTER.xml  
* 149-bootstrapping-manager-alternative-setup-REMOTE.xml          
* 149-bootstrapping-manager-setup-LOCAL.xml                       
* 149-bootstrapping-manager-setup-REMOTE-CLUSTER.xml              
* 149-bootstrapping-manager-setup-REMOTE.xml                      
* 150-bootstrapping-manager-alternative-dhcp-LOCAL.xml            
* 150-bootstrapping-manager-alternative-dhcp-REMOTE.xml           
* 150-bootstrapping-manager-dhcp-LOCAL.xml                        
* 150-bootstrapping-manager-dhcp-REMOTE.xml   


