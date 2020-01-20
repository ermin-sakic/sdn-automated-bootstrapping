/**
  *
  * @filename TreeUtils.java
  *
  * @date 30.04.18
  *
  * @author Mirza Avdic
  *
  *
 */
package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities;

import com.google.common.base.Optional;
import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.address.node.connector.Addresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.host.AttachmentPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.HostNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.Thread.sleep;

/**
 * Various utility functions that are related to the tree processing.
 */
public class TreeUtils {

    private static DataBroker dataBroker = null;
    private static NetworkGraphService networkGraphService = null;
    private static String topologyId;

    private static final Logger LOG = LoggerFactory.getLogger(TreeUtils.class);

    public static DataBroker getDataBroker() {
        return dataBroker;
    }

    public static void setDataBroker(DataBroker dataBroker, NetworkGraphService networkGraphService) {
        TreeUtils.dataBroker = dataBroker;
        TreeUtils.networkGraphService = networkGraphService;
    }


    public static String getTopologyId() {
        return topologyId;
    }

    public static void setTopologyId(String topologyId) {
        TreeUtils.topologyId = topologyId;
    }


    /**
     * Finds the root node of the hop-by-hop built tree.
     * The result can be parsed to find out root node and root port on that node
     *
     * @param controllerIP IP address of the controller which is treated as s a root
     * @param topologyId   ID of the topology that is searched
     * @return String in the format NodeId:NodeConnectorId (e.g. openflow:1:3) or empty String if the root is not found
     */
    public static String findRootSwitchAndRootSwitchPort(String controllerIP, String topologyId) {
        //get all nodes from the NetworkTopology

        List<Node> networkTopologyNodes = getNetworkTopologyNodes(topologyId);
        String rootNode = "";

        for (Node topologyNode : networkTopologyNodes) {
            if (topologyNode.getKey().getNodeId().getValue().contains("host")) {
                HostNode hostNode = topologyNode.getAugmentation(HostNode.class);
                for (AttachmentPoints ap : hostNode.getAttachmentPoints()) {
                    if (ap.getKey().getTpId().getValue().contains("openflow")) {
                        for (Addresses addresses : hostNode.getAddresses()) {
                            if (addresses.getIp().getIpv4Address().getValue().equals(controllerIP)) {
                                rootNode = ap.getKey().getTpId().getValue();
                                break;
                            }
                        }
                    }
                }
            }
        }
        LOG.debug("Root switch and port: {}", rootNode);
        return rootNode;

    }

    /**
     * Returns a list of all nodes stored in the NetworkTopology datastore
     *
     * @param topologyId
     * @return List<Node>
     */
    public static List<Node> getNetworkTopologyNodes(String topologyId) {

        InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                .generateTopologyInstanceIdentifier(topologyId);

        Topology topology = null;
        ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction();
        try {
            Optional<Topology> topologyOptional = readOnlyTransaction
                    .read(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier).get();
            if (topologyOptional.isPresent()) {
                topology = topologyOptional.get();
            }
        } catch (Exception e) {
            LOG.error("Error reading topology {}", topologyInstanceIdentifier);
            readOnlyTransaction.close();
            throw new RuntimeException(
                    "Error reading from operational store, topology : " + topologyInstanceIdentifier, e);
        }
        readOnlyTransaction.close();
        if (topology == null) {
            return null;
        }

        List<Node> topologyNodes = topology.getNode();
        if (topologyNodes == null || topologyNodes.isEmpty()) {
            return null;
        }

        return topologyNodes;
    }

    /**
     * Returns a list of all links stored in the NetworkTopology datastore
     *
     * @param topologyId
     * @return List<Link>
     */
    public static List<Link> getNetworkTopologyLinks(String topologyId) {

        InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                .generateTopologyInstanceIdentifier(topologyId);

        Topology topology = null;
        ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction();
        try {
            Optional<Topology> topologyOptional = readOnlyTransaction
                    .read(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier).get();
            if (topologyOptional.isPresent()) {
                topology = topologyOptional.get();
            }
        } catch (Exception e) {
            LOG.error("Error reading topology {}", topologyInstanceIdentifier);
            readOnlyTransaction.close();
            throw new RuntimeException(
                    "Error reading from operational store, topology : " + topologyInstanceIdentifier, e);
        }
        readOnlyTransaction.close();
        if (topology == null) {
            return null;
        }

        List<Link> topologyLinks = topology.getLink();
        if (topologyLinks == null || topologyLinks.isEmpty()) {
            return null;
        }

        return topologyLinks;

    }

    /**
     * Converts NodeId to Node in the given topology
     *
     * @param nodesId
     * @param topologyId
     * @return
     */
    public static List<Node> getTopologyNodesFromTopologyNodeIds(List<NodeId> nodesId, String topologyId) {
        List<Node> desiredNodes = new ArrayList<>();
        List<Node> allNodes = getNetworkTopologyNodes(topologyId);
        for (Node node: allNodes) {
            if (nodesId.contains(node.getNodeId())) {
                desiredNodes.add(node);
            }
        }

        return desiredNodes;
    }

    /**
     * Converts NodeId to Node in the given topology
     *
     * @param nodesId
     * @return
     */
    public static List<Node> getTopologyNodesFromTopologyNodeIds(List<NodeId> nodesId) {
        List<Node> desiredNodes = new ArrayList<>();
        List<Node> allNodes = getNetworkTopologyNodes(topologyId);
        for (Node node: allNodes) {
            if (nodesId.contains(node.getNodeId())) {
                desiredNodes.add(node);
            }
        }

        return desiredNodes;
    }

    /**
     * Finds out the root port (the one leading toward the root node) of the node
     * provided via nodeID
     *
     * @param nodeID
     * @param controllerIp
     * @param topologyId
     * @return root port of the nodeID
     */
    public static synchronized String findRootPort(String nodeID, String controllerIp, String topologyId) {

        HopByHopTreeGraph currentTree = networkGraphService.getCurrentHopByHopTreeGraph();
        if (currentTree == null) {
            LOG.warn("NetworkGraphService currently not available!");
            return "";
        }

        if (!currentTree.containsVertex(new NodeId(nodeID))) {
            LOG.warn("Node {} does not exist in the tree!", nodeID);
            return "";
        }

        String root = findRootSwitchAndRootSwitchPort(controllerIp, topologyId);
        String[] rootParts = root.split(":");
        String rootNodeId = rootParts[0] + ":" + rootParts[1];
        String rootNodePort = rootParts[2];
        LOG.debug("rootNodeId: {}", rootNodeId);
        LOG.debug("rootNodePort: {}", rootNodePort);
        NodeId rootId = new NodeId(rootNodeId);
        NodeId nodeId = new NodeId(nodeID);

        if (nodeID.contains("host"))
            return "This is host";

        if (rootId.getValue().equals(nodeId.getValue())) {
            return rootNodePort;
        } else {
            Graph<NodeId, Link> g = networkGraphService.getCurrentHopByHopTreeGraph();
            DijkstraShortestPath<NodeId, Link> shortestPath = new DijkstraShortestPath<NodeId, Link>(g);
            List<Link> sp = shortestPath.getPath(rootId, nodeId);

            for (Link link : sp) {
                if (link.getSource().getSourceNode().getValue().equals(nodeId.getValue())) {
                    String rootPort = link.getSource().getSourceTp().getValue();
                    return rootPort.split(":")[2];
                } else if (link.getDestination().getDestNode().getValue().equals(nodeId.getValue())) {
                    String rootPort = link.getDestination().getDestTp().getValue();
                    return rootPort.split(":")[2];
                }
            }

            return "";
        }
    }

    /**
     * For a given nodeID finds which ports can be used for tree broadcasting.
     * The heuristic is as follows:
     * For only topology relevant switch ports return those ones that:
     *      are not rootPort (the closest one towards a controller)
     *      are not ports leading to the node of the same tree level
     *      lead to the  neighbor nodes of the higher tree level
     *
     * @param nodeID
     * @param controllerIp
     * @param topologyId
     * @return ports leading to the higher level neighbor tree nodes
     */
    public static synchronized List<String> findQuasiBroadcastPorts(String nodeID, String controllerIp, String topologyId) {

        HopByHopTreeGraph currentTree = networkGraphService.getCurrentHopByHopTreeGraph();
        List<String> quasiBroadcastPorts = new ArrayList<String>();
        if (currentTree == null) {
            LOG.warn("NetworkGraphService currently not available!");
            return null;
        }

        if (!currentTree.containsVertex(new NodeId(nodeID))) {
            LOG.warn("Node {} does not exist in the tree!", nodeID);
            return null;
        }

        String root = findRootSwitchAndRootSwitchPort(controllerIp, topologyId);
        String[] rootParts = root.split(":");
        String rootNodeId = rootParts[0] + ":" + rootParts[1];
        String rootNodePort = rootParts[2];
        LOG.debug("rootNodeId: {}", rootNodeId);
        LOG.debug("rootNodePort: {}", rootNodePort);
        NodeId rootId = new NodeId(rootNodeId);
        NodeId nodeId = new NodeId(nodeID);

        if (nodeID.contains("host"))
            return null;

        if (rootId.getValue().equals(nodeId.getValue())) {
            Collection<NodeId> neighbors = currentTree.getNeighbors(rootId);
            LOG.debug("findQuasiBroadcastPorts: next level neighbors -> {}", neighbors.toString());
            for (NodeId neighbour: neighbors){
                if (neighbour.getValue().contains("host")) {
                    continue;
                } else {
                    Link link = currentTree.findEdge(rootId, neighbour);
                    LOG.debug("findQuasiBroadcastPorts: Edge Src -> {} Edge Dst -> {}",
                            link.getSource().getSourceTp().getValue(), link.getDestination().getDestTp().getValue());
                    if (link.getSource().getSourceNode().getValue().equals(rootId.getValue())) {
                        quasiBroadcastPorts.add(link.getSource().getSourceTp().getValue().split(":")[2]);
                    } else {
                        quasiBroadcastPorts.add(link.getDestination().getDestTp().getValue().split(":")[2]);
                    }
                }
            }

            return quasiBroadcastPorts;

        } else {
            Collection<NodeId> neighbors = currentTree.getNextLevelNeighbors(nodeId);
            LOG.debug("findQuasiBroadcastPorts: next level neighbors -> {}", neighbors.toString());
            for (NodeId neighbour: neighbors){
                    Link link = currentTree.findEdge(nodeId, neighbour);
                    LOG.debug("findQuasiBroadcastPorts: Edge Src -> {} Edge Dst -> {}",
                        link.getSource().getSourceTp().getValue(), link.getDestination().getDestTp().getValue());
                    if (link.getSource().getSourceNode().getValue().equals(nodeId.getValue())) {
                        quasiBroadcastPorts.add(link.getSource().getSourceTp().getValue().split(":")[2]);

                    } else {
                        quasiBroadcastPorts.add(link.getDestination().getDestTp().getValue().split(":")[2]);
                    }
            }
            return quasiBroadcastPorts;
        }
    }

    /**
     * For a given nodeId find tree ports that belong to this node
     *
     * @param nodeId
     * @return
     */
    public static synchronized List<String> findTreePorts(String nodeId) {

        HopByHopTreeGraph currentTree = networkGraphService.getCurrentHopByHopTreeGraph();
        if (currentTree == null) {
            LOG.warn("NetworkGraphService currently not available!");
            return null;
        }

        if (!currentTree.containsVertex(new NodeId(nodeId))) {
            LOG.warn("Node {} does not exist in the tree!", nodeId);
            return null;
        }

        Collection<Link> treeIncidentEdges = currentTree.getIncidentEdges(new NodeId(nodeId));
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
     * Finds out the node level in the tree
     * @param nodeID
     * @return the node level in the tree
     */
    public static synchronized int findNodeTreeLevel(String nodeID) {

        HopByHopTreeGraph currentTree = networkGraphService.getCurrentHopByHopTreeGraph();
        if (currentTree == null) {
            LOG.warn("NetworkGraphService currently not available!");
            return -1;
        }

        if (!currentTree.containsVertex(new NodeId(nodeID))) {
            LOG.warn("Node {} does not exist in the tree!", nodeID);
            return -1;
        }

        return currentTree.getVertexTreeLevel(new NodeId(nodeID));

    }

    /**
     * For a given nodeID estimates which ports can be used for tree broadcasting.
     * The heuristic is as follows:
     * For all available switch ports (not only topology relevant) return those ones that:
     *      are not rootPort (the closest one towards a controller)
     *      are not ports leading to the node of the same tree level
     *      lead to the  neighbor nodes of the higher tree level
     *
     * @param nodeID
     * @param controllerIp
     * @param topologyId
     * @return ports leading to the higher level neighbor tree nodes
     */
    public static synchronized List<String> estimateQuasiBroadcastingPortsBasedOnCurrentData(String nodeID, String controllerIp, String topologyId) throws InterruptedException {

        List<String> ports = new ArrayList<String>();
        HopByHopTreeGraph currentTree = networkGraphService.getCurrentHopByHopTreeGraph();

        String rootPort = findRootPort(nodeID, controllerIp, topologyId);
        Integer nodeTreeLevel = currentTree.getVertexTreeLevel(new NodeId(nodeID));
        if (nodeTreeLevel == null) {
            LOG.warn("Node {} still not added into the tree", nodeID );
            return new ArrayList<>();
        }

        // find other nodes on the same level
        Collection<NodeId> allNodesInTheTree = currentTree.getVertices();
        NodeId leaderController = currentTree.getGraphControllerLeader();
        List<NodeId> sameLevelNodes = new ArrayList<NodeId>();
        for (NodeId node: allNodesInTheTree){
            if (currentTree.getVertexTreeLevel(node).equals(nodeTreeLevel)
                    && !node.getValue().equals(leaderController.getValue())
                    && !node.getValue().equals(nodeID)) {
                sameLevelNodes.add(node);
            }
        }
        LOG.debug("IOFI: Same level Nodes of the Node {} are {}", nodeID, sameLevelNodes.toString());

        // find nodes on the level - 1
        List<NodeId> previousLevelNodes = new ArrayList<NodeId>();
        for (NodeId node: allNodesInTheTree){
            if (currentTree.getVertexTreeLevel(node).equals(nodeTreeLevel - 1)
                    && !node.getValue().equals(leaderController.getValue())
                    && !node.getValue().equals(nodeID)) {
                previousLevelNodes.add(node);
            }
        }
        LOG.debug("IOFI: Previous level Nodes of the Node {} are {}", nodeID, previousLevelNodes.toString());

        // find nodes on the level + 1
        List<NodeId> nextLevelNodes = new ArrayList<NodeId>();
        for (NodeId node: allNodesInTheTree){
            if (currentTree.getVertexTreeLevel(node).equals(nodeTreeLevel + 1)
                    && !node.getValue().equals(leaderController.getValue())
                    && !node.getValue().equals(nodeID)) {
                nextLevelNodes.add(node);
            }
        }
        LOG.debug("IOFI: Next level Nodes of the Node {} are {}", nodeID, nextLevelNodes.toString());

        // find all ports on the switch
        List<NodeConnector> nodeConnectors = null;
        while (nodeConnectors == null) { // with multiple controllers can be an issue they do not synchronize very fast
            LOG.warn("NodeConnectors for the node {} still not available in the DS", nodeID);
            nodeConnectors = InitialFlowUtils.getAllNodeConnectorsFromNode(nodeID, dataBroker);
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        LOG.debug("NodeConnectors for the node {} are available in the DS", nodeID);

        // estimate if each connector is suitable for being broadcasted on
        // may happen that some host is also connected
        // fixed in phase II when neighbors are discovered
        for (NodeConnector nodeConnector: nodeConnectors) {
            if (nodeConnector.getKey().getId().getValue().contains("LOCAL")){
                LOG.debug("IOFI: Node {}, port {} is LOCAL", nodeID, nodeConnector.getKey().getId().getValue());
                continue;
            } else if (nodeConnector.getKey().getId().getValue().split(":")[2].equals(rootPort)) {
                LOG.debug("IOFI: Node {}, port {} is ROOT", nodeID, nodeConnector.getKey().getId().getValue());
                continue;
            } else if (nodeConnectorInTheLinkBetweenNodes(nodeConnector, sameLevelNodes,
                    previousLevelNodes, nextLevelNodes, topologyId)) {
                LOG.debug("IOFI: Node {}, port {} leads to the node of the same level", nodeID, nodeConnector.getKey().getId().getValue());
                continue;
            } else {
                // should be safe for broadcasting
                LOG.debug("IOFI: Node {}, port {} is OK for broadcast", nodeID, nodeConnector.getKey().getId().getValue());
                ports.add(nodeConnector.getKey().getId().getValue());
            }
        }
        return ports;
    }

    /**
     * Checks if the given NodeConnector is part of the link that connects the NodeConnector´s
     * node and some other node that are on the same tree level (we do not want to broadcast to
     * these links) in order to avoid storming effects
     *
     * Checks also if the NodeConnector leads to the node of the smaller level
     * it can happen that a child has multiple parents in the previous level
     *
     * @param nodeConnector
     * @param sameLevelNodes
     * @param topologyId
     * @return true if such a link exists; false otherwise
     */
    private static boolean nodeConnectorInTheLinkBetweenNodes(NodeConnector nodeConnector, List<NodeId> sameLevelNodes,
                                                              List<NodeId> previousLevelNodes, List<NodeId> nextLevelNodes, String topologyId) {

        List<Link> topologyLinks = getNetworkTopologyLinks(topologyId);

        LOG.debug("IOFI: Processing same level nodes as the node {}", getNodeIdFromNodeConnectorId(nodeConnector.getId().getValue()));
        for (NodeId neighbor: sameLevelNodes){
            LOG.debug("IOFI: Processing the same level node {}", neighbor.getValue());
            for (Link link: topologyLinks){
                if (link.getSource().getSourceNode().getValue().equals(neighbor.getValue())) {
                    LOG.debug("IOFI: Same level node {} is part of the link: S->{} D->{}",
                            neighbor.getValue(), link.getSource().getSourceNode().getValue(), link.getDestination().getDestNode().getValue());
                    if (link.getDestination().getDestTp().getValue().equals(nodeConnector.getId().getValue())){
                        LOG.debug("IOFI: Same level node {} has the node connector {} as destination",
                                neighbor.getValue(), nodeConnector.getId().getValue());
                        LOG.debug("IOFI: DO NOT BROADCAST ON THIS NODE CONNECTOR");
                        return true;
                    }
                } else if (link.getDestination().getDestNode().getValue().equals(neighbor.getValue())) {
                    LOG.debug("IOFI: Same level node {} is part of the link: S->{} D->{}",
                            neighbor.getValue(), link.getSource().getSourceNode().getValue(), link.getDestination().getDestNode().getValue());
                    if (link.getSource().getSourceTp().getValue().equals(nodeConnector.getId().getValue())) {
                        LOG.debug("IOFI: Same level node {} has the node connector {} as destination",
                                neighbor.getValue() ,nodeConnector.getId().getValue());
                        LOG.debug("IOFI: DO NOT BROADCAST ON THIS NODE CONNECTOR");
                        return true;
                    }
                }
            }
        }
        LOG.debug("IOFI: Processing previous level nodes of the node {}", getNodeIdFromNodeConnectorId(nodeConnector.getId().getValue()));
        for (NodeId neighbor: previousLevelNodes){
            LOG.debug("IOFI: Processing the previous level node", neighbor.getValue());
            for (Link link: topologyLinks){
                if (link.getSource().getSourceNode().getValue().equals(neighbor.getValue())) {
                    LOG.debug("IOFI: Previous level node {} is part of the link: S->{} D->{}",
                            neighbor.getValue(), link.getSource().getSourceNode().getValue(), link.getDestination().getDestNode().getValue());
                    if (link.getDestination().getDestTp().getValue().equals(nodeConnector.getId().getValue())){
                        LOG.debug("IOFI: Previous level node {} has the node connector {} as destination",
                                neighbor.getValue(), nodeConnector.getId().getValue());
                        LOG.debug("IOFI: DO NOT BROADCAST ON THIS NODE CONNECTOR");
                        return true;
                    }
                } else if (link.getDestination().getDestNode().getValue().equals(neighbor.getValue())) {
                    LOG.debug("IOFI: Previous level node {} is part of the link: S->{} D->{}",
                            neighbor.getValue(), link.getSource().getSourceNode().getValue(), link.getDestination().getDestNode().getValue());
                    if (link.getSource().getSourceTp().getValue().equals(nodeConnector.getId().getValue())) {
                        LOG.debug("IOFI: Previous level node {} has the node connector {} as destination",
                                neighbor.getValue(), nodeConnector.getId().getValue());
                        LOG.debug("IOFI: DO NOT BROADCAST ON THIS NODE CONNECTOR");
                        return true;
                    }
                }
            }
        }

        // try to avoid multiple links to the same next level node
        // IDEA:
        // if the next level node has already a connection to the node of the same level
        // as the node which node connector has been examined, then reject that
        // node connector
        // PROBLEM: sometimes it takes too much for tthe LLDP to discover next level nodes
        // and this is practically impossible to always have without some large timeout values
        // It is not so important it just forces a certain tree shape

        /*
        HopByHopTreeGraph currentTree = networkGraphService.getCurrentHopByHopTreeGraph();
        NodeId nodeId = new NodeId(getNodeIdFromNodeConnectorId(nodeConnector.getId().getValue()));
        Integer nodeLevel = currentTree.getVertexTreeLevel(nodeId);

        LOG.info("IOFI: Processing next level nodes of the node {}", getNodeIdFromNodeConnectorId(nodeConnector.getId().getValue()));
        LOG.info("IOFI: Next level nodes: {}", nextLevelNodes.toString());
        for (NodeId neighbor: nextLevelNodes){
            LOG.info("IOFI: Processing the next level node {}", neighbor.getValue());
            for (Link link: topologyLinks){
                if (link.getSource().getSourceNode().getValue().equals(neighbor.getValue())) {
                    LOG.info("IOFI: Next level node {} is part of the link: S->{} D->{}",
                            neighbor.getValue(), link.getSource().getSourceNode().getValue(), link.getDestination().getDestNode().getValue());
                    NodeId examinedNode = new NodeId(link.getDestination().getDestNode().getValue());
                    LOG.info("IOFI: Currently examined node is {}", examinedNode.getValue());
                    LOG.info("IOFI: examinedNodeLevel: {} currentNodeLevel: {}", currentTree.getVertexTreeLevel(examinedNode),
                            nodeLevel);
                    printCurrentTopologyLinksNicely(topologyId);
                    LOG.info("areNeighborsInTheTopology: {}", areNeigborsInTheTopology(neighbor, new NodeId(getNodeIdFromNodeConnectorId(nodeConnector.getId().getValue())), topologyId));
                    if ((currentTree.getVertexTreeLevel(examinedNode) <=  nodeLevel)
                            && areNeigborsInTheTopology(neighbor, new NodeId(getNodeIdFromNodeConnectorId(nodeConnector.getId().getValue())), topologyId)
                            && !examinedNode.getValue().equals(getNodeIdFromNodeConnectorId(nodeConnector.getId().getValue()))) {
                        LOG.info("IOFI: The next level node {} has already been connected to the same level node {}",
                                neighbor.getValue(), examinedNode.getValue());
                        LOG.info("IOFI: DO NOT BROADCAST ON THIS NODE CONNECTOR");
                        return true;
                    }
                } else if (link.getDestination().getDestNode().getValue().equals(neighbor.getValue())) {
                    LOG.info("IOFI: Next level node {} is part of the link: S->{} D->{}",
                            neighbor.getValue(), link.getSource().getSourceNode().getValue(), link.getDestination().getDestNode().getValue());
                    NodeId examinedNode = new NodeId(link.getSource().getSourceNode().getValue());
                    LOG.info("IOFI: Currently examined node is {}", examinedNode.getValue());
                    LOG.info("IOFI: examinedNodeLevel: {} currentNodeLevel: {}", currentTree.getVertexTreeLevel(examinedNode),
                            nodeLevel);
                    printCurrentTopologyLinksNicely(topologyId);
                    LOG.info("areNeighborsInTheTopology: {}", areNeigborsInTheTopology(neighbor, new NodeId(getNodeIdFromNodeConnectorId(nodeConnector.getId().getValue())), topologyId));
                    if ((currentTree.getVertexTreeLevel(examinedNode) <= nodeLevel)
                            && areNeigborsInTheTopology(neighbor, new NodeId(getNodeIdFromNodeConnectorId(nodeConnector.getId().getValue())), topologyId)
                            && !examinedNode.getValue().equals(getNodeIdFromNodeConnectorId(nodeConnector.getId().getValue()))) {
                        LOG.info("IOFI: The next level node {} has already been connected to the same level node {}",
                                neighbor.getValue(), examinedNode.getValue());
                        LOG.info("IOFI: DO NOT BROADCAST ON THIS NODE CONNECTOR");
                        return true;
                    }
                }
            }
        }
        */

        nodeConnector.getAugmentation(FlowCapableNodeConnector.class).getName();
        LOG.debug("IOFI: NodeConnector {} is OK", nodeConnector.getId().getValue());
        return false;
    }

    /**
     * Parse nodeConnectorId and return nodeId
     *
     * @param nodeConnectorId
     * @return
     */
    public static String getNodeIdFromNodeConnectorId(String nodeConnectorId) {
        if (nodeConnectorId.contains("openflow")) {
            if (nodeConnectorId.split(":").length == 3) {
                String[] parsedNodeConnectorId = nodeConnectorId.split(":");
                return parsedNodeConnectorId[0] + ":" + parsedNodeConnectorId[1];

            } else {
                return "";
            }
        } else {
            return "";
        }
    }

}
