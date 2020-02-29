/**
  *
  * @filename FlowReconfiguratorForResilienceStateListener.java
  *
  * @date 14.05.18
  *
  * @author Mirza Avdic
  *
  *
 */

package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.*;
import eu.virtuwind.registryhandler.impl.BootstrappingSwitchStateImpl;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.javatuples.Pair;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingStatus;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingState;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.HostNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import java.util.*;
import java.util.concurrent.TimeUnit;

import java.util.Random;

import static java.lang.Thread.sleep;

/**
 * The class that implements the re-embedding of particular control flows to provide resilient bootstrapping.
 */
public class FlowReconfiguratorForResilienceStateListener implements Runnable, ClusteredDataTreeChangeListener<SwitchBootsrappingState> {

    private static final Logger LOG = LoggerFactory.getLogger(FlowReconfiguratorForResilienceStateListener.class);

    // SAL SERVICES
    private DataBroker dataBroker;
    private SalFlowService salFlowService;

    // FLAGS
    private static boolean isLeader = false;

    // CONTROLLERS' IP LIST
    private static ArrayList<String> ctlList;

    // INSTANCE OBJECT
    static FlowReconfiguratorForResilienceStateListener flowReconf = null;

    // TOPOLOGY ID
    private static String topologyId;

    // ResiliencePathManager instance
    /*
        For a different resilience logic or algorithm initialize resiliencePathManager variable with a new
        implementation of the ResiliencePathManager interface
     */
    private static final ResiliencePathManager resiliencePathManager = new ResiliencePathManagerImpl();

    // LOCK
    private static final String LOCK = "LOCK";

    // State trackers
    private static HashMap<String, ResiliencePathManagerImpl.ResilienceStates> controllerPairStateTracker = new HashMap<>();
    private static HashMap<String, HashMap<String, ResiliencePathManagerImpl.ResilienceStates>> switchControllerPairStateTracker = new HashMap<>();

    // Measurements
    private static boolean notifyMeasurementsOrchestratorPeriodicCheckerTrigger = false;
    private static boolean bootstrappingDone = false;
    ScheduledThreadPoolExecutorWrapper scheduler = new ScheduledThreadPoolExecutorWrapper(1);

    // keep track of the nodes that already were this state
    public static List<String> intermediateResilienceDoneNodes = new ArrayList<>();

    // periodic checker -> sometimes PathManager cannot find disjoint paths even though they exist -> try periodically
    ScheduledThreadPoolExecutorWrapper schedulerResilience = new ScheduledThreadPoolExecutorWrapper(1);
    private static boolean  ifPeriodicResilienceCheckerStarted = false;

    /**
     * Default constructor for the flow reconfiguration
     * @param dataBroker
     * @param salFlowService
     * @param flowTableId
     */
    public FlowReconfiguratorForResilienceStateListener(DataBroker dataBroker, SalFlowService salFlowService, short flowTableId) {
        LOG.info("Setting up a new FlowReconfiguratorForResilienceStateListener instance.");

        this.dataBroker = dataBroker;
        this.salFlowService = salFlowService;
        flowReconf = this;
    }

    /**
     * Set controller IP address list
     * @param controllerList
     */
    public static void setCtlList(ArrayList<String> controllerList) {
        FlowReconfiguratorForResilienceStateListener.ctlList = controllerList;
    }

    public static FlowReconfiguratorForResilienceStateListener getInstance() {
        if(flowReconf != null) {
            return flowReconf;
        } else {
            LOG.error("FlowReconfiguratorForResilienceStateListener must be initialized first using the default constructor!");
            return null;
        }
    }

    public static String getTopologyId() {
        return topologyId;
    }

    public static void setTopologyId(String topologyId) {
        FlowReconfiguratorForResilienceStateListener.topologyId = topologyId;
    }

    public void run() {

        HostUtilities.InterfaceConfiguration myIfConfig =
                HostUtilities.returnMyIfConfig(ctlList);
        if (myIfConfig == null) {
            LOG.warn("Provided controller IP addresses do not found on this machine.");
            return;
        }

        try {
            if (isLeader == true) { //Is current leader of the cluster topic?

                updateResilienceIfNeeded();

                /**
                 * Periodically check if all possible communication pairs have been provided with the resilience
                 * if yes write to log "Measurements done"; this will be checked by the measurement-orchestrator so that
                 * the measurement procedure can be preempted and closed sooner; in this way we can speed up the measurements
                 *
                 * NOTE: Currently multihoming of controllers is not considered
                 */
                if (!notifyMeasurementsOrchestratorPeriodicCheckerTrigger) {
                    scheduler.scheduleAtFixedRate(new Runnable() {

                        @Override
                        public void run() {
                            if (!bootstrappingDone) {
                                LOG.info("Checking if bootstrapping is done???");
                                List<Node> allNodes = InitialFlowUtils.getAllRealNodes(dataBroker);
                                List<Node> resilientNodes = getOpenFlowNodesInCertainState(SwitchBootsrappingState.State.INTERMEDIATERESILIENCEINSTALLED.getName());
                                if ((allNodes.size() - ctlList.size()) == resilientNodes.size()
                                        && checkIfResilienceEmbeddedForAllControllers()) {

                                    LOG.info("Measurements done");

                                /*
                                    When initial bootstrapping has been finished trigger NetworkExtensionManager

                                    NOTE: when doing measurements of switch extension bootstrapping time
                                    reduce period to smaller value (now it is set arbitrarily to 10 s)
                                 */
                                    Thread networkExtensionManagerThread = new Thread(new NetworkExtensionManager());
                                    networkExtensionManagerThread.start();

                                    bootstrappingDone = true;
                                    scheduler.shutdown();

                                }
                                notifyMeasurementsOrchestratorPeriodicCheckerTrigger = true;
                            }

                        }
                    }, 0, 10000, TimeUnit.MILLISECONDS);
                }

                /**
                 * Testing has shown that for some reason the PathManager does not return a disjoint path for some
                 * communication pairs immediately, even though they exist in the currently discovered topology.
                 * For this reason a periodic resilience checker is started. It will check and try to compute these paths
                 * later again. It turned out to work very good (it would be good to investigate why PathManager cannot
                 * compute paths sometimes -> possible issue: LinkAger deletes certain links in the moment when the PathManager
                 * is invoked -> additional synchronization necessary)
                 */
                if (!ifPeriodicResilienceCheckerStarted) {
                    schedulerResilience.scheduleAtFixedRate(new Runnable() {
                        @Override
                        public void run() {
                          updateResilienceIfNeeded();
                        }
                    }, 0, 10000, TimeUnit.MILLISECONDS);
                }

            }
        } catch(Exception e){
            LOG.error("Flow re-emebedding failed!");
            for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                LOG.error("FlowReconfiguratorForResilienceStateListener: Stack root cause trace -> {}", m);
            }
        }

    }

    /**
     * Listener on SwitchBootsrappingState changes
     *
     * @param collection
     */
    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<SwitchBootsrappingState>> collection) {
        //LOG.info("Bootstrapping Status onDataTreeChanged {} ", collection );
        for (DataTreeModification<SwitchBootsrappingState> change : collection) {
            DataObjectModification<SwitchBootsrappingState> mod = change.getRootNode();
            switch (mod.getModificationType()) {
                case DELETE:
                    LOG.info(" Delete after data {} ", mod.getDataAfter());
                    break;
                case SUBTREE_MODIFIED:
                    LOG.info("FlowReconfiguratorForResilienceStateListener modified: {}", mod.getDataAfter().getState().getName());
                    if(mod.getDataAfter().getState().equals(SwitchBootsrappingState.State.INITIALOFRULESPHASEIIDONE))
                    {
                        LOG.info("Node {} has reached the INITIALOFRULESPHASEIIDONE state",
                                change.getRootPath().getRootIdentifier().firstKeyOf(Node.class).getId().getValue());
                        /**
                         * trigger resilient reembedding
                         */
                        Thread executor = new Thread(this);
                        executor.start();
                    }
                    break; // TODO: Fix at some point
                case WRITE:
                    break;
                default:
                    throw new IllegalArgumentException("Unhandled node modification type " + mod.getModificationType());
            }
        }
    }

    /**
     * Call necessary ResiliencePathManager methods for each communication pair in order to check whether there are
     * new updates regarding the resilience;  based on that decide to change a switch state or not.
     *
     */
    private void updateResilienceIfNeeded() {

        // Lock necessary in order to avoid race conditions and unnecessary multiple installing of the same rules
        synchronized (LOCK) {
            List<Node> listOfOOfNodes = getOpenFlowNodesInCertainState(SwitchBootsrappingState.State.INITIALOFRULESPHASEIIDONE.getName());

            // for all discovered controllers
            List<String> discoveredControllers = multipleControllersInTopologyDiscovered(ctlList);
            LOG.info("Currently discovered controllers in the topology {}", discoveredControllers.toString());
            Integer threadId = new Random().nextInt(1000);
            for (String ctlIpAddress : discoveredControllers) {
                for (Node ofNode : listOfOOfNodes) {
                    LOG.info("{}: Resilience decisions for the node {} ", threadId, ofNode.getId().getValue());

                    NodeId nodeId = ofNode.getId();
                    NodeId controllerId = new NodeId(HostUtilities.getHostNodeByIp(ctlIpAddress).getAttachmentPoints().get(0).getCorrespondingTp().getValue());

                    Pair<ResiliencePathManagerImpl.ResilienceStates, String> updateResult
                            = (Pair<ResiliencePathManagerImpl.ResilienceStates, String>) resiliencePathManager.updateResilientPathsBetweenS2CNodes(nodeId, controllerId);
                    LOG.info("ResiliencePathManager updateResilientPathsBetweenNodes message: {}", updateResult.getValue1());
                    String msg = (String) resiliencePathManager.embedResilientPathsBetweenS2CNodes(nodeId, controllerId);
                    LOG.info("ResiliencePathManager embedResilientPathsBetweenNodes message: {}", msg);


                    // update the resilience state of the communication pair
                    if (!switchControllerPairStateTracker.containsKey(nodeId.getValue())) {
                        HashMap<String, ResiliencePathManagerImpl.ResilienceStates> commPairState = new HashMap<>();
                        commPairState.put(controllerId.getValue(), updateResult.getValue0());
                        switchControllerPairStateTracker.put(nodeId.getValue(), commPairState);
                    } else {
                        HashMap<String, ResiliencePathManagerImpl.ResilienceStates> currentStatesOfPairs = switchControllerPairStateTracker.get(nodeId.getValue());
                        currentStatesOfPairs.put(controllerId.getValue(), updateResult.getValue0());
                        switchControllerPairStateTracker.put(nodeId.getValue(), currentStatesOfPairs);
                    }

                    printSwitchControllerPairStateTrackerNicely();

                    if (checkIfResilienceEmbedded(nodeId.getValue())) {

                        BootstrappingSwitchStateImpl stateManager = BootstrappingSwitchStateImpl.getInstance();
                        stateManager.writeBootstrappingSwitchState(nodeId, new SwitchBootsrappingStateBuilder()
                                .setState(SwitchBootsrappingState.State.INTERMEDIATERESILIENCEINSTALLED)
                                .build());
                        String currentState = stateManager.readBootstrappingSwitchStateName(nodeId);
                        LOG.info("FlowReconfiguratorForResilienceStateListener state in node {} changed: {}", nodeId.getValue(), currentState);
                        if (!intermediateResilienceDoneNodes.contains(nodeId.getValue())) {
                            intermediateResilienceDoneNodes.add(nodeId.getValue());
                        }
                    }
                }
            }

            // If multiple controllers are discovered try to embed two disjoint resilient paths between each of them
            LOG.info("Discovered number of controllers: {}", discoveredControllers.size());
            if (discoveredControllers.size() > 1) {
                List<ControllerPair> discoveredPairs = generateAllUniqueControllerPairs(discoveredControllers);
                for (ControllerPair controllerPair: discoveredPairs) {
                    NodeId c1 = new NodeId(controllerPair.getControllerId1());
                    NodeId c2 = new NodeId(controllerPair.getControllerId2());

                    Pair<ResiliencePathManagerImpl.ResilienceStates, String> updateResult
                            = (Pair<ResiliencePathManagerImpl.ResilienceStates, String>) resiliencePathManager.updateResilientPathsBetweenC2CNodes(c1, c2);
                    LOG.info("ResiliencePathManager updateResilientPathsBetweenC2CNodes message: {}", updateResult.getValue1());
                    String msg = (String) resiliencePathManager.embedResilientPathsBetweenC2CNodes(c1, c2);
                    LOG.info("ResiliencePathManager embedResilientPathsBetweenC2CNodes message: {}", msg);

                    controllerPairStateTracker.put(c1.getValue() + "-" + c2.getValue(), updateResult.getValue0());

                    printControllerPairStateTrackerNicely();

                    // TODO: augment HostNode yang model with some additional states; not necessary now

                }
            }
        }
    }

    /**
     * It checks whether disjoint paths between a switch with provided switchId and all controllers are embedded
     *
     * @param switchId
     * @return true if yes, otherwise false
     */
    private boolean checkIfResilienceEmbedded(String switchId) {
        Integer controllerNumber = ctlList.size();
        if (switchControllerPairStateTracker.containsKey(switchId)) {
            HashMap<String, ResiliencePathManagerImpl.ResilienceStates> stateMap = switchControllerPairStateTracker.get(switchId);
            if (stateMap.size() < controllerNumber) {
                return false;
            } else {
                List<Boolean> checker = new ArrayList<>();
                for (ResiliencePathManagerImpl.ResilienceStates state: stateMap.values()) {
                    if (state == ResiliencePathManagerImpl.ResilienceStates.RESILIENCE_PROVIDED_NOW ||
                            state == ResiliencePathManagerImpl.ResilienceStates.RESILIENCE_PROVIDED_BEFORE) {
                        checker.add(true);
                    }
                }
                if (checker.size() == controllerNumber){
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    /**
     * It checks whether there are 2 disjoint paths between each controller to controller pair
     *
     * @return true if yes, otherwise false
     */
    private boolean checkIfResilienceEmbeddedForAllControllers() {
        List<ControllerPair> controllerPairs = generateAllUniqueControllerPairs();
        if (controllerPairs.size() == controllerPairStateTracker.size()) {
            List<Boolean> checker = new ArrayList<>();
            for (ResiliencePathManagerImpl.ResilienceStates state: controllerPairStateTracker.values()) {
                if (state == ResiliencePathManagerImpl.ResilienceStates.RESILIENCE_PROVIDED_BEFORE ||
                        state == ResiliencePathManagerImpl.ResilienceStates.RESILIENCE_PROVIDED_NOW) {
                    checker.add(true);
                }
            }
            if (checker.size() == controllerPairs.size()){
                return true;
            } else {
                return false;
            }

        } else {
            return false;
        }
    }

    /**
     * Utility method that prints out resilience states between each controller pair in a nice manner
     *
     */
    private void printControllerPairStateTrackerNicely() {
        Set<Map.Entry<String, ResiliencePathManagerImpl.ResilienceStates>> entries = controllerPairStateTracker.entrySet();

        for (Map.Entry<String, ResiliencePathManagerImpl.ResilienceStates> entry: entries) {
            LOG.info("{}:{}", entry.getKey(), entry.getValue().toString());
        }
    }

    /**
     *  Utility method that prints out resilience states between each switch-controller pair
     *
     */
    private void printSwitchControllerPairStateTrackerNicely() {
        Set<Map.Entry<String, HashMap<String, ResiliencePathManagerImpl.ResilienceStates>>> entries = switchControllerPairStateTracker.entrySet();

        for (Map.Entry<String, HashMap<String, ResiliencePathManagerImpl.ResilienceStates>> entry1: entries) {
            for (Map.Entry<String, ResiliencePathManagerImpl.ResilienceStates> entry2: entry1.getValue().entrySet()) {
                LOG.info("{}-{}:{}", entry1.getKey(), entry2.getKey(), entry2.getValue().toString());
            }
        }
    }


    /**
     * Returns a list of IP addresses of already discovered controllers
     *
     * @param ctlList
     * @return a list of IPs of discovered controllers
     */
    private List<String> multipleControllersInTopologyDiscovered(ArrayList<String> ctlList) {
        List<String> discoveredControllers = new ArrayList<>();
        for (String controllerIP:ctlList) {
            HostNode discoveredController = HostUtilities.getHostNodeByIp(controllerIP);
            if (discoveredController != null) {
                discoveredControllers.add(discoveredController.getAddresses().get(0).getIp().getIpv4Address().getValue());
            }
        }
        return discoveredControllers;
    }


    /**
     *  Does all possible unique permutations of the ctlList and
     *  populates the corresponding list with generated pairs
     *  Used to create unicast resilient rules between all controllers
     *
     * @param ctlList
     * @return all unique controller pair permutations
     */
    private List<ControllerPair> generateAllUniqueControllerPairs(List<String> ctlList) {
        List<ControllerPair> controllerPairs = new LinkedList<>();
        for (String controllerIp1: ctlList) {
            for (String controllerIp2 : ctlList) {
                if (controllerIp1.equals(controllerIp2)) {
                    continue;
                } else {
                    ControllerPair cp = new ControllerPair(controllerIp1, controllerIp2);
                    if (!controllerPairs.isEmpty()) {
                        boolean uniquePair = true;
                        for (ControllerPair pair: controllerPairs) {
                            if (pair.equalsPair(cp))
                                uniquePair = false;
                        }
                        if (uniquePair) {
                            controllerPairs.add(cp);
                        }
                    } else {
                        controllerPairs.add(cp);
                    }

                }
            }
        }
        return controllerPairs;
    }

    /**
     * Does all possible unique permutations of the ctlList and
     * populates the corresponding list with generated pairs
     * Used to create unicast resilient rules between all controllers
     *
     * @return
     */
    private List<ControllerPair> generateAllUniqueControllerPairs() {
        List<ControllerPair> controllerPairs = new LinkedList<>();
        for (String controllerIp1: ctlList) {
            for (String controllerIp2 : ctlList) {
                if (controllerIp1.equals(controllerIp2)) {
                    continue;
                } else {
                    ControllerPair cp = new ControllerPair(controllerIp1, controllerIp2);
                    if (!controllerPairs.isEmpty()) {
                        boolean uniquePair = true;
                        for (ControllerPair pair: controllerPairs) {
                            if (pair.equalsPair(cp))
                                uniquePair = false;
                        }
                        if (uniquePair) {
                            controllerPairs.add(cp);
                        }
                    } else {
                        controllerPairs.add(cp);
                    }

                }
            }
        }
        return controllerPairs;
    }


    /**
     * For a given state returns the list of inventory nodes that have reached this bootstrapping state.
     *
     * @param state
     * @return list of inventory nodes that are currently in the given state
     */
    public List<Node> getOpenFlowNodesInCertainState(String state) {
        // Obtain list of nodes
        List<Node> nodeList = new ArrayList<>();
        InstanceIdentifier<Nodes> nodesIid = InstanceIdentifier.builder(Nodes.class).build();
        ReadOnlyTransaction nodesTransaction = dataBroker.newReadOnlyTransaction();

        boolean notFetched = true;
        while(notFetched) {
            try {
                CheckedFuture<Optional<Nodes>, ReadFailedException> nodesFuture = nodesTransaction
                        .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
                Optional<Nodes> nodesOptional = nodesFuture.checkedGet();

                notFetched = false;

                if (nodesOptional != null && nodesOptional.isPresent()) {
                    nodeList = nodesOptional.get().getNode();

                    ArrayList<Node> openFlowNodeList = new ArrayList<>();
                    for(Node node:nodeList) {
                        String nodeState = InitialFlowUtils.getSwitchBootstrappingState(
                                new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId(node.getId().getValue()),
                                dataBroker);
                        if(node.getId().getValue().contains("openflow")
                                && nodeState.equals(state))
                            openFlowNodeList.add(node);
                    }

                    LOG.info("Successfully fetched a list of OpenFlow nodes in the bootstrapping state {}.", state);
                    return openFlowNodeList;
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch list of nodes with Exception: " + e.getMessage());
                for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                    LOG.info("getOpenFlowNodesInCertainState: Stack root cause trace -> {}", m);
                }
                notFetched = true;
                sleep_some_time(100);
            }

        }

        return null;
    }

    /**
     * Implements the reaction to cluster leadership changes.
     *
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("FlowReconfiguratorForResilienceStateListener ownership change logged: " + ownershipChange);

        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the FlowReconfiguratorForResilienceStateListener leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the FlowReconfiguratorForResilienceStateListener follower.");
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

    protected InstanceIdentifier<BootstrappingStatus> getObservedPath() {
        return InstanceIdentifier.create(BootstrappingStatus.class);
    }

    private void sleep_some_time(long milis) {
        try {
            sleep(milis);
        } catch (InterruptedException exc) {
            exc.printStackTrace();
        }
    }

}





