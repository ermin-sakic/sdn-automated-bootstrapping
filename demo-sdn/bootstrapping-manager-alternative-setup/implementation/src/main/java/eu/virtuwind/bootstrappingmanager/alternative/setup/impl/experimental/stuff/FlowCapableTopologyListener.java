package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 07.05.18
 */
/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

import static eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff.FlowCapableNodeMapping.toTopologyLink;

import com.google.common.base.Preconditions;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.FlowTopologyDiscoveryListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkDiscovered;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkOverutilized;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkRemoved;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkUtilizationNormal;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class FlowCapableTopologyListener implements FlowTopologyDiscoveryListener {

    private static final Logger LOG = LoggerFactory.getLogger(FlowCapableTopologyListener.class);
    Set<String> linkAdded = new HashSet<>();
    Graph<NodeId, Link> networkGraph = null;
    private final InstanceIdentifier<Topology> iiToTopology;

    FlowCapableTopologyListener(final InstanceIdentifier<Topology> topology) {
        this.iiToTopology = Preconditions.checkNotNull(topology);
    }

    @Override
    public void onLinkDiscovered(final LinkDiscovered notification) {
        final Link link = toTopologyLink(notification);

        List<Link> links = new ArrayList<>();
        links.add(link);
        addLinks(links);

        //LOG.info("LINK DISCOVERED {}", link.getKey().getLinkId().getValue());
        //LOG.info("LINK DISCOVERED source-> {}", link.getSource().getSourceNode().getValue());
        //LOG.info("LINK DISCOVERED destination-> {}", link.getDestination().getDestNode().getValue());
    }

    @Override
    public void onLinkOverutilized(final LinkOverutilized notification) {
        // NOOP
    }

    @Override
    public void onLinkRemoved(final LinkRemoved notification) {
        /*
        processor.enqueueOperation(new TopologyOperation() {
            @Override
            public void applyOperation(final TransactionChainManager manager) {
                Optional<Link> linkOptional = Optional.absent();
                try {
                    // read that checks if link exists (if we do not do this we might get an exception on delete)
                    linkOptional = manager.readFromTransaction(LogicalDatastoreType.OPERATIONAL,
                            TopologyManagerUtil.linkPath(toTopologyLink(notification), iiToTopology)).checkedGet();
                } catch (ReadFailedException e) {
                    LOG.warn("Error occurred when trying to read Link: {}", e.getMessage());
                    LOG.debug("Error occurred when trying to read Link.. ", e);
                }
                if (linkOptional.isPresent()) {
                    manager.addDeleteOperationToTxChain(LogicalDatastoreType.OPERATIONAL,
                            TopologyManagerUtil.linkPath(toTopologyLink(notification), iiToTopology));
                }
            }

            @Override
            public String toString() {
                return "onLinkRemoved";
            }
        });
        */
    }

    @Override
    public void onLinkUtilizationNormal(final LinkUtilizationNormal notification) {
        // NOOP
    }

    public synchronized void addLinks(List<Link> links) {
        if (links == null || links.isEmpty()) {
            LOG.info("In addLinks: No link added as links is null or empty.");
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

    private synchronized boolean linkAlreadyAdded(Link link) {
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
            LOG.info("FCTL: Source-> {} Destination-> {}", link.getSource().getSourceNode().getValue(),
                    link.getDestination().getDestNode().getValue());
            return false;
        }
    }

    private synchronized boolean linkAlreadyAddedCheck(Link link) {
        String linkAddedKey = null;
        if (link.getDestination().getDestTp().hashCode() > link.getSource().getSourceTp().hashCode()) {
            linkAddedKey = link.getSource().getSourceTp().getValue() + link.getDestination().getDestTp().getValue();
        } else {
            linkAddedKey = link.getDestination().getDestTp().getValue() + link.getSource().getSourceTp().getValue();
        }
        if (linkAdded.contains(linkAddedKey)) {
            return true;
        } else {
            return false;
        }
    }


}