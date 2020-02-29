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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

/**
 * Speeding up network discovery by always probing the last two discovered levels with LLDP packets.
 * These LLDP packets are sent each second instead of each 5 s, which is the case in the OpenFlowPlugin.
 */
public class LLDPSpeakerEdge implements AutoCloseable, NodeConnectorEventsObserver, Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(LLDPSpeakerEdge.class);
    /* In order to speed up the discovery procedure flood period interval reduced to 0.1s; was 5 */
    private static final long LLDP_FLOOD_PERIOD = 1000; // was 5s

    private final PacketProcessingService packetProcessingService;
    private final ScheduledThreadPoolExecutorWrapper scheduledExecutorService;
    private final Map<InstanceIdentifier<NodeConnector>, TransmitPacketInput> nodeConnectorMap =
            new ConcurrentHashMap<>();
    private static List<InstanceIdentifier<NodeConnector>> nodeConnectorListCached =
            new ArrayList<>();
    private final ScheduledFuture<?> scheduledSpeakerTask;
    private final MacAddress addressDestionation = new MacAddress("01:23:00:00:00:01");
    private static Integer inactiveCounter = 0;
    private static final Integer inactiveMax = 600; // inactivity timer -> plays a role in this case 600/5 = 120 seconds this service active
    private static NetworkGraphService networkGraphService;
    private static HopByHopTreeGraph hopByHopTreeGraph = null;
    private static String topologyId;
    private static ArrayList<String> ctlList = new ArrayList<String>();
    // set through the EOS
    private static boolean isLeader = false;


    public static ArrayList<String> getCtlList() {
        return ctlList;
    }

    public static void setCtlList(ArrayList<String> ctlList) {
        LLDPSpeakerEdge.ctlList = ctlList;
    }

    public static String getTopologyId() {
        return topologyId;
    }

    public static void setTopologyId(String topologyId) {
        LLDPSpeakerEdge.topologyId = topologyId;
    }

    public LLDPSpeakerEdge(final PacketProcessingService packetProcessingService, NetworkGraphService networkGraphService) {
        this(packetProcessingService, new ScheduledThreadPoolExecutorWrapper(1));
        this.networkGraphService = networkGraphService;
    }


    public LLDPSpeakerEdge(final PacketProcessingService packetProcessingService,
                           final ScheduledThreadPoolExecutorWrapper scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
        scheduledSpeakerTask = this.scheduledExecutorService
                .scheduleAtFixedRate(this, LLDP_FLOOD_PERIOD,LLDP_FLOOD_PERIOD, TimeUnit.MILLISECONDS);
        this.packetProcessingService = packetProcessingService;
        LOG.info("LLDPSpeakerEdge started, it will send LLDP frames each {} seconds", LLDP_FLOOD_PERIOD);
    }

    /**
     * Closes this resource, relinquishing any underlying resources.
     */
    @Override
    public void close() {
        nodeConnectorMap.clear();
        scheduledExecutorService.shutdown();
        scheduledSpeakerTask.cancel(true);
        LOG.trace("LLDPSpeakerEdge stopped sending LLDP frames.");
    }

    /**
     * Send LLDP frames last two levels known openflow switch ports.
     */
    @Override
    public void run() {
        if (isLeader) {


            List<InstanceIdentifier<NodeConnector>> connectorsToSendLLDPOn = new ArrayList<>();

            LOG.debug("LLDPSpeakerEdge speaking :)");
            if (inactiveCounter < inactiveMax) {
                if (hopByHopTreeGraph == null) {
                    hopByHopTreeGraph = networkGraphService.getCurrentHopByHopTreeGraph();
                    if (hopByHopTreeGraph == null)
                        return;
                    if (hopByHopTreeGraph.getVertexCount() == 0) {
                        return;
                    }
                }
                /**
                 * Get all current level nodes and previous level discovered nodes
                 */
                List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId> nodeIds =
                        hopByHopTreeGraph.getAllNodesOfTheLevel(hopByHopTreeGraph.getTreeDepth());
                List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId> previousLevelNodeIds =
                        hopByHopTreeGraph.getAllNodesOfTheLevel(hopByHopTreeGraph.getTreeDepth() - 1);


                // Get the IP address of this controller instance
                HostUtilities.InterfaceConfiguration myIfConfig =
                        HostUtilities.returnMyIfConfig(ctlList);
                if (myIfConfig == null) {
                    LOG.warn("Provided controller IP addresses do not found on this machine.");
                    return;
                }
                String ctlIpAddr = myIfConfig.getIpAddr();

                /**
                 * For each previous level node find out which ports lead to the next level and remember those ports
                 * They will be used later for probing
                 */
                LOG.info("Previous level nodes are {}", previousLevelNodeIds.toString());
                for (org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId nodeId : previousLevelNodeIds) {
                    if (nodeId.getValue().contains("host")) {
                        continue;
                    }
                    try {
                        List<String> quasiBroadPorts = TreeUtils.estimateQuasiBroadcastingPortsBasedOnCurrentData(nodeId.getValue(), ctlIpAddr, topologyId);
                        LOG.debug("Previous level node {} quasiBroadcastPorts: {}", nodeId.getValue(), quasiBroadPorts.toString());
                        for (String port : quasiBroadPorts) {
                            InstanceIdentifier<NodeConnector> nodeConnectorInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                                    .child(Node.class, new NodeKey(new NodeId(port.split(":")[0] + ":" + port.split(":")[1])))
                                    .child(NodeConnector.class, new NodeConnectorKey(new NodeConnectorId(port))).build();
                            connectorsToSendLLDPOn.add(nodeConnectorInstanceIdentifier);
                        }
                    } catch (Exception e) {
                        for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                            LOG.error("LLDPSpeaker: Stack root cause trace -> {}", m);
                        }
                        LOG.warn("Topology Shard leader did not respond in the required time -> akka.pattern.AskTimeoutException");
                    }

                }

                LOG.info("Last level nodes are {}", nodeIds.toString());
                for (org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId nodeId : nodeIds) {
                    if (nodeId.getValue().contains("host")) {
                        continue;
                    }
                    List<String> quasiBroadPorts = null;
                    try {
                        quasiBroadPorts = TreeUtils.estimateQuasiBroadcastingPortsBasedOnCurrentData(nodeId.getValue(), ctlIpAddr, topologyId);
                    } catch (InterruptedException e) {
                        for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                            LOG.error("LLDPSpeaker: Stack root cause trace -> {}", m);
                        }
                    }
                    LOG.debug("Last level node {} quasiBroadcastPorts: {}", nodeId.getValue(), quasiBroadPorts.toString());
                    for (String port : quasiBroadPorts) {
                        InstanceIdentifier<NodeConnector> nodeConnectorInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                                .child(Node.class, new NodeKey(new NodeId(port.split(":")[0] + ":" + port.split(":")[1])))
                                .child(NodeConnector.class, new NodeConnectorKey(new NodeConnectorId(port))).build();
                        connectorsToSendLLDPOn.add(nodeConnectorInstanceIdentifier);
                    }
                }
                if (connectorsToSendLLDPOn.equals(nodeConnectorListCached)) {
                    // to avoid running forever
                    //inactiveCounter++;
                } else {
                    // reset counter if different ports appear
                    inactiveCounter = 0;
                }
                nodeConnectorListCached = connectorsToSendLLDPOn;
                LOG.debug("Sending LLDP frames to {} edge ports...", connectorsToSendLLDPOn.size());
                for (InstanceIdentifier<NodeConnector> nodeConnectorInstanceId : connectorsToSendLLDPOn) {
                    NodeConnectorId nodeConnectorId = nodeConnectorInstanceId.firstKeyOf(NodeConnector.class).getId();
                    if (!(nodeConnectorId == null))
                        LOG.debug("Sending LLDP through edge port {}", nodeConnectorId.getValue());
                    if (!(nodeConnectorInstanceId == null))
                        if (nodeConnectorMap.containsKey(nodeConnectorInstanceId)) {
                            packetProcessingService.transmitPacket(nodeConnectorMap.get(nodeConnectorInstanceId));
                            LOG.debug("LLDP packet sent in LLDPSpeakerEdge");
                        }
                }

            } else {
                //close executor
                this.close();
                LOG.debug("LLDPSpeakerEdge Executor closed -> inactive counter reached value {}!", inactiveCounter);
            }
        } else {
            LOG.debug("Not a leader for LLDPSpeakerEdge");
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
                    "Port {} already in LLDPSpeakerEdge.nodeConnectorMap, no need for additional processing",
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
        LOG.trace("Port {} added to LLDPSpeakerEdge.nodeConnectorMap", nodeConnectorId.getValue());

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
        LOG.trace("Port {} removed from LLDPSpeakerEdge.nodeConnectorMap", nodeConnectorId.getValue());
    }

    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("LLDPSpeakerEdge ownership change logged: " + ownershipChange);

        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the LLDPSpeakerEdge leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the LLDPSpeakerEdge follower.");
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
