package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 07.05.18
 */

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.PacketUtils;
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

//import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;

public class DHCPPacketInListener implements PacketProcessingListener {


    private static final Logger LOG = LoggerFactory.getLogger(DHCPPacketInListener.class);

    public DHCPPacketInListener() {
        LOG.info("DHCPPacketInListener default constructor");
    }

    @Override
    public void onPacketReceived(PacketReceived packetReceived) {

        String clientMacAddress = PacketUtils.getClientMACAddressFromDHCP(packetReceived.getPayload());

        if (clientMacAddress != null) {
            LOG.info("Packet-In is DHCP packet from the client with hardware address {}", clientMacAddress);
        }
    }


}