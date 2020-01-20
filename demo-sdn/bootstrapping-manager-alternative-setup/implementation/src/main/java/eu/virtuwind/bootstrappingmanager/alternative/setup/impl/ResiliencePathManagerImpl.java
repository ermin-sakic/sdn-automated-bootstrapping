package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.CheckedFuture;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.ControllerPair;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.IPPrefAddrInfo;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import eu.virtuwind.pathmanager.impl.PathManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.javatuples.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Thread.sleep;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 10.07.18
 */

/**
 * This implementation of ResiliencePathManager assumes and implements the following logic:
 *
 * For each communication pair, i.e. S-C and C-C, check first if there is an already embedded resilience for
 * the communication pair:
 *  YES -> skip further processing and return/use cached paths
 *  NO -> try to compute a new disjoint path pair based on the current discovered network topology,
 *        cache the found paths/path
 *
 * If an algorithm returns only one path, embed and cache it. Later check if two paths are available and compare the
 * algorithm result with the one that is cached for this particular communication pair. If the algorithm has
 * found two disjoint paths replace the old cached path with the new ones and embed them in the corresponding nodes.
 *
 * The implementation relies on the PathManager module routing capabilities in order to find disjoint paths
 * Current used algorithm is Dijkstra SPA with the disjoint constraint
 *
 */
public class ResiliencePathManagerImpl implements ResiliencePathManager<Pair<ResiliencePathManagerImpl.ResilienceStates, String>, String> {
    private static final Logger LOG = LoggerFactory.getLogger(ResiliencePathManagerImpl.class);

    // SAL SERVICES
    private static DataBroker dataBroker;
    private static SalFlowService salFlowService;

    // TOPOLOGY ID
    private static String topologyId;

    // CONTROLLERS' IP LIST
    private static ArrayList<String> ctlList;

    // EMBEDDED PATHS CACHES
    private static HashMap<String, List<List<Link>>> embeddedS2CPathsCache = new HashMap<>();
    private static HashMap<ControllerPair, List<List<Link>>> embeddedC2CPathsCache = new HashMap<>();

    // LIST OD COMMUNICATION PAIRS READY TO BE UPDATED
    private List<String> readyForResilienceUpdate = new LinkedList<>();
    private List<ControllerPair> controllersReadyForResilienceUpdate = new LinkedList<>();

    // All unique controller pair possibilities
    private static List<ControllerPair> controllerPairs = new ArrayList<>();

    // OTHER
    private static short flowTableId;
    private AtomicLong flowIdInc = new AtomicLong(3000);

    // FOR MEASUREMENTS
    private static final Map<String,String> controllerRecognizeCookies;
    static
    {
        controllerRecognizeCookies = new HashMap<String, String>();
        controllerRecognizeCookies.put("10.10.0.101", "aaaaa");
        controllerRecognizeCookies.put("10.10.0.102", "bbbbb");
        controllerRecognizeCookies.put("10.10.0.103", "ccccc");
    }


    // ENUM codes for resilience states
    public enum ResilienceStates { NO_RESILIENCE, RESILIENCE_PROVIDED_NOW, RESILIENCE_PROVIDED_BEFORE }

    public ResiliencePathManagerImpl() {
        LOG.info("ResiliencePathManagerImpl object created");
    }

    public static void setSalFlowService(SalFlowService salFlowService) {
        ResiliencePathManagerImpl.salFlowService = salFlowService;
    }

    public static void setFlowTableId(short flowTableId) {
        ResiliencePathManagerImpl.flowTableId = flowTableId;
    }

    public static void setDataBroker(DataBroker dataBroker) {
        ResiliencePathManagerImpl.dataBroker = dataBroker;
    }

    public static String getTopologyId() {
        return topologyId;
    }

    public static void setTopologyId(String topologyId) {
        ResiliencePathManagerImpl.topologyId = topologyId;
    }


    public static ArrayList<String> getCtlList() {
        return ctlList;
    }

    public static void setCtlList(ArrayList<String> ctlList) {
        ResiliencePathManagerImpl.ctlList = ctlList;
    }


    @Override
    public List<List<Link>> get2DisjointPathsBetweenNodes(NodeId node1, NodeId node2) {
        // retrieve the 2 max disjoint paths between this ofNode and the controller
        List<List<Link>> foundResilientPaths = null;
        LOG.debug("Computing disjoint paths for the pair {}-{}", node1.getValue(), node2.getValue());
        synchronized (this) { // there are some errors when 2 threads simultaneously enter this part of the code
            while (foundResilientPaths == null) {
                LOG.info("Waiting for the PathManager to provide us with the computed path(s)");
                foundResilientPaths = PathManager.getInstance().findResilientBestEffortPathWithTopologyRefreshed(node1, node2);
                sleep_some_time(100);
            }
        }
        return  foundResilientPaths;
    }

    @Override
    public Pair<ResilienceStates, String> updateResilientPathsBetweenS2CNodes(NodeId node1, NodeId node2) {

        String message;

        LOG.info("Update for the S-C pair: {}-{}", node1.getValue(), node2.getValue());

        String ofSwitch, controller;
        if (node1.getValue().contains("openflow")) {
            ofSwitch = node1.getValue();
            controller = node2.getValue();
        } else {
            ofSwitch = node2.getValue();
            controller = node1.getValue();
        }

        String communicationPair = giveMeProperS2CPairName(node1, node2);


        if (embeddedS2CPathsCache.containsKey(communicationPair)) {
            LOG.info("Communication pair: {} has already been examined -> Consulting cache to decide what to do next", communicationPair);
            if (embeddedS2CPathsCache.get(communicationPair).size() == 2) {
                message = "Communication pair: " + communicationPair + " already provided with the resilience";
                LOG.info(message);
                return new Pair<>(ResilienceStates.RESILIENCE_PROVIDED_BEFORE, message);
            } else {
                LOG.info("Communication pair: {} has only one unicast path embedded -> Try to find another one", communicationPair);
                // compute again the pair and check if size is bigger than 2 check for uniqueness
                // retrieve the 2 max disjoint paths between this ofNode and the controller
                List<List<Link>> foundResilientPaths = null;
                foundResilientPaths = get2DisjointPathsBetweenNodes(node1, node2);
                if (!checkIfPathsEqual(foundResilientPaths) && foundResilientPaths.size() == 2) {
                    embeddedS2CPathsCache.put(communicationPair, foundResilientPaths);
                    readyForResilienceUpdate.add(communicationPair);
                    message = "The new resilience examination of the communication pair " + communicationPair + " has found 2 disjoint paths";
                    LOG.info(message);
                    LOG.info("Found paths:");
                    printFoundPathsNicely(foundResilientPaths);
                    return new Pair<>(ResilienceStates.RESILIENCE_PROVIDED_NOW, message);
                } else {
                    message = "The new resilience examination of the communication pair " + communicationPair + " could not find 2 disjoint paths. Leaving old state in the cache.";
                    LOG.info(message);
                    return new Pair<>(ResilienceStates.NO_RESILIENCE, message);
                }
            }
        } else {

            LOG.info("Examining resilience for the first time for pair {}", communicationPair);
            // retrieve the 2 max disjoint paths between this ofNode and the controller
            List<List<Link>> foundResilientPaths = null;
            foundResilientPaths = get2DisjointPathsBetweenNodes(node1, node2);

            if (!checkIfPathsEqual(foundResilientPaths)) { // always 2 if they do not exist it returns the same paths
                message = "The algorithm has found more than one path between the node " + ofSwitch + " and the controller " + controller;
                LOG.info(message);
                LOG.info("Found paths:");
                printFoundPathsNicely(foundResilientPaths);
                // cache the found paths
                embeddedS2CPathsCache.put(communicationPair, foundResilientPaths);
                readyForResilienceUpdate.add(communicationPair);
                return new Pair<>(ResilienceStates.RESILIENCE_PROVIDED_NOW, message);
            } else {
                message = "The algorithm has found only one path between the node " + ofSwitch + " and the controller " + controller;
                LOG.info(message);
                // Remove the duplicate
                foundResilientPaths.remove(1);
                LOG.info("Found path:");
                printFoundPathsNicely(foundResilientPaths);
                // cache the found path
                embeddedS2CPathsCache.put(communicationPair, foundResilientPaths);
                readyForResilienceUpdate.add(communicationPair);
                return new Pair<>(ResilienceStates.NO_RESILIENCE, message);

            }

        }
    }

    @Override
    public String embedResilientPathsBetweenS2CNodes(NodeId node1, NodeId node2) {

        String communicationPair = giveMeProperS2CPairName(node1, node2);
        LOG.debug("embedResilientPathsBetweenS2CNodes: {}", communicationPair);
        List<List<Link>> foundResilientPaths;
        String message;
        if (readyForResilienceUpdate.contains(communicationPair)){
            foundResilientPaths = embeddedS2CPathsCache.get(communicationPair);
            message = "Embedding resilient paths for the communication pair " + communicationPair;
            LOG.info(message);

            String ofSwitch, controller;
            if (node1.getValue().contains("openflow")) {
                ofSwitch = node1.getValue();
                controller = node2.getValue();
            } else {
                ofSwitch = node2.getValue();
                controller = node1.getValue();
            }
            String ctlIpAddress = null;
            while (ctlIpAddress == null) {
                ctlIpAddress = HostUtilities.getHostNodeIpById(controller);
                LOG.warn("Controller IP address for the node {} still not available in the DS", controller);
                sleep_some_time(100);
            }
            LOG.info("Successfully fetched an IP address from the DS for {}: {}", controller, ctlIpAddress);
            Node ofNode = getNodeByNodeId(ofSwitch);

            LOG.info("Identify branching points for ARP/SSH/OF connections starting at the source switch node " + ofSwitch + " and controller " + controller);

            List<String> outputPortsAtSwitchSource = new ArrayList<String>();

            if (foundResilientPaths.size() > 1) {
                for (List<Link> path : foundResilientPaths) {
                    if (!outputPortsAtSwitchSource.contains(path.get(0).getSource().getSourceTp().getValue()))
                        outputPortsAtSwitchSource.add(path.get(0).getSource().getSourceTp().getValue());
                }
                LOG.info("Identified branching points " + outputPortsAtSwitchSource.toString() + " for ARP/SSH/OF connections starting at the source switch node.");

            } else {
                LOG.info("No branching points found for ARP/SSH/OF connections starting at the source switch node {} and controller {}",
                        ofSwitch, controller);
            }

            // C->S TRAFFIC e.g. C->S->S->S

            // Get a list of all existing nodes
            List<Node> allNodes = InitialFlowUtils.getAllRealNodes(dataBroker);

            // for each path
            for (List<Link> path : foundResilientPaths) {

                // Generate the iterators. Start just after the last element, e.g. starting from the node closest to the controller
                ListIterator<Link> pathLinksIterFw = path.listIterator(path.size());

                // Iterate the forward path.
                while (pathLinksIterFw.hasPrevious()) {
                    Link currentPathLink = pathLinksIterFw.previous();

                    // Find out the right node that is the source of the link
                    Node nodeToEmbedRuleOn = null;
                    for (Node node : allNodes) {
                        if (currentPathLink.getSource().getSourceNode().getValue().equals(node.getId().getValue()))
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
                            oPorts.add(currentPathLink.getSource().getSourceTp().getValue());
                            LOG.info("Embedding a flow rule on an identified non-branching node " + nodeToEmbedRuleOn.getId().getValue() + ", outputting on: " + oPorts.toString());
                        }

                        LOG.info("Started embedding forward ARP/SSH/OF flow on node: " + nodeToEmbedRuleOn.getId().getValue() + ", controllerIp: " + ctlIpAddress +
                                ", switch: " + ofNode.getId().getValue() + ", and link: " + currentPathLink.getLinkId());

                        // ARP embedding
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdFw,
                                InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 200, new Ipv4Prefix(ctlIpAddress + "/32"),
                                        new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"), oPorts));
                        // SSH embedding
                        IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
                        srcPrefix.setIpv4Prefix(new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"));
                        IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo();
                        dstPrefix.setIpv4Prefix(new Ipv4Prefix(ctlIpAddress + "/32"));

                        InstanceIdentifier<Flow> flowIdSSHSwToCtl = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdSSHSwToCtl,
                                InitialFlowUtils.createIPLayer4Flow(flowTableId,  new PortNumber(22), srcPrefix,
                                        null, dstPrefix, 200, oPorts));
                        // OF embedding
                        Flow flowOFSwToController = InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcPrefix, new PortNumber(6633), dstPrefix, 200, oPorts);
                        InstanceIdentifier<Flow> flowIdOFSwToCtl = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdOFSwToCtl, flowOFSwToController);

                    }

                }
            }

            LOG.info("Forward embedding finished");

            // REVERSE PATH EMBEDDING S->C TRAFFIC e.g. S->S->S->C

            LOG.info("Identify branching points for ARP/SSH/OF connections starting at the source controller node");
            // Identify branching points for connections starting at the source controller node
            ArrayList<String> outputPortsAtControllerSource = new ArrayList<String>();
            if (foundResilientPaths.size() > 1) {
                for (List<Link> path : foundResilientPaths) {
                    if (path.size() > 1) {
                        if (!outputPortsAtControllerSource.contains(path.get(path.size() - 2).getDestination().getDestTp().getValue()))
                            outputPortsAtControllerSource.add(path.get(path.size() - 2).getDestination().getDestTp().getValue());
                    }
                }
                LOG.info("Identified branching points " + outputPortsAtSwitchSource.toString() + " for ARP/SSH/OF connections starting at the source controller node.");

            } else {
                LOG.info("No branching points found for ARP/SSH/OF connections starting at the source controller node");
            }

            for (List<Link> path : foundResilientPaths) {

                // Generate the iterators. Start at the beginning, e.g. start now from the switch
                ListIterator<Link> pathLinksIterRv = path.listIterator();

                // Iterate the reverse path.
                while (pathLinksIterRv.hasNext()) {
                    Link currentPathLink = pathLinksIterRv.next();

                    // Find out the right node that is the source of the link
                    Node nodeToEmbedRuleOn = null;
                    for (Node node : allNodes) {
                        if (currentPathLink.getDestination().getDestNode().getValue().equals(node.getId().getValue()))
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
                            LOG.info("Embedding a controller-switch ARP/SSH/OF flow rule on an identified branching node " + nodeToEmbedRuleOn.getId().getValue() + ". Outputting on ports: " + oPorts.toString());
                        } else {
                            // Use the controller IP as the destination IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the source
                            oPorts.add(currentPathLink.getDestination().getDestTp().getValue());
                            LOG.info("Embedding a controller-switch ARP/SSH/OF flow rule on an identified non-branching node " + nodeToEmbedRuleOn.getId().getValue() + ". Outputting on ports: " + oPorts.toString());
                        }

                        LOG.info("Started embedding reverse ARP/SSH/OF flow on node: " + nodeToEmbedRuleOn.getId().getValue() + ", controllerIp: " + ctlIpAddress +
                                ", switch: " + ofNode.getId().getValue() + ", and link: " + currentPathLink.getLinkId());

                        // ARP embedding
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdRv,
                                InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 201, new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"),
                                        new Ipv4Prefix(ctlIpAddress + "/32"), oPorts));
                        // SSH embedding
                        IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
                        srcPrefix.setIpv4Prefix(new Ipv4Prefix(ctlIpAddress + "/32"));
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
            }
            LOG.info("Reverse embedding finished");

            // TRAFFIC INTENDED FOR THE SWITCH ITSELF
            LOG.info("Finally, add an entry on the switch itself to forward the packets to its local interface for node " + ofNode.getId().getValue() + " and IP address " + ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue());
            // Use the controller IP as the source IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the destination
            InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(ofNode);
            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
            InstanceIdentifier<Flow> flowIdLocal = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

            List<String> oPorts = new ArrayList<String>();
            oPorts.add("local");

            // ARP embedding
            InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdLocal,
                    InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 201,
                            new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"),
                            null, oPorts));

            // SSH embedding
            IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
            // technically src ctl prefix not necessary
            //srcPrefix.setIpv4Prefix(new Ipv4Prefix(ctlIpAddress + "/32"));
            IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo();
            dstPrefix.setIpv4Prefix(new Ipv4Prefix(ofNode.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue() + "/32"));

            InstanceIdentifier<Flow> flowIdSSHSwLocal = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdSSHSwLocal,
                    InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcPrefix, new PortNumber(22), dstPrefix, 201, oPorts));
            // OF embedding
            // to recognize the flow rule during measurements since it will be overwritten multiple times 
            String cookie = controllerRecognizeCookies.get(ctlIpAddress);
            Flow flowOFSwLocal = InitialFlowUtils.createIPLayer4Flow(flowTableId, new PortNumber(6633), srcPrefix, null , dstPrefix, 201, oPorts, cookie);
            InstanceIdentifier<Flow> flowIdOFSwLocal = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdOFSwLocal, flowOFSwLocal);

            // clean old unnecessary rules
            // remove the same local rules from the InitialPhaseI with lower priorities
            List<FlowCookie> oldRulesToRemove = InitialFlowUtils.removableFlowsIdentifiedByCookiesAfterUnicast.get(ofNode.getId().getValue());
            if (oldRulesToRemove != null) {
                for (FlowCookie flowCookie : oldRulesToRemove) {
                    InitialFlowUtils.removeOpenFlowFlow(salFlowService, flowCookie, ofNode.getId().getValue());
                }
            }

            /*
            // Dummy rule for measurements
            InstanceIdentifier<Flow> flowIdDummy = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
            IPPrefAddrInfo srcIpAddrInfo = new IPPrefAddrInfo();
            srcIpAddrInfo.setIpv4Prefix(new Ipv4Prefix("245.245.245.245/32"));
            IPPrefAddrInfo dstIpAddrInfo = new IPPrefAddrInfo();
            dstIpAddrInfo.setIpv4Prefix(new Ipv4Prefix("232.232.232.232/32"));
            List<String> oports = new ArrayList<>();
            oports.add("controller");

            // fot measurement purposes
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

            // remove the pair from the update buffer
            if(readyForResilienceUpdate.remove(communicationPair)) {
                LOG.info("Communication pair {} removed from the readyForResilienceUpdate buffer", communicationPair);
            } else {
                LOG.warn("Something went wrong and the communication pair {} could not be removed from the readyForResilienceUpdate buffer!");
            }
            return message;
        } else {
            message = "ResiliencePathManager -> nothing new for the communication pair " + communicationPair;
            LOG.info(message);
            return  message;
        }
    }

    @Override
    public Pair<ResilienceStates, String> updateResilientPathsBetweenC2CNodes(NodeId node1, NodeId node2) {

        String message;

        LOG.info("Update for the C2C pair:{}-{}", node1.getValue(), node2.getValue());
        ControllerPair communicationPair = giveMeProperC2CPairName(node1, node2);

        if (embeddedC2CPathsCache.containsKey(communicationPair)) {
            LOG.info("Communication pair: {} has already been examined -> Consulting cache to decide what to do next", communicationPair);
            if (embeddedC2CPathsCache.get(communicationPair).size() == 2) {
                message = "Communication pair: " + communicationPair + " already provided with the resilience";
                LOG.info(message);
                return new Pair<>(ResilienceStates.RESILIENCE_PROVIDED_BEFORE, message);
            } else {
                LOG.info("Communication pair: {} has only one unicast path embedded -> Try to find another one", communicationPair);
                // compute again the pair and check if size is bigger than 2 check for uniqueness
                // retrieve the 2 max disjoint paths between this ofNode and the controller
                List<List<Link>> foundResilientPaths = null;
                foundResilientPaths = get2DisjointPathsBetweenNodes(node1, node2);
                if (!checkIfPathsEqual(foundResilientPaths) && foundResilientPaths.size() == 2) {
                    embeddedC2CPathsCache.put(communicationPair, foundResilientPaths);
                    controllersReadyForResilienceUpdate.add(communicationPair);
                    message = "The new resilience examination of the communication pair " + communicationPair + " has found 2 disjoint paths";
                    LOG.info(message);
                    LOG.info("Found paths:");
                    printFoundPathsNicely(foundResilientPaths);
                    return new Pair<>(ResilienceStates.RESILIENCE_PROVIDED_NOW, message);
                } else {
                    message = "The new resilience examination of the communication pair " + communicationPair + " could not find 2 disjoint paths. Leaving old state in the cache.";
                    LOG.info(message);
                    return new Pair<>(ResilienceStates.NO_RESILIENCE, message);
                }

            }
        } else {

            LOG.info("Examining resilience for the first time for pair {}", communicationPair);
            // retrieve the 2 max disjoint paths between this ofNode and the controller
            List<List<Link>> foundResilientPaths = null;
            foundResilientPaths = get2DisjointPathsBetweenNodes(node1, node2);

            if (!checkIfPathsEqual(foundResilientPaths)) { // always 2 if they do not exist it returns the same paths
                message = "The algorithm has found more than one path for the communication pair " + communicationPair.toString();
                LOG.info(message);
                LOG.info("Found paths:");
                printFoundPathsNicely(foundResilientPaths);
                // cache the found paths
                embeddedC2CPathsCache.put(communicationPair, foundResilientPaths);
                controllersReadyForResilienceUpdate.add(communicationPair);
                return new Pair<>(ResilienceStates.RESILIENCE_PROVIDED_NOW, message);
            } else {
                message = "The algorithm has found only one path for the communication pair " + communicationPair.toString();
                LOG.info(message);
                // Remove the duplicate
                foundResilientPaths.remove(1);
                LOG.info("Found path:");
                printFoundPathsNicely(foundResilientPaths);
                // cache the found path
                embeddedC2CPathsCache.put(communicationPair, foundResilientPaths);
                controllersReadyForResilienceUpdate.add(communicationPair);
                return new Pair<>(ResilienceStates.NO_RESILIENCE, message);

            }

        }

    }

    @Override
    public String embedResilientPathsBetweenC2CNodes(NodeId node1, NodeId node2) {

        ControllerPair communicationPair = giveMeProperC2CPairName(node1, node2);
        LOG.debug("embedResilientPathsBetweenC2CNodes: {}", communicationPair);
        List<List<Link>> foundResilientPaths;
        String message;

        if (controllersReadyForResilienceUpdate.contains(communicationPair)) {

            foundResilientPaths = embeddedC2CPathsCache.get(communicationPair);
            message = "Embedding the synchronization control channel flows for controllers: " + node1.getValue() + " to " + node2.getValue();
            LOG.info(message);

            String controllerIp1 = communicationPair.getControllerIp1();
            String controllerIp2 = communicationPair.getControllerIp2();

            // Identify branching points for connections starting at the source switch node
            LOG.info("Identify branching points for C-C connections starting at the source node " + controllerIp1 + " and destination node " + controllerIp2);
            List<String> outputPortsAtControllerSource = new ArrayList<String>();
            if (foundResilientPaths.size() > 1) {
                for (List<Link> path : foundResilientPaths) {
                    if (!outputPortsAtControllerSource.contains(path.get(1).getSource().getSourceTp().getValue()))
                        outputPortsAtControllerSource.add(path.get(1).getSource().getSourceTp().getValue());
                }
                LOG.info("Identified branching points " + outputPortsAtControllerSource.toString() + " for C-C connections starting at the source" + controllerIp1  + "  node.");
            } else {
                LOG.info("No branching points found for C-C connections starting at the source controller node {} and destination controller {}",
                        controllerIp1, controllerIp2);
            }

            // C2->C1 direction

            // Get a list of all existing nodes
            List<Node> allNodes = InitialFlowUtils.getAllRealNodes(dataBroker);

            // for each path
            for (List<Link> path : foundResilientPaths) {

                // Generate the iterators. Start just after the last element, e.g. C2
                ListIterator<Link> pathLinksIterFw = path.listIterator(path.size());

                // Iterate the forward path.
                while (pathLinksIterFw.hasPrevious()) {
                    Link currentPathLink = pathLinksIterFw.previous();

                    // Find out the right node that is the source of the link
                    Node nodeToEmbedRuleOn = null;
                    for (Node node : allNodes) {
                        if (currentPathLink.getSource().getSourceNode().getValue().equals(node.getId().getValue()))
                            nodeToEmbedRuleOn = node; // The Node on which the flow rule is to be embedded
                    }

                    // If this is not a host node, embed the flow rule on it
                    if (nodeToEmbedRuleOn != null && !nodeToEmbedRuleOn.getId().getValue().contains("host")) {
                        InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(nodeToEmbedRuleOn);
                        InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                        InstanceIdentifier<Flow> flowIdFwC2C = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                        InstanceIdentifier<Flow> flowIdFwArp = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

                        LOG.info("Started embedding forward C2C flow on node:" + nodeToEmbedRuleOn.getId().getValue() + ", source:" + controllerIp1 +
                                ", destination:" + controllerIp2 + ", link:" + currentPathLink.getLinkId().getValue());

                        List<String> oPorts = new ArrayList<String>();
                        if (nodeToEmbedRuleOn.getId().getValue().equals(path.get(1).getSource().getSourceNode().getValue()) && foundResilientPaths.size() > 1) {
                            oPorts = outputPortsAtControllerSource;
                            LOG.info("For embedding of C2C flows identified a branching node: " + nodeToEmbedRuleOn.getId().getValue() + "; Outgoing ports " + oPorts.toString());
                        } else {
                            // Use the controller IP as the destination IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the source
                            LOG.info("For embedding of C2C flows identified a non-branching node: " + nodeToEmbedRuleOn.getId().getValue());
                            oPorts.add(currentPathLink.getSource().getSourceTp().getValue());
                        }

                        IPPrefAddrInfo srcIpPrefAddrInfo = new IPPrefAddrInfo();
                        IPPrefAddrInfo dstIpPrefAddrInfo = new IPPrefAddrInfo();

                        srcIpPrefAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerIp1 + "/32"));
                        dstIpPrefAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerIp2 + "/32"));

                        // AKKA C-C TCP embedding
                        // Use the controller IP 2 as the destination IP field of the OF header
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdFwC2C,
                                InitialFlowUtils.createIPLayer4Flow(
                                        flowTableId, null, srcIpPrefAddrInfo,
                                        null, dstIpPrefAddrInfo,
                                        200, oPorts
                                )
                        );

                        // ARP embedding
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdFwArp,
                                InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 201,
                                        new Ipv4Prefix(controllerIp2 + "/32"),
                                        new Ipv4Prefix(controllerIp1 + "/32"), oPorts));

                    }
                }
            }

            LOG.info("Forward embedding finished");

            // C1->C2 direction

            // Now embed the reverse as well
            LOG.info("Identify branching points for C-C connections starting at the source node " + controllerIp2 + " and destination node " + controllerIp1);
            outputPortsAtControllerSource = new ArrayList<String>();
            if (foundResilientPaths.size() > 1) {
                for (List<Link> path : foundResilientPaths) {
                    if (path.size() > 1) {
                        if (!outputPortsAtControllerSource.contains(path.get(path.size() - 2).getDestination().getDestTp().getValue()))
                            outputPortsAtControllerSource.add(path.get(path.size() - 2).getDestination().getDestTp().getValue());
                    }
                }
                LOG.info("Identified branching points " + outputPortsAtControllerSource.toString() + " for C-C connections starting at the source" + controllerIp2  + "  node.");
            } else {
                LOG.info("No branching points found for C-C connections starting at the source controller node {} and destination controller {}",
                        controllerIp2, controllerIp1);
            }

            for (List<Link> path : foundResilientPaths) {

                // Generate the iterators. Start just after the last element, e.g. C1
                ListIterator<Link> pathLinksIterRv = path.listIterator();

                // Iterate the reverse path.
                while (pathLinksIterRv.hasNext()) {
                    Link currentPathLink = pathLinksIterRv.next();

                    // Find out the right node that is the source of the link
                    Node nodeToEmbedRuleOn = null;
                    for (Node node : allNodes) {
                        if (currentPathLink.getDestination().getDestNode().getValue().equals(node.getId().getValue()))
                            nodeToEmbedRuleOn = node; // The Node on which the flow rule is to be embedded
                    }

                    // If this is not a host node, embed the flow rule on it
                    if (nodeToEmbedRuleOn != null && !nodeToEmbedRuleOn.getId().getValue().contains("host")) {
                        InstanceIdentifier<Node> nodeIid = InitialFlowUtils.getNodeInstanceId(nodeToEmbedRuleOn);
                        InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeIid, flowTableId);
                        InstanceIdentifier<Flow> flowIdRvC2C = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                        InstanceIdentifier<Flow> flowIdArp = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

                        LOG.info("Started embedding reverse C2C flow on node:" + nodeToEmbedRuleOn.getId().getValue() + ", source:" + controllerIp2 +
                                ", destination:" + controllerIp1 + ", link:" + currentPathLink.getLinkId().getValue());

                        List<String> oPorts = new ArrayList<String>();
                        if (path.size() > 1 && nodeToEmbedRuleOn.getId().getValue().equals(path.get(path.size() - 2).getDestination().getDestNode().getValue()) && foundResilientPaths.size() > 1) {
                            oPorts = outputPortsAtControllerSource;
                            LOG.info("For the C2C flow" + " identified a branching node: " + path.get(path.size() - 2).getDestination().getDestNode().getValue() + "; Outgoing ports " + oPorts.toString());
                        } else {
                            // Use the controller IP as the destination IP field of the OpenFlow Control channel and OpenFlow node bridge IP as the source
                            LOG.info("For the C2C flow" + " identified a non-branching node: " + nodeToEmbedRuleOn.getId().getValue());
                            oPorts.add(currentPathLink.getDestination().getDestTp().getValue());
                        }

                        IPPrefAddrInfo srcAddrInfo = new IPPrefAddrInfo();
                        IPPrefAddrInfo dstAddrInfo = new IPPrefAddrInfo();

                        srcAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerIp2 + "/32"));
                        dstAddrInfo.setIpv4Prefix(new Ipv4Prefix(controllerIp1 + "/32"));

                        // AKKA C-C TCP embedding
                        // Use the controller IP 2 as the destination IP field of the OF header
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdRvC2C,
                                InitialFlowUtils.createIPLayer4Flow(
                                        flowTableId, null, srcAddrInfo,
                                        null, dstAddrInfo, 200, oPorts
                                ));

                        // ARP embedding
                        InitialFlowUtils.writeFlowToController(salFlowService, nodeIid, tableId, flowIdArp,
                                InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 201,
                                        new Ipv4Prefix(controllerIp1 + "/32"),
                                        new Ipv4Prefix(controllerIp2 + "/32"), oPorts));

                    }
                }
            }

            if (controllersReadyForResilienceUpdate.remove(communicationPair)) {
                LOG.info("Communication pair {} removed from the controllersReadyForResilienceUpdate buffer", communicationPair);
            } else {
                LOG.warn("Something went wrong and the communication pair {} could not be removed from the controllersReadyForResilienceUpdate buffer!");
            }
            return message;

        } else {
            message = "ResiliencePathManager -> nothing new for the communication pair " + communicationPair.toString();
            LOG.info(message);
            return  message;
        }

    }

    private  boolean ifC2CPair(NodeId node1, NodeId node2) {
        if (node1.getValue().contains("host") && node2.getValue().contains("host")) {
            return true;
        } else {
            return false;
        }
    }

    private void sleep_some_time(long miliseconds) {
        try {
            sleep(miliseconds);
        } catch (InterruptedException e) {
            for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                LOG.error("Stack root cause trace -> {}", m);
            }
        }
    }

    /**
     *  Check if 2 provided paths are equal
     *
     * @param paths
     * @return
     */
    private boolean checkIfPathsEqual(List<List<Link>> paths) {
        // Assumption: paths always have 2 elements
        List<Link> path1 = paths.get(0);
        List<Link> path2 = paths.get(1);

        LOG.debug("Printing paths in the checkIfPathsEqual");
        printFoundPathsNicely(paths);

        boolean forwardCheck = forwardCheckPathsEquality(path1, path2);
        if (forwardCheck) {
            return true;
        }

        boolean backwardCheck = backwardCheckPathsEquality(path1, path2);

        if (backwardCheck) {
            return true;
        } else {
            return  false;
        }
    }

    /**
     * Check if 2 paths are equal when traversing forward through both paths
     *
     * @param path1
     * @param path2
     * @return
     */
    private boolean forwardCheckPathsEquality(List<Link> path1, List<Link> path2) {
        if (path1.size() == path2.size()) {
            Iterator<Link> linkItP1 = path1.iterator();
            Iterator<Link> linkItP2 = path2.iterator();

            while (linkItP1.hasNext() && linkItP2.hasNext()) {
                Link p1Link = linkItP1.next();
                Link p2Link = linkItP2.next();
                if (!p1Link.getSource().getSourceTp().getValue().equals(p2Link.getSource().getSourceTp().getValue())
                        || !p1Link.getDestination().getDestTp().getValue().equals(p2Link.getDestination().getDestTp().getValue()))
                    return false;
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check if 2 paths are equal when traversing in forward (path1) and backward (path2) directions
     *
     * @param path1
     * @param path2
     * @return
     */
    private boolean backwardCheckPathsEquality(List<Link> path1, List<Link> path2) {
        if (path1.size() == path2.size()) {
            Iterator<Link> linkItP1 = path1.iterator();
            Iterator<Link> linkItP2 = Lists.reverse(path2).iterator();

            while (linkItP1.hasNext() && linkItP2.hasNext()) {
                Link p1Link = linkItP1.next();
                Link p2Link = linkItP2.next();
                if (!p1Link.getSource().getSourceTp().getValue().equals(p2Link.getSource().getSourceTp().getValue())
                        || !p1Link.getDestination().getDestTp().getValue().equals(p2Link.getDestination().getDestTp().getValue()))
                    return false;
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Prints provided paths in a nicer manner
     *
     * @param paths
     */
    private void printFoundPathsNicely(List<List<Link>> paths){
        if (paths == null) {
            return;
        }
        int pathNum = 1;
        LOG.info("------------------------------------------------------------");
        for (List<Link> path: paths) {
            LOG.info("Path number {} contains the following links:", pathNum);
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


    /**
     * Method to get  all links from Topology. Links contain all the relevant information
     * @param db DataBroker from which toplogy links should be extracted
     * @return List<Link> found in the topology
     */
    public static List<Link> getAllLinks(DataBroker db) {
        List<Link> linkList = new ArrayList<>();

        try {
            TopologyId topoId = new TopologyId(topologyId);
            InstanceIdentifier<Topology> nodesIid = InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, new TopologyKey(topoId)).toInstance();
            ReadOnlyTransaction nodesTransaction = db.newReadOnlyTransaction();
            CheckedFuture<com.google.common.base.Optional<Topology>, ReadFailedException> nodesFuture = nodesTransaction
                    .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
            Optional<Topology> nodesOptional = nodesFuture.checkedGet();

            if (nodesOptional != null && nodesOptional.isPresent())
                linkList = nodesOptional.get().getLink();
            // LOG.info("Nodelist: " + nodeList);

            return linkList;
        } catch (Exception e) {

            LOG.info("Node Fetching Failed with Exception: " + e.toString());
            return linkList;
        }
    }

    /**
     * Returns a node object with provided NodeId currently available in the inventory.
     *
     * @return
     */
    public Node getNodeByNodeId(String nodeId) {
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

                       for(Node node: nodeList) {
                           if (node.getId().getValue().equals(nodeId))  {
                               LOG.info("Successfully fetched an OpenFlow node from the DS.");
                               return node;
                           }
                       }
                   }
            } catch (Exception e) {
                LOG.error("Failed to fetch list of nodes with Exception: " + e.getMessage());
                notFetched = true;
            }
        }

        return null;
    }

    private boolean checkIfPairInS2CCache(NodeId node1, NodeId node2) {
        if (embeddedS2CPathsCache.containsKey(node1.getValue() + "-" + node2.getValue())) {
            return true;
        } else if (embeddedS2CPathsCache.containsKey(node2.getValue() + "-" + node1.getValue())) {
            return true;
        } else {
            return false;
        }
    }

    private String giveMeProperS2CPairName(NodeId node1, NodeId node2) {
        // search if available in cache in order to avoid duplicates with reverse names
        if (checkIfPairInS2CCache(node1, node2)) {
            if (embeddedS2CPathsCache.containsKey(node1.getValue() + "-" + node2.getValue())) {
                return node1.getValue() + "-" + node2.getValue();
            } else {
                return node2.getValue() + "-" + node1.getValue();
            }
        } else {
            return node1.getValue() + "-" + node2.getValue();
        }
    }

    private boolean checkIfPairInC2CCache(NodeId node1, NodeId node2) {
        if (embeddedC2CPathsCache.containsKey(new ControllerPair(node1, node2))) {
            return true;
        } else if (embeddedC2CPathsCache.containsKey(new ControllerPair(node2, node1))) {
            return true;
        } else {
            return false;
        }
    }

    private ControllerPair giveMeProperC2CPairName(NodeId node1, NodeId node2) {
        // search if available in cache in order to avoid duplicates with reverse names
        if (checkIfPairInC2CCache(node1, node2)) {
            if (embeddedC2CPathsCache.containsKey(new ControllerPair(node1, node2))) {
                return new ControllerPair(node1, node2);
            } else {
                return new ControllerPair(node2, node1);
            }
        } else {
            return new ControllerPair(node1, node2);
        }
    }
}
