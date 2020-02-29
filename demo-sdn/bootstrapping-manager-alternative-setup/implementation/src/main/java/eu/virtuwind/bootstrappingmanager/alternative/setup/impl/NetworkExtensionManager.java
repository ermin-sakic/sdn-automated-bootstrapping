package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 14.08.18
 */


import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.*;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.PacketUtils;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.ScheduledThreadPoolExecutorWrapper;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Install MST rules to support later network extensions after ResiliencePathManager has finished its job.
 * Listen to DHCP Packet-In messages, which may be arriving from the new connected switch
 * Allow new connected switches access to the MST, but only on one port, in order to avoid loops
 *
 */
public class NetworkExtensionManager implements Runnable, PacketProcessingListener, ClusteredDataTreeChangeListener<NodeConnector> {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkExtensionManager.class);

    // set through the EOS
    private static boolean isLeader = false;

    // Initialization
    private static DataBroker dataBroker;
    private static SalFlowService salFlowService;
    private static short flowTableId;
    private final static String topologyId = TopologyLinkDataChangeHandler.getTopologyId();

    // Graph stuff
    private static NetworkGraphService networkGraphService;

    // Tracking linkDown States
    private static HashMap<String, Boolean> isLinkDown = new HashMap<String, Boolean>();

    // MacAddress duplicate tracker
    private static HashMap<String, String> macAddressDuplicateTracker = new HashMap<>();

    // Thread pool
    private static ScheduledThreadPoolExecutorWrapper executorScheduler = new ScheduledThreadPoolExecutorWrapper(1);

    // config flag that enables/disables NEM features
    private static Boolean nemEnabled = true;


    /**
     * Default Constructor
     */
    public NetworkExtensionManager(){

        LOG.info("NetworkExtensionManager object created.");
        // create references to the NetworkGraphService objects

    }

    /**
     * Constructor
     *
     * @param dataBroker
     */
    public NetworkExtensionManager(DataBroker dataBroker, SalFlowService salFlowService, NetworkGraphService ngs) {

        LOG.info("NetworkExtensionManager object created.");
        this.dataBroker = dataBroker;
        this.salFlowService = salFlowService;
        networkGraphService = ngs;
    }

    /**
     * Set nemEnabled flag via XML config
     *
     * @param nemEnabled
     */
    public static void setNemEnabled(Boolean nemEnabled) {
        NetworkExtensionManager.nemEnabled = nemEnabled;
    }

    /**
     * Triggered by FlowReconfiguratorForResilienceStateListener - leader only
     */
    @Override
    public synchronized void run() {

        if (nemEnabled) {
            if (networkGraphService.getNetworkGraph() != null) {

                Collection<NodeId> switches = networkGraphService.getNetworkGraph().getVertices();

                // install special rules that send DHCP packets from the previously non-functional ports to the controller
                for (NodeId switchId : switches) {
                    if (!switchId.getValue().contains("host")) {
                        List<NodeConnector> nonFunctionalPorts = getAllNonFunctionalNodeConnectorsFromNode(switchId.getValue(), dataBroker);
                        if ((nonFunctionalPorts != null) && (!nonFunctionalPorts.isEmpty())) {
                            // install special rules for this node connectors
                            installSpecialTreeRulesInSwitch(switchId, nonFunctionalPorts);
                            LOG.info("Special tree rule installed for the NodeConnectors {}", nonFunctionalPorts.toString());
                        }
                    }
                }

                //compute alternative trees if a link in the current tree fails
                networkGraphService.computeAlternativeTrees();
                networkGraphService.writeAlternativeTreesInDS();

            } else {
                LOG.warn("networkGraph is empty!!!");
            }
        }
    }

    /**
     * Processes the DHCP DISCOVERY PACKET-IN from the new added switch in the network
     *
     * @param packetReceived
     */
    @Override
    public synchronized void onPacketReceived(PacketReceived packetReceived) {

        if (nemEnabled) {
            String clientMacAddress = PacketUtils.getClientMACAddressFromDHCP(packetReceived.getPayload());

            if (clientMacAddress != null) {
                if (isLeader) {
                    LOG.info("Packet-In is DHCP packet from the client with hardware address {}", clientMacAddress);

                    if (!macAddressDuplicateTracker.containsKey(clientMacAddress)) {
                        macAddressDuplicateTracker.put(clientMacAddress, packetReceived.getIngress().toString());
                    }
                    /*
                        TODO: TEST!!!!

                        Functionality of this listener has never been fully tested due to lack of time do extend the emulator and support
                        later network extensions

                        The new appeared link should be processed  and added to the tree graph, as well as to the tree topology in the datastore

                        SEE: updateTree method

                      */

                    /* Check if the clientMacAddress exists already in the topology, if yes something is wrong in the network.
                     */
                    if (!IfMacExistsInTheTopology(clientMacAddress)) {
                        // only allow DHCP DISCOVERY from the same ingress port in order to avoid loops
                        if (macAddressDuplicateTracker.get(clientMacAddress).equals(packetReceived.getIngress().toString())) {
                            // find out on which switch the ingress port is located
                            InstanceIdentifier<NodeConnector> nodeConnectorInstanceIdentifier = (InstanceIdentifier<NodeConnector>) packetReceived.getIngress().getValue();
                            String nodeConnectorId = nodeConnectorInstanceIdentifier.firstKeyOf(NodeConnector.class).getId().getValue();
                            // update tree rules for the switch that owns this nodeConnectorId
                            updateTree(nodeConnectorId);
                            // TODO: OPTIMIZATION -> clean the special rule for this NodeConnector
                            /**
                             *  It does not represent a problem, but it will be a redundant rule.
                             *  Essentially, all special rules (in all switches) leading to the new discovered switch should be
                             *  removed.
                             */
                        }
                    } else {
                        LOG.warn("Switch with MAC {} has malfunctioning DHCP client!!!", clientMacAddress);
                    }
                } else {
                    LOG.info("Packet-In DHCP packet received, but not the leader!!!");
                }
            }
        }
    }

    /**
     * Installs the necessary special rule if new ports (NodeConnectors) are added after the bootstrapping procedure
     * has finished.
     *
     * Example of adding a new link after the bootstrapping has been finished:
     * # Creates a new veth pair between sw_1 and sw_2
     * ip -n sw_1 link add veth_test1 type veth peer name veth_test2 netns sw_2
     * # Adding ports to the OVS bridge
     * sudo docker exec -u root sw_1 ovs-vsctl add-port br100 veth_test1
     * sudo docker exec -u root sw_2 ovs-vsctl add-port br100 veth_test2
     * # Bringing ports up
     * sudo docker exec -u root sw_1 ip link set veth_test1 up
     * sudo docker exec -u root sw_2 ip link set veth_test2 up
     *
     * In the same way the network can be extended with a new switch.
     *
     * @param changes
     */
    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<NodeConnector>> changes) {

        synchronized (this) {

            if (isLeader && nemEnabled) {
                for (DataTreeModification<NodeConnector> change : changes) {

                    DataObjectModification.ModificationType modtype = change.getRootNode().getModificationType();

                    if (modtype.equals(DataObjectModification.ModificationType.WRITE)) {
                        if (change.getRootNode().getDataBefore() == null) {
                            LOG.debug("CONNECTOR-W: we have a write");
                            String nodeConnectorId = change.getRootPath().getRootIdentifier().firstKeyOf(NodeConnector.class).getId().getValue();
                            if (nodeConnectorId == null)
                                nodeConnectorId = "";

                            NodeConnector nodeConnector = change.getRootNode().getDataAfter();
                            FlowCapableNodeConnector flowConnector = nodeConnector.getAugmentation(FlowCapableNodeConnector.class);

                            LOG.debug("CONNECTOR-W: NodeConnectorId-> {}", nodeConnectorId);
                            LOG.debug("CONNECTOR-W: PortNumber-> {}", flowConnector.getPortNumber().getUint32());
                            LOG.debug("CONNECTOR-W: PortName-> {}", flowConnector.getName());
                            LOG.debug("CONNECTOR-W: isLinkDown-> {}", flowConnector.getState().isLinkDown());

                            if (flowConnector.getState().isLinkDown()) {
                                // install special rules for this node connector
                                // because all links at the beginning, except the manual added one should be alive
                                List<NodeConnector> nonFunctionalPorts = new ArrayList<>();
                                nonFunctionalPorts.add(nodeConnector);
                                installSpecialTreeRulesInSwitch(new NodeId(nodeConnectorId.split(":")[0] + ":" + nodeConnectorId.split(":")[1]),
                                        nonFunctionalPorts);
                                LOG.info("Special tree rule installed for the NodeConnectors {}", nonFunctionalPorts.toString());
                            }

                            // add entry to isLinkDown cache
                            isLinkDown.put(nodeConnectorId, flowConnector.getState().isLinkDown());

                        }

                    } else if (modtype.equals(DataObjectModification.ModificationType.SUBTREE_MODIFIED)) {

                        LOG.debug("CONNECTOR-M: we have a modification");
                        NodeConnector nodeConnectorBefore = change.getRootNode().getDataBefore();
                        NodeConnector nodeConnectorAfter = change.getRootNode().getDataAfter();
                        FlowCapableNodeConnector flowConnectorAfter = nodeConnectorAfter.getAugmentation(FlowCapableNodeConnector.class);

                        LOG.debug("CONNECTOR-M: NodeConnectorId-> {}", nodeConnectorAfter.getId().getValue());
                        LOG.debug("CONNECTOR-M: PortNumber-> {}", flowConnectorAfter.getPortNumber().getUint32());
                        LOG.debug("CONNECTOR-M: PortName-> {}", flowConnectorAfter.getName());
                        LOG.debug("CONNECTOR-M: isLinkDown-> {}", flowConnectorAfter.getState().isLinkDown());

                        /*
                            Only for later link failures after the bootstrapping procedure has finished
                         */
                        if (nodeConnectorBefore != null) {

                            FlowCapableNodeConnector flowConnectorBefore = nodeConnectorBefore.getAugmentation(FlowCapableNodeConnector.class);
                            /*
                                Debugging showed that the before state is always equal to the after state!!!!
                                for that reason local cache implementation is done
                             */
                            LOG.debug("Before: {}, After: {}", flowConnectorBefore.getState().isLinkDown(), flowConnectorAfter.getState().isLinkDown());

                            // fetch the current state
                            Boolean beforeState = isLinkDown.get(nodeConnectorAfter.getId().getValue());
                            // update cache with the new state
                            isLinkDown.put(nodeConnectorAfter.getId().getValue(), flowConnectorAfter.getState().isLinkDown());

                            /*
                                This is considered to be a link failure
                             */
                            if (beforeState == false && flowConnectorAfter.getState().isLinkDown() == true) {


                                LOG.info("Link failure has happened -> {}", nodeConnectorAfter.getId().getValue());

                                // find the failed link
                                Link failedLink = getLinkFromNodeConnectorIdInNetworkgraph(nodeConnectorAfter.getId().getValue());
                                if (failedLink == null) {
                                    LOG.info("Failed link was already processed and removed from networkGraph");
                                    return;
                                }
                                LOG.info("Failed linkId -> {}", failedLink.getLinkId().getValue());


                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Return all NodeConnectors of the provided nodeId that are down and are not LOCAL (internal) ports.
     *
     * @param nodeId
     * @param dataBroker
     * @return
     */
    private List<NodeConnector> getAllNonFunctionalNodeConnectorsFromNode(String nodeId, DataBroker dataBroker) {

        List<NodeConnector> allNodeConnectors = InitialFlowUtils.getAllNodeConnectorsFromNode(nodeId, dataBroker);
        if (allNodeConnectors == null) {
            return null;
        }
        List<NodeConnector> nonFunctionalNodeConnectors = new ArrayList<>();
        for (NodeConnector nodeConnector: allNodeConnectors) {
            if ( !nodeConnector.getId().getValue().contains("LOCAL")
                    && nodeConnector.getAugmentation(FlowCapableNodeConnector.class).getState().isLinkDown() == true
                    && nodeConnector.getAugmentation(FlowCapableNodeConnector.class).getState().isBlocked() == false
                    && nodeConnector.getAugmentation(FlowCapableNodeConnector.class).getState().isLive() == false) {

                nonFunctionalNodeConnectors.add(nodeConnector);
            }
        }

        return nonFunctionalNodeConnectors;
    }

    /**
     * Install rule that sends DHCP DISCOVER packets arriving at the previously non-functional port to the controller
     *
     * @param switchId
     * @param nodeConnectors
     */
    private void installSpecialTreeRulesInSwitch(NodeId switchId, List<NodeConnector> nodeConnectors) {

        InstanceIdentifier<Node> nodeId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId(switchId))).build();
        InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeId, flowTableId);

        List<String> outPorts = new ArrayList<>();
        outPorts.add("controller");

        for (NodeConnector nodeConnector: nodeConnectors) {
            String inPort = nodeConnector.getId().getValue().split(":")[2];
            InstanceIdentifier<Flow> flowIdDiscReq = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow flowDiscReq = InitialFlowUtils.createDhcpFlowMultipleOutputPortsOneInputPort(flowTableId, true, new PortNumber(68), new PortNumber(67), 99, inPort, outPorts, switchId.getValue());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdDiscReq, flowDiscReq);
        }
    }

    /**
     * Check if the provided MAC address is already a MAC of some LOCAL port in the topology
     *
     * @param clientMacAddress
     * @return
     */
    private boolean IfMacExistsInTheTopology(String clientMacAddress) {
        List<Node> topologyNodes = null;
        while (topologyNodes == null) {
            topologyNodes = InitialFlowUtils.getAllRealNodes(dataBroker);
        }

        for (Node topologyNode: topologyNodes) {
            List<NodeConnector> nodeConnectors = topologyNode.getNodeConnector();
            for (NodeConnector nodeConnector: nodeConnectors) {
                // only LOCAL port sends DHCP messages
                if (nodeConnector.getId().getValue().contains("LOCAL")) {
                    String nodeConnectorMAC = nodeConnector.getAugmentation(FlowCapableNodeConnector.class).getHardwareAddress().getValue();
                    if (nodeConnectorMAC.contains(clientMacAddress))
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Update tree rules in a switch that is connected to a new added switch
     * 
     * @param nodeConnectorId
     */
    private void updateTree(String nodeConnectorId) {

         /*
            TODO: TESTING !!!!

         */
        // notify TopologyLinkDataChangeHandler about the new nodeConnector that should appear
        TopologyLinkDataChangeHandler.nodeConnectorToCheck.add(nodeConnectorId);

        // allow this port to broadcast in the tree
        String switchId = nodeConnectorId.split(":")[0] + ":" + nodeConnectorId.split(":")[1];
        String newLivePort = nodeConnectorId.split(":")[2];
        List<String> treePorts = networkGraphService.findTreePorts(switchId);
        treePorts.add(newLivePort);


        // special rules and old tree rules should be overwritten
        // tree extension part
        executorScheduler.execute(new InstallTreeRulesInTheSwitch(new NodeId(switchId), treePorts));
    }

    /**
     * Find a link that contains the provided nodeConnectorId
     *
     * @param nodeConnectorId
     * @return
     */
    private Link getLinkFromNodeConnectorId(String nodeConnectorId) {
        List<Link> links = TreeUtils.getNetworkTopologyLinks(topologyId);
        Link tempLink = null;

        for (Link link: links) {
            if (link.getSource().getSourceTp().getValue().equals(nodeConnectorId)){
                tempLink = link;
                break;
            } else if (link.getDestination().getDestTp().getValue().equals(nodeConnectorId)) {
                tempLink = link;
                break;
            }
        }

        return  tempLink;
    }

    /**
     * Find a link that contains the provided nodeConnectorId but from the local cache
     *
     * @param nodeConnectorId
     * @return
     */
    private Link getLinkFromNodeConnectorIdInNetworkgraph(String nodeConnectorId) {
        Collection<Link> links = networkGraphService.getNetworkGraph().getEdges();
        Link tempLink = null;

        for (Link link: links) {
            if (link.getSource().getSourceTp().getValue().equals(nodeConnectorId)){
                tempLink = link;
                break;
            } else if (link.getDestination().getDestTp().getValue().equals(nodeConnectorId)) {
                tempLink = link;
                break;
            }
        }

        return  tempLink;
    }

    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("NetworkExtensionManager ownership change logged: " + ownershipChange);
        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the NetworkExtensionManager leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the NetworkExtensionManager follower.");
            setFollower();
        }
    }

    /**
     * Confirms this instance is the current cluster leader.
     */
    public static void setLeader() { isLeader = true; }

    /**
     * Sets this instance as a cluster follower.
     */
    public static void setFollower() {isLeader = false;}

}
