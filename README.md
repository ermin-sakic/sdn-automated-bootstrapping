# Automated Bootstrapping of In-Band Controlled SDNs

OpenDaylight-compatible modules and exemplary network emulation for bootstrapping of a multi-controller OpenFlow-based in-band control plane.

Full details of the designs are provided in our ACM SIGCOMM SOSR 2020 publication titled: "Automated Bootstrapping of A Fault-Resilient In-Band Control Plane".

By default, the provided configurations implement the HHC approach (no reliance on RSTP) and they do so in a local environment, i.e., all controller instances and switches are instantiated in separate network namespaces in the local host.

---


## Directory Explanations
- *sdn-demo*: Holds the core controller code for building of the ODL distribution.

- *emulated-topolgoy*: Orchestrates the network emulation for easy setup of all topologies described in the paper. 3x3 grid is selected as default. The accompanying config file allows for topology selection, modifications to controller(s) placement, and local / remote emulation. 

- *odl-dependencies*: Contains the modified accompanying modules, used in OpenFlow interactions, host / controller topology discovery etc. The implementation of this differs slightly from same-named off-the-shelf ODL modules (details provided in the paper).

---

## Design

Overview of the two approaches for bootstrapping of in-band control networks:
[![HSW Bootstrapping Design (relies on RSTP support in data plane)](figures/hsw_seq.png)]()

[![HHC Iterative Bootstrapping Design (no RSTP requirement)](figures/hhc_seq.png)]()

Details are presented in attached publication.

## Usage

### Build Tools:
- OpenJDK 8+
- Docker-CE latest package (each OVS instance is hosted in a separate container interconnected using veth pairs)
- Maven

### Integration tools:
- openvswitch-switch
- Postman (Google-Chrome App)
- Evaluation / Measurement [optional]: Python3 & PyPlot/Matplotlib

### Build Guide:
- Build the customized openflowplugin, odl-l2switch and openflowjava components using ```build.sh``` in the corresponding sub-directories of ```odl-dependencies```.
- Build the ```emulated-topology``` (ref. contained ```bash README.md``` for further instructions)
- Copy the ```run_arping.sh``` script to your chosen location and update the path to script correspondingly in ```demo_sdn/configure_arping_local_remote.sh```
- Build the ```bash demo-sdn``` using the ```build.sh``` script inside (ref. contained ```bash README.md``` for further information)

### Runtime:
- Run the ```bash emulate_network.sh``` script in ```emulated-topology```
- In demo-sdn, run one of the ```bash exec*``` scripts after the 1st script above has finished (the network and controller namespace are necessary for controller instantiation)
- Execute the REST script for startup of DHCP server (POSTMAN attached) - only if enabled in XML files starting with "149"

---

## Support

Reach out to us:

- Twitter at <a href="http://twitter.com/TUM_LKN" target="_blank">`@TUM_LKN`</a>

---

## License

[![License](https://img.shields.io/badge/License-EPL%201.0-red.svg)](https://opensource.org/licenses/EPL-1.0)

- **[EPL license](https://opensource.org/licenses/EPL-2.0)**
