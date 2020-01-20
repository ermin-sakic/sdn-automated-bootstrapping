package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 07.05.18
 */
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
//import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.openflowplugin.applications.topology.lldp.utils.LLDPDiscoveryUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkDiscovered;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkDiscoveredBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff.FlowCapableNodeMapping.toTopologyLink;

public class LLDPDiscoveryListener implements PacketProcessingListener {

    Set<String> linkAdded = new HashSet<>();
    Graph<NodeId, Link> networkGraph = null;

    private static final Logger LOG = LoggerFactory.getLogger(LLDPDiscoveryListener.class);

    public LLDPDiscoveryListener() {

    }

    @Override
    public void onPacketReceived(PacketReceived lldp) {
        NodeConnectorRef src = LLDPDiscoveryUtils.lldpToNodeConnectorRef(lldp.getPayload(), true);
        if (src != null) {
            final NodeKey nodeKey = lldp.getIngress().getValue().firstKeyOf(Node.class);
            //LOG.info("LLDP: LLDP packet received for destination node {}", nodeKey);
            if (nodeKey != null) {
                LinkDiscoveredBuilder ldb = new LinkDiscoveredBuilder();
                ldb.setDestination(lldp.getIngress());
                ldb.setSource(new NodeConnectorRef(src));
                LinkDiscovered ld = ldb.build();

                Link link = toTopologyLink(ld);

                List<Link> links = new ArrayList<>();
                links.add(link);
                addLinks(links);

            } else {
                LOG.debug("LLDP: LLDP packet ignored. Unable to extract node-key from packet-in ingress.");
            }
        }
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
            LOG.info("LLDP: Source-> {} Destination-> {}", link.getSource().getSourceNode().getValue(),
                    link.getDestination().getDestNode().getValue());
            return false;
        }
    }

}