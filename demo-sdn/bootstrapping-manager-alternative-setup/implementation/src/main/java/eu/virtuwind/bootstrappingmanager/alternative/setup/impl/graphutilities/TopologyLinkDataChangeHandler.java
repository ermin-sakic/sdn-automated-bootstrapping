
/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import edu.uci.ics.jung.graph.Graph;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.InitialFlowWriter;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.NetworkExtensionManager;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.IPPrefAddrInfo;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2switch.loopremover.rev140714.StpStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2switch.loopremover.rev140714.StpStatusAwareNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2switch.loopremover.rev140714.StpStatusAwareNodeConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens to data change events on topology links
 * {@link org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link}
 * and maintains a topology graph using provided NetworkGraphService
 * {@link eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.NetworkGraphService}.
 *
 */
public class TopologyLinkDataChangeHandler implements ClusteredDataChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(TopologyLinkDataChangeHandler.class);

    private static DataBroker dataBroker;
    private static SalFlowService salFlowService;
    private static String cpNetworkPrefix;
    private static short flowTableId;


    private static final String DEFAULT_TOPOLOGY_ID = "flow:1";

    private final ScheduledExecutorService topologyDataChangeEventProcessor = Executors.newScheduledThreadPool(1);

    private final NetworkGraphService networkGraphService;
    private static String topologyId;

    // to access from outside
    public static List<Link> allLinks = new ArrayList<Link>();
    public static List<Link> mstLinks = new ArrayList<Link>();

    // EOS stuff
    private static boolean isLeader = false;

    // To communicate with NetworkExtensionManager
    public static List<String> nodeConnectorToCheck = new LinkedList<>();

    // config flag that enables/disables NEM features
    private static Boolean nemEnabled = true;



    public TopologyLinkDataChangeHandler(DataBroker dB, SalFlowService sFS, NetworkGraphService networkGraphService) {
        Preconditions.checkNotNull(dB, "dataBroker should not be null.");
        Preconditions.checkNotNull(networkGraphService, "networkGraphService should not be null.");
        dataBroker = dB;
        salFlowService = sFS;
        this.networkGraphService = networkGraphService;
    }

    public void setTopologyId(String topologyId) {
        if (topologyId == null || topologyId.isEmpty()) {
            this.topologyId = DEFAULT_TOPOLOGY_ID;
        } else
            this.topologyId = topologyId;
    }

    public static String getTopologyId() { return topologyId; }

    /**
     *  Set control plane prefix
     *
     * @param cpNetworkPrefix
     */
    public static void setCpNetworkPrefix(String cpNetworkPrefix) {
        TopologyLinkDataChangeHandler.cpNetworkPrefix = cpNetworkPrefix;
    }

    /**
     * Set flow table id
     *
     * @param flowTableId
     */
    public static void setFlowTableId(short flowTableId) {
        TopologyLinkDataChangeHandler.flowTableId = flowTableId;
    }

    /**
     * Set nemEnabled flag via XML config
     *
     * @param nemEnabled
     */
    public static void setNemEnabled(Boolean nemEnabled) {
        TopologyLinkDataChangeHandler.nemEnabled = nemEnabled;
    }


    /**
     * Registers as a data listener to receive changes done to
     * {@link org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link}
     * under
     * {@link org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology}
     * operation data root.
     */
    public ListenerRegistration<DataChangeListener> registerAsDataChangeListener() {
        InstanceIdentifier<Link> linkInstance = InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId))).child(Link.class).build();
        return dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL, linkInstance, this,
                AsyncDataBroker.DataChangeScope.BASE);
    }

    /**
     * Handler for onDataChanged events
     *
     * @param dataChangeEvent
     *            The data change event to process.
     */
    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> dataChangeEvent) {

        synchronized (this) {
            if (dataChangeEvent == null) {
                return;
            }
            Map<InstanceIdentifier<?>, DataObject> createdData = dataChangeEvent.getCreatedData();
            Set<InstanceIdentifier<?>> removedPaths = dataChangeEvent.getRemovedPaths();
            Map<InstanceIdentifier<?>, DataObject> originalData = dataChangeEvent.getOriginalData();
            boolean isLinkAdded = false;
            boolean isLinkRemoved = false;
            List<Link> addedLinks = new ArrayList<>();
            List<Link> removedLinks = new ArrayList<>();


            if (createdData != null && !createdData.isEmpty()) {
                Set<InstanceIdentifier<?>> linksIds = createdData.keySet();
                for (InstanceIdentifier<?> linkId : linksIds) {
                    if (Link.class.isAssignableFrom(linkId.getTargetType())) {

                        Link link = (Link) createdData.get(linkId);
                        isLinkAdded = true;
                        LOG.info("Graph is updated! Added Link {} <-> {}", link.getSource().getSourceTp().getValue(),
                                link.getDestination().getDestTp().getValue());
                        addedLinks.add(link);
                    }
                }
            }

            if (removedPaths != null && !removedPaths.isEmpty() && originalData != null && !originalData.isEmpty()) {
                for (InstanceIdentifier<?> instanceId : removedPaths) {
                    if (Link.class.isAssignableFrom(instanceId.getTargetType())) {

                        Link link = (Link) originalData.get(instanceId);
                        isLinkRemoved = true;
                        LOG.info("Graph is updated! Removed Link {} <-> {}", link.getSource().getSourceTp().getValue(),
                                link.getDestination().getDestTp().getValue());
                        removedLinks.add(link);

                    }
                }
            }


            if (isLeader && (isLinkAdded || isLinkRemoved)) {

                topologyDataChangeEventProcessor.execute(new TopologyDataChangeEventProcessor(isLinkAdded, isLinkRemoved, addedLinks, removedLinks));

            } else if (!isLeader && (isLinkAdded || isLinkRemoved)) {

                LOG.info("Link changed in the topology but not a leader.");

            } else {

                LOG.info("No new link added/removed!");
            }
        }
    }

    /**
     * Updates hop-by-hop tree and topology graph object
     */
    private class TopologyDataChangeEventProcessor implements Runnable {

        private boolean isLinkAdded;
        private boolean isLinkRemoved;
        List<Link> addedLinks;
        List<Link> removedLinks;

        /**
         * Constructor
         * @param isLinkAdded
         * @param isLinkRemoved
         */
        TopologyDataChangeEventProcessor(boolean isLinkAdded, boolean isLinkRemoved, List<Link> addedLinks, List<Link> removedLinks) {
            this.isLinkAdded = isLinkAdded;
            this.isLinkRemoved = isLinkRemoved;
            this.addedLinks = addedLinks;
            this.removedLinks = removedLinks;
        }

        /**
         * Topology update processor
         */
        @Override
        public synchronized void run() {

            LOG.debug("In network graph refresh thread.");

            if (isLinkAdded) {

                if (!addedLinks.isEmpty()) {
                    networkGraphService.addLinks(addedLinks);
                    networkGraphService.addLinksToHopByHopTree(addedLinks);
                } else {
                    return;
                }

                Integer numberOfLinksFromNEM = nodeConnectorToCheck.size();
                if (!nodeConnectorToCheck.isEmpty()) {
                    // when the tree is updated in NetworkExtensionManager recompute alternatives
                    networkGraphService.computeAlternativeTrees();
                    networkGraphService.writeAlternativeTreesInDS();
                    for (int i = 0; i < numberOfLinksFromNEM; i++) {
                        // front removal
                        LOG.info("New link through NEM added -> {}", getLinkFromNodeConnectorId(nodeConnectorToCheck.get(0)).getLinkId().getValue());
                        nodeConnectorToCheck.remove(0);
                    }
                }

            } else if (isLinkRemoved) {
                /*
                    Assumption link failure can happen only after bootstrapping has been done
                    Recovery in case of a transitional fault is not considered in this thesis.
                 */
                if (!removedLinks.isEmpty() && nemEnabled) {
                    // remove the link from the full topology graph
                    networkGraphService.removeLinksFromNetworkGraph(removedLinks);
                    for (Link failedLink: removedLinks){
                        LOG.info("Removed linkId -> {}", failedLink.getLinkId().getValue());
                        // if the failed link is part of the current tree
                        if (networkGraphService.getCurrentHopByHopTreeGraph().containsEdge(failedLink)) {
                            LOG.info("Removed link {} is in the tree!", failedLink.getLinkId().getValue());
                            // delete the old tree using cookies
                            LOG.info("Removing old tree rules!");
                            removeBrokenTreeRules();
                            // load the alternative tree
                            HopByHopTreeGraph alternativeHopByHopTreeGraph =
                                    new HopByHopTreeGraph(networkGraphService.createGraphClone(networkGraphService.getAlternativeTreeGraphs().get(failedLink)));
                            // install alternative tree rules to switches
                            for (NodeId switchId: alternativeHopByHopTreeGraph.getVertices()) {
                                if (!switchId.getValue().contains("host")) {
                                    List<String> treePorts = findTreePorts(switchId.getValue(), alternativeHopByHopTreeGraph);
                                    Thread t = new Thread(new InstallTreeRulesInTheSwitch(switchId, treePorts));
                                    t.start();
                                    // wait till rules are embedded
                                    try {
                                        t.join();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    LOG.info("Alternative tree rules installed in {}", switchId.getValue());
                                }
                            }
                            LOG.info("Alternative tree installed in the network!!!");
                            // clear current tree
                            // when a failed link becomes available again this line will enable it to be integrated in the tree
                            // this leads to inconsistencies between DS tree and local cache tree
                            //networkGraphService.clearCurrentHopByHopTree();

                            // find edge differences between the currentHopByHopTreeGraph and alternativeHopByHopTreeGraph
                            // necessary for proper DS cleaning
                            List<Link> edgeDifferences = new LinkedList<>();
                            for (Link link: networkGraphService.getCurrentHopByHopTreeGraph().getEdges()) {
                                if (!alternativeHopByHopTreeGraph.containsEdge(link)) {
                                    edgeDifferences.add(link);
                                }
                            }
                            // Update the current tree in NetworkGraphService as well
                            networkGraphService.setCurrentHopByHopTreeGraph(alternativeHopByHopTreeGraph);
                            // create alternative trees for the new current tree
                            networkGraphService.computeAlternativeTrees();
                            networkGraphService.writeAlternativeTreesInDS();
                            LOG.info("New alternative trees computed and written into DS");


                            // clear the old alternative trees from the DS
                            for (Link link: edgeDifferences) {
                                networkGraphService.deleteAlternativeTreeFromDS(link.getLinkId().getValue());
                                LOG.info("Cleaning the old alternative trees stored in DS");
                            }

                        }
                    }
                } else {
                    return;
                }

            } else {
                LOG.error("This should not be written!!!");
                return;
            }

            HopByHopTreeGraph currentTree = networkGraphService.getCurrentHopByHopTreeGraph();
            currentTree.writeCurrentTreeInDS();
            LOG.info("-------------------------------------------------------------------------------");
            LOG.info("EXAMINING NGS PROCESSED LINKS");
            LOG.info("-------------------------------------------------------------------------------");
            LOG.info("My tree result:");

            LOG.info("Vertices:");
            LOG.info(currentTree.getVerticesFormatedNicely());
            LOG.info("Edges:");
            LOG.info(currentTree.getEdgesFormatedNicely());

            LOG.info("-------------------------------------------------------------------------------");

            LOG.debug("Done with network graph refresh thread.");
        }

        /**
         * Extract tree ports from the treeGraph for the given switch
         *
         * @param nodeId
         * @return
         */
        private List<String> findTreePorts(String nodeId, HopByHopTreeGraph treeGraph) {

            if (treeGraph == null) {
                LOG.warn("HopByHopTreeGraph currently not available!");
                return null;
            }

            if (!treeGraph.containsVertex(new NodeId(nodeId))) {
                LOG.warn("Node {} does not exist in the HopByHopTreeGraph!", nodeId);
                return null;
            }

            Collection<Link> treeIncidentEdges = treeGraph.getIncidentEdges(new NodeId(nodeId));
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
         * Delete the currentTreeGraph rules in all switches
         *
         */
        private void removeBrokenTreeRules(){

            for (String nodeId: InitialFlowUtils.removableFlowCookiesPhaseII.keySet()) {
                for (FlowCookie flowCookie: InitialFlowUtils.removableFlowCookiesPhaseII.get(nodeId)) {
                    InitialFlowUtils.removeOpenFlowFlow(salFlowService, flowCookie, nodeId);
                }
            }
            InitialFlowUtils.removableFlowCookiesPhaseII.clear();
        }

    }

    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("TopologyLinkDataChangeHandler ownership change logged: " + ownershipChange);

        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the TopologyLinkDataChangeHandler leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the TopologyLinkDataChangeHandler follower.");
            setFollower();
        }
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
     * Confirms this instance is the current cluster leader.
     */
    public static void setLeader() {isLeader = true;}

    /**
     * Sets this instance as a cluster follower.
     */
    public static void setFollower() {isLeader = false;}

}

