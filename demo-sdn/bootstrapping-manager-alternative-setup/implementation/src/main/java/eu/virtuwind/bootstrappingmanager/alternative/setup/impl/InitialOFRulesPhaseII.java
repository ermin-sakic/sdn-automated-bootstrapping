/**
  *
  * @filename InitialOFRulesPhaseII.java
  *
  * @date 30.04.18
  *
  * @author Mirza Avdic
  *
  *
 */  
package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.HopByHopTreeGraph;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.NetworkGraphService;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.TreeUtils;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.IPPrefAddrInfo;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.ScheduledThreadPoolExecutorWrapper;
import eu.virtuwind.registryhandler.impl.BootstrappingRegistryImpl;
import eu.virtuwind.registryhandler.impl.BootstrappingSwitchStateImpl;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingDatastore;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.ControllerIpListSlave;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.SwitchBootstrappingAugmentation;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.TreeUtils.*;
import static eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils.getAllRealNodes;

/**
 * The class that implements installing tree rules.
 */
public class InitialOFRulesPhaseII implements ClusteredDataTreeChangeListener<SwitchBootsrappingState> {
    private static final Logger LOG = LoggerFactory.getLogger(InitialOFRulesPhaseII.class);

    // MD-SAL access
    private static DataBroker db = null;
    private static SalFlowService salFlowService = null;

    // NetworkGraphService reference
    private static NetworkGraphService networkGraphService = null;

    // XML config populated in SetupModule
    private static short flowTableId;
    private static String cpNetworkPrefix;
    private static String topologyId;
    private static ArrayList<String> ctlList = new ArrayList<String>();

    //  Thread pools
    private static ScheduledThreadPoolExecutorWrapper executorScheduler = new ScheduledThreadPoolExecutorWrapper(20);
    private static ScheduledThreadPoolExecutorWrapper executorSchedulerRefresher = new ScheduledThreadPoolExecutorWrapper(20);

    // Refreshers' config
    private static Long DEFAULT_MONITOR_REFRESH_PERIOD = 500L; // can be arbitrarily set up (a little bit larger than LLDPSpeakerEdge flooding interval)
    private static HashMap<Integer, Boolean> isRefresherStartedForThisLevel = new HashMap<>();
    private final static String nextLevelTriggerLock = "LOCK";
    private final static  String nextLevelTriggerRefresherLock = "LOCK";
    private final static HashMap<Integer, Integer> nextLevelTriggerRefresherExecCounters = new HashMap<>();
    private static Long maxExecNum = 30L;
    private static boolean nextLevelTriggerRefreshersFiniteEnabled = false;

    // For performance reasons cache already processed levels
    private static HashMap<Integer, Integer> numberOfNodesByTreeLevelsCache = new HashMap<>();

    // Set through the EOS
    private static boolean isLeader = false;

    // Keep track of the nodes that already were this state
    public static List<String> initialOFPhaseIIDoneNodes = new ArrayList<>();

    // Remember tree rules installed in switches in order to delete them later if swapping an alternative tree
    private static HashMap<FlowCookie, String> removableTreeFlowCookies = new HashMap<FlowCookie, String>();


    public InitialOFRulesPhaseII(DataBroker db, SalFlowService salFlowService, NetworkGraphService ngs) {
        this.db = db;
        this.salFlowService = salFlowService;
        this.networkGraphService = ngs;
    }


    public static List<String> getInitialOFPhaseIIDoneNodes() {
        return initialOFPhaseIIDoneNodes;
    }

    public static short getFlowTableId() {
        return flowTableId;
    }

    public static void setFlowTableId(short flowTableId) {
        InitialOFRulesPhaseII.flowTableId = flowTableId;
    }

    public static boolean isNextLevelTriggerRefreshersFiniteEnabled() {
        return nextLevelTriggerRefreshersFiniteEnabled;
    }

    public static void setNextLevelTriggerRefreshersFiniteEnabled(boolean nextLevelTriggerRefreshersEnabled) {
        InitialOFRulesPhaseII.nextLevelTriggerRefreshersFiniteEnabled = nextLevelTriggerRefreshersEnabled;
    }

    public static Long getDefaultMonitorRefreshPeriod() {
        return DEFAULT_MONITOR_REFRESH_PERIOD;
    }

    public static void setDefaultMonitorRefreshPeriod(Long defaultMonitorRefreshPeriod) {
        DEFAULT_MONITOR_REFRESH_PERIOD = defaultMonitorRefreshPeriod;
    }

    public static void setMaxExecNum(Long maxExecNum) {
        InitialOFRulesPhaseII.maxExecNum = maxExecNum;
    }

    public static void setCpNetworkPrefix(String cpNetworkPrefix) {
        InitialOFRulesPhaseII.cpNetworkPrefix = cpNetworkPrefix;
    }

    public static void setCtlList(ArrayList<String> ctlList) {
        InitialOFRulesPhaseII.ctlList = ctlList;
    }

    public static String getTopologyId() {
        return topologyId;
    }

    public static void setTopologyId(String topologyId) {
        InitialOFRulesPhaseII.topologyId = topologyId;
    }

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<SwitchBootsrappingState>> changes) {

        for (DataTreeModification<SwitchBootsrappingState> change : changes) {

            NodeId switchId = change.getRootPath().getRootIdentifier().firstKeyOf(Node.class).getId();

            //get a state before a change -> can throw NullPointerException
            String previousState = null;
            try {
                previousState = change.getRootNode().getDataBefore().getState().getName();
                LOG.info("InitialOFRulesPhaseII for the switch {} and previous switch state {}", switchId, previousState);

            } catch (NullPointerException e) {
                LOG.info("First time OF-SESSION-ESTABLISHED with node {}", switchId.getValue());
            }
            // get a state after a change has happened
            String afterState = change.getRootNode().getDataAfter().getState().getName();
            LOG.info("InitialOFRulesPhaseII for the switch {} and after state {}", switchId, afterState);

            if (isLeader) {

                    if (afterState.equals(SwitchBootsrappingState.State.INITIALOFRULESPHASEIDONE.getName())
                            && previousState != null
                            && previousState.equals(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYDONE.getName())
                            && !initialOFPhaseIIDoneNodes.contains(switchId.getValue())) {

                        LOG.info("PHII: Initial OF Rules Phase II ready for node {}", switchId.getValue());

                        Thread executor = new Thread(new InitialOFRulesPhaseII.InitialOFRulesPhaseIIExecutor(switchId));
                        executor.start();

                        try {
                            executor.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        synchronized (this) {
                            BootstrappingSwitchStateImpl stateManager = BootstrappingSwitchStateImpl.getInstance();
                            stateManager.writeBootstrappingSwitchState(switchId, new SwitchBootsrappingStateBuilder()
                                    .setState(SwitchBootsrappingState.State.INITIALOFRULESPHASEIIDONE)
                                    .build());
                            initialOFPhaseIIDoneNodes.add(switchId.getValue());
                            String currentState = stateManager.readBootstrappingSwitchStateName(switchId);
                            LOG.info("PHII: InitialOFRulesPhaseII state changed to {} for the node {} ", currentState, switchId.getValue());
                        }

                        executorScheduler.execute(new NextLevelTrigger(
                                new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId(
                                        switchId.getValue())));

                    }
                } else {
                    LOG.info("Not a leader!");
                }
            }
    }

    /**
     * Generating and installing tree rules.
     */
    private class InitialOFRulesPhaseIIExecutor implements Runnable {

        private NodeId switchId;

        public InitialOFRulesPhaseIIExecutor(NodeId switchId) {
            this.switchId = switchId;
        }

        /**
         * Make a string from the list of nodeConnectors
         * @param nodeConnectors
         * @return
         */
        private String extractNodeConnectorsToString(List<NodeConnector> nodeConnectors) {
            StringBuilder sb = new StringBuilder();
            for (NodeConnector nc: nodeConnectors) {
                sb.append(nc.getId().getValue());
                sb.append(" ");
            }
            sb.append(System.lineSeparator());

            return sb.toString();
        }

        @Override
        public void run() {

            // find functional ports of the switch -> Inventory DS
            List<NodeConnector> functionalNodeConnectors = InitialFlowUtils.getAllFunctionalNodeConnectorsFromNode(switchId.getValue(), db);
            LOG.info("NodeConnectors that are functional for node {} -> {}", switchId.getValue(), extractNodeConnectorsToString(functionalNodeConnectors));

            // wait till all the functional NodeConnectors appear in NetworkTopology DS
            while (!InitialFlowUtils.nodeConnectorsInTopology(functionalNodeConnectors, db)) {
                LOG.info("All available links on node {} still not discovered", switchId.getValue());
                sleep_some_time(150);
            }
            LOG.info("All links connected to node {} are discovered.", switchId.getValue());

            // wait till the tree algorithm has processed all the functional ports
            while (!networkGraphService.ifNodeConnectorsProcessedByTree(functionalNodeConnectors)) {
                LOG.info("All functional NodeConnectors on node {} still not processed by the tree algorithm", switchId.getValue());
                sleep_some_time(100);
            }
            LOG.info("All NodeConnectors from the node {} processed by the tree algorithm.", switchId.getValue());
            LOG.info("Ready for InitialOFPHASEII");

            // when initial config is provided via REST
            if (ctlList.size() == 0) {
                // Retrieve the controller IP lists
                BootstrappingDatastore datastore = BootstrappingRegistryImpl.getInstance().readBootstrappingConfigurationFromDataStore();

                assert datastore != null;
                ctlList.add(datastore.getControllerIpMaster());

                List<ControllerIpListSlave> slaveCtlIpAddr = datastore.getControllerIpListSlave();

                for (ControllerIpListSlave ctlIp : slaveCtlIpAddr)
                    ctlList.add(ctlIp.getIpAddr());
            }

            // find interface of the controller instance on which this code is being executed
            HostUtilities.InterfaceConfiguration myIfConfig =
                    HostUtilities.returnMyIfConfig(ctlList);
            if (myIfConfig == null) {
                LOG.warn("Provided controller IP addresses do not found on this machine.");
                return;
            }
            String ctlIpAddr = myIfConfig.getIpAddr();

            // find the tree ports for the given switchId
            List<String> treePorts = findTreePorts(switchId.getValue());
            LOG.debug("IOFII: Node:{} -> tree ports: {}", switchId.getValue(), treePorts.toString());

            /**
             * Older algorithm version
             */
            /*
            // find the tree port leading to the controller
            String rootPort = findRootPort(switchId.getValue(), ctlIpAddr, topologyId);
            // find the tree ports that do not lead to the controller
            List<String> quasiMstTreePorts = findQuasiBroadcastPorts(switchId.getValue(), ctlIpAddr, topologyId);

            //**********************************************************************************************************
            // if tree was not ready
            // (should not be the case because we wait until  NodeConnectors are processed by the tree algorithm) - try to estimate tree ports
            // this was the older version of the tree algorithm
            if (quasiMstTreePorts == null) {
                    // should not happen, depends if the timeout value has been chosen properly
                    try {
                        quasiMstTreePorts = estimateQuasiBroadcastingPortsBasedOnCurrentData(switchId.getValue(), ctlIpAddr,
                            topologyId);
                    } catch (InterruptedException e) {
                        for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                            LOG.error("InitialOFRulesPhaseII: Stack root cause trace -> {}", m);
                        }
                    }
                    LOG.debug("IOFII: Node: {} has estimated quasi broadcast ports: {}", switchId.getValue(), quasiMstTreePorts.toString());
            }
            //**********************************************************************************************************
            */

            LOG.info("Setting OF flows. Overwriting rules from the Phase I.");

            // identifiers for salFlowService
            InstanceIdentifier<Node> nodeId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, new NodeKey(switchId)).build();
            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeId, flowTableId);

            // remember rules for later eventual removal
            List<FlowCookie> rulesToRemember = new ArrayList<>();

            /* Port aggregation in one list */
            List<String> treeUsablePorts = treePorts;

            /*
               Older version
             */
            /*
            List<String> treeUsablePorts = new ArrayList<>();
            treeUsablePorts.add(rootPort);
            for (String port: quasiMstTreePorts) {
                treeUsablePorts.add(port);
            }
            */

            LOG.debug("IOFII: Node:{} -> treeUsablePorts: {}", switchId.getValue(), treeUsablePorts.toString());

            /*  OF TRAFFIC RULES */
            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
            IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo();
            for (String inPort: treeUsablePorts) {
                List<String> outPorts = new ArrayList<>(treeUsablePorts);
                outPorts.remove(inPort);

                InstanceIdentifier<Flow> flowIdFromController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                InstanceIdentifier<Flow> flowIdToController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                // OF traffic for the controller broadcast
                srcPrefix = new IPPrefAddrInfo();
                dstPrefix = new IPPrefAddrInfo(); // leave intentionally empty
                //dstPrefix.setIpv4Prefix(new Ipv4Prefix(ctlIpAddr + "/32"));
                srcPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));
                dstPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));


                Flow flowToController = InitialFlowUtils.createIPLayer4FlowGeneral(flowTableId, null, srcPrefix, new PortNumber(6633), dstPrefix, 99, inPort, outPorts, switchId.getValue());
                rulesToRemember.add(flowToController.getCookie());
                InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdToController, flowToController);

                // OF traffic for other nodes from the controller broadcast
                srcPrefix = new IPPrefAddrInfo();
                dstPrefix = new IPPrefAddrInfo();
                srcPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));
                dstPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));

                Flow flowFromController = InitialFlowUtils.createIPLayer4FlowGeneral(flowTableId, new PortNumber(6633), srcPrefix, null, dstPrefix, 99, inPort, outPorts, switchId.getValue());
                rulesToRemember.add(flowFromController.getCookie());
                InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdFromController, flowFromController);

            }

            /*----------------------------------------------------------------------------------------------------------------------------------------*/


            /*  DHCP TRAFFIC RULES */
            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            LOG.info("Setting DHCP flows. Overwriting rules from the Phase I.");
            /*
                All DHCP traffic broadcast into the tree
                Assumption: no more DHCP traffic for me
             */

            for (String inPort: treeUsablePorts) {
                List<String> outPorts = new ArrayList<>(treeUsablePorts);
                outPorts.remove(inPort);

                // DHCP DISCOVER and REQUEST traffic for the controller broadcast
                InstanceIdentifier<Flow> flowIdDiscReq = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                InstanceIdentifier<Flow> flowIdOfferAck = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                Flow flowDiscReq = null;
                Flow flowOfferAck = null;

                flowDiscReq = InitialFlowUtils.createDhcpFlowMultipleOutputPortsOneInputPort(flowTableId, true, new PortNumber(68), new PortNumber(67), 99, inPort, outPorts, switchId.getValue());
                rulesToRemember.add(flowDiscReq.getCookie());
                InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdDiscReq, flowDiscReq);

                // DHCP OFFER and ACK traffic for other nodes from the controller broadcast
                flowOfferAck = InitialFlowUtils.createDhcpFlowMultipleOutputPortsOneInputPort(flowTableId, true, new PortNumber(67), new PortNumber(68), 99, inPort, outPorts, switchId.getValue());
                rulesToRemember.add(flowOfferAck.getCookie());
                InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdOfferAck, flowOfferAck);

            }

            /*----------------------------------------------------------------------------------------------------------------------------------------*/


            /* SSH TRAFFIC */
            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            LOG.info("Setting SSH flows. Overwriting rules from the Phase I.");


            for (String inPort: treeUsablePorts) {
                List<String> outPorts = new ArrayList<>(treeUsablePorts);
                outPorts.remove(inPort);

                InstanceIdentifier<Flow> flowIdSSHToController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                InstanceIdentifier<Flow> flowIdSSHFromController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                Flow flowSSHToController = null;
                Flow flowSSHFromController = null;

                // SSH traffic for the controller broadcast
                srcPrefix = new IPPrefAddrInfo();
                srcPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));
                dstPrefix = new IPPrefAddrInfo();
                dstPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));

                flowSSHToController = InitialFlowUtils.createIPLayer4FlowGeneral(flowTableId, new PortNumber(22), srcPrefix, null, dstPrefix, 99, inPort, outPorts, switchId.getValue());
                rulesToRemember.add(flowSSHToController.getCookie());
                InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdSSHToController, flowSSHToController);

                // SSH traffic for other nodes from the controller broadcast
                flowSSHFromController = InitialFlowUtils.createIPLayer4FlowGeneral(flowTableId, null, srcPrefix, new PortNumber(22), dstPrefix, 99, inPort, outPorts, switchId.getValue());
                rulesToRemember.add(flowSSHFromController.getCookie());
                InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdSSHFromController, flowSSHFromController);

            }

            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            /* ARP TRAFFIC RULES */
            /*----------------------------------------------------------------------------------------------------------------------------------------*/

            LOG.info("Setting ARP flows used for controller discovery. Overwriting rules from the Phase I.");
            // General ARP broadcasting - if not for me broadcast

            for (String inPort: treeUsablePorts) {
                List<String> outPorts = new ArrayList<>(treeUsablePorts);
                outPorts.remove(inPort);

                InstanceIdentifier<Flow> flowIdGeneralARP = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                Flow flowGeneralARP = null;

                // ARP traffic for other nodes from the controller broadcast
                flowGeneralARP = InitialFlowUtils.createARPFlowGeneral(flowTableId, 99, inPort, outPorts, switchId.getValue());
                rulesToRemember.add(flowGeneralARP.getCookie());
                InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdGeneralARP, flowGeneralARP);

            }

            // remember phaseII rules for this switch
            InitialFlowUtils.removableFlowCookiesPhaseII.put(switchId.getValue(), rulesToRemember);

            // remove old rules when new are installed (safer)
            List<FlowCookie> oldRulesToRemove = InitialFlowUtils.removableFlowCookiesPhaseI.get(switchId.getValue());
            if (oldRulesToRemove != null) {
                for (FlowCookie flowCookie : oldRulesToRemove) {
                    InitialFlowUtils.removeOpenFlowFlow(salFlowService, flowCookie, switchId.getValue());
                }
            }

        }
    }

    /**
     * Check periodically if all the switches at the same level have been provided with the tree rules.
     * If yes, trigger next level switches to continue with the bootstrapping.
     */
    private class NextLevelTrigger implements Runnable {

        private org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId switchId;

        public NextLevelTrigger(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId switchId) {
            this.switchId = switchId;
        }

        @Override
        public void run() {
            synchronized (nextLevelTriggerLock) {
                BootstrappingSwitchStateImpl stateManager = BootstrappingSwitchStateImpl.getInstance();
                List<Node> inventoryNodes = new ArrayList<>();
                try {
                    inventoryNodes = getAllRealNodes(db);
                } catch (NullPointerException e) {
                    LOG.warn("NLTM: I-> Inventory nodes currently not available.");
                }

                // get the current tree version
                HopByHopTreeGraph treeGraph = networkGraphService.getCurrentHopByHopTreeGraph();

                // processing node level
                Integer thisNodeTreeLevel = treeGraph.getVertexTreeLevel(switchId);

                LOG.info("NLTM: Triggered by Node {} with level {}", switchId.getValue(), thisNodeTreeLevel);


                // get all switch nodes on this level
                List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId> sameLevelNodes
                        = treeGraph.getSwitchNodesOfTheLevel(thisNodeTreeLevel);

                // find out how many nodes you expect on this level
                Integer expectedNumberOfCurrentLevelNodes;
                try {
                    if (thisNodeTreeLevel == 0) {
                        // there is always only one node with the level 0
                        expectedNumberOfCurrentLevelNodes = 1;
                        numberOfNodesByTreeLevelsCache.put(0, expectedNumberOfCurrentLevelNodes);
                        LOG.info("NLTM: Level 0 node {} processed", switchId.getValue());

                    } else if (!numberOfNodesByTreeLevelsCache.isEmpty() &&
                            (numberOfNodesByTreeLevelsCache.containsKey(thisNodeTreeLevel))) {
                        LOG.info("NLTM: The number of nodes in the level {} already computed, consulting cache for node {}...",
                                thisNodeTreeLevel, switchId.getValue());
                        expectedNumberOfCurrentLevelNodes = numberOfNodesByTreeLevelsCache.get(thisNodeTreeLevel);

                    } else {
                        LOG.info("NLTM: The number of nodes in the level {} is being computed for the first time for node {}",
                                thisNodeTreeLevel, switchId.getValue());
                        expectedNumberOfCurrentLevelNodes = findOutNumberOfNextLevelNodes(treeGraph, thisNodeTreeLevel - 1);
                        numberOfNodesByTreeLevelsCache.put(thisNodeTreeLevel, expectedNumberOfCurrentLevelNodes);

                    }
                } catch (Exception e) {
                    LOG.error("NLTM: Exception");
                    for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                        LOG.info("NLTM: Stack root cause trace -> {}", m);
                    }

                    return;
                }

                LOG.info("NLTM: expectedNumberOfCurrentLevelNodes: {} sameLevelNode.size(): {} in the level {}",
                        expectedNumberOfCurrentLevelNodes, sameLevelNodes.size(), thisNodeTreeLevel);
                if (expectedNumberOfCurrentLevelNodes != sameLevelNodes.size()) {
                    LOG.info("NLTM: Node {} ready, but some of the nodes on level {} are not discovered. Come back later!",
                            switchId.getValue(), thisNodeTreeLevel);
                    return;
                }

                // possible since level 0 node has only one node
                for (Node inventoryNode : inventoryNodes) {
                    org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId topologyNodeId
                            = new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId(inventoryNode.getId().getValue());
                    if (sameLevelNodes.contains(topologyNodeId)) {// you do not have to check yourself but it does not matter
                        if (inventoryNode.getAugmentation(SwitchBootstrappingAugmentation.class)
                                .getSwitchBootsrappingState().getState().getName()
                                .equals(SwitchBootsrappingState.State.INITIALOFRULESPHASEIIDONE.getName())
                                || inventoryNode.getAugmentation(SwitchBootstrappingAugmentation.class)
                                .getSwitchBootsrappingState().getState().getName()
                                .equals(SwitchBootsrappingState.State.INTERMEDIATERESILIENCEINSTALLED.getName())) {

                            LOG.info("NLTM: Node {} has the level {} and has finished the Initial OF Phase II.",
                                    inventoryNode.getId().getValue(), treeGraph.getVertexTreeLevel(topologyNodeId));

                        } else {
                            LOG.info("NLTM: Node {} with the level {} is in this state {} and still not ready.",
                                    inventoryNode.getId().getValue(),
                                    treeGraph.getVertexTreeLevel(topologyNodeId),
                                    inventoryNode.getAugmentation(SwitchBootstrappingAugmentation.class)
                                            .getSwitchBootsrappingState().getState().getName());
                            // wait for the last configured node to trigger the next level configuration
                            // stop the thread here
                            LOG.info("NLTM: Come again when the node has already reached the INITIALOFRULESPHASEIIDONE state.");
                            return;
                        }
                    }
                }

                // try to refresh fetched nodes
                try {
                    inventoryNodes = getAllRealNodes(db);
                } catch (NullPointerException e) {
                    LOG.warn("NLTM: II-> Inventory nodes currently not available.");
                }
                LOG.info("NLTM: Current inventory size: {}", inventoryNodes.size());
                // trigger configuring the next level switches
                for (Node inventoryNode : inventoryNodes) {
                    String state = "";
                    try {
                        state = inventoryNode.getAugmentation(SwitchBootstrappingAugmentation.class)
                                .getSwitchBootsrappingState().getState().getName();
                    } catch (NullPointerException e) {
                        LOG.warn("NLTM: State currently not available for the node {}", inventoryNode.getId().getValue());
                    }

                    if (state.equals(SwitchBootsrappingState.State.OFSESSIONESTABLISHED.getName())) {

                        stateManager.writeBootstrappingSwitchState(inventoryNode.getId(), new SwitchBootsrappingStateBuilder()
                                .setState(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYRULEINSTALLED)
                                .build());

                        ControllerSelfDiscovery.initialSelfDiscoveryRuleInstalledDoneNodes.add(inventoryNode.getId().getValue());
                        LOG.info("NLTM: Informing node {} to proceed with InitialOFPhaseI", inventoryNode.getId().getValue());

                    }
                }
                LOG.trace("NLTM: REACHED");

                // if some node from the next level has been discovered later, the refresher will trigger its further configuration
                if (isRefresherStartedForThisLevel.isEmpty()) {
                    // for the root node
                    isRefresherStartedForThisLevel.put(thisNodeTreeLevel, true);

                    LOG.info("NLTM: Starting refresher for the root node level.");
                    executorSchedulerRefresher.schedule(new NextLevelTriggerRefresher(thisNodeTreeLevel), DEFAULT_MONITOR_REFRESH_PERIOD, TimeUnit.MILLISECONDS);

                } else { // start a refresher
                    LOG.debug("NLTM: {}", isRefresherStartedForThisLevel.toString());
                    LOG.debug("NLTM: node level {}", thisNodeTreeLevel);
                    if (isRefresherStartedForThisLevel.get(thisNodeTreeLevel) == null) {

                        isRefresherStartedForThisLevel.put(thisNodeTreeLevel, true);

                        LOG.info("NLTM: Starting refresher for the level {}.", thisNodeTreeLevel);
                        // start the refresher for this level
                        executorSchedulerRefresher.schedule(new NextLevelTriggerRefresher(thisNodeTreeLevel), DEFAULT_MONITOR_REFRESH_PERIOD, TimeUnit.MILLISECONDS);

                    } else {
                        LOG.info("NLTM: Refresher already started for the level {}.", thisNodeTreeLevel);
                    }
                }

            }
        }

        /**
         * Finds out the number of the expected number of switches on the level+1 by examining
         * nodeConnectors on the current level
         *
         * @param treeGraph
         * @param level
         * @return
         */
        private Integer findOutNumberOfNextLevelNodes(HopByHopTreeGraph treeGraph, int level) {

            List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId> nodes = treeGraph.getSwitchNodesOfTheLevel(level);
            List<NodeConnector> connectorsExpectedToLeadToNextLevelNodes = new ArrayList<>();

            for (org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId node: nodes) {
                Collection<Link> incidentEdges = treeGraph.getIncidentEdges(node);
                List<NodeConnector> functionalNodeConnectors = InitialFlowUtils.getAllFunctionalNodeConnectorsFromNode(node.getValue(), db);
                while (functionalNodeConnectors == null) {
                    functionalNodeConnectors = InitialFlowUtils.getAllFunctionalNodeConnectorsFromNode(node.getValue(), db);
                    sleep_some_time(100);
                }
                for (NodeConnector nodeConnector: functionalNodeConnectors) {
                    for (Link edge: incidentEdges){
                        if (edge.getSource().getSourceTp().getValue().equals(nodeConnector.getId().getValue())) {
                            if(checkIfNodeConnectorNextLevel(edge.getDestination().getDestTp().getValue(), treeGraph, level))
                                connectorsExpectedToLeadToNextLevelNodes.add(nodeConnector);
                        } else if (edge.getDestination().getDestTp().getValue().equals(nodeConnector.getId().getValue())) {
                            if(checkIfNodeConnectorNextLevel(edge.getSource().getSourceTp().getValue(), treeGraph, level))
                                connectorsExpectedToLeadToNextLevelNodes.add(nodeConnector);
                        }
                    }
                }
            }

            return  connectorsExpectedToLeadToNextLevelNodes.size();

        }

        private boolean checkIfNodeConnectorNextLevel(String nodeConnectorId, HopByHopTreeGraph treeGraph, Integer currentLevel) {
            String nodeId = nodeConnectorId.split(":")[0] + ":" + nodeConnectorId.split(":")[1];
            if (nodeId.contains("host")){
                // if edge leads to the controller do not count
                return false;
            } else if (currentLevel > treeGraph.getVertexTreeLevel(new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId(nodeId))) {
                // if edge leads to the lower level do not count
                return false;
            } else {
                // otherwise count it as an edge that leads to the next level switch
                return true;
            }
        }
    }

    /**
     * In case next level switches were not triggered before, try periodically again.
     */
    private class NextLevelTriggerRefresher implements Runnable {

        private Integer level;
        public NextLevelTriggerRefresher(Integer level) {
            this.level = level;
        }

        @Override
        public void run() {

            Integer execNum = 0;
            LOG.debug("nextLevelTriggerRefreshersFiniteEnabled = {}", nextLevelTriggerRefreshersFiniteEnabled);
            if (nextLevelTriggerRefreshersFiniteEnabled) {
                if (nextLevelTriggerRefresherExecCounters.containsKey(level)) {
                    nextLevelTriggerRefresherExecCounters.put(level, nextLevelTriggerRefresherExecCounters.get(level) + 1);
                } else {
                    nextLevelTriggerRefresherExecCounters.put(level, 1);
                }
                execNum = nextLevelTriggerRefresherExecCounters.get(level);
                LOG.info("NLTR: Refresher for level {} has been executed {} times", level, execNum);
            }

            if (execNum < maxExecNum) {

                // Schedule itself again here if something goes wrong in the code later
                executorSchedulerRefresher.schedule(new NextLevelTriggerRefresher(level), DEFAULT_MONITOR_REFRESH_PERIOD, TimeUnit.MILLISECONDS);

                synchronized (nextLevelTriggerRefresherLock) {

                    LOG.info("NLTR: Refresher for the level {} working", level);
                    BootstrappingSwitchStateImpl stateManager = BootstrappingSwitchStateImpl.getInstance();
                    // check if other next level nodes are done with phase II
                    HopByHopTreeGraph treeGraph = networkGraphService.getCurrentHopByHopTreeGraph();
                    List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId> sameLevelNodes
                            = treeGraph.getSwitchNodesOfTheLevel(level);
                    List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId> nextLevelNodes
                            = treeGraph.getSwitchNodesOfTheLevel(level + 1);


                    // try to refresh fetched nodes
                    List<Node> inventoryNodes = null;
                    while (inventoryNodes == null){
                        try {
                            inventoryNodes = getAllRealNodes(db);
                        } catch (NullPointerException e) {
                            LOG.warn("NLTR: Inventory nodes currently not available.");
                        }
                    }

                    // continue configuring the next level switches
                    LOG.info("Checking ithe states of the level {} switches", level + 1);
                    for (Node inventoryNode : inventoryNodes) {
                        org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId topologyNodeId
                                = new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId(inventoryNode.getId().getValue());
                        if (nextLevelNodes.contains(topologyNodeId)) { // this should prevent NullPointerException when retrieving a switch state
                            LOG.info("NLTR: inventoryNode-> {}", inventoryNode.getId().getValue());
                            String state = null;
                            try {
                                state = inventoryNode.getAugmentation(SwitchBootstrappingAugmentation.class)
                                        .getSwitchBootsrappingState().getState().getName();
                            } catch (NullPointerException e) {
                                // try again with new attempt
                                LOG.warn("Switch state still not written to the switch {}", inventoryNode.getId().getValue());
                            }
                            LOG.trace("NLTR: IsStateNull {}", state == null);
                            // for some reason try block skipped sometimes here
                            if (state == null) {
                                return;
                            }

                            org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId nodeId =
                                    new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId(inventoryNode.getId().getValue());

                            boolean selfDiscoveryAlreadyDoneForNode;

                            selfDiscoveryAlreadyDoneForNode = ControllerSelfDiscovery.initialSelfDiscoveryDoneNodes.contains(nodeId.getValue());


                            if (state.equals(SwitchBootsrappingState.State.OFSESSIONESTABLISHED.getName())
                                    && nextLevelNodes.contains(nodeId)) {// samelevelnodes to next level nodes (unnecessary)

                                LOG.info("NLTR: Node {} has the state {} and level {}", inventoryNode.getId().getValue(), state,
                                        level+1);


                                stateManager.writeBootstrappingSwitchState(inventoryNode.getId(), new SwitchBootsrappingStateBuilder()
                                        .setState(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYRULEINSTALLED)
                                        .build());

                                ControllerSelfDiscovery.initialSelfDiscoveryRuleInstalledDoneNodes.add(nodeId.getValue());

                                LOG.info("NLTR: Informing node {} to proceed with ControllerSelfDiscovery", inventoryNode.getId().getValue());

                            } else if (state.equals(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYRULEINSTALLED.getName())
                                    && nextLevelNodes.contains(nodeId)
                                    && ctlList.size() == 1
                                    && !selfDiscoveryAlreadyDoneForNode) {
                                /* necessary only when we have 1 controller, otherwise followers will change the state
                                    of a switch when they probe the network with ARP self-discovery packet
                                */

                                LOG.info("NLTR: Node {} has the state {} and level {}", inventoryNode.getId().getValue(), state,
                                        level+1);

                                stateManager.writeBootstrappingSwitchState(inventoryNode.getId(), new SwitchBootsrappingStateBuilder()
                                        .setState(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYDONE)
                                        .build());

                                ControllerSelfDiscovery.initialSelfDiscoveryDoneNodes.add(nodeId.getValue());

                                LOG.info("NLTR: Informing node {} to proceed with InitialOFPhaseI", inventoryNode.getId().getValue());

                            } else {
                                LOG.info("NLTR: Node {} has the state {} and level {} -> already triggered", inventoryNode.getId().getValue(), state,
                                        level+1);
                            }
                        }
                    }

                }
            } else {
                // stop the scheduler
                LOG.info("NLTR: Stopping refresher for the level {}", level);
            }

        }
    }


    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("InitialOFRulesPhaseII ownership change logged: " + ownershipChange);
        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the InitialOFRulesPhaseII leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the InitialOFRulesPhaseII follower.");
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

    private void sleep_some_time(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
