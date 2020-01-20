/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.l2switch.arphandler.core;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.l2switch.arphandler.inventory.InventoryReader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;

/**
 * PacketDispatcher sends packets out to the network.
 */
public class PacketDispatcher {

    private final static Logger LOG = LoggerFactory.getLogger(PacketDispatcher.class);
    private InventoryReader inventoryReader;
    private PacketProcessingService packetProcessingService;

    public void setPacketProcessingService(PacketProcessingService packetProcessingService) {
        this.packetProcessingService = packetProcessingService;
    }

    public void setInventoryReader(InventoryReader inventoryReader) {
        this.inventoryReader = inventoryReader;
    }

    /**
     * Dispatches the packet in the appropriate way - flood or unicast.
     *
     * @param payload
     *            The payload to be sent.
     * @param ingress
     *            The NodeConnector where the payload came from.
     * @param srcMac
     *            The source MacAddress of the packet.
     * @param destMac
     *            The destination MacAddress of the packet.
     */
    public void dispatchPacket(byte[] payload, NodeConnectorRef ingress, MacAddress srcMac, MacAddress destMac) {
        //LOG.info("DEMO: Dispatching an ARP packet 2");

        inventoryReader.readInventory();

        String nodeId = ingress.getValue().firstIdentifierOf(Node.class).firstKeyOf(Node.class, NodeKey.class).getId()
                .getValue();
        NodeConnectorRef srcConnectorRef = inventoryReader.getControllerSwitchConnectors().get(nodeId);

        if (srcConnectorRef == null) {
            refreshInventoryReader();
            srcConnectorRef = inventoryReader.getControllerSwitchConnectors().get(nodeId);
        }
        inventoryReader.getSwitchNodeConnectors();
        NodeConnectorRef destNodeConnector = inventoryReader
                .getNodeConnector(ingress.getValue().firstIdentifierOf(Node.class), destMac);
        if (srcConnectorRef != null) {
            if (destNodeConnector != null) {
                sendPacketOut(payload, srcConnectorRef, destNodeConnector);
            } else {
                for(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node node:
                        inventoryReader.getTopology("flow:1").getNode()) {
                    floodPacket(node.getNodeId().getValue(), payload, ingress, srcConnectorRef);
                }
            }
        } else {
            LOG.info("Cannot send packet out or flood as controller node connector is not available for node {}.",
                    nodeId);
        }
    }

    /**
     * Floods the packet.
     *
     * @param payload
     *            The payload to be sent.
     * @param origIngress
     *            The NodeConnector where the payload came from.
     */
    public void floodPacket(String nodeId, byte[] payload, NodeConnectorRef origIngress,
                            NodeConnectorRef controllerNodeConnector) {

        List<NodeConnectorRef> nodeConnectors = inventoryReader.getSwitchNodeConnectors().get(nodeId);

        if (nodeConnectors == null) {
            refreshInventoryReader();
            nodeConnectors = inventoryReader.getSwitchNodeConnectors().get(nodeId);
            if (nodeConnectors == null) {
               // LOG.info("Cannot flood packets, as inventory doesn't have any node connectors for node {}", nodeId);
                return;
            }
        }


        List<NodeConnectorRef> allNodeConnectors = new ArrayList<>();
        for(NodeConnectorRef conn:nodeConnectors)
            allNodeConnectors.add(conn);

        for(NodeConnectorRef nodeConn:allNodeConnectors) {
            //Ensure flooding happens only on exit ports
            for (Link link : inventoryReader.getLinksFromPhyTopology("flow:1")) {
                if(!link.getLinkId().getValue().contains("host")) {

                    String nodeConnRefConn = nodeConn.getValue().firstIdentifierOf(NodeConnector.class)
                            .firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId().getValue();

                    //LOG.info("Source of link: " + link.getSource().getSourceTp().getValue() + " comp.to " + nodeConnRefConn);

                    if (link.getSource().getSourceTp().getValue().contains(nodeConnRefConn) ||
                            link.getDestination().getDestTp().getValue().contains(nodeConnRefConn)) {
                        //LOG.info("Excluding from the list of outputs!");
                        nodeConnectors.remove(nodeConn);
                    }
                }
            }
        }

        //LOG.info("List of node connectors to send out on: " + nodeConnectors.toString());
        for (NodeConnectorRef ncRef : nodeConnectors) {
            String ncId = ncRef.getValue().firstIdentifierOf(NodeConnector.class)
                    .firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId().getValue();
            // Don't flood on discarding node connectors & origIngress
            if (!ncId.equals(origIngress.getValue().firstIdentifierOf(NodeConnector.class)
                    .firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId().getValue())) {
                //LOG.info("Sending out on: " + ncId + "!");
                sendPacketOut(payload, origIngress, ncRef);
            }
        }
    }

    /**
     * Sends the specified packet on the specified port.
     *
     * @param payload
     *            The payload to be sent.
     * @param ingress
     *            The NodeConnector where the payload came from.
     * @param egress
     *            The NodeConnector where the payload will go.
     */
    public void sendPacketOut(byte[] payload, NodeConnectorRef ingress, NodeConnectorRef egress) {
        if (ingress == null || egress == null)
            return;
        InstanceIdentifier<Node> egressNodePath = getNodePath(egress.getValue());
        TransmitPacketInput input = new TransmitPacketInputBuilder() //
                .setPayload(payload) //
                .setNode(new NodeRef(egressNodePath)) //
                .setEgress(egress) //
                .setIngress(ingress) //
                .build();
        packetProcessingService.transmitPacket(input);
    }

    private void refreshInventoryReader() {
        inventoryReader.setRefreshData(true);
        inventoryReader.readInventory();
    }

    private InstanceIdentifier<Node> getNodePath(final InstanceIdentifier<?> nodeChild) {
        return nodeChild.firstIdentifierOf(Node.class);
    }

}
