package eu.virtuwind.resourcemonitor.impl;


import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.AddressCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.address.node.connector.Addresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class ResourceMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceMonitor.class);

    public void monitorResources() {
	LOG.info("test function");
    }


    /**
     * Method to get  all Nodes from Toplogy
     * @param db - DataBroker to extract data fromn md-sal
     * @return List<Node> - list of nodes found in the topoology
     */

    public static List<Node> getAllNodes(DataBroker db) {
        List<Node> nodeList = new ArrayList<>();

        try {
            //Topology Id
            TopologyId topoId = new TopologyId("flow:1");
            //get the InstanceIdentifier
            InstanceIdentifier<Topology> nodesIid = InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, new TopologyKey(topoId)).toInstance();
            ReadOnlyTransaction nodesTransaction = db.newReadOnlyTransaction();

            //Read from operational database
            CheckedFuture<Optional<Topology>, ReadFailedException> nodesFuture = nodesTransaction
                    .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
            Optional<Topology> nodesOptional = nodesFuture.checkedGet();

            if (nodesOptional != null && nodesOptional.isPresent())
                nodeList = nodesOptional.get().getNode();
            //System.out.println("\n\n" + nodeList);
            //LOG.info("Nodelist: " + nodeList);
            return nodeList;
        }

        catch (Exception e) {
            LOG.info("Node Fetching Failed");
            return nodeList;
        }

       // return nodeList;
    }

    /**
     * Method to get  all links from Toplogy. Links contain all the relevant information
     * @param db DataBroker from which toplogy links should be extracted
     * @return List<Link> found in the topology
     */

    public static List<Link> getAllLinks(DataBroker db) {
        List<Link> linkList = new ArrayList<>();

        try {
            TopologyId topoId = new TopologyId("flow:1");
            InstanceIdentifier<Topology> nodesIid = InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, new TopologyKey(topoId)).toInstance();
            ReadOnlyTransaction nodesTransaction = db.newReadOnlyTransaction();
            CheckedFuture<Optional<Topology>, ReadFailedException> nodesFuture = nodesTransaction
                    .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
            Optional<Topology> nodesOptional = nodesFuture.checkedGet();

            if (nodesOptional != null && nodesOptional.isPresent())
                linkList = nodesOptional.get().getLink();
           // LOG.info("Nodelist: " + nodeList);

            return linkList;
        }

        catch (Exception e) {

            LOG.info("Node Fetching Failed");

            return linkList;
        }

       // return linkList;
    }


    /**
     * Returns the node from a given ip address
     */
    public static String getNodeFromIpAddress(String ipAddress, DataBroker db) {
        LOG.info("Finding node with IP address {}. ", ipAddress);
        try {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> nodeList = new ArrayList<>();
            InstanceIdentifier<Nodes> nodesIid = InstanceIdentifier.builder(
                    Nodes.class).build();
            ReadOnlyTransaction nodesTransaction = db.newReadOnlyTransaction();
            CheckedFuture<Optional<Nodes>, ReadFailedException> nodesFuture = nodesTransaction
                    .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
            Optional<Nodes> nodesOptional = Optional.absent();
            nodesOptional = nodesFuture.checkedGet();

            if (nodesOptional != null && nodesOptional.isPresent()) {
                nodeList = nodesOptional.get().getNode();
            }

            if (nodeList != null) {
                for (org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node n : nodeList) {
                    List<NodeConnector> nodeConnectors = n.getNodeConnector();
                    for (NodeConnector nc : nodeConnectors) {
                        AddressCapableNodeConnector acnc = nc
                                .getAugmentation(AddressCapableNodeConnector.class);
                        if (acnc != null && acnc.getAddresses() != null) {
                            // get address list from augmentation.
                            List<Addresses> addresses = acnc.getAddresses();
                            for (Addresses address : addresses) {

								/*LOG.info(
										"Checking address {} for connector {}",
										address.getIp().getIpv4Address()
												.getValue(), nc.getId()
												.getValue()); */
                                if (address.getIp().getIpv4Address().getValue()
                                        .equals(ipAddress))
                                    return n.getId().getValue();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.info("IP address reading failed");
        }
        return null;
    }


}
