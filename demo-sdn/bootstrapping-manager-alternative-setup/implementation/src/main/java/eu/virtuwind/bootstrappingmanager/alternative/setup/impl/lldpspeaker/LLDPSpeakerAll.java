/*
 * Copyright (c) 2014 Pacnet and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
  *
  * @filename LLDPSpeakerEdge.java
  *
  * @date 18.05.18
  *
  * @author Mirza Avdic
  *
  *
 */

package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.lldpspeaker;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.HopByHopTreeGraph;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.NetworkGraphService;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.TreeUtils;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.ScheduledThreadPoolExecutorWrapper;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.*;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Objects of this class send LLDP frames to all flow-capable ports that can
 * be discovered through inventory. (LLDPSpeaker clone from OpenFlowPlugin )
 *
 * WHY: because LLDPSpeaker from the ODL openflow plugin can be executed on the controller instance that
 * is not a bootstrapping leader (other ENTITY) and that can cause that LLDP and OF packets are not forwarded
 * properly because in that part of the network it might happen that switches are still not configured with necessary
 * rules
 */
public class LLDPSpeakerAll implements AutoCloseable, NodeConnectorEventsObserver, Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(LLDPSpeakerAll.class);
    private static final long LLDP_FLOOD_PERIOD = 5000; // was 5s

    private final PacketProcessingService packetProcessingService;
    private final ScheduledThreadPoolExecutorWrapper scheduledExecutorService;
    private final Map<InstanceIdentifier<NodeConnector>, TransmitPacketInput> nodeConnectorMap =
            new ConcurrentHashMap<>();
    private final ScheduledFuture<?> scheduledSpeakerTask;
    private final MacAddress addressDestionation = new MacAddress("01:23:00:00:00:01");
    private static Integer inactiveCounter = 0;
    private static final Integer inactiveMax = 600; // inactivity timer -> plays a role in this case 600/5 = 120 seconds this service active
    private static String topologyId;
    private static ArrayList<String> ctlList = new ArrayList<String>();
    // set through the EOS
    private static boolean isLeader = false;


    public static ArrayList<String> getCtlList() {
        return ctlList;
    }

    public static void setCtlList(ArrayList<String> ctlList) {
        LLDPSpeakerAll.ctlList = ctlList;
    }

    public static String getTopologyId() {
        return topologyId;
    }

    public static void setTopologyId(String topologyId) {
        LLDPSpeakerAll.topologyId = topologyId;
    }

    public LLDPSpeakerAll(final PacketProcessingService packetProcessingService) {
        this(packetProcessingService, new ScheduledThreadPoolExecutorWrapper(1));
    }


    public LLDPSpeakerAll(final PacketProcessingService packetProcessingService,
                          final ScheduledThreadPoolExecutorWrapper scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
        scheduledSpeakerTask = this.scheduledExecutorService
                .scheduleAtFixedRate(this, LLDP_FLOOD_PERIOD,LLDP_FLOOD_PERIOD, TimeUnit.MILLISECONDS);
        this.packetProcessingService = packetProcessingService;
        LOG.info("LLDPSpeakerAll started, it will send LLDP frames each {} seconds", LLDP_FLOOD_PERIOD);
    }

    /**
     * Closes this resource, relinquishing any underlying resources.
     */
    @Override
    public void close() {
        nodeConnectorMap.clear();
        scheduledExecutorService.shutdown();
        scheduledSpeakerTask.cancel(true);
        LOG.trace("LLDPSpeakerAll stopped sending LLDP frames.");
    }

    /**
     * Send LLDP frames to all known openflow switch ports.
     */
    @Override
    public void run() {
        if (isLeader) {

            LOG.debug("LLDPSpeakerAllspeaking :)");
            if (inactiveCounter < inactiveMax) {

                LOG.debug("Sending LLDP frames to {} ports...", nodeConnectorMap.size());
                for (InstanceIdentifier<NodeConnector> nodeConnectorInstanceId : nodeConnectorMap.keySet()) {
                    NodeConnectorId nodeConnectorId = nodeConnectorInstanceId.firstKeyOf(NodeConnector.class).getId();
                    if (!(nodeConnectorId == null))
                        LOG.debug("Sending LLDP through port {}", nodeConnectorId.getValue());
                    if (!(nodeConnectorInstanceId == null))
                        if (nodeConnectorMap.containsKey(nodeConnectorInstanceId)) {
                            packetProcessingService.transmitPacket(nodeConnectorMap.get(nodeConnectorInstanceId));
                            LOG.debug("LLDP packet sent in LLDPSpeakerAll");
                        }
                }

            } else {
                //close executor
                this.close();
                LOG.debug("LLDPSpeakerAll Executor closed -> inactive counter reached value {}!", inactiveCounter);
            }
        } else {
            LOG.debug("Not a leader for LLDPSpeakerAll");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nodeConnectorAdded(final InstanceIdentifier<NodeConnector> nodeConnectorInstanceId,
                                   final FlowCapableNodeConnector flowConnector) {
        NodeConnectorId nodeConnectorId = InstanceIdentifier.keyOf(nodeConnectorInstanceId).getId();

        // nodeConnectorAdded can be called even if we already sending LLDP
        // frames to
        // port, so first we check if we actually need to perform any action
        if (nodeConnectorMap.containsKey(nodeConnectorInstanceId)) {
            LOG.trace(
                    "Port {} already in LLDPSpeakerAll.nodeConnectorMap, no need for additional processing",
                    nodeConnectorId.getValue());
            return;
        }

        // Prepare to build LLDP payload
        InstanceIdentifier<Node> nodeInstanceId = nodeConnectorInstanceId.firstIdentifierOf(Node.class);
        NodeId nodeId = InstanceIdentifier.keyOf(nodeInstanceId).getId();
        MacAddress srcMacAddress = flowConnector.getHardwareAddress();
        Long outputPortNo = flowConnector.getPortNumber().getUint32();

        // No need to send LLDP frames on local ports
        if (outputPortNo == null) {
            LOG.trace("Port {} is local, not sending LLDP frames through it", nodeConnectorId.getValue());
            return;
        }

        // Generate packet with destination switch and port
        TransmitPacketInput packet = new TransmitPacketInputBuilder()
                .setEgress(new NodeConnectorRef(nodeConnectorInstanceId))
                .setNode(new NodeRef(nodeInstanceId))
                .setPayload(LLDPUtil
                        .buildLldpFrame(nodeId, nodeConnectorId, srcMacAddress, outputPortNo, addressDestionation))
                .build();

        // Save packet to node connector id -> packet map to transmit it every 5
        // seconds
        nodeConnectorMap.put(nodeConnectorInstanceId, packet);
        LOG.trace("Port {} added to LLDPSpeakerAll.nodeConnectorMap", nodeConnectorId.getValue());

        // Transmit packet for first time immediately
        packetProcessingService.transmitPacket(packet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nodeConnectorRemoved(final InstanceIdentifier<NodeConnector> nodeConnectorInstanceId) {
        nodeConnectorMap.remove(nodeConnectorInstanceId);
        NodeConnectorId nodeConnectorId = InstanceIdentifier.keyOf(nodeConnectorInstanceId).getId();
        LOG.trace("Port {} removed from LLDPSpeakerAll.nodeConnectorMap", nodeConnectorId.getValue());
    }

    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("LLDPSpeakerAll ownership change logged: " + ownershipChange);

        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the LLDPSpeakerAll leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the LLDPSpeakerAll follower.");
            setFollower();
        }
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
