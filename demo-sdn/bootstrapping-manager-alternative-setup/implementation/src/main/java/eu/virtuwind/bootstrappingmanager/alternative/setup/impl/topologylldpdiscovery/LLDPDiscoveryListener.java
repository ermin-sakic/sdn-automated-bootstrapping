/**
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.topologylldpdiscovery;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.InstanceIdentifierUtils;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.TreeUtils;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.topologylldpdiscovery.utils.LLDPDiscoveryUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkDiscovered;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkDiscoveredBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.LinkId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.Destination;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.Source;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.link.attributes.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.*;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * To be sure that LLDP is always processed,  LLDPDiscoveryListener from the OpenFlowPlugin is extended and used.
 * If not disabled, LLDP packet is always processed twice, here and in the OpenFlowPlugin
 */
public class LLDPDiscoveryListener implements PacketProcessingListener {
    private static final Logger LOG = LoggerFactory.getLogger(LLDPDiscoveryListener.class);
    private final LLDPLinkAger lldpLinkAger;
    private final NotificationProviderService notificationService;
    private static DataBroker dataBroker;
    private static String topologyId = "flow:1";
    private static List<MyDiscoveredLink> soFarDiscoveredLinks = new ArrayList<>();
    private static Boolean topologyDSCreated = null;

    public LLDPDiscoveryListener(NotificationProviderService notificationService, LLDPLinkAger lldpLinkAger) {
        this.notificationService = notificationService;
        this.lldpLinkAger = lldpLinkAger;
    }


    public static String getTopologyId() {
        return topologyId;
    }

    public static void setTopologyId(String topologyId) {
        LLDPDiscoveryListener.topologyId = topologyId;
    }

    public static DataBroker getDataBroker() {
        return dataBroker;
    }

    public static void setDataBroker(DataBroker dataBroker) {
        LLDPDiscoveryListener.dataBroker = dataBroker;
    }


    @Override
    public void onPacketReceived(PacketReceived lldp) {
        String connectionCookie = "";
        String flowCookie = "";
        try {
            connectionCookie = String.format("0x%016x",lldp.getConnectionCookie().getValue());
        } catch (NullPointerException e) {
            LOG.warn("Connection cookie not at disposal");
        }
        try {
            flowCookie = String.format("0x%016x",lldp.getFlowCookie().getValue());
        } catch (NullPointerException e) {
            LOG.warn("Flow cookie not at disposal");
        }

        LOG.debug("PacketIn received, Connection Cookie:{}, Flow Cookie: {}",
                connectionCookie,
                flowCookie);
        NodeConnectorRef src = LLDPDiscoveryUtils.lldpToNodeConnectorRef(lldp.getPayload(), true);
        if(src != null) {
            LinkDiscoveredBuilder ldb = new LinkDiscoveredBuilder();
            ldb.setDestination(lldp.getIngress());
            ldb.setSource(new NodeConnectorRef(src));
            LinkDiscovered ld = ldb.build();

            MyDiscoveredLink myDiscoveredLink = linkDiscoveredToMyDiscoveredLink(ld);

            if (!soFarDiscoveredLinks.contains(myDiscoveredLink)) {
                soFarDiscoveredLinks.add(myDiscoveredLink);
                // update the topology DS
                writeCurrentlyDiscoveredLinksToDS();
                // update the local link discovery tracker
                LOG.debug("Currently discovered links:");
                for (MyDiscoveredLink printLink: soFarDiscoveredLinks)
                    LOG.debug(printLink.toString());
            }
            notificationService.publish(ld);
            // Ager already works within the openflowplugin
            //lldpLinkAger.put(ld);
        }
    }

    private class MyDiscoveredLink {
        private Link link;

        public MyDiscoveredLink(String sourceNode, String destinationNode, String sourceNodeConnector, String destinationNodeConnector) {
            Source src = new SourceBuilder()
                    .setSourceNode(new NodeId(sourceNode))
                    .setSourceTp(new TpId(sourceNodeConnector))
                    .build();
            Destination dst = new DestinationBuilder()
                    .setDestNode(new NodeId(destinationNode))
                    .setDestTp(new TpId(destinationNodeConnector))
                    .build();
            link = new LinkBuilder().setLinkId(new LinkId(sourceNodeConnector))
                    .setSource(src)
                    .setDestination(dst)
                    .setKey(new LinkKey(new LinkId(sourceNodeConnector)))
                    .build();
        }

        public Link getLink() {
            return link;
        }

        public String getSourceNode() {
            return link.getSource().getSourceNode().getValue();
        }

        public String getDestinationNode() {
            return link.getDestination().getDestNode().getValue();
        }

        public String getSourceNodeConnector() {
            return link.getSource().getSourceTp().getValue();
        }

        public String getDestinationNodeConnector() {
            return link.getDestination().getDestTp().getValue();
        }

        @Override
        public String toString() {
            return "MyDiscoveredLink{" +
                    "sourceNode='" + getSourceNode() + '\'' +
                    ", destinationNode='" + getDestinationNode() + '\'' +
                    ", sourceNodeConnector='" + getSourceNodeConnector() + '\'' +
                    ", destinationNodeConnector='" + getDestinationNodeConnector() + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) { // removes duplicates
            if (this == o) return true;
            if (!(o instanceof MyDiscoveredLink)) return false;
            MyDiscoveredLink that = (MyDiscoveredLink) o;
            return  (Objects.equals(getSourceNodeConnector(), that.getSourceNodeConnector()) &&
                    Objects.equals(getDestinationNodeConnector(), that.getDestinationNodeConnector())) ||
                    (Objects.equals(getSourceNodeConnector(), that.getDestinationNodeConnector()) &&
                    Objects.equals(getDestinationNodeConnector(), that.getSourceNodeConnector()));
        }


    }

    private MyDiscoveredLink linkDiscoveredToMyDiscoveredLink(LinkDiscovered ld) {
        String srcNode = ld.getSource().getValue().firstKeyOf(Node.class).getId().getValue();
        String dstNode = ld.getDestination().getValue().firstKeyOf(Node.class).getId().getValue();
        String srcNodeConnector = ld.getSource().getValue().firstKeyOf(NodeConnector.class).getId().getValue();
        String dstNodeConnector = ld.getDestination().getValue().firstKeyOf(NodeConnector.class).getId().getValue();

        return new MyDiscoveredLink(srcNode, dstNode, srcNodeConnector, dstNodeConnector);

    }

    private void writeCurrentlyDiscoveredLinksToDS() {

        // create topology DS if is is not already created
        if (topologyDSCreated == null) {
            List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node> topologyNodes
                    = TreeUtils.getNetworkTopologyNodes(topologyId);
            if (topologyNodes.isEmpty()){
                topologyDSCreated = false;
            } else {
                topologyDSCreated = true;
            }
        }

        if (!topologyDSCreated) {
            InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                    .generateTopologyInstanceIdentifier(topologyId);
            WriteTransaction createTopologyHopByHopTreeWriteTranscation = dataBroker.newWriteOnlyTransaction();
            Topology treeTopology = new TopologyBuilder().setTopologyId(new TopologyId(topologyId)).build();

            createTopologyHopByHopTreeWriteTranscation.put(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier, treeTopology);

            CheckedFuture<Void, TransactionCommitFailedException> futureTreeTopology =
                    createTopologyHopByHopTreeWriteTranscation.submit();

            Futures.addCallback(futureTreeTopology, new FutureCallback<Void>() {
                        public void onSuccess(Void v) {
                            LOG.info("Topology Data-Tree Initialized");
                        }
                        public void onFailure(Throwable thrown) {
                            LOG.info("Topology Data-Tree Initialization Failed.");
                        }
                    }
            );

            topologyDSCreated = true;
        }

        InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                .generateTopologyInstanceIdentifier(topologyId);

        WriteTransaction onlyWriteTransaction = dataBroker.newWriteOnlyTransaction();
        List<Link> currentlyDiscoveredLinks = new ArrayList<>();
        List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node> currentlyDiscoveredNodes
                = new ArrayList<>();

        // populating currently discovered links in the topology
        for (MyDiscoveredLink l: soFarDiscoveredLinks) {
            currentlyDiscoveredLinks.add(l.getLink());
        }

        // populating currently discovered nodes in the topology
        List<Node> inventoryNodes = InitialFlowUtils.getAllRealNodes(dataBroker);
        for (Node inventoryNode: inventoryNodes) {
            org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder topologyNodeBuilder
                    = new NodeBuilder();
            List<NodeConnector> nodeConnectors = inventoryNode.getNodeConnector();
            List<TerminationPoint> terminationPoints = new ArrayList<>();
            for (NodeConnector nc: nodeConnectors){
                TerminationPointBuilder tpb = new TerminationPointBuilder();
                tpb.setTpId(new TpId(nc.getId().getValue()));
                tpb.setKey(new TerminationPointKey(tpb.getTpId()));
                List<TpId> tpIdRefs = new ArrayList<>();
                tpIdRefs.add(tpb.getTpId());
                tpb.setTpRef(tpIdRefs);
                terminationPoints.add(tpb.build());
            }
            topologyNodeBuilder.setNodeId(new NodeId(inventoryNode.getId().getValue()))
                    .setTerminationPoint(terminationPoints)
                    .setKey(new NodeKey(topologyNodeBuilder.getNodeId()));
            currentlyDiscoveredNodes.add(topologyNodeBuilder.build());
        }

        // building topology object ot store in the DS
        Topology topologyToWrite = new TopologyBuilder().setTopologyId(new TopologyId(topologyId))
                .setLink(currentlyDiscoveredLinks).setNode(currentlyDiscoveredNodes).build();

        // merge or put  -> merge because host dicovery will be overwritten in case of put
        onlyWriteTransaction.merge(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier, topologyToWrite);
        CheckedFuture<Void, TransactionCommitFailedException> futureTreeTopology =
                onlyWriteTransaction.submit();

        Futures.addCallback(futureTreeTopology, new FutureCallback<Void>() {
                    public void onSuccess(Void v) {
                        LOG.info("Topology Data-Tree Refreshed");
                    }
                    public void onFailure(Throwable thrown) {
                        LOG.info("Topology Data-Tree Refreshing Failed.");
                    }
                }
        );
    }
}