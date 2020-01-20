/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.l2switch.arphandler.inventory;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.AddressCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.address.node.connector.Addresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2switch.loopremover.rev140714.StpStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2switch.loopremover.rev140714.StpStatusAwareNodeConnector;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * InventoryReader reads the opendaylight-inventory tree in MD-SAL data store.
 */
public class InventoryReader implements DataChangeListener{

    private static final Logger LOG = LoggerFactory.getLogger(InventoryReader.class);
    private DataBroker dataService;
    // Key: SwitchId, Value: NodeConnectorRef that corresponds to NC between
    // controller & switch
    private HashMap<String, NodeConnectorRef> controllerSwitchConnectors;
    // Key: SwitchId, Value: List of node connectors on this switch
    private HashMap<String, List<NodeConnectorRef>> switchNodeConnectors;
    private List<ListenerRegistration<DataChangeListener>> listenerRegistrationList = new ArrayList<>();

    public void setRefreshData(boolean refreshData) {
        this.refreshData = refreshData;
    }

    private boolean refreshData = false;
    private long refreshDataDelay = 20L;
    private boolean refreshDataScheduled = false;
    private final ScheduledExecutorService nodeConnectorDataChangeEventProcessor = Executors.newScheduledThreadPool(1);

    /**
     * Construct an InventoryService object with the specified inputs.
     *
     * @param dataService
     *            The DataBrokerService associated with the InventoryService.
     */
    public InventoryReader(DataBroker dataService) {
        this.dataService = dataService;
        controllerSwitchConnectors = new HashMap<String, NodeConnectorRef>();
        switchNodeConnectors = new HashMap<String, List<NodeConnectorRef>>();
    }


  private void registerAsDataChangeListener(){
    InstanceIdentifier<NodeConnector> nodeConnector = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class)
            .child(NodeConnector.class)
            .build();

    this.listenerRegistrationList.add(dataService.registerDataChangeListener(
            LogicalDatastoreType.OPERATIONAL,
            nodeConnector,
            this, AsyncDataBroker.DataChangeScope.BASE));


    InstanceIdentifier<StpStatusAwareNodeConnector> stpStatusAwareNodeConnector
            = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class)
            .child(NodeConnector.class)
            .augmentation(StpStatusAwareNodeConnector.class)
            .build();
    this.listenerRegistrationList.add(dataService.registerDataChangeListener(
            LogicalDatastoreType.OPERATIONAL,
            stpStatusAwareNodeConnector,
            this, AsyncDataBroker.DataChangeScope.BASE));

  }


    public HashMap<String, NodeConnectorRef> getControllerSwitchConnectors() {
        return controllerSwitchConnectors;
    }

    public HashMap<String, List<NodeConnectorRef>> getSwitchNodeConnectors() {
        return switchNodeConnectors;
    }

  @Override
  public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> asyncDataChangeEvent){
/*    if (asyncDataChangeEvent == null) {
      LOG.info("In onDataChanged: No processing done as change even is null.");
      return;
    }

    if(!refreshDataScheduled) {
      synchronized(this) {
        if(!refreshDataScheduled) {
          nodeConnectorDataChangeEventProcessor.schedule(new NodeConnectorDataChangeEventProcessor(),refreshDataDelay, TimeUnit.MILLISECONDS);
          refreshDataScheduled = true;
        }
      }
    }*/

  }


  public void close() {
    for (ListenerRegistration lr:listenerRegistrationList){
      lr.close();
    }
  }

    /**
     * Read the Inventory data tree to find information about the Nodes and
     * NodeConnectors. Create the list of NodeConnectors for a given switch.
     * Also determine the STP status of each NodeConnector.
     */
    public void readInventory() {
        // Only run once for now
        if (!refreshData) {
            return;
        }
        synchronized (this) {
            if (!refreshData)
                return;
            // Read Inventory
            InstanceIdentifier.InstanceIdentifierBuilder<Nodes> nodesInsIdBuilder = InstanceIdentifier
                    .<Nodes>builder(Nodes.class);
            Nodes nodes = null;
            ReadOnlyTransaction readOnlyTransaction = dataService.newReadOnlyTransaction();

            try {
                Optional<Nodes> dataObjectOptional = null;
                dataObjectOptional = readOnlyTransaction
                        .read(LogicalDatastoreType.OPERATIONAL, nodesInsIdBuilder.build()).get();
                if (dataObjectOptional.isPresent())
                    nodes = (Nodes) dataObjectOptional.get();
            } catch (InterruptedException e) {
                LOG.error("Failed to read nodes from Operation data store.");
                readOnlyTransaction.close();
                throw new RuntimeException("Failed to read nodes from Operation data store.", e);
            } catch (ExecutionException e) {
                LOG.error("Failed to read nodes from Operation data store.");
                readOnlyTransaction.close();
                throw new RuntimeException("Failed to read nodes from Operation data store.", e);
            }

            if (nodes != null) {
                // Get NodeConnectors for each node
                for (Node node : nodes.getNode()) {
                    ArrayList<NodeConnectorRef> nodeConnectorRefs = new ArrayList<NodeConnectorRef>();
                    List<NodeConnector> nodeConnectors = node.getNodeConnector();
                    if (nodeConnectors != null) {
                        for (NodeConnector nodeConnector : nodeConnectors) {
                            // Read STP status for this NodeConnector
                            StpStatusAwareNodeConnector saNodeConnector = nodeConnector
                                    .getAugmentation(StpStatusAwareNodeConnector.class);

                            if (saNodeConnector != null && StpStatus.Discarding.equals(saNodeConnector.getStatus())) {
                                continue;
                            }
                            if (nodeConnector.getKey().toString().contains("LOCAL")) {
                                continue;
                            }
                            NodeConnectorRef ncRef = new NodeConnectorRef(InstanceIdentifier.<Nodes>builder(Nodes.class)
                                    .<Node, NodeKey>child(Node.class, node.getKey())
                                    .<NodeConnector, NodeConnectorKey>child(NodeConnector.class, nodeConnector.getKey())
                                    .build());
                            nodeConnectorRefs.add(ncRef);
                        }
                    }

                    switchNodeConnectors.put(node.getId().getValue(), nodeConnectorRefs);
                    NodeConnectorRef ncRef = new NodeConnectorRef(InstanceIdentifier.<Nodes>builder(Nodes.class)
                            .<Node, NodeKey>child(Node.class, node.getKey())
                            .<NodeConnector, NodeConnectorKey>child(NodeConnector.class,
                                    new NodeConnectorKey(new NodeConnectorId(node.getId().getValue() + ":LOCAL")))
                            .build());
                    LOG.debug("Local port for node {} is {}", node.getKey(), ncRef);
                    controllerSwitchConnectors.put(node.getId().getValue(), ncRef);
                }
            }
            readOnlyTransaction.close();
            refreshData = false;

            if(0 == listenerRegistrationList.size()){
              registerAsDataChangeListener();
            }
        }
    }

    // DEMO NETSOFT



    // DEMO NETSOFT
    /**
     * Generate a topology identifier.
     * @param topologyId
     */
    public static InstanceIdentifier<Topology> generateTopologyInstanceIdentifier(final String topologyId) {
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId))).build();
    }

    // DEMO NETSOFT
    /**
     * Returns a specified substrate topology by name
     * @param toponame  the name of the the topology to retrieve.
     */

    public Topology getTopology(String toponame) {
        if(toponame.isEmpty()|| toponame==""){toponame="flow:1";}

        InstanceIdentifier<Topology> topologyInstanceIdentifier = generateTopologyInstanceIdentifier(toponame);
        Topology topology = null;
        ReadOnlyTransaction readOnlyTransaction = dataService.newReadOnlyTransaction();
        try {
            Optional<Topology> topologyOptional = readOnlyTransaction.read(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier).get();
            if (topologyOptional.isPresent()) {
                topology = topologyOptional.get();

            }
        } catch (Exception e) {
            LOG.error("Error reading topology {}", topologyInstanceIdentifier);
            readOnlyTransaction.close();
            throw new RuntimeException("Error reading from operational store, topology : " + topologyInstanceIdentifier, e);
        }

        readOnlyTransaction.close();
        if (topology == null) {
            return null;
        }


        return topology;
    }


    // DEMO NETSOFT
    /**
     * Returns a list of links in the  physical topology, if the topology name is not specified, a default openflow topology is  returned
     * @param topo  id of the physical topology to be retrieved.
     * @return List<Link> list of links
     */

    public List<Link> getLinksFromPhyTopology(String topo) {
        //we assuming the default substratrate Topology id is flow:1 ie the topology provided by the openflowplugin
        if(topo.isEmpty()|| topo==""){topo="flow:1";}
        Topology topology= getTopology(topo);
        if(topology == null) {
            LOG.error("Topology not set ");
        }

        List<Link> linkList = topology.getLink();

        if(linkList == null || linkList.isEmpty()) {

            LOG.error("Links not present in topology");
        }
        List<Link> internalLinks = new ArrayList<>();
        for (Link link : linkList) {
            internalLinks.add(link);
        }

        return internalLinks;
    }

    /**
     * Get the NodeConnector on the specified node with the specified MacAddress
     * observation.
     *
     * @param nodeInsId
     *            InstanceIdentifier for the node on which to search for.
     * @param macAddress
     *            MacAddress to be searched for.
     * @return NodeConnectorRef that pertains to the NodeConnector containing
     *         the MacAddress observation.
     */
    public NodeConnectorRef getNodeConnector(InstanceIdentifier<Node> nodeInsId, MacAddress macAddress) {
        if (nodeInsId == null || macAddress == null) {
            return null;
        }

        NodeConnectorRef destNodeConnector = null;
        long latest = -1;
        ReadOnlyTransaction readOnlyTransaction = dataService.newReadOnlyTransaction();
        try {
            Optional<Node> dataObjectOptional = null;
            dataObjectOptional = readOnlyTransaction.read(LogicalDatastoreType.OPERATIONAL, nodeInsId).get();
            if (dataObjectOptional.isPresent()) {
                Node node = (Node) dataObjectOptional.get();
                LOG.debug("Looking address{} in node : {}", macAddress, nodeInsId);
                if (node.getNodeConnector() != null) {
                    for (NodeConnector nc : node.getNodeConnector()) {
                        // Don't look for mac in discarding node connectors
                        StpStatusAwareNodeConnector saNodeConnector = nc
                                .getAugmentation(StpStatusAwareNodeConnector.class);
                        if (saNodeConnector != null && StpStatus.Discarding.equals(saNodeConnector.getStatus())) {
                            continue;
                        }
                        LOG.debug("Looking address{} in nodeconnector : {}", macAddress, nc.getKey());
                        AddressCapableNodeConnector acnc = nc.getAugmentation(AddressCapableNodeConnector.class);
                        if (acnc != null) {
                            List<Addresses> addressesList = acnc.getAddresses();
                            for (Addresses add : addressesList) {
                                if (macAddress.equals(add.getMac())) {
                                    if (add.getLastSeen() > latest) {
                                        destNodeConnector = new NodeConnectorRef(
                                                nodeInsId.child(NodeConnector.class, nc.getKey()));
                                        latest = add.getLastSeen();
                                        LOG.debug("Found address{} in nodeconnector : {}", macAddress, nc.getKey());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LOG.debug("Node connectors data is not present for node {}", node.getId());
                }
            }
        } catch (InterruptedException e) {
            LOG.error("Failed to read nodes from Operation data store.");
            readOnlyTransaction.close();
            throw new RuntimeException("Failed to read nodes from Operation data store.", e);
        } catch (ExecutionException e) {
            LOG.error("Failed to read nodes from Operation data store.");
            readOnlyTransaction.close();
            throw new RuntimeException("Failed to read nodes from Operation data store.", e);
        }
        readOnlyTransaction.close();
        return destNodeConnector;
    }

  private class NodeConnectorDataChangeEventProcessor implements Runnable {

    @Override
    public void run() {
      controllerSwitchConnectors.clear();
      switchNodeConnectors.clear();
      refreshDataScheduled = false;
      setRefreshData(true);
      readInventory();
    }

  }

}
