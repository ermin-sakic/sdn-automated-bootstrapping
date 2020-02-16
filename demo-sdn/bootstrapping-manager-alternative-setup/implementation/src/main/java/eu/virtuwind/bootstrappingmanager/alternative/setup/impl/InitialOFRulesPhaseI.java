/**
  *
  * @filename InitialOFRulesPhaseI.java
  *
  * @date 26.04.18
  *
  * @author Mirza Avdic
  *
  *
 */
package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.AnSshConnector;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.IPPrefAddrInfo;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import eu.virtuwind.registryhandler.impl.BootstrappingRegistryImpl;
import eu.virtuwind.registryhandler.impl.BootstrappingSwitchStateImpl;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingDatastore;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.ControllerIpListSlave;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingState;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.TreeUtils.estimateQuasiBroadcastingPortsBasedOnCurrentData;
import static eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.TreeUtils.findRootPort;

/**
 * The class that implements installing ill-tree rules.
 */
public class InitialOFRulesPhaseI implements ClusteredDataTreeChangeListener<SwitchBootsrappingState> {
    private static final Logger LOG = LoggerFactory.getLogger(InitialOFRulesPhaseI.class);
    private static DataBroker db = null;
    private static SalFlowService salFlowService = null;
    private static short flowTableId;
    private NodeId switchId;
    private static ArrayList<String> ctlList = new ArrayList<String>();
    private static String sshUsername, sshPassword = null;
    final ScheduledThreadPoolExecutor executorScheduler = new ScheduledThreadPoolExecutor(20);
    private static String cpNetworkPrefix;
    private static String topologyId;

    // set through the EOS
    private static boolean isLeader = false;

    // keep track of the nodes that already were this state
    public static List<String> initialOFPhaseIDoneNodes = new ArrayList<>();

    public InitialOFRulesPhaseI(DataBroker db, SalFlowService salFlowService) {
        this.db = db;
        this.salFlowService = salFlowService;
    }

    public static List<String> getInitialOFPhaseIDoneNodes() {
        return initialOFPhaseIDoneNodes;
    }

    public static short getFlowTableId() {
        return flowTableId;
    }

    public static void setFlowTableId(short flowTableId) {
        InitialOFRulesPhaseI.flowTableId = flowTableId;
    }

    public static void setCtlList(ArrayList<String> ctlList) {
        InitialOFRulesPhaseI.ctlList = ctlList;
    }

    public static String getCpNetworkPrefix() {
        return cpNetworkPrefix;
    }

    public static void setCpNetworkPrefix(String networkPrefix) {
        InitialOFRulesPhaseI.cpNetworkPrefix = networkPrefix;
    }

    public static String getTopologyId() {
        return topologyId;
    }

    public static void setTopologyId(String topologyId) {
        InitialOFRulesPhaseI.topologyId = topologyId;
    }


    public static void setSSHConfiguration(String username, String password) {
        sshUsername = username;
        sshPassword = password;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<SwitchBootsrappingState>> changes) {

            for (DataTreeModification<SwitchBootsrappingState> change : changes) {

                // get a node-id on which the change has happened
                switchId = change.getRootPath().getRootIdentifier().firstKeyOf(Node.class).getId();
                //get a state before the change -> can throw NullPointerException
                String previousState = null;
                try {
                    previousState = change.getRootNode().getDataBefore().getState().getName();
                    LOG.debug("InitialOFRulesPhaseI for the switch {} and previous switch state {}", switchId, previousState);

                } catch (NullPointerException e) {
                    LOG.warn("First time OF-SESSION-ESTABLISHED with node {}", switchId.getValue());
                }

                // get a state after the change has happened
                String afterState = change.getRootNode().getDataAfter().getState().getName();
                LOG.debug("InitialOFRulesPhaseI for the switch {} and after state {}", switchId, afterState);
                if (isLeader) {

                    if (afterState.equals(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYDONE.getName())
                            && previousState != null
                            && previousState.equals(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYRULEINSTALLED.getName())
                            && !initialOFPhaseIDoneNodes.contains(switchId.getValue())) {
                        LOG.info("Initial OF Rules Phase I ready for node {}", switchId.getValue());

                        // Follower controllers will change the state of a switch to CONTROLLERSELFDISCOVERYDONE.
                        // The leader expects this when a switch reaches this piece of code.
                        //synchronized (ControllerSelfDiscovery.initialSelfDiscoveryDoneNodesLock) {
                            if (!ControllerSelfDiscovery.initialSelfDiscoveryDoneNodes.contains(switchId.getValue())) {
                                ControllerSelfDiscovery.initialSelfDiscoveryDoneNodes.add(switchId.getValue());
                            }
                        //}

                        //Thread executor = new Thread(new InitialOFRulesPhaseIExecutorNormal());
                        Thread executor = new Thread(new InitialOFRulesPhaseIExecutorTree());

                        executor.start();
                        try { // wait till the InitialOFRules have been installed in order to change the switch state
                            executor.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        synchronized (this) {
                            BootstrappingSwitchStateImpl stateManager = BootstrappingSwitchStateImpl.getInstance();
                            stateManager.writeBootstrappingSwitchState(switchId, new SwitchBootsrappingStateBuilder()
                                    .setState(SwitchBootsrappingState.State.INITIALOFRULESPHASEIDONE)
                                    .build());
                            String currentState = stateManager.readBootstrappingSwitchStateName(switchId);
                            LOG.info("InitialOFRulesPhaseI state in node {} changed to {}", switchId.getValue(), currentState);
                            initialOFPhaseIDoneNodes.add(switchId.getValue());
                        }
                    }

                } else {
                    LOG.info("Not a leader!");
                }
            }
    }

    private class InitialOFRulesPhaseIExecutorTree implements Runnable {

        @Override
        public void run() {

            InstanceIdentifier<Node> nodeId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, new NodeKey(switchId)).build();
            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeId, flowTableId);
            String switchIpAddress = InitialFlowUtils.getNodeIPAddress(switchId, db);


            if(ctlList.size() == 0) {
                // Retrieve the controller IP lists
                BootstrappingDatastore datastore = BootstrappingRegistryImpl.getInstance().readBootstrappingConfigurationFromDataStore();

                assert datastore != null;
                ctlList.add(datastore.getControllerIpMaster());

                List<ControllerIpListSlave> slaveCtlIpAddr = datastore.getControllerIpListSlave();

                for(ControllerIpListSlave ctlIp:slaveCtlIpAddr)
                    ctlList.add(ctlIp.getIpAddr());
            }

            HostUtilities.InterfaceConfiguration myIfConfig =
                    HostUtilities.returnMyIfConfig(ctlList);
            if (myIfConfig == null) {
                LOG.warn("Provided controller IP addresses do not found on this machine.");
                return;
            }
            String ctlIpAddr = myIfConfig.getIpAddr();

            String rootPort = "";
            while (rootPort == ""){
                rootPort = findRootPort(switchId.getValue(), ctlIpAddr, topologyId );
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            LOG.info("For node {} this is the root port {}", switchId.getValue(), rootPort);

            // add more processing since quasiMstTreePorts are still not available in order to avoid broadcasting storms
            List<String> quasiMstTreePortsEstimation = null;
            try {
                quasiMstTreePortsEstimation = estimateQuasiBroadcastingPortsBasedOnCurrentData(switchId.getValue(), ctlIpAddr,
                        topologyId);
            } catch (InterruptedException e) {
                for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                    LOG.error("InitialOFRulesPhaseI: Stack root cause trace -> {}", m);
                }
            }

            LOG.info("For node {} these are estimated quasi ports {}", switchId.getValue(), quasiMstTreePortsEstimation.toString());

            // remember these rules to remove them in phase II
            List<FlowCookie> rulesToBeRemoved = new ArrayList<>();
            // remember these rules to remove them when unicast rules are installed
            List<FlowCookie> rulesToBeRemovedAfterUnicast = new ArrayList<>();

            LOG.info("Setting OF flows.");
            /*  OF TRAFFIC RULES */
            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            InstanceIdentifier<Flow> flowIdFromController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            InstanceIdentifier<Flow> flowIdToController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            InstanceIdentifier<Flow> flowIdFromControllerForSwitch = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow flowFromController = null;
            Flow flowToController = null;
            Flow flowFromControllerForSwitch = null;

            // OF traffic for me (install first otherwise no connection :))
            List<String> oPorts = new ArrayList<String>();
            oPorts.add("local");
            IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
            IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo();
            dstPrefix.setIpv4Prefix(new Ipv4Prefix(switchIpAddress + "/32"));

            flowFromControllerForSwitch = InitialFlowUtils.createIPLayer4Flow(flowTableId, new PortNumber(6633),
                    srcPrefix, null, dstPrefix, 101, oPorts);
            rulesToBeRemovedAfterUnicast.add(flowFromControllerForSwitch.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdFromControllerForSwitch, flowFromControllerForSwitch);

            // OF traffic for the controller
            oPorts = new ArrayList<String>();
            oPorts.add(rootPort);
            srcPrefix = new IPPrefAddrInfo();
            dstPrefix = new IPPrefAddrInfo(); // leave intentionally empty
            dstPrefix.setIpv4Prefix(new Ipv4Prefix(ctlIpAddr + "/32"));

            flowToController = InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcPrefix, new PortNumber(6633), dstPrefix, 100, oPorts);
            rulesToBeRemoved.add(flowToController.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdToController, flowToController);

            // OF traffic for other nodes from the controller
            oPorts = quasiMstTreePortsEstimation;
            srcPrefix = new IPPrefAddrInfo();
            dstPrefix = new IPPrefAddrInfo();
            srcPrefix.setIpv4Prefix(new Ipv4Prefix(ctlIpAddr + "/32"));
            dstPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));

            flowFromController = InitialFlowUtils.createIPLayer4Flow(flowTableId, new PortNumber(6633), srcPrefix, null, dstPrefix, 100, oPorts);
            rulesToBeRemoved.add(flowFromController.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdFromController, flowFromController);


            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            /*  LLDP TRAFFIC RULES */
            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            LOG.info("Setting LLDP flows.");
            //add LLDP-to-Controller flows //try root also here
            InstanceIdentifier<Flow> flowId = InitialFlowUtils.getFlowInstanceId(tableId,  InitialFlowWriter.flowIdInc.getAndIncrement());
            // not using
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowId, InitialFlowUtils.createLldpToControllerFlow(flowTableId, 100, null));

            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            /*  DHCP TRAFFIC RULES */
            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            LOG.info("Setting DHCP flows.");
            InstanceIdentifier<Flow> flowIdDiscReq = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            // sometimes the rule is not installed with this method
            // Flow flowDiscReq = InitialFlowUtils.createDhcpFlow(flowTableId, true, new PortNumber(68), new PortNumber(67), 100, rootPort);
            List<String> rootPortList = new ArrayList<>();
            rootPortList.add(rootPort);
            Flow flowDiscReq = InitialFlowUtils.createDhcpFlowMultipleOutputPorts(flowTableId, true, new PortNumber(68), new PortNumber(67), 100, rootPortList);
            rulesToBeRemoved.add(flowDiscReq.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdDiscReq, flowDiscReq);

            InstanceIdentifier<Flow> flowIdOfferAck = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow flowOfferAck = InitialFlowUtils.createDhcpFlowMultipleOutputPorts(flowTableId, true, new PortNumber(67), new PortNumber(68), 100, quasiMstTreePortsEstimation);
            rulesToBeRemoved.add(flowOfferAck.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdOfferAck, flowOfferAck);


            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            /*  SSH TRAFFIC RULES */
            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            LOG.info("Setting SSH flows.");

            // SSH for the controller
            InstanceIdentifier<Flow> flowIdSSHToController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            //Flow flowSSHToController = InitialFlowUtils.createSSHFlow(flowTableId, false, new PortNumber(22),
            //        null ,100, rootPort);

            oPorts = new ArrayList<>();
            oPorts.add(rootPort);
            srcPrefix = new IPPrefAddrInfo();
            dstPrefix = new IPPrefAddrInfo();
            dstPrefix.setIpv4Prefix(new Ipv4Prefix(ctlIpAddr + "/32"));

            Flow flowSSHToController = InitialFlowUtils.createIPLayer4Flow(flowTableId, new PortNumber(22), srcPrefix,
                    null, dstPrefix,  100, oPorts);
            rulesToBeRemoved.add(flowSSHToController.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdSSHToController, flowSSHToController);

            // SSH for the switches
            InstanceIdentifier<Flow> flowIdSSHFromController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            //Flow flowSSHFromController = InitialFlowUtils.createSSHFlowMultiplePorts(flowTableId, false, null,
            //        new PortNumber(22),100, quasiMstTreePortsEstimation);

            srcPrefix = new IPPrefAddrInfo();
            dstPrefix = new IPPrefAddrInfo();
            srcPrefix.setIpv4Prefix(new Ipv4Prefix(ctlIpAddr + "/32"));

            Flow flowSSHFromController = InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcPrefix,
                    new PortNumber(22), dstPrefix,  100, quasiMstTreePortsEstimation);
            rulesToBeRemoved.add(flowSSHFromController.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdSSHFromController, flowSSHFromController);

            // SSH traffic for LOCAL
            oPorts = new ArrayList<>();
            oPorts.add("local");
            IPPrefAddrInfo dst = new IPPrefAddrInfo();
            IPPrefAddrInfo src = new IPPrefAddrInfo();
            dst.setIpv4Prefix(new Ipv4Prefix(switchIpAddress + "/32"));

            InstanceIdentifier<Flow> flowIdSSHLocal = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow flowSSHFromControllerForSwitch = InitialFlowUtils.createIPLayer4Flow(flowTableId, null, src, new PortNumber(22), dst, 101, oPorts);
            rulesToBeRemovedAfterUnicast.add(flowSSHFromControllerForSwitch.getCookie());
            // it does not check srcPrefix for null you have to provide an object otherwise will not work
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdSSHLocal, flowSSHFromControllerForSwitch);

            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            /*  ARP TRAFFIC RULES */
            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            LOG.info("Setting ARP flows intended for the switch.");
            // ADD THE ARP/NDP FLOW USED FOR SWITCH DISCOVERY
            oPorts = new ArrayList<>();
            oPorts.add("local");

            InstanceIdentifier<Flow> flowIdtest1 = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow flowARPFromControllerForSwitch = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 101, new Ipv4Prefix(switchIpAddress + "/32"), null, oPorts);
            rulesToBeRemovedAfterUnicast.add(flowARPFromControllerForSwitch.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdtest1, flowARPFromControllerForSwitch);

            LOG.info("Setting ARP flows used for controller discovery.");
            // ADD THE ARP/NDP FLOW USED FOR CONTROLLER DISCOVERY

            for (String ctl : ctlList) {
                if (ctl.equals(ctlIpAddr)) {
                    InstanceIdentifier<Flow> flowIdARPRoot = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());

                    oPorts = new ArrayList<>();
                    oPorts.add(rootPort);

                    Flow flowARPRoot = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 101, new Ipv4Prefix(ctl + "/32"), null, oPorts);
                    rulesToBeRemoved.add(flowARPRoot.getCookie());
                    InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdARPRoot, flowARPRoot);


                    LOG.info("ARP Broadcasting not for LOCAL or ROOT");

                    InstanceIdentifier<Flow> flowArpQuasiBroadcastId = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                    Flow flowArpQuasiBroadcast = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 100, new Ipv4Prefix(cpNetworkPrefix),
                            new Ipv4Prefix(ctl + "/32"), quasiMstTreePortsEstimation);
                    rulesToBeRemoved.add(flowArpQuasiBroadcast.getCookie());
                    InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowArpQuasiBroadcastId, flowArpQuasiBroadcast);

                }
            }

            // remember rules for removal in the phase II
            InitialFlowUtils.removableFlowCookiesPhaseI.put(switchId.getValue(), rulesToBeRemoved);
            // remember rules for removal after unicast rules
            InitialFlowUtils.removableFlowsIdentifiedByCookiesAfterUnicast.put(switchId.getValue(), rulesToBeRemovedAfterUnicast);


            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            /*
                Sometimes, for some unknown reason, some rules from the phase I are not installed.
                Since the next level configuration depends on the correctly configured
                previous level, we have to ensure that these rules are always installed.
                Otherwise, the scheme will hang in this state forever.

                The idea here is to start a periodic checker, after the rules are "installed"
                for the first time, that will check if all the rules from the phase I are installed.
                If yes, it will stop its execution, if not, it will try to install them again and do checking again.

                PROBLEM: Need more coordination, sometimes scheduled after the rules are deleted in the InitialOFRulesPhaseII,
                even though it has been called much sooner than the InitialOFRulesPhaseII.
                It installs rules again, even though they were deleted, which leads to inconsistent flow table sizes.

             */
            //executorScheduler.schedule(new CheckIfPhaseICorrectlyInstalled(switchIpAddress, switchId), 100, TimeUnit.MILLISECONDS);
        }
    }

    private class CheckIfPhaseICorrectlyInstalled implements Runnable {

        private String switchIp;
        private NodeId switchId;
        private static final int SSH_PORT = 22;
        private static final int SSH_TIMEOUT = 5000;

        CheckIfPhaseICorrectlyInstalled(String switchIp, NodeId switchId) {
            this.switchIp = switchIp;
            this.switchId = switchId;
        }

        @Override
        public void run() {
            LOG.info("OF PhaseI Rules checker started for {}:{}!", switchId.getValue(), switchIp);
            // retrieve output of ovs-ofctl dump-flows br100
            AnSshConnector sshConnector = new AnSshConnector(switchIp, SSH_PORT, sshUsername, sshPassword, SSH_TIMEOUT);
            String command = "echo '" + sshPassword + "' | sudo -kS ovs-ofctl -O OpenFlow13 dump-flows br100";
            String output = new String();
            try {
                if(!sshConnector.isOpen())
                    sshConnector.open();
                 output = sshConnector.executeCommand(command);
                LOG.info("dump-flows for switch {}: \n {}", switchIp, output);
            } catch (Exception e) {
                e.printStackTrace();
                for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                    LOG.info("OF Phase I Rules checker: Stack root cause trace -> {}", m);
                }
            }

            LOG.info("Checking if all PhaseI rules are installed on node {}", switchId.getValue());
            List<FlowCookie> phaseIFlowRuleCookies =  InitialFlowUtils.removableFlowCookiesPhaseI.get(switchId.getValue());
            boolean allPhaseIRulesInstalled = true;
            for (FlowCookie flowCookie: phaseIFlowRuleCookies) {
                LOG.info("Cookie: {}", flowCookie.getValue().toString());
                LOG.info("Cookie HEX: {}", String.format("0x%x",flowCookie.getValue()));
                if (output.contains(String.format("0x%x",flowCookie.getValue()))) {
                    continue;
                } else {
                    allPhaseIRulesInstalled = false;
                    break;
                }
            }

            if (!allPhaseIRulesInstalled) {
                LOG.info("Some OF rules from the PhaseI are missing. Trying to install them again...");
                executorScheduler.execute(new InitialOFRulesPhaseIExecutorTree());
            } else {
                LOG.info("All OF rules from the PhaseI are installed. Periodic checker stopped!");
            }
        }
    }

    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("InitialOFRulesPhaseI ownership change logged: " + ownershipChange);

        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the InitialOFRulesPhaseI leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the InitialOFRulesPhaseI follower.");
            setFollower();
        }
    }

    /**
     * Confirms this instance is the current cluster leader.
     */
    public static void setLeader() {isLeader = true;}

    /**
     * Sets this instance as a cluster follower.
     */
    public static void setFollower() {isLeader = false;}
}
