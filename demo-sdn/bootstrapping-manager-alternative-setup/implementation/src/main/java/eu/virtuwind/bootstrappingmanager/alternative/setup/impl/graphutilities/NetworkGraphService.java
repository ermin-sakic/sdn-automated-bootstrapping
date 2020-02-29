/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Modified by Mirza Avdic 26.04.2018
 */


package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities;

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

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InstanceIdentifierUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processing logic of the current topology and the tree topology.
 */
public class NetworkGraphService {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkGraphService.class);
    private static DataBroker dataBroker;

    // set through the EOS
    private static boolean isLeader = false;

    // topologyId
    private static String topologyId = "flow:1";

    // To denote isolated links in the tree
    private static final Integer INFINITY = new Integer(999999);

    // Graph stuff
    private static Graph<NodeId, Link> networkGraph = null;
    private static Set<String> linkAdded = new HashSet<>();
    private static HopByHopTreeGraph currentHopByHopTree = null;
    private static Set<String> linkAddedToTree = new HashSet<>();
    private static List<String> topologyNodeConnectorsAlreadyProcessedInTree = new ArrayList<>();
    private static Map<Link, Graph<NodeId, Link>> alternativeTreeGraphs = new HashMap<>();


    /**
     * Constructor
     *
     * @param dataBroker
     */
    public NetworkGraphService(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    /**
     * Topology id setter
     * @param topologyId
     */
    public static void setTopologyId(String topologyId) {
        NetworkGraphService.topologyId = topologyId;
    }

    /**
     * Adds links to existing graph or creates new directed graph with given
     * links if graph was not initialized.
     *
     * @param links
     *            The links to add.
     */
    public static synchronized void addLinks(List<Link> links) {
        if (links == null || links.isEmpty()) {
            LOG.debug("In addLinks: No link added as links is null or empty.");
            return;
        }

        if (networkGraph == null) {
            networkGraph = new SparseMultigraph<>();
        }

        for (Link link : links) {
            if (linkAlreadyAdded(link)) {
                continue;
            }
            NodeId sourceNodeId = link.getSource().getSourceNode();
            NodeId destinationNodeId = link.getDestination().getDestNode();
            networkGraph.addVertex(sourceNodeId);
            networkGraph.addVertex(destinationNodeId);
            networkGraph.addEdge(link, sourceNodeId, destinationNodeId, EdgeType.UNDIRECTED);
        }

    }

    private static boolean linkAlreadyAdded(Link link) {
        String linkAddedKey = null;
        if (link.getDestination().getDestTp().hashCode() > link.getSource().getSourceTp().hashCode()) {
            linkAddedKey = link.getSource().getSourceTp().getValue() + link.getDestination().getDestTp().getValue();
        } else {
            linkAddedKey = link.getDestination().getDestTp().getValue() + link.getSource().getSourceTp().getValue();
        }
        if (linkAdded.contains(linkAddedKey)) {
            return true;
        } else {
            linkAdded.add(linkAddedKey);
            return false;
        }
    }

    /**
     * Remove links from the networkGraph object
     *
     * @param links
     */
    public static synchronized void removeLinksFromNetworkGraph(List<Link> links) {
        Preconditions.checkNotNull(networkGraph, "Graph is not initialized, add links first.");

        if (links == null || links.isEmpty()) {
            LOG.debug("In removeLinks: No link removed as links is null or empty.");
            return;
        }

        for (Link link : links) {
            LOG.debug("Removing link {} from networkGraph!", link.getLinkId().getValue());
            printGraph(networkGraph, "Prior link removal");
            networkGraph.removeEdge(link);
            printGraph(networkGraph, "After link removal");
            String linkAddedKey = null;
            if (link.getDestination().getDestTp().hashCode() > link.getSource().getSourceTp().hashCode()) {
                linkAddedKey = link.getSource().getSourceTp().getValue() + link.getDestination().getDestTp().getValue();
            } else {
                linkAddedKey = link.getDestination().getDestTp().getValue() + link.getSource().getSourceTp().getValue();
            }
            boolean res = linkAdded.remove(linkAddedKey);
            LOG.debug("Link successfully removed from linkAddedToNetworkGraph: {}", res);
        }
    }

    /**
     * Clears the prebuilt graph, in case same service instance is required to
     * process a new graph.
     */
    public static synchronized void clear() {
        networkGraph = null;
        linkAdded.clear();
    }

    /**
     * Get networkGraph reference
     *
     * @return
     */
    public static synchronized Graph<NodeId, Link> getNetworkGraph() {
        return networkGraph;
    }

    /**
     * Get alternativeTreeGraphs reference
     *
     * @return
     */
    public static synchronized Map<Link, Graph<NodeId, Link>> getAlternativeTreeGraphs() {
        return alternativeTreeGraphs;
    }

    /**
     * Forms MST(minimum spanning tree) from network graph and returns links
     * that are not in MST.
     *
     * @return The links in the MST (minimum spanning tree)
     */
    public static synchronized List<Link> getLinksInMst() {
        List<Link> linksInMst = new ArrayList<>();
        if (networkGraph != null) {
            PrimMinimumSpanningTree<NodeId, Link> networkMst = new PrimMinimumSpanningTree<>(
                    DelegateTree.<NodeId, Link>getFactory());
            Graph<NodeId, Link> mstGraph = networkMst.transform(networkGraph);
            Collection<Link> mstLinks = mstGraph.getEdges();
            linksInMst.addAll(mstLinks);
        }
        return linksInMst;
    }

    /**
     * Get all the links in the network.
     *
     * @return The links in the network.
     */
    public static List<Link> getAllLinks() {
        List<Link> allLinks = new ArrayList<>();
        if (networkGraph != null) {
            allLinks.addAll(networkGraph.getEdges());
        }
        return allLinks;
    }

    /**
     * Get current HopByHopTree instance
     *
     * @return HopByHopTree instance
     */
    public static synchronized HopByHopTreeGraph getCurrentHopByHopTreeGraph() {
        return currentHopByHopTree;
    }

    /**
     * Get current HopByHopTree instance
     *
     * @return HopByHopTree instance
     */
    public static synchronized void setCurrentHopByHopTreeGraph(HopByHopTreeGraph hopByHopTreeGraph) {
         currentHopByHopTree = hopByHopTreeGraph;
    }

    /**
     * Clear the current HopByHopTree instance
     *
     */
    public static synchronized void clearCurrentHopByHopTree() {

        currentHopByHopTree = null;
        linkAddedToTree.clear();
    }

    private static boolean linkAlreadyAddedToTree(Link link) {
        String linkAddedKey = null;
        if (link.getDestination().getDestTp().hashCode() > link.getSource().getSourceTp().hashCode()) {
            linkAddedKey = link.getSource().getSourceTp().getValue() + link.getDestination().getDestTp().getValue();
        } else {
            linkAddedKey = link.getDestination().getDestTp().getValue() + link.getSource().getSourceTp().getValue();
        }
        if (linkAddedToTree.contains(linkAddedKey)) {
            return true;
        } else {
            linkAddedToTree.add(linkAddedKey);
            return false;
        }
    }

    /**
     * Adds links that conform the tree logic
     * Peer links are ignored
     *
     * @param links
     */
    public static synchronized void addLinksToHopByHopTree(List<Link> links) {

        if (links == null || links.isEmpty()) {
            LOG.debug("In addLinks: No link added as links is null or empty.");
            return;
        }

        if (currentHopByHopTree == null) {
            currentHopByHopTree = new HopByHopTreeGraph(dataBroker);
            LOG.debug("New empty tree created");
        }

        for (Link link : links) { // 1-2 equals 2-1 , etc.
            if (linkAlreadyAddedToTree(link)) {
                continue;
            }
            LOG.debug("Current number of vertices in the tree {}", currentHopByHopTree.getVertexCount());
            // processes link to the controller
            LOG.debug("addLinksToHopByHopTree processing link: source: {}, destination: {}", link.getSource().getSourceNode().getValue(),
                    link.getDestination().getDestNode().getValue());
            // Identifying root node
            if (currentHopByHopTree.getVertexCount() == 0) {
                if(link.getSource().getSourceNode().getValue().contains("host")) {
                    currentHopByHopTree.setGraphRootNode(link.getDestination().getDestNode());
                    LOG.debug("Root identified: {}", currentHopByHopTree.getGraphRootNode().getValue());
                    currentHopByHopTree.setGraphControllerLeader(link.getSource().getSourceNode());
                    LOG.debug("Leader controller identified: {}", currentHopByHopTree.getGraphControllerLeader().getValue());
                } else if (link.getDestination().getDestNode().getValue().contains("host")) {
                    currentHopByHopTree.setGraphRootNode(link.getSource().getSourceNode());
                    LOG.debug("Root identified: {}", currentHopByHopTree.getGraphRootNode().getValue());
                    currentHopByHopTree.setGraphControllerLeader(link.getDestination().getDestNode());
                    LOG.debug("Leader controller identified: {}", currentHopByHopTree.getGraphControllerLeader().getValue());
                }
            }
            LOG.debug("Link being processed: {}-{}", link.getSource().getSourceTp().getValue(), link.getDestination().getDestTp().getValue());
            NodeId linkSrcNodeId = link.getSource().getSourceNode();
            NodeId linkDstNodeId = link.getDestination().getDestNode();

            if (currentHopByHopTree.containsVertex(linkSrcNodeId) && currentHopByHopTree.containsVertex(linkDstNodeId)) {
                processLinkBothNodesAlreadyInTree(link);
            } else if (currentHopByHopTree.containsVertex(linkSrcNodeId) && !currentHopByHopTree.containsVertex(linkDstNodeId)) {
                processLinkOneNodeAlreadyInTree(link, linkSrcNodeId);
            } else if (!currentHopByHopTree.containsVertex(linkSrcNodeId) && currentHopByHopTree.containsVertex(linkDstNodeId)) {
                processLinkOneNodeAlreadyInTree(link, linkDstNodeId);
            } else {
                processLinkBothNodesNewInTree(link);
            }
        }

    }

    /**
     * Process a link which nodes are already part of the tree
     *
     * @param link
     */
    private static void processLinkBothNodesAlreadyInTree(Link link){

        NodeId linkSourceNodeId = link.getSource().getSourceNode();
        NodeId linkDestinationNodeId = link.getDestination().getDestNode();
        // check if it is better
        LOG.debug("Both nodes of the link already in the tree -> Evaluating whether this is a better link");
        // if yes replace the old one
        Integer linkSourceNodeTreeLevel = currentHopByHopTree.getVertexTreeLevel(linkSourceNodeId);
        Integer linkDestinationNodeTreeLevel = currentHopByHopTree.getVertexTreeLevel(linkDestinationNodeId);

        if (linkSourceNodeTreeLevel == linkDestinationNodeTreeLevel) {

            LOG.debug("New link connecting the same level {} nodes -> no valid tree link", linkSourceNodeTreeLevel);

        } else if (linkSourceNodeTreeLevel > linkDestinationNodeTreeLevel) {

            LOG.debug("LinkSrcNode: {} has bigger level than LinkDstNode: {} -> {} > {}", linkSourceNodeId.getValue(),
                    linkDestinationNodeId.getValue(), linkSourceNodeTreeLevel, linkDestinationNodeTreeLevel);

            processLinkBothNodesAlreadyInTreeProcessor(link, linkSourceNodeId, linkDestinationNodeId);


        } else if (linkSourceNodeTreeLevel < linkDestinationNodeTreeLevel) {

            LOG.debug("LinkDstNode: {} has bigger level than LinkSrcNode: {} -> {} > {}", linkDestinationNodeId.getValue(),
                    linkSourceNodeId.getValue(), linkDestinationNodeTreeLevel, linkSourceNodeTreeLevel);

            processLinkBothNodesAlreadyInTreeProcessor(link, linkDestinationNodeId, linkSourceNodeId);

        }

        // Necessary to do the synchronization in the InitialOFRulesPhaseII
        if (!topologyNodeConnectorsAlreadyProcessedInTree.contains(link.getSource().getSourceTp().getValue())) {
            topologyNodeConnectorsAlreadyProcessedInTree.add(link.getSource().getSourceTp().getValue());
        }

        if (!topologyNodeConnectorsAlreadyProcessedInTree.contains(link.getDestination().getDestTp().getValue())) {
            topologyNodeConnectorsAlreadyProcessedInTree.add(link.getDestination().getDestTp().getValue());
        }
    }

    /**
     * An actual decision maker when both link nodes are already part of the tree
     *
     * @param link
     * @param nodeToCheck
     * @param nodeWithLowerLevelOfNodeToCheck
     */
    private static void processLinkBothNodesAlreadyInTreeProcessor(Link link, NodeId nodeToCheck, NodeId nodeWithLowerLevelOfNodeToCheck) {

        Collection<NodeId> treeNeighbors = currentHopByHopTree.getNeighbors(nodeToCheck);
        // should be only one node neighbor here -> not true
        // can happen that the next level node is also discovered, e.g. a controller attached to the switch
        // check only for the parent, ignore next level neighbors
        for (NodeId neighbor: treeNeighbors) {
            if (currentHopByHopTree.getVertexTreeLevel(neighbor)
                    == (currentHopByHopTree.getVertexTreeLevel(nodeToCheck) - 1)) {
                if (currentHopByHopTree.getVertexTreeLevel(neighbor)
                        <= currentHopByHopTree.getVertexTreeLevel(nodeWithLowerLevelOfNodeToCheck)) {
                    LOG.debug("Neighbor:{} of nodeToCheck:{} has <= tree level compared to nodeWithLowerLevelOfNodeToCheck:{} -> new link is not better",
                            neighbor.getValue(), nodeToCheck.getValue(), nodeWithLowerLevelOfNodeToCheck.getValue());
                    continue;
                } else if (currentHopByHopTree.getVertexTreeLevel(neighbor)
                        > currentHopByHopTree.getVertexTreeLevel(nodeWithLowerLevelOfNodeToCheck)) {
                    LOG.debug("Neighbor:{} of nodeToCheck:{} has > tree level compared to nodeWithLowerLevelOfNodeToCheck:{} -> new link is better, replace the old one!",
                            neighbor.getValue(), nodeToCheck.getValue(), nodeWithLowerLevelOfNodeToCheck.getValue());
                    //delete old link and insert a new one
                    Link oldLink = currentHopByHopTree.findEdge(nodeToCheck, neighbor);
                    LOG.debug("Removing an old link between {} and {}", nodeToCheck.getValue(), neighbor.getValue());
                    currentHopByHopTree.removeEdge(oldLink);
                    LOG.debug("Inserting a new link between {} and {}", nodeToCheck.getValue(), nodeWithLowerLevelOfNodeToCheck.getValue());
                    currentHopByHopTree.addEdge(link, nodeToCheck, nodeWithLowerLevelOfNodeToCheck);
                    LOG.debug("Updating the tree level of the node {}", nodeToCheck.getValue());
                    // first get the children if they exist
                    List<NodeId> children = currentHopByHopTree.getNextLevelNeighbors(nodeToCheck);
                    LOG.debug("It was {}", currentHopByHopTree.getVertexTreeLevel(nodeToCheck));
                    LOG.debug("Now it is {}", currentHopByHopTree.setVertexTreeLevel(nodeToCheck, currentHopByHopTree.getVertexTreeLevel(nodeWithLowerLevelOfNodeToCheck) + 1));
                    // update levels of the children
                    LOG.debug("Updating the tree level of the {} children", nodeToCheck.getValue());
                    if (children == null) {
                        LOG.debug("No children for node {}", nodeToCheck.getValue());
                    } else {
                        LOG.debug("Children: {}", children.toString());
                        for (NodeId child : children) {
                            LOG.debug("Child level was {}", currentHopByHopTree.getVertexTreeLevel(child));
                            currentHopByHopTree.setVertexTreeLevel(child, currentHopByHopTree.getVertexTreeLevel(nodeToCheck) + 1);
                            LOG.debug("Now the child level is {}", currentHopByHopTree.getVertexTreeLevel(child));
                        }
                    }
                }
            } else if (currentHopByHopTree.getVertexTreeLevel(neighbor) == INFINITY) {
                /*
                    If LLDP messages are sent in some weird order so that the first discovered next hop link is the one
                    connecting next hop switches and the one connecting current and next hop switches.
                    In that case an isolated island link may appear, which is connected to the tree when the links connecting
                    next and current hop switches are discovered. This connection is processed here.
                 */
                if (currentHopByHopTree.getVertexTreeLevel(nodeToCheck) == INFINITY) {
                    // TODO: TEST
                    LOG.debug("New link is connecting an isolated island link {}:{}", nodeToCheck.getValue(), neighbor.getValue());
                    LOG.debug("Inserting a new link between {} and {}", nodeToCheck.getValue(), nodeWithLowerLevelOfNodeToCheck.getValue());
                    currentHopByHopTree.addEdge(link, nodeToCheck, nodeWithLowerLevelOfNodeToCheck);

                    Integer newLevelOfNodeToCheck = currentHopByHopTree.getVertexTreeLevel(nodeWithLowerLevelOfNodeToCheck) + 1;
                    LOG.debug("Node {} had level INFINITY, now the new level is {}", nodeToCheck.getValue(), newLevelOfNodeToCheck);
                    currentHopByHopTree.setVertexTreeLevel(nodeToCheck, newLevelOfNodeToCheck);
                    LOG.debug("Changing level of the INFINITY node peer {}", neighbor.getValue());
                    currentHopByHopTree.setVertexTreeLevel(neighbor, newLevelOfNodeToCheck + 1);
                    LOG.debug("INFINITY peer node {} had level INFINITY, now the new level is {}", neighbor.getValue(), newLevelOfNodeToCheck + 1);

                } else {
                    LOG.warn("Something is wrong; this should have never been written!");
                }
            }
        }
    }

    /**
     * Process a link where only one node that is part of the link is
     * also part of the tree
     *
     * @param link
     * @param nodeAlreadyInTree
     */
    private static void processLinkOneNodeAlreadyInTree(Link link, NodeId nodeAlreadyInTree){
        NodeId sourceNodeId = link.getSource().getSourceNode();
        NodeId destinationNodeId = link.getDestination().getDestNode();

        if (nodeAlreadyInTree.getValue().equals(sourceNodeId.getValue())) {
            currentHopByHopTree.addVertex(destinationNodeId);
            currentHopByHopTree.addEdge(link, sourceNodeId, destinationNodeId, EdgeType.UNDIRECTED);
            LOG.debug("Appending link: Node {} is already in the graph, tree extension with the new node {}",
                   nodeAlreadyInTree.getValue(), destinationNodeId.getValue());

            if (currentHopByHopTree.getGraphRootNode().getValue().equals(sourceNodeId.getValue())) {
                LOG.debug("Node {} equals root.", sourceNodeId.getValue());
                currentHopByHopTree.setVertexTreeLevel(destinationNodeId, 1);
                LOG.debug("Node {} has level {}", destinationNodeId.getValue(), 1);
            } else {
                int level = currentHopByHopTree.getVertexTreeLevel(sourceNodeId) + 1;
                LOG.debug("Node {} has level {}", sourceNodeId.getValue(), currentHopByHopTree.getVertexTreeLevel(sourceNodeId));
                currentHopByHopTree.setVertexTreeLevel(destinationNodeId, level);
                LOG.debug("Node {} has been assigned a level of value {}", destinationNodeId.getValue(), level);
            }
        } else if (nodeAlreadyInTree.getValue().equals(destinationNodeId.getValue())) {
            currentHopByHopTree.addVertex(sourceNodeId);
            currentHopByHopTree.addEdge(link, sourceNodeId, destinationNodeId, EdgeType.UNDIRECTED);
            LOG.debug("Appending link: Node {} is already in the graph, tree extension with the new node {}",
                    nodeAlreadyInTree.getValue(), sourceNodeId.getValue());

            if (currentHopByHopTree.getGraphRootNode().getValue().equals(destinationNodeId.getValue())) {
                LOG.debug("Node {} equals root.", destinationNodeId.getValue());
                currentHopByHopTree.setVertexTreeLevel(sourceNodeId, 1);
                LOG.debug("Node {} has level {}", sourceNodeId.getValue(), 1);
            } else {
                int level = currentHopByHopTree.getVertexTreeLevel(destinationNodeId) + 1;
                LOG.debug("Node {} has level {}", destinationNodeId.getValue(), currentHopByHopTree.getVertexTreeLevel(destinationNodeId));
                currentHopByHopTree.setVertexTreeLevel(sourceNodeId, level);
                LOG.debug("Node {} has been assigned a level of value {}", sourceNodeId.getValue(), level);
            }
        } else {
            LOG.error("Provided nodeAlreadyInTree is not found in the provided link");
        }

        // Necessary to do the synchronization in the InitialOFRulesPhaseII
        if (!topologyNodeConnectorsAlreadyProcessedInTree.contains(link.getSource().getSourceTp().getValue())) {
            topologyNodeConnectorsAlreadyProcessedInTree.add(link.getSource().getSourceTp().getValue());
        }

        if (!topologyNodeConnectorsAlreadyProcessedInTree.contains(link.getDestination().getDestTp().getValue())) {
            topologyNodeConnectorsAlreadyProcessedInTree.add(link.getDestination().getDestTp().getValue());
        }

    }

    /**
     * Process a link where both nodes of the link are new in the tree
     *
     * @param link
     */
    private static void processLinkBothNodesNewInTree(Link link){

        NodeId linkSourceNodeId = link.getSource().getSourceNode();
        NodeId linkDestinationNodeId = link.getDestination().getDestNode();
        // normally first link discovered between root and some other node
        LOG.debug("Completely new link with source {} and destination {}",
                linkSourceNodeId.getValue(), linkDestinationNodeId.getValue());
        currentHopByHopTree.addVertex(linkSourceNodeId);
        currentHopByHopTree.addVertex(linkDestinationNodeId);
        currentHopByHopTree.addEdge(link, linkSourceNodeId, linkDestinationNodeId, EdgeType.UNDIRECTED);
        if (currentHopByHopTree.getGraphRootNode().getValue().equals(linkSourceNodeId.getValue())) {
            LOG.debug("Source Node {} equals to the root node {}", currentHopByHopTree.getGraphRootNode().getValue(),
                    linkSourceNodeId.getValue());
            currentHopByHopTree.setVertexTreeLevel(linkSourceNodeId, 0);
            currentHopByHopTree.setVertexTreeLevel(linkDestinationNodeId, 1);
            LOG.debug("Node {} has level 0", linkSourceNodeId.getValue());
            LOG.debug("Node {} has level 1", linkDestinationNodeId.getValue());
        } else if (currentHopByHopTree.getGraphRootNode().getValue().equals(linkDestinationNodeId.getValue())) {
            LOG.debug("Destination Node {} equals to the root node {}", linkDestinationNodeId.getValue(),
                    currentHopByHopTree.getGraphRootNode().getValue());
            currentHopByHopTree.setVertexTreeLevel(linkDestinationNodeId, 0);
            currentHopByHopTree.setVertexTreeLevel(linkSourceNodeId, 1);
            LOG.debug("Node {} has level 0", linkDestinationNodeId.getValue());
            LOG.debug("Node {} has level 1", linkSourceNodeId.getValue());
        } else { // 2 isolated nodes not part of the current tree discovered; possible due to the random order of LLDP messages received
            LOG.debug("A new isolated link found: Destination Node->{} Source Node->{}",
                    linkDestinationNodeId.getValue(), linkSourceNodeId.getValue());
            LOG.debug("The isolated nodes are initially assigned a level of value INFINITY");
            currentHopByHopTree.setVertexTreeLevel(linkSourceNodeId, INFINITY);
            currentHopByHopTree.setVertexTreeLevel(linkDestinationNodeId, INFINITY);
        }

        // Necessary to do the synchronization in the InitialOFRulesPhaseII
        if (!topologyNodeConnectorsAlreadyProcessedInTree.contains(link.getSource().getSourceTp().getValue())) {
            topologyNodeConnectorsAlreadyProcessedInTree.add(link.getSource().getSourceTp().getValue());
        }

        if (!topologyNodeConnectorsAlreadyProcessedInTree.contains(link.getDestination().getDestTp().getValue())) {
            topologyNodeConnectorsAlreadyProcessedInTree.add(link.getDestination().getDestTp().getValue());
        }

    }

    /**
     * Checks if the list of NodeConnectors has been processed by the tree algorithm
     * Useful for the sync purposes in InitialOFRulesPhaseII
     *
     * @param nodeConnectors
     * @return
     */
    public static boolean ifNodeConnectorsProcessedByTree(List<NodeConnector> nodeConnectors) {
        for (NodeConnector nodeConnector: nodeConnectors) {
            if (!topologyNodeConnectorsAlreadyProcessedInTree.contains(nodeConnector.getId().getValue()))
                return false;
        }

        return true;
    }


    /**
     * Return a copy of the provided graph
     *
     * @param graph
     */
    public static Graph<NodeId, Link> createGraphClone(Graph<NodeId, Link> graph) {

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
     * Print graph nicely
     *
     * @param graph
     */
    public static void printGraph(Graph<NodeId, Link> graph, String graphName) {

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
     * Computes an alternative tree for each link failure in the current tree
     */
    public static void computeAlternativeTrees() {

        if (!alternativeTreeGraphs.isEmpty()) {
            alternativeTreeGraphs.clear();
        }

        Collection<Link> currentTreeLinks = currentHopByHopTree.getEdges();

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
     * Write all alternative trees in DS
     */
    public static void writeAlternativeTreesInDS() {

        // for each alternative tree create a topology and write it into the DS
        for (Map.Entry<Link, Graph<NodeId, Link>> alternativeTree: alternativeTreeGraphs.entrySet()) {

            String alternativeTreeTopologyId = "mst-alternative-" + alternativeTree.getKey().getLinkId().getValue();

            LOG.info("writeAlternativeTreesInDS: Writing " + alternativeTreeTopologyId);

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
    public static void deleteAlternativeTreeFromDS(String linkId) {

        String alternativeTreeTopologyId = "mst-alternative-" + linkId;

        LOG.info("deleteAlternativeTreeFromDS: Writing " + alternativeTreeTopologyId);

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
     * Print alternativeTreeGraphs object
     */
    public static void printAlternativeTreesMap(){
        for (Link link: alternativeTreeGraphs.keySet()) {
            printGraph(alternativeTreeGraphs.get(link), "In case of failure -> " + link.getLinkId().getValue());
        }
    }

    /**
     * Extract tree ports from the currentTree for the fiven nodeId
     *
     * @param nodeId
     * @return
     */
    public static List<String> findTreePorts(String nodeId) {

        if (currentHopByHopTree == null) {
            LOG.warn("HopByHopTreeGraph currently not available!");
            return null;
        }

        if (!currentHopByHopTree.containsVertex(new NodeId(nodeId))) {
            LOG.warn("Node {} does not exist in the HopByHopTreeGraph!", nodeId);
            return null;
        }

        Collection<Link> treeIncidentEdges = currentHopByHopTree.getIncidentEdges(new NodeId(nodeId));
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
     * Read network-topology container from DS
     *
     * @return
     */
    private static  List<Topology> readAllTopologiesFromDS() {

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
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("NetworkGraphService ownership change logged: " + ownershipChange);
        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the NetworkGraphService leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the NetworkGraphService follower.");
            setFollower();
        }
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
                    addLinks(dsTopology.getLink());
                    LOG.info("Adding {} topology to networkGraph variable", topologyId);
                } else if (dsTopology.getTopologyId().getValue().equals("hopbyhoptree")) {
                    // who is root in the tree that is issue
                    currentHopByHopTree = new HopByHopTreeGraph(transformTopologyToGraph(dsTopology));
                    LOG.info("Adding {} topology to currentTreeGraph variable", "hopbyhoptree");
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
