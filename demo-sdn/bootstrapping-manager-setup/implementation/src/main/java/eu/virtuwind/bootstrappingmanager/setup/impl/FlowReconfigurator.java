package eu.virtuwind.bootstrappingmanager.setup.impl;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.HostUtilities;
import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.IPPrefAddrInfo;
import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.InitialFlowUtils;
import eu.virtuwind.pathmanager.impl.PathManager;
import eu.virtuwind.registryhandler.impl.BootstrappingRegistryImpl;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.setup.impl.rev150722.modules.module.configuration.SetupImpl;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingStatus;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.address.node.connector.Addresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.HostNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Thread.sleep;

/**
 * A class that implements the re-embedding of particular control flows to provide resilient bootstrapping.
 */
public class FlowReconfigurator implements Runnable, ClusteredDataTreeChangeListener<BootstrappingStatus> {

    private static final Logger LOG = LoggerFactory.getLogger(FlowReconfigurator.class);

    // SAL SERVICES
    private DataBroker dataBroker;
    private SalFlowService salFlowService;

    // TIMEOUTS
    private int waitingPeriod;

    // FLAGS
    private final boolean ARPING_REQUIRED = true;
    private static boolean arpingRunning = false;
    private static boolean isLeader = false;

    // OTHER
    private short flowTableId;
    private AtomicLong flowIdInc = new AtomicLong(3000);
    private static String  ARPING_SCRIPT_PATH;
    private static boolean isIpv6Enabled;
    private static boolean isICMPEnabled;
    private static int repeatReconfiguration = 0;

    private static ArrayList<String> ctlList;

    /**
     * Currently you can choose one of the two available approaches:
     * - Rohringer thesis: ARP, OF and C-C traffic rules are reembedded
     *      - OF resilient rules are provided in the following manner:
     *          find a unicast disjoint path for each controller from a switch (e.g. S-C1, S-C2, S-C3 -> 3 disjoint paths)
     *      - ARP and C-C rules are provided in the following manner:
     *          find two disjoint paths for each S-C and C-C pair
     *          (e.g. S-C1 -> 2 disjoint paths, S-C2 -> 2 disjoint paths, S-C3 -> 2 disjoint paths
     *              C1-C2 -> 2 disjoint paths, C1-C3 -> 2 disjoint paths, C2-C3 -> 2 disjoint paths)
     *
     * - This thesis: ARP, SSH, OF and C-C traffic rules are reembedded
     *
     *      - All resilient rules (ARP/OF/SSH/C-C) are provided in the following manner:
     *          find two disjoint paths for each S-C and C-C pair
     *          (e.g. S-C1 -> 2 disjoint paths, S-C2 -> 2 disjoint paths, S-C3 -> 2 disjoint paths
     *              C1-C2 -> 2 disjoint paths, C1-C3 -> 2 disjoint paths, C2-C3 -> 2 disjoint paths)
     *
     */
    public static final String ONEDISJOINTPATHFOREACHCONTROLLER = "ONE-DISJOINT-PATH-FOR-EACH-CONTROLLER";
    public static final String TWODISJOINTPATHSFOREACHCONTROLLER = "TWO-DISJOINT-PATHS-FOR-EACH-CONTROLLER";
    private static String flowReconfiguratorFlavour;


    static FlowReconfigurator flowReconf = null;

    /**
     * Default constructor for the flow reconfiguration
     * @param dataBroker
     * @param salFlowService
     * @param flowTableId
     * @param waitingTime
     */
    public FlowReconfigurator(DataBroker dataBroker, SalFlowService salFlowService, short flowTableId, int waitingTime) {
        LOG.info("Setting up a new FlowReconfigurator instance.");

        this.dataBroker = dataBroker;
        this.salFlowService = salFlowService;
        this.flowTableId = flowTableId;
        this.waitingPeriod = waitingTime;
        this.registerBootstrappingStatusListener();

        flowReconf = this;
    }

    public static void setCtlList(ArrayList<String> controllerList) {
        ctlList = controllerList;
    }

    public static FlowReconfigurator getInstance(String flowReconfiguratorFlavourChosen) {
        if(flowReconf != null) {
            flowReconfiguratorFlavour = flowReconfiguratorFlavourChosen;
            return flowReconf;
        } else {
            LOG.error("FlowReconfigurator must be initialized first using the default constructor!");
            return null;
        }
    }

    /**
     * Enables or disables ICMP traffic between controllers in the network
     * @param flag The boolean binary parameter.
     */
    public static void setICMPEnabled(boolean flag) {
        isICMPEnabled = flag;
    }

    /**
     * Enables or disables the mode for IPv6 default flow cofnigurations
     * @param arg The boolean binary parameter.
     */
    public static void setIPv6Enabled(boolean arg) {
        isIpv6Enabled = arg;
    }


    public static void setArpingPath(String path) {
        ARPING_SCRIPT_PATH = path;
    }

    /**
     * The executor instance for flow reembedding initiated after the STP
     * is disabled and some minimum time has passed.
     */
    public void run() {

        // Wait for reasonable time for STP to finish being disabled and topology fully reinitialized.
        try {
            LOG.info("Flow re-embedding thread (Re-)initiated!");
            sleep(waitingPeriod); // ms
        } catch (Exception e) {
            LOG.error("UNEXPECTED: Thread failed to sleep(). Returning.");
            return;
        }

        for(String ipAddr:ctlList)
            HostUtilities.isIPAvailableLocally(ipAddr);

        LOG.info("Waited reasonable amount of time for all network devices to " +
                "discover the full topology after disabling the STP;");

        // Update the status of bootstrapping procedure (notify other instances)
        BootstrappingRegistryImpl.getInstance()
                .writeBootstrappingStatusToDataStore(
                        new BootstrappingStatusBuilder().setCurrentStatus(BootstrappingStatus.CurrentStatus.PrepareControllerDiscovery)
                );

        /** Here we wait a bit to make sure the controllers were discovered and ARPs were punted **/
        /*
        try {
            sleep(2000);
        } catch (InterruptedException e) {
            LOG.error(e.getMessage());
        }
        */


        /**
         *  ######## Execute the path reconfiguration logic here ########
         */
        while (!allHostNodesSeen(ctlList)) {
            // loop
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // TODO: Global parameter block until the leader is available

        try {
            if (isLeader == true) { //Is current leader of the cluster topic?

                // Ensure that all nodes were previously seen before re-embedding the flows, blocks until so.
                LOG.info("##### Reembedding the flows in awesome manner #####");
                LOG.info("FlowReconfigurator flavour: {}", flowReconfiguratorFlavour);

                /** First reembed the switch-controller ARP flows **/
                List<Node> listOfOOfNodes = getOpenFlowNodes();
                // Get a list of all existing links
                List<Link> allLinks = getAllLinks(dataBroker);

                /** Now embed the switch-controller ARP channel flows **/
                for (Node ofNode : listOfOOfNodes) {
                    if (flowReconfiguratorFlavour.equals(ONEDISJOINTPATHFOREACHCONTROLLER)) {
                        LOG.info("Embedding the ARP flows for OpenFlow node: " + ofNode.getId().getValue() + " of total of " + listOfOOfNodes.size());
                    } else if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                        LOG.info("Embedding the ARP/OF/SSH flows for OpenFlow node: " + ofNode.getId().getValue() + " of total of " + listOfOOfNodes.size());
                    }
                    // And for every controller
                    for (String controllerIp : ctlList) {
                        // Find the unidirectional paths
                        // TODO: sometimes returns null examine that
                        List<List<Link>> foundResilientPaths = null;
                        //List<List<Link>> foundResilientPaths = PathManager.getInstance().findResilientBestEffortPath(new NodeId(ofNode.getId().getValue()), new NodeId(getHostNodeByIp(controllerIp).getAttachmentPoints().get(0).getCorrespondingTp().getValue()));
                        while (foundResilientPaths == null) {
                            LOG.info("Trying to find 2 disjoint paths for the pair {}:{}", ofNode.getId().getValue(), controllerIp);
                           foundResilientPaths = PathManager.getInstance().findResilientBestEffortPathWithTopologyRefreshed(new NodeId(ofNode.getId().getValue()), new NodeId(getHostNodeByIp(controllerIp).getAttachmentPoints().get(0).getCorrespondingTp().getValue()));
                           sleep(100);
                        }

                        // For debugging
                        printFoundPathsNicely(foundResilientPaths, ofNode.getId().getValue());

                        if (flowReconfiguratorFlavour.equals(ONEDISJOINTPATHFOREACHCONTROLLER)) {
                            LOG.info("Identify branching points for ARP connections starting at the source switch node" + ofNode.getId().getValue() + " and controller " + controllerIp);
                        } else if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                            LOG.info("Identify branching points for ARP/OF/SSH connections starting at the source switch node" + ofNode.getId().getValue() + " and controller " + controllerIp);
                        }
                        List<String> outputPortsAtSwitchSource = new ArrayList<String>();
                        if (foundResilientPaths.size() > 1)
                            for (List<Link> path : foundResilientPaths) {
                                if (!outputPortsAtSwitchSource.contains(path.get(0).getSource().getSourceTp().getValue()))
                                    outputPortsAtSwitchSource.add(path.get(0).getSource().getSourceTp().getValue());
                            }

                        if (flowReconfiguratorFlavour.equals(ONEDISJOINTPATHFOREACHCONTROLLER)) {
                            LOG.info("Identified branching points " + outputPortsAtSwitchSource.toString() + " for ARP connections starting at the source switch node.");
                        } else if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                            LOG.info("Identified branching points " + outputPortsAtSwitchSource.toString() + " for ARP/OF/SSH connections starting at the source switch node.");
                        }

                        for (List<Link> path : foundResilientPaths) {
                            List<String> foundLinksOnPathFw = new LinkedList<>();

                            for (Link l : path)
                                foundLinksOnPathFw.add(l.getLinkId().getValue());



                            // Generate the iterators. Start just after the last element.
                            ListIterator<String> pathLinksIterFw = foundLinksOnPathFw.listIterator(foundLinksOnPathFw.size());

                            // Iterate the forward path.
                            while (pathLinksIterFw.hasPrevious()) {
                                String currentPathLink = pathLinksIterFw.previous();

                                // For all the links
                                for (Link link : allLinks) {
                                    // if the link contains this particular link on path ID
                                    if (link.getLinkId().getValue().startsWith(currentPathLink) && !link.getLinkId().getValue().startsWith("host")) {

                                        // Find out the right node that is the source of the link
                                        Node nodeToEmbedRuleOn = null;
                                        for (Node node : InitialFlowUtils.getAllRealNodes(dataBroker)) {
                                            if (link.getSource().getSourceNode().getValue().equals(node.getId().getValue()))
                                                nodeToEmbedRuleOn = node; // The Node on which the flow rule is to be embedded
                                        }

                                        // If this is not a host node, embed the flow rule on it
                                        if (nodeToEmbedRuleOn != null && !nodeToEmbedRuleOn.getId().getValue().contains("host")) {
                                            InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(nodeToEmbedRuleOn);
                                            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                                            InstanceIdentifier<Flow> flowIdFw = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

                                            List<String> oPorts = new ArrayList<String>();
                                            if (nodeToEmbedRuleOn.getId().getValue().equals(path.get(0).getSource().getSourceNode().getValue()) && foundResilientPaths.size() > 1) {
                                                oPorts = outputPortsAtSwitchSource;
                                                LOG.info("Embedding a flow rule on an identified branching node " + nodeToEmbedRuleOn.getId().getValue() + ", outputting on: " + oPorts.toString());
                                            } else {
                                                // Use the controller IP as the destination IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the source
                                                oPorts.add(link.getSource().getSourceTp().getValue());
                                                LOG.info("Embedding a flow rule on an identified non-branching node " + nodeToEmbedRuleOn.getId().getValue() + ", outputting on: " + oPorts.toString());
                                            }

                                            if (flowReconfiguratorFlavour.equals(ONEDISJOINTPATHFOREACHCONTROLLER)) {
                                                LOG.info("Started embedding forward ARP/NDP flow on node:" + nodeToEmbedRuleOn.getId().getValue() + ", for controllerIp: " + controllerIp +
                                                        " and switch: " + nodeToEmbedRuleOn.getId().getValue() + ", for link: " + link.getLinkId().getValue());
                                            } else if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                                                LOG.info("Started embedding forward ARP/OF/SSH flow on node:" + nodeToEmbedRuleOn.getId().getValue() + ", for controllerIp: " + controllerIp +
                                                        " and switch: " + nodeToEmbedRuleOn.getId().getValue() + ", for link: " + link.getLinkId().getValue());
                                            }

                                            if (!isIpv6Enabled) {
                                                InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdFw,
                                                        InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 200, new Ipv4Prefix(controllerIp + "/32"),
                                                                new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"), oPorts));

                                                if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                                                    // SSH embedding
                                                    IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
                                                    srcPrefix.setIpv4Prefix(new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"));
                                                    IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo();
                                                    dstPrefix.setIpv4Prefix(new Ipv4Prefix(controllerIp + "/32"));

                                                    InstanceIdentifier<Flow> flowIdSSHSwToCtl = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                                                    InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdSSHSwToCtl,
                                                            InitialFlowUtils.createIPLayer4Flow(flowTableId, new PortNumber(22), srcPrefix,
                                                                    null, dstPrefix, 200, oPorts));
                                                    // OF embedding
                                                    Flow flowOFSwToController = InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcPrefix, new PortNumber(6633), dstPrefix, 200, oPorts);
                                                    InstanceIdentifier<Flow> flowIdOFSwToCtl = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                                                    InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdOFSwToCtl, flowOFSwToController);
                                                }
                                            }
                                            else if (isIpv6Enabled)
                                                continue; // TODO: Figure out how to handle the NDPs
                                        }
                                    }
                                }
                            }
                        }


                        if (flowReconfiguratorFlavour.equals(ONEDISJOINTPATHFOREACHCONTROLLER)) {
                            LOG.info("Identify branching points for ARP connections starting at the source controller node");
                        } else if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                            LOG.info("Identify branching points for ARP/OF/SSH connections starting at the source controller node");
                        }
                        // Identify branching points for connections starting at the source controller node
                        ArrayList<String> outputPortsAtControllerSource = new ArrayList<String>();
                        if (foundResilientPaths.size() > 1)
                            for (List<Link> path : foundResilientPaths) {
                                if (path.size() > 1) {
                                    if (!outputPortsAtControllerSource.contains(path.get(path.size() - 2).getDestination().getDestTp().getValue()))
                                        outputPortsAtControllerSource.add(path.get(path.size() - 2).getDestination().getDestTp().getValue());
                                }
                            }

                        for (List<Link> path : foundResilientPaths) {
                            List<String> foundLinksOnPathRv = new LinkedList<>();

                            for (Link l : path)
                                foundLinksOnPathRv.add(l.getLinkId().getValue());

                            // Get a list of all existing links - unnecessary
                            //List<Link> allLinks = getAllLinks(dataBroker);

                            // Generate the iterators. Start just after the last element.
                            ListIterator<String> pathLinksIterRv = foundLinksOnPathRv.listIterator();

                            // Iterate the forward path.
                            while (pathLinksIterRv.hasNext()) {
                                String currentPathLink = pathLinksIterRv.next();

                                // For all the links
                                for (Link link : allLinks) {

                                    // if the link contains this particular link on path ID
                                    if (link.getLinkId().getValue().startsWith(currentPathLink)) {
                                        if (flowReconfiguratorFlavour.equals(ONEDISJOINTPATHFOREACHCONTROLLER)) {
                                            LOG.info("ARP checking link: " + link.getLinkId().getValue());
                                        } else if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                                            LOG.info("ARP/OF/SSH checking link: " + link.getLinkId().getValue());
                                        }

                                        // Find out the right node that is the source of the link
                                        Node nodeToEmbedRuleOn = null;
                                        for (Node node : InitialFlowUtils.getAllRealNodes(dataBroker)) {
                                            if (link.getDestination().getDestNode().getValue().equals(node.getId().getValue()))
                                                nodeToEmbedRuleOn = node; // The Node on which the flow rule is to be embedded
                                        }

                                        // If this is not a host node, embed the flow rule on it
                                        if (nodeToEmbedRuleOn != null && !nodeToEmbedRuleOn.getId().getValue().contains("host")) {
                                            InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(nodeToEmbedRuleOn);
                                            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                                            InstanceIdentifier<Flow> flowIdRv = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

                                            List<String> oPorts = new ArrayList<String>();
                                            if (foundResilientPaths.size() > 1 && path.size() > 1 && nodeToEmbedRuleOn.getId().getValue().equals(path.get(path.size() - 2).getDestination().getDestNode().getValue())) {
                                                oPorts = outputPortsAtControllerSource;
                                                if (flowReconfiguratorFlavour.equals(ONEDISJOINTPATHFOREACHCONTROLLER)) {
                                                    LOG.info("Embedding a controller-switch ARP flow rule on an identified branching node " + nodeToEmbedRuleOn.getId().getValue() + ". Outputting on ports: " + oPorts.toString());
                                                } else if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                                                    LOG.info("Embedding a controller-switch ARP/OF/SSH flow rule on an identified branching node " + nodeToEmbedRuleOn.getId().getValue() + ". Outputting on ports: " + oPorts.toString());
                                                }
                                            } else {
                                                // Use the controller IP as the destination IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the source
                                                oPorts.add(link.getDestination().getDestTp().getValue());
                                                if (flowReconfiguratorFlavour.equals(ONEDISJOINTPATHFOREACHCONTROLLER)) {
                                                    LOG.info("Embedding a controller-switch ARP flow rule on an identified non-branching node " + nodeToEmbedRuleOn.getId().getValue() + ". Outputting on ports: " + oPorts.toString());
                                                } else if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                                                    LOG.info("Embedding a controller-switch ARP/OF/SSH flow rule on an identified non-branching node " + nodeToEmbedRuleOn.getId().getValue() + ". Outputting on ports: " + oPorts.toString());
                                                }
                                            }

                                            if (flowReconfiguratorFlavour.equals(ONEDISJOINTPATHFOREACHCONTROLLER)) {
                                                LOG.info("Started embedding reverse ARP flow on node: " + nodeToEmbedRuleOn.getId().getValue() + ", controllerIp: " + controllerIp +
                                                        ", switch: " + ofNode.getId().getValue() + ", and link: " + link.getLinkId());
                                            } else if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                                                LOG.info("Started embedding reverse ARP/OF/SSH flow on node: " + nodeToEmbedRuleOn.getId().getValue() + ", controllerIp: " + controllerIp +
                                                        ", switch: " + ofNode.getId().getValue() + ", and link: " + link.getLinkId());
                                            }

                                            if (!isIpv6Enabled) {
                                                InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdRv,
                                                        InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 201, new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"),
                                                                new Ipv4Prefix(controllerIp + "/32"), oPorts));

                                                if (flowReconfiguratorFlavour.equals(TWODISJOINTPATHSFOREACHCONTROLLER)) {
                                                    // SSH embedding
                                                    IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
                                                    srcPrefix.setIpv4Prefix(new Ipv4Prefix(controllerIp + "/32"));
                                                    IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo();
                                                    dstPrefix.setIpv4Prefix(new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"));

                                                    InstanceIdentifier<Flow> flowIdSSHCtlToSw = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                                                    InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdSSHCtlToSw,
                                                            InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcPrefix,
                                                                    new PortNumber(22), dstPrefix, 201, oPorts));
                                                    // OF embedding
                                                    Flow flowOFCtlToSw = InitialFlowUtils.createIPLayer4Flow(flowTableId, new PortNumber(6633), srcPrefix, null, dstPrefix, 201, oPorts);
                                                    InstanceIdentifier<Flow> flowIdOFCtlToSw = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                                                    InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdOFCtlToSw, flowOFCtlToSw);
                                                }
                                            }
                                            else if (isIpv6Enabled)
                                                continue; // TODO: Figure out how to handle the NDPs
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LOG.info("Finally, add an entry on the switch itself to forward the packets to its local interface for node " + ofNode.getId().getValue() + " and IP address " + ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue());
                    // Use the controller IP as the source IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the destination
                    InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(ofNode);
                    InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                    InstanceIdentifier<Flow> flowIdLocal = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

                    List<String> oPorts = new ArrayList<String>();
                    oPorts.add("local");
                    if (!isIpv6Enabled) {
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdLocal,
                                InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 201,
                                        new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"),
                                        null, oPorts));

                        // SSH embedding
                        IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
                        // technically src ctl prefix not necessary; but suitable for measurements
                        //srcPrefix.setIpv4Prefix(new Ipv4Prefix(con + "/32"));
                        IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo();
                        dstPrefix.setIpv4Prefix(new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"));

                        InstanceIdentifier<Flow> flowIdSSHSwLocal = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdSSHSwLocal,
                                InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcPrefix, new PortNumber(22), dstPrefix, 201, oPorts));
                        // OF embedding
                        Flow flowOFSwLocal = InitialFlowUtils.createIPLayer4Flow(flowTableId, new PortNumber(6633), srcPrefix, null , dstPrefix, 201, oPorts);
                        InstanceIdentifier<Flow> flowIdOFSwLocal = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdOFSwLocal, flowOFSwLocal);
                    }
                    else if (isIpv6Enabled)
                        continue; // TODO: Figure out how to handle the NDPs
                }

                /**
                 * Rohringer approach -> disjoint path for each of the available controllers
                 */
                if (flowReconfiguratorFlavour.equals(ONEDISJOINTPATHFOREACHCONTROLLER)) {
                    /** Now reembedd the OpenFlow channel flows **/
                    for (Node ofNode : listOfOOfNodes) {
                        LOG.info("Embedding the OpenFlow control channel flows for OpenFlow node: " + ofNode.getId().getValue() + " of total of " + listOfOOfNodes.size());
                        // And for every controller
                        List<NodeId> listOfCtlNodeIds = new ArrayList<>();
                        for (String controllerIp : ctlList) { // For each controller
                            // Find the unidirectional paths // TODO: Modify this to get instead two disjoint path to two controllers from a single node
                            NodeId controllerNodeId = new NodeId(getHostNodeByIp(controllerIp).getAttachmentPoints().get(0).getCorrespondingTp().getValue());
                            listOfCtlNodeIds.add(controllerNodeId);
                        }

                        // Find the disjoint paths to multiple controllers
                        List<List<Link>> foundResilientPaths = PathManager.getInstance().findBestEffortDisjointPaths(new NodeId(ofNode.getId().getValue()), listOfCtlNodeIds);

                        HashMap<NodeId, List<Link>> ctlPathMapping = new HashMap<>();
                        HashMap<String, List<Link>> ipAddrPathMapping = new HashMap<>();

                        for (int i = 0; i < foundResilientPaths.size(); i++) {
                            ctlPathMapping.put(listOfCtlNodeIds.get(i), foundResilientPaths.get(i));
                            ipAddrPathMapping.put(ctlList.get(i), foundResilientPaths.get(i));
                        }

                        // Get a list of all existing links - unnecessary
                        //List<Link> allLinks = getAllLinks(dataBroker);

                        for (String controllerNodeIp : ipAddrPathMapping.keySet()) {
                            List<Link> path = ipAddrPathMapping.get(controllerNodeIp);
                            // Generate the iterators. Start just after the last element.
                            ListIterator<Link> pathLinksIterFw = path.listIterator(path.size());
                            // Iterate in reverse the forward path.
                            while (pathLinksIterFw.hasPrevious()) {
                                String currentPathLink = pathLinksIterFw.previous().getLinkId().getValue();

                                // For all the links
                                for (Link link : allLinks) {
                                    // if the link contains this particular link on path ID
                                    if (link.getLinkId().getValue().startsWith(currentPathLink) && !link.getLinkId().getValue().startsWith("host")) {

                                        // Find out the right node that is the source of the link
                                        Node nodeToEmbedRuleOn = null;
                                        for (Node node : InitialFlowUtils.getAllRealNodes(dataBroker)) {
                                            if (link.getSource().getSourceNode().getValue().equals(node.getId().getValue()))
                                                nodeToEmbedRuleOn = node; // The Node on which the flow rule is to be embedded
                                        }

                                        // If this is not a host node, embed the flow rule on it
                                        if (nodeToEmbedRuleOn != null && !nodeToEmbedRuleOn.getId().getValue().contains("host")) {
                                            InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(nodeToEmbedRuleOn);
                                            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                                            InstanceIdentifier<Flow> flowIdFw = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

                                            LOG.info("Started embedding forward OpenFlow flow on node:" + nodeToEmbedRuleOn.getId().getValue() + ", controllerIp:" + controllerNodeIp +
                                                    ", switch:" + nodeToEmbedRuleOn.getId().getValue() + ", link:" + link.getLinkId().getValue());


                                            List<String> oPorts = new ArrayList<String>();
//                                        if (nodeToEmbedRuleOn.getId().getValue().equals(ofNode.getId().getValue()) && foundResilientPaths.size() > 1) {
//                                            LOG.info("For path " + path.toString() + " identified a branching node: " + nodeToEmbedRuleOn);
//                                            oPorts = outputPortsAtSwitchSource;
//                                        } else {
                                            // Use the controller IP as the destination IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the source
                                            LOG.info("For path " + path.toString() + " identified a non-branching node: " + nodeToEmbedRuleOn.getId().getValue());
                                            oPorts.add(link.getSource().getSourceTp().getValue());
//                                        }

                                            if (!isIpv6Enabled) {
                                                IPPrefAddrInfo srcIpAddrInfo = new IPPrefAddrInfo();
                                                srcIpAddrInfo.setIpv4Prefix(new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"));
                                                IPPrefAddrInfo dstIpAddrInfo = new IPPrefAddrInfo();
                                                dstIpAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerNodeIp + "/32"));

                                                InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdFw,
                                                        InitialFlowUtils.createIPLayer4Flow(
                                                                flowTableId, null, srcIpAddrInfo,
                                                                new PortNumber(6633), dstIpAddrInfo, 200, oPorts
                                                        )
                                                );
                                            } else if (isIpv6Enabled) {
                                                IPPrefAddrInfo srcIpAddrInfo = new IPPrefAddrInfo();
                                                srcIpAddrInfo.setIpv6Prefix(new Ipv6Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv6Address().getValue() + "/128"));
                                                IPPrefAddrInfo dstIpAddrInfo = new IPPrefAddrInfo();
                                                dstIpAddrInfo.setIpv6Prefix(new Ipv6Prefix(controllerNodeIp + "/128"));

                                                InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdFw,
                                                        InitialFlowUtils.createIPLayer4Flow(
                                                                flowTableId, null, srcIpAddrInfo,
                                                                new PortNumber(6633), dstIpAddrInfo, 200, oPorts
                                                        )
                                                );
                                            }
                                        }
                                    }
                                }
                            }
                            // Embed the same path in reverse
                            ListIterator<Link> pathLinksIterRv = path.listIterator();
                            // Iterate in reverse the backwards path.
                            while (pathLinksIterRv.hasNext()) {
                                String currentPathLink = pathLinksIterRv.next().getLinkId().getValue();

                                // For all the links
                                for (Link link : allLinks) {
                                    // if the link contains this particular link on path ID
                                    if (link.getLinkId().getValue().startsWith(currentPathLink)) {
                                        LOG.info("OF Checking link: " + link.getLinkId().getValue());

                                        // Find out the right node that is the source of the link
                                        Node nodeToEmbedRuleOn = null;
                                        for (Node node : InitialFlowUtils.getAllRealNodes(dataBroker)) {
                                            if (link.getDestination().getDestNode().getValue().equals(node.getId().getValue()))
                                                nodeToEmbedRuleOn = node; // The Node on which the flow rule is to be embedded
                                        }

                                        // If this is not a host node, embed the flow rule on it
                                        if (nodeToEmbedRuleOn != null && !nodeToEmbedRuleOn.getId().getValue().contains("host")) {
                                            InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(nodeToEmbedRuleOn);
                                            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                                            InstanceIdentifier<Flow> flowIdRv = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

                                            LOG.info("Started embedding reverse OpenFlow flow on node:" + nodeToEmbedRuleOn.getId().getValue() + ", controllerIp:" + controllerNodeIp +
                                                    ", switch:" + ofNode.getId().getValue() + ", link:" + link.getLinkId().getValue());

                                            List<String> oPorts = new ArrayList<String>();
                                            oPorts.add(link.getDestination().getDestTp().getValue());

                                            if (!isIpv6Enabled) {
                                                IPPrefAddrInfo srcIpAddrInfo = new IPPrefAddrInfo();
                                                srcIpAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerNodeIp + "/32"));
                                                IPPrefAddrInfo dstIpAddrInfo = new IPPrefAddrInfo();
                                                dstIpAddrInfo.setIpv4Prefix(new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"));

                                                // Use the controller IP as the source IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the destination
                                                InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdRv,
                                                        InitialFlowUtils.createIPLayer4Flow(
                                                                flowTableId, new PortNumber(6633), srcIpAddrInfo,
                                                                null, dstIpAddrInfo, 200, oPorts
                                                        )
                                                );
                                            } else if (isIpv6Enabled) {
                                                IPPrefAddrInfo srcIpAddrInfo = new IPPrefAddrInfo();
                                                srcIpAddrInfo.setIpv6Prefix(new Ipv6Prefix(controllerNodeIp + "/128"));
                                                IPPrefAddrInfo dstIpAddrInfo = new IPPrefAddrInfo();
                                                dstIpAddrInfo.setIpv6Prefix(new Ipv6Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv6Address().getValue() + "/128"));

                                                // Use the controller IP as the source IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the destination
                                                InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdRv,
                                                        InitialFlowUtils.createIPLayer4Flow(
                                                                flowTableId, new PortNumber(6633), srcIpAddrInfo,
                                                                null, dstIpAddrInfo, 200, oPorts
                                                        )
                                                );
                                            }
                                        }
                                    }
                                }
                            }
                            // Finally, add an entry on the switch itself to forward the packets to its local interface
                            // Use the controller IP as the source IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the destination
                            InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(ofNode);
                            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                            InstanceIdentifier<Flow> flowIdLocal = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

                            List<String> oPorts = new ArrayList<String>();
                            oPorts.add("local");

                            if (!isIpv6Enabled) {
                                IPPrefAddrInfo srcIpAddrInfo = new IPPrefAddrInfo();
                                srcIpAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerNodeIp + "/32"));
                                IPPrefAddrInfo dstIpAddrInfo = new IPPrefAddrInfo();
                                dstIpAddrInfo.setIpv4Prefix(new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"));

                                InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdLocal,
                                        InitialFlowUtils.createIPLayer4Flow(
                                                flowTableId, new PortNumber(6633),
                                                srcIpAddrInfo, null, dstIpAddrInfo,
                                                200, oPorts
                                        )
                                );
                            } else if (isIpv6Enabled) {
                                IPPrefAddrInfo srcIpAddrInfo = new IPPrefAddrInfo();
                                srcIpAddrInfo.setIpv6Prefix(new Ipv6Prefix(controllerNodeIp + "/128"));
                                IPPrefAddrInfo dstIpAddrInfo = new IPPrefAddrInfo();
                                dstIpAddrInfo.setIpv6Prefix(new Ipv6Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv6Address().getValue() + "/128"));

                                InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdLocal,
                                        InitialFlowUtils.createIPLayer4Flow(
                                                flowTableId, new PortNumber(6633),
                                                srcIpAddrInfo, null, dstIpAddrInfo,
                                                200, oPorts
                                        )
                                );
                            }
                        }

                        /*
                        // Dummy rule for measurements - DEBUGGING
                        InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(ofNode);
                        InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                        InstanceIdentifier<Flow> flowIdDummy = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                        IPPrefAddrInfo srcIpAddrInfo = new IPPrefAddrInfo();
                        srcIpAddrInfo.setIpv4Prefix(new Ipv4Prefix("245.245.245.245/32"));
                        IPPrefAddrInfo dstIpAddrInfo = new IPPrefAddrInfo();
                        dstIpAddrInfo.setIpv4Prefix(new Ipv4Prefix("232.232.232.232/32"));
                        List<String> oports = new ArrayList<>();
                        oports.add("controller");
                        LOG.info("Before the DUMMY rule!");
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdDummy,
                                InitialFlowUtils.createIPLayer4Flow(
                                        flowTableId, new PortNumber(8888),
                                        srcIpAddrInfo, new PortNumber(8888), dstIpAddrInfo,
                                        500, oports
                                )
                        );
                        LOG.info("After the DUMMY rule!");
                        */
                    }
                }

                /** Now embed the controller-controller OpenFlow channel flows **/
                for (int i = 0; i < ctlList.size(); i++) {
                    String controllerIp1 = ctlList.get(i);
                    // And for every controller
                    for (int j = i + 1; j < ctlList.size(); j++) {
                        String controllerIp2 = ctlList.get(j);

                        // For each controller that is not the source controller
                        if (!controllerIp1.equals(controllerIp2)) {
                            LOG.info("Embedding the OpenFlow control channel flows for controllers: " + controllerIp1 + " to " + controllerIp2);

                            // Find the unidirectional paths
                            List<List<Link>> foundResilientPaths = PathManager.getInstance().findResilientBestEffortPath(new NodeId(getHostNodeByIp(controllerIp1).getAttachmentPoints().get(0).getCorrespondingTp().getValue()), new NodeId(getHostNodeByIp(controllerIp2).getAttachmentPoints().get(0).getCorrespondingTp().getValue()));

                            // Identify branching points for connections starting at the source switch node
                            List<String> outputPortsAtControllerSource = new ArrayList<String>();
                            if (foundResilientPaths.size() > 1)
                                for (List<Link> path : foundResilientPaths) {
                                    if (!outputPortsAtControllerSource.contains(path.get(1).getSource().getSourceTp().getValue()))
                                        outputPortsAtControllerSource.add(path.get(1).getSource().getSourceTp().getValue());
                                }

                            for (List<Link> path : foundResilientPaths) {
                                List<String> foundLinksOnPathFw = new LinkedList<>();

                                for (Link l : path)
                                    foundLinksOnPathFw.add(l.getLinkId().getValue());

                                // Get a list of all existing links - unnecessary
                                //List<Link> allLinks = getAllLinks(dataBroker);

                                // Generate the iterators. Start just after the last element.
                                ListIterator<String> pathLinksIterFw = foundLinksOnPathFw.listIterator(foundLinksOnPathFw.size());

                                // Iterate the forward path.
                                while (pathLinksIterFw.hasPrevious()) {
                                    String currentPathLink = pathLinksIterFw.previous();

                                    // For all the links
                                    for (Link link : allLinks) {
                                        // if the link contains this particular link on path ID
                                        if (link.getLinkId().getValue().startsWith(currentPathLink) && !link.getLinkId().getValue().startsWith("host")) {

                                            // Find out the right node that is the source of the link
                                            Node nodeToEmbedRuleOn = null;
                                            for (Node node : InitialFlowUtils.getAllRealNodes(dataBroker)) {
                                                if (link.getSource().getSourceNode().getValue().equals(node.getId().getValue()))
                                                    nodeToEmbedRuleOn = node; // The Node on which the flow rule is to be embedded
                                            }

                                            // If this is not a host node, embed the flow rule on it
                                            if (nodeToEmbedRuleOn != null && !nodeToEmbedRuleOn.getId().getValue().contains("host")) {
                                                InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(nodeToEmbedRuleOn);
                                                InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                                                InstanceIdentifier<Flow> flowIdFwC2C = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                                                InstanceIdentifier<Flow> flowIdFwArp = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                                                InstanceIdentifier<Flow> flowIdFwICMP = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

                                                LOG.info("Started embedding forward C2C flow on node:" + nodeToEmbedRuleOn.getId().getValue() + ", source:" + controllerIp1 +
                                                        ", destination:" + controllerIp2 + ", link:" + link.getLinkId().getValue());

                                                List<String> oPorts = new ArrayList<String>();
                                                if (nodeToEmbedRuleOn.getId().getValue().equals(path.get(1).getSource().getSourceNode().getValue()) && foundResilientPaths.size() > 1) {
                                                    oPorts = outputPortsAtControllerSource;
                                                    LOG.info("For embedding of C2C flows identified a branching node: " + nodeToEmbedRuleOn.getId().getValue() + "; Outgoing ports " + oPorts.toString());
                                                } else {
                                                    // Use the controller IP as the destination IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the source
                                                    LOG.info("For embedding of C2C flows identified a non-branching node: " + nodeToEmbedRuleOn.getId().getValue());
                                                    oPorts.add(link.getSource().getSourceTp().getValue());
                                                }

                                                IPPrefAddrInfo srcIpPrefAddrInfo = new IPPrefAddrInfo();
                                                IPPrefAddrInfo dstIpPrefAddrInfo = new IPPrefAddrInfo();
                                                if (!isIpv6Enabled) {
                                                    srcIpPrefAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerIp1 + "/32"));
                                                    dstIpPrefAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerIp2 + "/32"));
                                                } else if (isIpv6Enabled) {
                                                    srcIpPrefAddrInfo.setIpv6Prefix(new Ipv6Prefix(controllerIp1 + "/128"));
                                                    dstIpPrefAddrInfo.setIpv6Prefix(new Ipv6Prefix(controllerIp2 + "/128"));
                                                }

                                                // Use the controller IP 2 as the destination IP field of the OF header
                                                InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdFwC2C,
                                                        InitialFlowUtils.createIPLayer4Flow(
                                                                flowTableId, null, srcIpPrefAddrInfo,
                                                                null, dstIpPrefAddrInfo,
                                                                200, oPorts
                                                        )
                                                );

                                                if (!isIpv6Enabled) {
                                                    InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdFwArp,
                                                            InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 201,
                                                                    new Ipv4Prefix(controllerIp2 + "/32"),
                                                                    new Ipv4Prefix(controllerIp1 + "/32"), oPorts));
                                                } else if (isIpv6Enabled) {
                                                    continue; // TODO: Figure out how to handle the NDPs for controller-controller connections
                                                }

                                                /**
                                                 * To measure round-trip delays between controllers (resilience not necessary)
                                                 */
                                                if (isICMPEnabled) {
                                                    InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdFwICMP,
                                                            InitialFlowUtils.createICMPFlowForIPPair(flowTableId, 500,
                                                                    srcIpPrefAddrInfo, dstIpPrefAddrInfo, oPorts));
                                                }
                                            }
                                        }
                                    }
                                }
                            }


                            // Now embed the reverse as well
                            outputPortsAtControllerSource = new ArrayList<String>();
                            if (foundResilientPaths.size() > 1)
                                for (List<Link> path : foundResilientPaths) {
                                    if (path.size() > 1) {
                                        if (!outputPortsAtControllerSource.contains(path.get(path.size() - 2).getDestination().getDestTp().getValue()))
                                            outputPortsAtControllerSource.add(path.get(path.size() - 2).getDestination().getDestTp().getValue());
                                    }
                                }

                            for (List<Link> path : foundResilientPaths) {
                                List<String> foundLinksOnPathRv = new LinkedList<>();

                                for (Link l : path)
                                    foundLinksOnPathRv.add(l.getLinkId().getValue());

                                // Get a list of all existing links - unnecessary
                                //List<Link> allLinks = getAllLinks(dataBroker);

                                // Generate the iterators. Start just after the last element.
                                ListIterator<String> pathLinksIterRv = foundLinksOnPathRv.listIterator();

                                // Iterate the forward path.
                                while (pathLinksIterRv.hasNext()) {
                                    String currentPathLink = pathLinksIterRv.next();

                                    // For all the links
                                    for (Link link : allLinks) {
                                        // if the link contains this particular link on path ID
                                        //if (link.getLinkId().getValue().startsWith(currentPathLink) && !link.getLinkId().getValue().startsWith("host")) {
                                        if (link.getLinkId().getValue().startsWith(currentPathLink)) {
                                            LOG.info("C2C Checking link: " + link.getLinkId().getValue());

                                            // Find out the right node that is the source of the link
                                            Node nodeToEmbedRuleOn = null;
                                            for (Node node : InitialFlowUtils.getAllRealNodes(dataBroker)) {
                                                if (link.getDestination().getDestNode().getValue().equals(node.getId().getValue()))
                                                    nodeToEmbedRuleOn = node; // The Node on which the flow rule is to be embedded
                                            }

                                            // If this is not a host node, embed the flow rule on it
                                            if (nodeToEmbedRuleOn != null && !nodeToEmbedRuleOn.getId().getValue().contains("host")) {
                                                InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(nodeToEmbedRuleOn);
                                                InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                                                InstanceIdentifier<Flow> flowIdRvC2C = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                                                InstanceIdentifier<Flow> flowIdArp = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                                                InstanceIdentifier<Flow> flowIdFwICMP = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());


                                                LOG.info("Started embedding reverse C2C flow on node:" + nodeToEmbedRuleOn.getId().getValue() + ", source:" + controllerIp2 +
                                                        ", destination:" + controllerIp1 + ", link:" + link.getLinkId().getValue());

                                                List<String> oPorts = new ArrayList<String>();
                                                if (path.size() > 1 && nodeToEmbedRuleOn.getId().getValue().equals(path.get(path.size() - 2).getDestination().getDestNode().getValue()) && foundResilientPaths.size() > 1) {
                                                    oPorts = outputPortsAtControllerSource;
                                                    LOG.info("For the C2C flow" + " identified a branching node: " + path.get(path.size() - 2).getDestination().getDestNode().getValue() + "; Outgoing ports " + oPorts.toString());
                                                } else {
                                                    // Use the controller IP as the destination IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the source
                                                    LOG.info("For the C2C flow" + " identified a non-branching node: " + nodeToEmbedRuleOn.getId().getValue());
                                                    oPorts.add(link.getDestination().getDestTp().getValue());
                                                }

                                                IPPrefAddrInfo srcAddrInfo = new IPPrefAddrInfo();
                                                IPPrefAddrInfo dstAddrInfo = new IPPrefAddrInfo();
                                                if (!isIpv6Enabled) {
                                                    srcAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerIp2 + "/32"));
                                                    dstAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerIp1 + "/32"));
                                                } else if (isIpv6Enabled) {
                                                    srcAddrInfo.setIpv6Prefix(new Ipv6Prefix(controllerIp2 + "/128"));
                                                    dstAddrInfo.setIpv6Prefix(new Ipv6Prefix(controllerIp1 + "/128"));
                                                }

                                                // Use the controller IP 2 as the destination IP field of the OF header
                                                InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdRvC2C,
                                                        InitialFlowUtils.createIPLayer4Flow(
                                                                flowTableId, null, srcAddrInfo,
                                                                null, dstAddrInfo, 200, oPorts
                                                        ));

                                                if (!isIpv6Enabled)
                                                    InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdArp,
                                                            InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 201,
                                                                    new Ipv4Prefix(controllerIp1 + "/32"),
                                                                    new Ipv4Prefix(controllerIp2 + "/32"), oPorts));
                                                else if (isIpv6Enabled) {
                                                    continue; // TODO: Figure out how to handle the NDPs for controller-controller connections
                                                }

                                                /**
                                                 * To measure round-trip delays between controllers (resilience not necessary)
                                                 */
                                                if (isICMPEnabled) {
                                                    InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdFwICMP,
                                                            InitialFlowUtils.createICMPFlowForIPPair(flowTableId, 500,
                                                                    srcAddrInfo, dstAddrInfo, oPorts));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TODO: ENSURE ARP HANDLING WORKS FOR ALL CONTROLLERS
                // TODO: ADD PER CONTROLLER DISJOINT SWITCH CONNECTION EMBEDDINGS
                InitialFlowUtils.removeInitialFlows(salFlowService);

            }
        } catch(Exception e){
            LOG.error("Flow re-emebedding failed!");
            LOG.error(e.getMessage());
            for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                LOG.error("Stack root cause trace -> {}", m);
            }
        }

        // Update the status of bootstrapping procedure (notify other instances)
        BootstrappingRegistryImpl.getInstance()
                .writeBootstrappingStatusToDataStore(
                        new BootstrappingStatusBuilder().setCurrentStatus(BootstrappingStatus.CurrentStatus.ReembeddingFinished)
                );

        /**
         * Due to the occasional above mentioned issues with the routing algorithm sometimes
         * the two disjoint paths are not found even though they exist in the topology.
         *
         * To be sure that the rules are going to be embedded one can execute FlowReconfigurator
         * multiple times.
         */
        if (repeatReconfiguration == 0) {
            // adding our tree rules necessary for the extension
            Thread networkExtensionManagerThread = new Thread(new NetworkExtensionManager());
            networkExtensionManagerThread.start();
        }

        if (repeatReconfiguration < 0) {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Thread t = new Thread(this);
            t.start();
            repeatReconfiguration++;
        }
    }

    /**
     * Method to get  all links from Toplogy. Links contain all the relevant information
     * @param db DataBroker from which toplogy links should be extracted
     * @return List<Link> found in the topology
     */

    public static List<Link> getAllLinks(DataBroker db) throws InterruptedException {
        List<Link> linkList = new ArrayList<>();
        boolean dataFetched = false;
        while (!dataFetched) {
            try {
                TopologyId topoId = new TopologyId("flow:1");
                InstanceIdentifier<Topology> nodesIid = InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, new TopologyKey(topoId)).toInstance();
                ReadOnlyTransaction nodesTransaction = db.newReadOnlyTransaction();
                CheckedFuture<Optional<Topology>, ReadFailedException> nodesFuture = nodesTransaction
                        .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
                Optional<Topology> nodesOptional = nodesFuture.checkedGet();

                if (nodesOptional != null && nodesOptional.isPresent()) {
                    linkList = nodesOptional.get().getLink();
                    dataFetched = true;
                }

            } catch (Exception e) {

                LOG.info("Node Fetching Failed with Exception: " + e.toString());
            }
            sleep(100);
        }

        return linkList;
    }

    public List<Node> getOpenFlowNodes() {
        // Obtain list of nodes
        List<Node> nodeList = new ArrayList<>();
        InstanceIdentifier<Nodes> nodesIid = InstanceIdentifier.builder(Nodes.class).build();
        ReadOnlyTransaction nodesTransaction = dataBroker.newReadOnlyTransaction();

        boolean notFetched = true;
        while(notFetched) {
            try {
                CheckedFuture<Optional<Nodes>, ReadFailedException> nodesFuture = nodesTransaction
                        .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
                Optional<Nodes> nodesOptional = nodesFuture.checkedGet();

                notFetched = false;

                if (nodesOptional != null && nodesOptional.isPresent()) {
                    nodeList = nodesOptional.get().getNode();

                    ArrayList<Node> openFlowNodeList = new ArrayList<>();
                    for(Node node:nodeList) {
                        if(node.getId().getValue().contains("openflow"))
                            openFlowNodeList.add(node);
                    }

                    LOG.info("Successfully fetched a list of OpenFlow nodes.");
                    return openFlowNodeList;
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch list of nodes with Exception: " + e.getMessage());
                notFetched = true;
            }
        }

        return null;
    }

    public Node getOpenFlowNodeByIp(String ipAddr) {
        // Obtain list of nodes
        List<Node> nodeList = new ArrayList<>();
        InstanceIdentifier<Nodes> nodesIid = InstanceIdentifier.builder(Nodes.class).build();
        ReadOnlyTransaction nodesTransaction = dataBroker.newReadOnlyTransaction();

        boolean notFetched = true;
        while(notFetched) {
            try {
                CheckedFuture<Optional<Nodes>, ReadFailedException> nodesFuture = nodesTransaction
                        .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
                Optional<Nodes> nodesOptional = nodesFuture.checkedGet();

                notFetched = false;

                if (nodesOptional != null && nodesOptional.isPresent()) {
                    nodeList = nodesOptional.get().getNode();
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch list of nodes with Exception " + e.getMessage());
                notFetched = true;
            }
        }

        LOG.info("Successfully fetched a list of nodes");

        FlowCapableNode flowCapableNode;
        String ip;

        for (int i = 0; i < nodeList.size(); i++) {
            try {
                flowCapableNode = nodeList.get(i).getAugmentation(FlowCapableNode.class);
                ip = flowCapableNode.getIpAddress().getIpv4Address().getValue();

                if(ip.contains(ipAddr)) {
                    LOG.info("Fetched IP address for node {}, IP = {}", nodeList.get(i).getId().getValue(), ip);
                    return nodeList.get(i);
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch IP address for node {}", nodeList.get(i).getId().getValue());
            }
        }

        return null;
    }

    public HostNode getHostNodeByIp(String ipAddr) {
        InstanceIdentifier<Topology> nwTopoIid =  InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(new TopologyId("flow:1")));
        ReadOnlyTransaction nodesTransaction = dataBroker.newReadOnlyTransaction();

        boolean notFetched = true;
        while(notFetched) {
            try {
                CheckedFuture<Optional<Topology>, ReadFailedException> nwTopo = nodesTransaction
                        .read(LogicalDatastoreType.OPERATIONAL, nwTopoIid);
                Optional<Topology> nwTopoOptional = nwTopo.checkedGet();

                notFetched = false;

                List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node> nodesList = null;
                if (nwTopoOptional != null && nwTopoOptional.isPresent())
                    nodesList = nwTopoOptional.get().getNode();

                for(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node node:nodesList){
                    if(node.getNodeId().getValue().contains("host")) {
                        HostNode hostNode = node.getAugmentation(HostNode.class);

                        for (Addresses listAddr : hostNode.getAddresses()) {
                            if (listAddr.getIp().getIpv4Address().toString().contains(ipAddr)) {
                                LOG.info("Successfully fetched the HostNode: " + hostNode.toString());
                                return hostNode;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch list of nodes with Exception " + e.getMessage());
                notFetched = true;
            }
        }

        return null;
    }


    /**
     * Evaluates the existence of all host nodes specified using the host IP addresses. BLOCKS until all nodes have been seen.
     * @param hostNodeIpAddresses The list of host node IP addresses.
     * @return True boolean in case all nodes are available in the "flow:1" Topology.
     */
    private boolean allHostNodesSeen(ArrayList<String> hostNodeIpAddresses) {
        LOG.info("Started looking for nodes: " + hostNodeIpAddresses.toString());

        for(String hostIp: hostNodeIpAddresses) {
            try {
                HostNode currentNode = null;

                LOG.info("Currently waiting on node: " + hostIp);

                currentNode = getHostNodeByIp(hostIp);

                if (currentNode == null) {
                    return false;
                }

                LOG.info("For IP " + hostIp + " identified the node " + currentNode.toString());
            } catch (Exception e) {
                LOG.error(e.getMessage());
                return false;
            }
        }

        LOG.info("All nodes identified: " + hostNodeIpAddresses.toString());

        return true;
    }

    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("flow-reconfig ownership change logged: " + ownershipChange);

        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the flow-reconfig leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the flow-reconfig follower.");
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

    public void registerBootstrappingStatusListener() {
        final DataTreeIdentifier<BootstrappingStatus> treeId = new DataTreeIdentifier<>(
                LogicalDatastoreType.OPERATIONAL, getObservedPath());
        dataBroker.registerDataTreeChangeListener(treeId, this);
        LOG.info("DHCP Status Listener registration success");
    }

    protected InstanceIdentifier<BootstrappingStatus> getObservedPath() {
        return InstanceIdentifier.create(BootstrappingStatus.class);
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<BootstrappingStatus>> collection) {
        LOG.info("Bootstrapping Status onDataTreeChanged {} ", collection );
        for (DataTreeModification<BootstrappingStatus> change : collection) {
            DataObjectModification<BootstrappingStatus> mod = change.getRootNode();
            switch (mod.getModificationType()) {
                case DELETE:
                    LOG.info(" Delete after data {} ", mod.getDataAfter());
                    break;
                case SUBTREE_MODIFIED:
                    break; // TODO: Fix at some point
                case WRITE:
                    if (mod.getDataBefore() == null) {
                        BootstrappingStatus fetchedDS = mod.getDataAfter();
                        LOG.info("BootstrappingStatus configuration updated remotely: {} ", fetchedDS);

                        if(fetchedDS.getCurrentStatus().equals(BootstrappingStatus.CurrentStatus.PrepareControllerDiscovery))
                        {
                            /**
                             *  Execute the arping script - once! so to allow for controller discovery - this should be done only after
                             // the switches have somewhat stabilized
                             */
                            if (ARPING_REQUIRED && !arpingRunning) {
                                try {
                                    // was a bug in the original implementation
                                    // since we listen on the ClusteredDataTreeChangeListener
                                    // it could happen that on some instances this part is execured before run()
                                    // which caused returnMyIfConfig to fail on some instances
                                    for(String ipAddr:ctlList)
                                        HostUtilities.isIPAvailableLocally(ipAddr);
                                    /*----------------------------------------------------------*/
                                    ArrayList<String> ipAddressesODL = new ArrayList();
                                    HostUtilities.InterfaceConfiguration myIfConfig =
                                            HostUtilities.returnMyIfConfig(ipAddressesODL);
                                    String target =
                                            new String(ARPING_SCRIPT_PATH + " " + myIfConfig.getIpAddr() + " " + myIfConfig.getIf());
                                    LOG.info("Executing the arping command: " + target);

                                    Runtime rt = Runtime.getRuntime();
                                    Process proc = rt.exec(target);
                                    proc.waitFor();
                                    StringBuffer result = new StringBuffer();
                                    BufferedReader reader =
                                            new BufferedReader(new InputStreamReader(proc.getInputStream()));

                                    arpingRunning = true; // set the flag
                                    String line = "";
                                    while ((line = reader.readLine()) != null) {
                                        result.append(line + "\n");
                                    }
                                } catch (Throwable t) {
                                    LOG.error(t.getMessage());
                                }
                            }
                        }
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unhandled node modification type " + mod.getModificationType());
            }
        }
    }

    /**
     * Prints provided paths
     *
     * @param paths
     */
    private void printFoundPathsNicely(List<List<Link>> paths, String nodeId){
        if (paths == null) {
            return;
        }
        int pathNum = 1;
        LOG.info("------------------------------------------------------------");
        for (List<Link> path: paths) {
            LOG.info("{} -> Path number {} contains the following links:", nodeId, pathNum);
            String output = "";
            for (Link link: path) {
                output += "SrcTp-> " + link.getSource().getSourceTp().getValue() + " DstTp-> " +
                        link.getDestination().getDestTp().getValue() + "\n";
            }
            LOG.info(output);
            pathNum++;
        }
        LOG.info("------------------------------------------------------------");
    }

}
