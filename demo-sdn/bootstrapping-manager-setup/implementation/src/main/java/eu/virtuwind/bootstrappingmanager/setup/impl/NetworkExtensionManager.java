package eu.virtuwind.bootstrappingmanager.setup.impl;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 14.08.18
 */

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import edu.uci.ics.jung.algorithms.shortestpath.PrimMinimumSpanningTree;
import edu.uci.ics.jung.graph.DelegateTree;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;

import java.util.*;
import java.util.concurrent.ExecutionException;

import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.IPPrefAddrInfo;
import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.InitialFlowUtils;
import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.InstanceIdentifierUtils;
import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.PacketUtils;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import static java.lang.Thread.sleep;

/**
 * Install MST rules to support later network extensions after FlowReconfigurator has finished its jobs.
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
    private static String cpNetworkPrefix;
    private static SalFlowService salFlowService;
    private static short flowTableId;
    private final static String dsTreeTopologyId = "mst";
    private final static String topologyId = "flow:1";
    private static boolean topologyDSCreated = false;

    // Graph stuff
    private static Graph<NodeId, Link> networkGraph;
    private static Graph<NodeId, Link> currentTreeGraph;
    private static Map<Link, Graph<NodeId, Link>> alternativeTreeGraphs = new HashMap<>();
    private static final Set<String> linkAddedToNetworkGraph = new HashSet<>();
    private static final Set<String> linkAddedTocurrentTreeGraph = new HashSet<>();
    private static HashMap<String, List<String>> treePortsCache = new HashMap<>();
    private static String lastFailedLinkId = "";
    private static HashMap<String, List<FlowCookie>> removableTreeFlowCookies = new HashMap<String, List<FlowCookie>>();

    // Tracking linkDown States
    private static HashMap<String, Boolean> isLinkDown = new HashMap<String, Boolean>();

    // config flag that enables/disables NEM features
    private static Boolean nemEnabled = true;

    // MacAddress duplicate tracker
    private static HashMap<String, String> macAddressDuplicateTracker = new HashMap<>();

    /**
     * Default Constructor
     */
    public NetworkExtensionManager(){
        LOG.info("NetworkExtensionManager object created.");
    }

    /**
     * Constructor
     *
     * @param dataBroker
     */
    public NetworkExtensionManager(DataBroker dataBroker, SalFlowService salFlowService) {
        this.dataBroker = dataBroker;
        this.salFlowService = salFlowService;
    }

    /**
     * Various setters
     *
     * @param cpNetworkPrefix
     */
    public static void setCpNetworkPrefix(String cpNetworkPrefix) {
        NetworkExtensionManager.cpNetworkPrefix = cpNetworkPrefix;
    }

    public static void setFlowTableId(short flowTableId) {
        NetworkExtensionManager.flowTableId = flowTableId;
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
     * Triggered by FlowReconfigurator - leader only
     */
    @Override
    public void run() {

        if (nemEnabled) {

            List<Link> networkLinks = null;
            try {
                networkLinks = FlowReconfigurator.getAllLinks(dataBroker);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            /**
             * During the Step 3a (InitialFlowWriter) some weird links appear occasionally (even loops);
             * This is because switches that do not have OF connection with the controller are transparent!!!
             * Also when RSTP gets disabled, temporarily, the learned topology can be incorrect.
             * these cause that networkGraph initially contains some unexisting links which leads to incorrect
             * input to Prim's algorithm. Thus, reset networkGraph here.
             */
            networkGraph = null;
            linkAddedToNetworkGraph.clear();

            addLinksIntoNetworkGraph(networkLinks);
            if (networkGraph != null) {
                PrimMinimumSpanningTree<NodeId, Link> networkMst = new PrimMinimumSpanningTree<>(
                        DelegateTree.<NodeId, Link>getFactory());
                Graph<NodeId, Link> mstGraph = networkMst.transform(networkGraph);
                Collection<Link> mstLinks = mstGraph.getEdges();
                addLinksIntoCurrentTreeGraph(mstLinks);
                printGraph(networkGraph, "MAIN-GRAPH");
                printGraph(currentTreeGraph, "MAIN-TREE");

                Collection<NodeId> switches = networkGraph.getVertices();

                // install normal tree rules to all switches
                for (NodeId switchId : switches) {
                    if (!switchId.getValue().contains("host")) {
                        List<String> treePorts = findTreePorts(switchId.getValue());
                        treePortsCache.put(switchId.getValue(), treePorts);
                        installTreeRulesInSwitch(switchId, treePorts);
                        LOG.info("Tree rules installed in {}", switchId.getValue());
                    }
                }

                // install special rules that send DHCP packets from the previously non-functional ports to the controller
                for (NodeId switchId : switches) {
                    if (!switchId.getValue().contains("host")) {
                        List<NodeConnector> nonFunctionalPorts = getAllNonFunctionalNodeConnectorsFromNode(switchId.getValue(), dataBroker);
                        if (!nonFunctionalPorts.isEmpty()) {
                            // install special rules for this node connectors
                            installSpecialTreeRulesInSwitch(switchId, nonFunctionalPorts);
                            LOG.info("Special tree rule installed for the NodeConnectors {}", nonFunctionalPorts.toString());
                        }
                    }
                }

                // write the tree topology to the datastore
                writeCurrentTreeInDS();

                //compute alternative trees if a link in the current tree fails
                computeAlternativeTrees();
                writeAlternativeTreesInDS();

            }

            // Just to know when to stop measurements
            try {
                sleep(60000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            LOG.info("Measurements done");
        }
    }

    /**
     * Processes the DHCP DISCOVERY PACKET-IN from the new added switch in the network
     *
     * @param packetReceived
     */
    @Override
    public void onPacketReceived(PacketReceived packetReceived) {

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

                        The new appeared link should be processed also and added to the tree graph, as well as to the tree topology in the datastore

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
                             *  It does not represent a problem, but it is will be a redundant rule.
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
            if (nemEnabled) {
                if (isLeader) {
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
                                    // install special rules for this node connectors
                                    List<NodeConnector> nonFunctionalPorts = new ArrayList<>();
                                    nonFunctionalPorts.add(nodeConnector);
                                    installSpecialTreeRulesInSwitch(new NodeId(nodeConnectorId.split(":")[0] + ":" + nodeConnectorId.split(":")[1]),
                                            nonFunctionalPorts);
                                    LOG.info("Special tree rule installed for the NodeConnectors {}", nonFunctionalPorts.toString());
                                }

                                // add entry to isLinkDown cache
                                isLinkDown.put(nodeConnectorId, flowConnector.getState().isLinkDown());

                                // build the topology
                                // find the added link
                                Link addedLink = getLinkFromNodeConnectorId(nodeConnectorId);
                                if (addedLink != null) {
                                    LOG.info("Added linkId -> {}", addedLink.getLinkId().getValue());
                                    // update the networkGraph
                                    List<Link> linksForAddition = new ArrayList<>();
                                    linksForAddition.add(addedLink);
                                    addLinksIntoNetworkGraph(linksForAddition);
                                }

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
                                Debugging showed that before state is always equal to the after state!!!!
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
                                // see the issue above
                                //if (flowConnectorBefore.getState().isLinkDown() == false && flowConnectorAfter.getState().isLinkDown() == true) {
                                if (beforeState == false && flowConnectorAfter.getState().isLinkDown() == true) {


                                    LOG.info("Link failure has happened -> {}", nodeConnectorAfter.getId().getValue());

                                    // find the failed link
                                    Link failedLink = getLinkFromNodeConnectorIdInNetworkgraph(nodeConnectorAfter.getId().getValue());
                                    if (failedLink == null) {
                                        LOG.info("Failed link was already processed and removed from networkGraph");
                                        return;
                                    }
                                    LOG.info("Failed linkId -> {}", failedLink.getLinkId().getValue());

                                    // remove failed link from the  networkGraph
                                    List<Link> linksForRemoval = new ArrayList<>();
                                    linksForRemoval.add(failedLink);
                                    removeLinksFromNetworkGraph(linksForRemoval);
                                    LOG.info("Failed link {} removed from the networkGraph!!", failedLink.getLinkId().getValue());

                                    // check if the link belongs to the tree
                                    boolean linkInTheCurrentTree = checkIfLinkInTheCurrentTree(nodeConnectorAfter.getId().getValue());
                                    LOG.info("Failed link is in the current tree: {}", linkInTheCurrentTree);

                                    if (linkInTheCurrentTree) {

                                        // delete the old tree using cookies
                                        LOG.info("Removing old tree rules!");
                                        removeBrokenTreeRules();

                                        // load the alternative tree - it becomes the current tree
                                        Graph<NodeId, Link> alternativeTreeGraph = createGraphClone(alternativeTreeGraphs.get(failedLink));

                                        // find edge differences between the currentHopByHopTreeGraph and alternativeHopByHopTreeGraph
                                        // necessary for proper DS cleaning
                                        List<Link> edgeDifferences = new LinkedList<>();
                                        for (Link link: currentTreeGraph.getEdges()) {
                                            if (!alternativeTreeGraph.containsEdge(link)) {
                                                edgeDifferences.add(link);
                                            }
                                        }

                                        // swap currentTreeGraph with alternativeTreeGraph
                                        printGraph(currentTreeGraph, "BEFORE");
                                        currentTreeGraph = alternativeTreeGraph;
                                        printGraph(currentTreeGraph, "AFTER");


                                        // clear previous values
                                        treePortsCache.clear();

                                        // install alternative tree rules to switches
                                        for (NodeId switchId : networkGraph.getVertices()) {
                                            if (!switchId.getValue().contains("host")) {
                                                List<String> treePorts = findTreePorts(switchId.getValue());
                                                treePortsCache.put(switchId.getValue(), treePorts);
                                                installTreeRulesInSwitch(switchId, treePorts);
                                                LOG.info("Alternative tree rules installed in {}", switchId.getValue());
                                            }
                                        }
                                        LOG.info("Alternative tree installed in the network!!!");

                                        // write the new current tree in DS
                                        writeCurrentTreeInDS();
                                        LOG.info("Alternative tree written into DS!!!");

                                        // create alternative trees for the new current tree
                                        printAlternativeTreesMap();
                                        computeAlternativeTrees();
                                        printAlternativeTreesMap();

                                        writeAlternativeTreesInDS();
                                        LOG.info("New alternative trees computed and written into DS");

                                        // clear the old alternative trees from the DS
                                        for (Link link: edgeDifferences) {
                                            deleteAlternativeTreeFromDS(link.getLinkId().getValue());
                                            LOG.info("Cleaning the old alternative trees stored in DS");
                                        }

                                    }
                                } else if (beforeState == true && flowConnectorAfter.getState().isLinkDown() == false) {

                                    // find the added link
                                    Link addedLink = getLinkFromNodeConnectorId(nodeConnectorAfter.getId().getValue());
                                    LOG.info("Added linkId -> {}", addedLink.getLinkId().getValue());

                                    // update the networkGraph
                                    List<Link> linksForAddition = new ArrayList<>();
                                    linksForAddition.add(addedLink);
                                    addLinksIntoNetworkGraph(linksForAddition);
                                }
                            }
                        }
                    }
                } else {
                    LOG.info("NodeConnector state changed, but not the leader!!!");
                }
            }
        }
    }


    /**
     * Add all topology links to the network graph object
     *
     * @param links
     */
    public static void addLinksIntoNetworkGraph(List<Link> links) {
        if (links == null || links.isEmpty()) {
            LOG.info("In addLinks: No link added as links is null or empty.");
            return;
        }

        if (networkGraph == null) {
            networkGraph = new SparseMultigraph<>();
        }

        for (Link link : links) {
            if (linkAlreadyAddedToNetworkGraph(link)) {
                LOG.info("Link already added before to networkGraph -> {}", link.getLinkId().getValue());
                continue;
            }
            LOG.info("Add link to networkGraph -> {}", link.getLinkId().getValue());
            NodeId sourceNodeId = link.getSource().getSourceNode();
            NodeId destinationNodeId = link.getDestination().getDestNode();
            networkGraph.addVertex(sourceNodeId);
            networkGraph.addVertex(destinationNodeId);
            networkGraph.addEdge(link, sourceNodeId, destinationNodeId, EdgeType.UNDIRECTED);
        }

    }

    /**
     * Add all topology links to the tree graph object
     *
     * @param links
     */
    public static void addLinksIntoCurrentTreeGraph(Collection<Link> links) {
        if (links == null || links.isEmpty()) {
            LOG.info("In addLinks: No link added as links is null or empty.");
            return;
        }

        if (currentTreeGraph == null) {
            currentTreeGraph = new SparseMultigraph<>();
        }

        for (Link link : links) {
            if (linkAlreadyAddedTocurrentTreeGraph(link)) {
                continue;
            }
            NodeId sourceNodeId = link.getSource().getSourceNode();
            NodeId destinationNodeId = link.getDestination().getDestNode();
            currentTreeGraph.addVertex(sourceNodeId);
            currentTreeGraph.addVertex(destinationNodeId);
            currentTreeGraph.addEdge(link, sourceNodeId, destinationNodeId, EdgeType.UNDIRECTED);
        }

    }

    /**
     * Check if the link is already added to the network graph
     *
     * @param link
     * @return
     */
    private static boolean linkAlreadyAddedToNetworkGraph(Link link) {
        String linkAddedKey = null;
        if (link.getDestination().getDestTp().hashCode() > link.getSource().getSourceTp().hashCode()) {
            linkAddedKey = link.getSource().getSourceTp().getValue() + link.getDestination().getDestTp().getValue();
        } else {
            linkAddedKey = link.getDestination().getDestTp().getValue() + link.getSource().getSourceTp().getValue();
        }
        if (linkAddedToNetworkGraph.contains(linkAddedKey)) {
            return true;
        } else {
            linkAddedToNetworkGraph.add(linkAddedKey);
            return false;
        }
    }

    /**
     * Check if the link is already added to the tree graph
     * @param link
     * @return
     */
    private static boolean linkAlreadyAddedTocurrentTreeGraph(Link link) {
        String linkAddedKey = null;
        if (link.getDestination().getDestTp().hashCode() > link.getSource().getSourceTp().hashCode()) {
            linkAddedKey = link.getSource().getSourceTp().getValue() + link.getDestination().getDestTp().getValue();
        } else {
            linkAddedKey = link.getDestination().getDestTp().getValue() + link.getSource().getSourceTp().getValue();
        }
        if (linkAddedTocurrentTreeGraph.contains(linkAddedKey)) {
            return true;
        } else {
            linkAddedTocurrentTreeGraph.add(linkAddedKey);
            return false;
        }
    }

    /**
     * Remove links from the networkGraph object
     *
     * @param links
     */
    private void removeLinksFromNetworkGraph(List<Link> links) {
        Preconditions.checkNotNull(networkGraph, "Graph is not initialized, add links first.");

        if (links == null || links.isEmpty()) {
            LOG.info("In removeLinks: No link removed as links is null or empty.");
            return;
        }

        for (Link link : links) {
            LOG.info("Removing link {} from networkGraph!", link.getLinkId().getValue());
            networkGraph.removeEdge(link);
            String linkAddedKey = null;
            if (link.getDestination().getDestTp().hashCode() > link.getSource().getSourceTp().hashCode()) {
                linkAddedKey = link.getSource().getSourceTp().getValue() + link.getDestination().getDestTp().getValue();
            } else {
                linkAddedKey = link.getDestination().getDestTp().getValue() + link.getSource().getSourceTp().getValue();
            }
            boolean res = linkAddedToNetworkGraph.remove(linkAddedKey);
            LOG.info("Link successfully removed from linkAddedToNetworkGraph: {}", res);
        }

    }

    /**
     * Return a copy of the provided graph
     *
     * @param graph
     */
    private static Graph<NodeId, Link> createGraphClone(Graph<NodeId, Link> graph) {

        Graph<NodeId, Link> tempGraph = new SparseMultigraph<>();

        Collection<Link> links = graph.getEdges();

        for (Link link: links) {
            NodeId sourceNodeId = link.getSource().getSourceNode();
            NodeId destinationNodeId = link.getDestination().getDestNode();
            tempGraph.addVertex(sourceNodeId);
            tempGraph.addVertex(destinationNodeId);
            tempGraph.addEdge(link, sourceNodeId, destinationNodeId, EdgeType.UNDIRECTED);
        }

        return  tempGraph;
    }

    /**
     * Extract tree ports from the tree graph for the given switch
     *
     * @param nodeId
     * @return
     */
    private List<String> findTreePorts(String nodeId) {

        if (currentTreeGraph == null) {
            LOG.warn("currentTreeGraph currently not available!");
            return null;
        }

        if (!currentTreeGraph.containsVertex(new NodeId(nodeId))) {
            LOG.warn("Node {} does not exist in the tree!", nodeId);
            return null;
        }

        Collection<Link> treeIncidentEdges = currentTreeGraph.getIncidentEdges(new NodeId(nodeId));
        List<String> treePorts = new ArrayList<>();

        for (Link link: treeIncidentEdges) {
            if (link.getSource().getSourceNode().getValue().equals(nodeId)) {
                String port = link.getSource().getSourceTp().getValue().split(":")[2];
                treePorts.add(port);
            } else if (link.getDestination().getDestNode().getValue().equals(nodeId)) {
                String port = link.getDestination().getDestTp().getValue().split(":")[2];
                treePorts.add(port);
            }
        }

        return treePorts;
    }

    /**
     * Install tree rules in the given switch
     *
     * @param switchId
     * @param treePorts
     */
    private void installTreeRulesInSwitch(NodeId switchId, List<String> treePorts) {

        IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
        IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo();
        List<FlowCookie> rulesToRemember = new ArrayList<>();

        for (String inPort: treePorts) {
            List<String> outPorts = new ArrayList<>(treePorts);
            outPorts.remove(inPort);

            InstanceIdentifier<Node> nodeId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, new NodeKey(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId(switchId))).build();
            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeId, flowTableId);

            InstanceIdentifier<Flow> flowIdFromController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            InstanceIdentifier<Flow> flowIdToController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            // OF traffic for the controller broadcast
            srcPrefix = new IPPrefAddrInfo();
            dstPrefix = new IPPrefAddrInfo(); // leave intentionally empty
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

            InstanceIdentifier<Flow> flowIdGeneralARP = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow flowSSHGeneralARP = null;

            // ARP traffic general both requests and replies
            flowSSHGeneralARP = InitialFlowUtils.createARPFlowGeneral(flowTableId, 99, inPort, outPorts, switchId.getValue());
            rulesToRemember.add(flowSSHGeneralARP.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdGeneralARP, flowSSHGeneralARP);

            // remember rule cookies for eventual later removal purposes
            removableTreeFlowCookies.put(switchId.getValue(), rulesToRemember);

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
        // notify NetworkExtensionTreeUpdate about the new nodeConnecotr that should appear 
        NetworkExtensionTreeUpdate.nodeConnectorToCheck = nodeConnectorId;
        
        String switchId = nodeConnectorId.split(":")[0] + ":" + nodeConnectorId.split(":")[1];
        String newLivePort = nodeConnectorId.split(":")[2];
        List<String> treePorts = treePortsCache.get(switchId);
        treePorts.add(newLivePort);
        // update cache
        treePortsCache.put(switchId, treePorts);

        // special rules and old tree rules should be overwritten
        installTreeRulesInSwitch(new NodeId(switchId), treePorts);

    }

    public static void writeCurrentTreeInDS() {

        // TODO: Optimize for performance compare if necessary to write
        if (!topologyDSCreated) {
            InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                    .generateTopologyInstanceIdentifier(dsTreeTopologyId);
            WriteTransaction createTopologyMSTTreeWriteTranscation = dataBroker.newWriteOnlyTransaction();
            Topology treeTopology = new TopologyBuilder().setTopologyId(new TopologyId(dsTreeTopologyId)).build();

            createTopologyMSTTreeWriteTranscation.put(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier, treeTopology);

            CheckedFuture<Void, TransactionCommitFailedException> futureTreeTopology =
                    createTopologyMSTTreeWriteTranscation.submit();

            Futures.addCallback(futureTreeTopology, new FutureCallback<Void>() {
                        public void onSuccess(Void v) {
                            LOG.info("Minimum-Spanning-Tree Topology Data-Tree Initialized");
                        }
                        public void onFailure(Throwable thrown) {
                            LOG.info("Minimum-Spanning-Tree Topology Data-Tree Initialization Failed.");
                        }
                    }
            );

            topologyDSCreated = true;
        }

        InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                .generateTopologyInstanceIdentifier(dsTreeTopologyId);

        WriteTransaction onlyWriteTransaction = dataBroker.newWriteOnlyTransaction();
        List<Link> treeEdges = new ArrayList<>(currentTreeGraph.getEdges());
        List<NodeId> treeVerticesIds = new ArrayList<>(currentTreeGraph.getVertices());
        List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node> treeVertices =
                InitialFlowUtils.getTopologyNodesFromTopologyNodeIds(treeVerticesIds, topologyId, dataBroker);
        Topology treeTopologyToWrite = new TopologyBuilder().setTopologyId(new TopologyId(dsTreeTopologyId))
                .setLink(treeEdges).setNode(treeVertices).build();

        // merge or put  -> merge preserves old data which leads to inconsistent links in the tree
        onlyWriteTransaction.put(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier, treeTopologyToWrite);
        CheckedFuture<Void, TransactionCommitFailedException> futureTreeTopology =
                onlyWriteTransaction.submit();

        Futures.addCallback(futureTreeTopology, new FutureCallback<Void>() {
                    public void onSuccess(Void v) {
                        LOG.info("Minimum-Spanning-Tree Topology Refreshed");
                    }
                    public void onFailure(Throwable thrown) {
                        LOG.info("Minimum-Spanning-Tree Topology Refreshing Failed.");
                    }
                }
        );
    }

    /**
     * Write all alternative trees in DS
     */
    public static void writeAlternativeTreesInDS() {

        // for each alternative tree create a topology and write it into the DS
        for (Map.Entry<Link, Graph<NodeId, Link>> alternativeTree: alternativeTreeGraphs.entrySet()) {

            String alternativeTreeTopologyId = "mst-alternative-" + alternativeTree.getKey().getLinkId().getValue();

            InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                    .generateTopologyInstanceIdentifier(alternativeTreeTopologyId);

            WriteTransaction onlyWriteTransaction = dataBroker.newWriteOnlyTransaction();
            List<Link> treeEdges = new ArrayList<>(alternativeTree.getValue().getEdges());
            List<NodeId> treeVerticesIds = new ArrayList<>(alternativeTree.getValue().getVertices());
            List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node> treeVertices =
                    InitialFlowUtils.getTopologyNodesFromTopologyNodeIds(treeVerticesIds, topologyId, dataBroker);
            Topology treeTopologyToWrite = new TopologyBuilder().setTopologyId(new TopologyId(alternativeTreeTopologyId))
                    .setLink(treeEdges).setNode(treeVertices).build();

            // merge or put  -> merge preserves old data which leads to inconsistent links in the tree
            onlyWriteTransaction.put(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier, treeTopologyToWrite);
            CheckedFuture<Void, TransactionCommitFailedException> futureTreeTopology =
                    onlyWriteTransaction.submit();

            Futures.addCallback(futureTreeTopology, new FutureCallback<Void>() {
                        public void onSuccess(Void v) {
                            LOG.info("Minimum-Spanning-Tree Alternative Refreshed");
                        }

                        public void onFailure(Throwable thrown) {
                            LOG.info("Minimum-Spanning-Tree Alternative Refreshing Failed.");
                        }
                    }
            );

        }
    }

    /**
     * Delete an alternative tree topology for DS
     *
     * @param linkId
     */
    private void deleteAlternativeTreeFromDS(String linkId) {

        String alternativeTreeTopologyId = "mst-alternative-" + linkId;

        InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                .generateTopologyInstanceIdentifier(alternativeTreeTopologyId);
        WriteTransaction onlyWriteTransaction = dataBroker.newWriteOnlyTransaction();
        onlyWriteTransaction.delete(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier);

        CheckedFuture<Void, TransactionCommitFailedException> futureTreeTopology =
                onlyWriteTransaction.submit();

        Futures.addCallback(futureTreeTopology, new FutureCallback<Void>() {
                    public void onSuccess(Void v) {
                        LOG.info("Minimum-Spanning-Tree Alternative topology deleted");
                    }

                    public void onFailure(Throwable thrown) {
                        LOG.info("Minimum-Spanning-Tree Alternative topology delete failure.");
                    }
                }
        );
    }

    /**
     * Read network-topology container from DS
     *
     * @return
     */
    private static List<Topology> readAllTopologiesFromDS() {

        List<Topology> currentStoredTopologiesInDS = new ArrayList<>();

        ReadOnlyTransaction readTransaction = dataBroker.newReadOnlyTransaction();
        InstanceIdentifier<NetworkTopology> nodeInstanceIdentifier = InstanceIdentifier.builder(NetworkTopology.class).build();

        try {
            Optional<NetworkTopology> optionalData = readTransaction.read(LogicalDatastoreType.OPERATIONAL, nodeInstanceIdentifier).get();

            for (Topology topology: optionalData.get().getTopology()) {
                currentStoredTopologiesInDS.add(topology);
            }

        } catch (ExecutionException | InterruptedException | NullPointerException e) {
            readTransaction.close();
            e.printStackTrace();
            LOG.error("Could not read the topologies stored in DS!");
        }

        return  currentStoredTopologiesInDS;
    }


    /**
     * Computes an alternative tree for each link failure in the current tree
     */
    public static void computeAlternativeTrees() {

        Collection<Link> currentTreeLinks = currentTreeGraph.getEdges();

        for (Link currentTreeLink: currentTreeLinks) {
            Graph<NodeId, Link> tempGraph = createGraphClone(networkGraph);
            tempGraph.removeEdge(currentTreeLink);
            printGraph(tempGraph, "computeAlternativeTrees: networkGraph for link -> " + currentTreeLink.getLinkId().getValue());
            /**
             * TODO: it does not work for unconnected graphs
             * either remove the unconnected vertices and run the algorithm or
             * find an alternative that works in this case
             *
             */
            PrimMinimumSpanningTree<NodeId, Link> networkMst = new PrimMinimumSpanningTree<>(
                    DelegateTree.<NodeId, Link>getFactory());
            Graph<NodeId, Link> alternativeTree = networkMst.transform(tempGraph);
            printGraph(alternativeTree, "computeAlternativeTrees: produced tree for link -> " + currentTreeLink.getLinkId().getValue());
            alternativeTreeGraphs.put(currentTreeLink, alternativeTree);
        }
    }

    /**
     * Print alternativeTreeGraphs object
     */
    private void printAlternativeTreesMap(){
        for (Link link: alternativeTreeGraphs.keySet()) {
            printGraph(alternativeTreeGraphs.get(link), "In case of failure -> " + link.getLinkId().getValue());
        }
    }

    /**
     * Check if the provided nodeConnector is part of the current tree
     *
     * @param nodeConnectorId
     * @return
     */
    private boolean checkIfLinkInTheCurrentTree(String nodeConnectorId) {

        LOG.debug("checkIfLinkInTheCurrentTree -> Checking NodeConnector: {}", nodeConnectorId);
        try {
            Collection<Link> linksInTheCurrentTree = currentTreeGraph.getEdges();
            boolean result = false;
            for (Link link : linksInTheCurrentTree) {
                if (link.getSource().getSourceTp().getValue().equals(nodeConnectorId)) {
                    result = true;
                    break;
                } else if (link.getDestination().getDestTp().getValue().equals(nodeConnectorId)) {
                    result = true;
                    break;
                }
            }
            LOG.debug("checkIfLinkInTheCurrentTree -> Checked NodeConnector: {}", nodeConnectorId);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Something is wrong in checkIfLinkInTheCurrentTree");
            return false;
        }
    }

    /**
     * Check if the nodeConnectorId belongs to the lastFailedLink
     *
     * @param nodeConnectorId
     * @return
     */
    private boolean checkIfDuplicateNotification(String nodeConnectorId){

        LOG.debug("checkIfDuplicateNotification -> Checking NodeConnector: {}", nodeConnectorId);
        Collection<Link> linksInTheCurrentTree = currentTreeGraph.getEdges();
        String linkId = "";

        // find the link in the current tree that contains the provided nodeConnectorId
        for (Link link: linksInTheCurrentTree) {
            if (link.getSource().getSourceTp().getValue().equals(nodeConnectorId)){
                linkId = link.getLinkId().getValue();
                break;
            } else if (link.getDestination().getDestTp().getValue().equals(nodeConnectorId)) {
                linkId = link.getLinkId().getValue();
                break;
            }
        }

        LOG.debug("checkIfDuplicateNotification -> Checked NodeConnector: {}", nodeConnectorId);

        // compare with the last cached value
        if (linkId.equals(lastFailedLinkId)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Find a link that contains the provided nodeConnectorId
     *
     * @param nodeConnectorId
     * @return
     */
    private Link getLinkFromNodeConnectorId(String nodeConnectorId) {
        // with distributed DS an issue -> have to try multiple times

        List<Link> links = null;
        while (links == null) {
            try {
                links = FlowReconfigurator.getAllLinks(dataBroker);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

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
        Collection<Link> links = networkGraph.getEdges();
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
     * Delete the currentTreeGraph rules in all switches
     *
     */
    private void removeBrokenTreeRules(){

        for (String nodeId: removableTreeFlowCookies.keySet()) {
            for (FlowCookie flowCookie: removableTreeFlowCookies.get(nodeId)) {
                InitialFlowUtils.removeOpenFlowFlow(salFlowService, flowCookie, nodeId);
            }
        }
        removableTreeFlowCookies.clear();
    }

    /**
     * Create Graph object from the provided Topology object
     *
     * @param topology
     */
    private static Graph<NodeId, Link> transformTopologyToGraph(Topology topology) {

        Graph<NodeId, Link> graph = new SparseMultigraph<>();

        for (Link link : topology.getLink()) {

            NodeId sourceNodeId = link.getSource().getSourceNode();
            NodeId destinationNodeId = link.getDestination().getDestNode();
            graph.addVertex(sourceNodeId);
            graph.addVertex(destinationNodeId);
            graph.addEdge(link, sourceNodeId, destinationNodeId, EdgeType.UNDIRECTED);
        }

        return  graph;
    }

    /**
     * Print graph nicely
     *
     * @param graph
     */
    private static void printGraph(Graph<NodeId, Link> graph, String graphName) {

        Collection<NodeId> vertices = graph.getVertices();
        Collection<Link> edges = graph.getEdges();

        LOG.debug("Graph -> {}", graphName);
        /*for (NodeId nodeId: vertices) {
            LOG.info("Vertex -> {}", nodeId.getValue());
        }*/

        for (Link link: edges) {
            LOG.debug("Edge -> {} - {}", link.getSource().getSourceTp().getValue(), link.getDestination().getDestTp().getValue());
        }
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
    public static void setLeader() {

        isLeader = true;

        List<Topology> storedTopologiesInDS = readAllTopologiesFromDS();

        // get all links from flow:1 topology
        List<Link> networkGraphLinks = new ArrayList<>();
        for (Topology topology: storedTopologiesInDS) {
            if (topology.getTopologyId().getValue().equals(topologyId)) {
                networkGraphLinks = topology.getLink();
                break;
            }
        }

        // load the topologies in the local cache variables
        if (!storedTopologiesInDS.isEmpty()) {
            for (Topology dsTopology: storedTopologiesInDS) {
                if (dsTopology.getTopologyId().getValue().equals(topologyId)) {
                    addLinksIntoNetworkGraph(dsTopology.getLink());
                    LOG.info("Adding {} topology to networkGraph variable", topologyId);
                } else if (dsTopology.getTopologyId().getValue().equals(dsTreeTopologyId)) {
                    addLinksIntoCurrentTreeGraph(dsTopology.getLink());
                    LOG.info("Adding {} topology to currentTreeGraph variable", dsTreeTopologyId);
                } else if (dsTopology.getTopologyId().getValue().contains("mst-alternative")) {
                    String linkId = dsTopology.getTopologyId().getValue().split("-")[2];
                    // find the link from linkId
                    Link keyLink = null;
                    for (Link link: networkGraphLinks) {
                        if (link.getLinkId().getValue().equals(linkId)) {
                            keyLink = link;
                            break;
                        }
                    }
                    // add alternative tree to the local cache
                    alternativeTreeGraphs.put(keyLink, transformTopologyToGraph(dsTopology));
                    LOG.info("Adding {} topology to alternativeTreeGraphs map", dsTopology.getTopologyId().getValue());
                }
            }
        }
    }

    /**
     * Sets this instance as a cluster follower.
     */
    public static void setFollower() {isLeader = false;}

}
