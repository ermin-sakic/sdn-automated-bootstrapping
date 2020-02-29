package eu.virtuwind.bootstrappingmanager.setup.impl.utilities;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.DropActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.DropActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.ArpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv6MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static java.lang.Thread.sleep;

/**
 * Created by ermin on 12.03.17.
 */
public class InitialFlowUtils {
    private static final int LLDP_ETHER_TYPE = 35020;
    private static final int IPV4_ETHER_TYPE = 2048;
    private static final int IPV6_ETHER_TYPE = 34525;
    private static final int ARP_ETHER_TYPE = 2054;
    private static final Logger LOG = LoggerFactory.getLogger(InitialFlowUtils.class);
    private static int flowIdleTimeout;
    private static int flowHardTimeout;
    private static boolean ipv6enabled;
    private static HashMap<FlowCookie, String> removableFlowCookies = new HashMap<FlowCookie, String>();

    public static Set<InstanceIdentifier<Node>> getAllNodes(DataBroker dataBroker)
    {
        Nodes nodes = null;
        try {
            InstanceIdentifier.InstanceIdentifierBuilder<Nodes> nodesInsIdBuilder = InstanceIdentifier.builder(Nodes.class);
            ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction();
            Optional<Nodes> dataObjectOptional = null;
            dataObjectOptional = readOnlyTransaction.read(LogicalDatastoreType.OPERATIONAL, nodesInsIdBuilder.build()).get();
            if(dataObjectOptional.isPresent()) {
                nodes = dataObjectOptional.get();
            }
            readOnlyTransaction.close();
        } catch(InterruptedException e) {
            LOG.error("Failed to read nodes from Operation data store.");
            throw new RuntimeException("Failed to read nodes from Operation data store.", e);
        } catch(ExecutionException e) {
            LOG.error("Failed to read nodes from Operation data store.");
            throw new RuntimeException("Failed to read nodes from Operation data store.", e);
        }

        List<InstanceIdentifier<Node>> listOfNodeIIds = new ArrayList<>();
        Set<InstanceIdentifier<Node>> nodeIds = new HashSet();
        for (Node node:nodes.getNode()) {
            nodeIds.add(getNodeInstanceId(node));
        }

        return nodeIds;
    }

    public static Set<InstanceIdentifier<?>> getAllNodeIIds(DataBroker dataBroker)
    {
        Nodes nodes = null;
        try {
            InstanceIdentifier.InstanceIdentifierBuilder<Nodes> nodesInsIdBuilder = InstanceIdentifier.builder(Nodes.class);
            ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction();
            Optional<Nodes> dataObjectOptional = null;
            dataObjectOptional = readOnlyTransaction.read(LogicalDatastoreType.OPERATIONAL, nodesInsIdBuilder.build()).get();
            if(dataObjectOptional.isPresent()) {
                nodes = dataObjectOptional.get();
            }
            readOnlyTransaction.close();
        } catch(InterruptedException e) {
            LOG.error("Failed to read nodes from Operation data store.");
            throw new RuntimeException("Failed to read nodes from Operation data store.", e);
        } catch(ExecutionException e) {
            LOG.error("Failed to read nodes from Operation data store.");
            throw new RuntimeException("Failed to read nodes from Operation data store.", e);
        }

        List<InstanceIdentifier<?>> listOfNodeIIds = new ArrayList<>();
        Set<InstanceIdentifier<?>> nodeIds = new HashSet();
        for (Node node:nodes.getNode()) {
            nodeIds.add(getNodeInstanceId(node));
        }

        return nodeIds;
    }

    public static void addRemovableFlowCookies(String nodeId, FlowCookie remCookie) {
        removableFlowCookies.put(remCookie, nodeId);
    }


    public static void removeInitialFlows(SalFlowService salFlowService) {
        for(FlowCookie fc:removableFlowCookies.keySet()) {
            removeOpenFlowFlow(salFlowService, fc, removableFlowCookies.get(fc));
        }
    }

    public static List<Node> getAllRealNodes(DataBroker dataBroker)
    {
        Nodes nodes = null;
        try {
            InstanceIdentifier.InstanceIdentifierBuilder<Nodes> nodesInsIdBuilder = InstanceIdentifier.builder(Nodes.class);
            ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction();
            Optional<Nodes> dataObjectOptional = null;
            dataObjectOptional = readOnlyTransaction.read(LogicalDatastoreType.OPERATIONAL, nodesInsIdBuilder.build()).get();
            if(dataObjectOptional.isPresent()) {
                nodes = dataObjectOptional.get();
            }
            readOnlyTransaction.close();
        } catch(InterruptedException e) {
            LOG.error("Failed to read nodes from Operation data store.");
            throw new RuntimeException("Failed to read nodes from Operation data store.", e);
        } catch(ExecutionException e) {
            LOG.error("Failed to read nodes from Operation data store.");
            throw new RuntimeException("Failed to read nodes from Operation data store.", e);
        }

        return nodes.getNode();
    }


    public static InstanceIdentifier<Node> getNodeInstanceId(Node node) {
        return InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(node.getId())).build();
    }

    /**
     * Configures the idle flow timeout for the set of default flows.
     * @param flowIdleTimeout The idle flow timeout.
     */
    public static void setFlowIdleTimeout(int flowIdleTimeout) {
        InitialFlowUtils.flowIdleTimeout = flowIdleTimeout;
    }

    /**
     * Configures the hard flow timeout for the set of defualt flows.
     * @param flowHardTimeout The hard flow timeout.
     */
    public static void setFlowHardTimeout(int flowHardTimeout) {
        InitialFlowUtils.flowHardTimeout = flowHardTimeout;
    }

    /**
     * Gets an IP address of a node (port connected to a controller)
     *
     * @param  node The instance identifier of the node
     * @return IP address of the node
     */
    public static String getNodeIPAddress(InstanceIdentifier<Node> node){

        LOG.info("Fetching node IP address from the data store");
        FlowCapableNode sw = (FlowCapableNode) node.augmentation(FlowCapableNode.class);
        if(sw != null && sw.getIpAddress() != null) {
            LOG.info("IP: " + sw.getIpAddress().toString());
            return sw.getIpAddress().toString();
        } else {
            return "NO IP!";
        }

    }

    /**
     * Gets an IP address of a node (port connected to a controller)
     *
     * @param  node The instance identifier of the node
     * @return IP address of the node
     */
    public static String getNodeIPAddress(Node node, DataBroker db){

        LOG.info("Fetching node {} IP address from the data store", node.getId().getValue());
        FlowCapableNode sw = null;
        String IP = null;
        InstanceIdentifier<FlowCapableNode> nodeInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(node.getId())).augmentation(FlowCapableNode.class).build();
        ReadOnlyTransaction readTransaction = db.newReadOnlyTransaction();

        try {
            Optional optionalData = readTransaction.read(LogicalDatastoreType.OPERATIONAL, nodeInstanceIdentifier).get();
            if (optionalData.isPresent()) {
                sw = (FlowCapableNode) optionalData.get();
                IP = sw.getIpAddress().getIpv4Address().getValue();
                return IP;
            }
        } catch (ExecutionException | InterruptedException e) {
            readTransaction.close();
        }

        return IP;
    }

    /**
     * Thread sleep with Exception catch wrapper
     *
     * @param millis
     */
    private static void sleep_some_time(long millis) {
        try {
            sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets an IP address of a node (port connected to a controller)
     *
     * @param  nodeId The node ID of the node
     * @return IP address of the node
     */
    public static String getNodeIPAddress(NodeId nodeId, DataBroker db){

        LOG.info("Fetching node IP address from the data store");
        FlowCapableNode sw = null;
        String IP = null;
        ReadOnlyTransaction readTransaction = db.newReadOnlyTransaction();
        InstanceIdentifier<FlowCapableNode> nodeInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId)).augmentation(FlowCapableNode.class).build();
        try {
            Optional optionalData = readTransaction.read(LogicalDatastoreType.OPERATIONAL, nodeInstanceIdentifier).get();
            if (optionalData.isPresent()) {
                sw = (FlowCapableNode) optionalData.get();
            }
        } catch (ExecutionException | InterruptedException e) {
            readTransaction.close();
        }
        if (sw != null) {
            try {
                IP = sw.getIpAddress().getIpv4Address().getValue();
                LOG.info("IP: " + IP);
            } catch (NullPointerException e) {
                LOG.warn("IP address for the node {} is not currently available in the datastore",
                        nodeId.getValue());
                return IP;
            }
            return IP;
        } else {
            LOG.info("NO IP!");
            return IP;
        }
    }

    /**
     * Enables or disables the mode for IPv6 default flow cofnigurations
     * @param arg The boolean binary parameter.
     */
    private static void setIPv6Enabled(boolean arg) {
        ipv6enabled = arg;
    }

    /**
     * Gets the instance identifier for the table of a particular node nodeId, in which the set
     * of default flow rules is to be embedded.
     * @param nodeId The node identifier for the node whose table instance identifier is to be returned.
     * @return The instance identifier of the table.
     */
    public static InstanceIdentifier<Table> getTableInstanceId(InstanceIdentifier<Node> nodeId, Short flowTableId) {
        // get flow table key
        TableKey flowTableKey = new TableKey(flowTableId);
        return nodeId.builder()
                .augmentation(FlowCapableNode.class)
                .child(Table.class, flowTableKey)
                .build();
    }

    /**
     * Gets the instance identifier for the particular flow structure, which consists
     * one of the default flow rules to be embedded.
     * @param tableId The InstanceIdentifier that uniquely identifies the flow table in which the flow will be embedded.
     * @return The instance identifier of the new flow structure.
     */
    public static InstanceIdentifier<Flow> getFlowInstanceId(InstanceIdentifier<Table> tableId, long flowID) {
        // generate unique flow key
        FlowId flowId = new FlowId(String.valueOf(flowID));
        FlowKey flowKey = new FlowKey(flowId);

        LOG.info("FLOW ID: " + flowId.getValue() + " FLOW KEY: " + flowKey.getId().getValue());
        return tableId.child(Flow.class, flowKey);
    }

    /**
     * @param tableId The table identifier for which the flow structure is to be applied.
     * @param priority The priority of the flow.
     * @return The generated flow structure.
     */
    public static Flow createARPFlowWithMatchPair(Short tableId, int priority, Ipv4Prefix dstPrefix, Ipv4Prefix srcPrefix, List<String> outputPorts) {

        Integer cookieInt = getRandomNumberInRange(1, 1000000000);

        // start building flow
        FlowBuilder arpFlow = new FlowBuilder() //
                .setTableId(tableId) //
                .setFlowName("arpflowtocontroller");

        // use its own hash code for id.
        arpFlow.setId(new FlowId(Long.toString(cookieInt)));

        // layer 2
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(new EthernetTypeBuilder()
                        .setType(new EtherType(Long.valueOf(ARP_ETHER_TYPE))).build());

        ArpMatchBuilder arpMatchBuilder = new ArpMatchBuilder();

        if(dstPrefix != null)
            arpMatchBuilder.setArpTargetTransportAddress(dstPrefix);
        if(srcPrefix != null)
            arpMatchBuilder.setArpSourceTransportAddress(srcPrefix);

        Match match = new MatchBuilder()
                .setEthernetMatch(ethernetMatchBuilder.build())
                .setLayer3Match(arpMatchBuilder.build())
                .build();

        // Create an Apply Action
        List<Action> actionList = new ArrayList<Action>();

        int orderId = 0;
        for(String outputPort:outputPorts) {
            if (outputPort.contains("normal"))
                actionList.add(getSendToNormalAction());
            else if (outputPort.contains("controller"))
                actionList.add(getSendToControllerAction());
            else if (outputPort.contains("local"))
                actionList.add(getSendToLocalAction());
            else {
                actionList.add(new ActionBuilder()
                        .setOrder(orderId)
                        .setKey(new ActionKey(orderId))
                        .setAction(new OutputActionCaseBuilder()
                                .setOutputAction(new OutputActionBuilder()
                                        .setOutputNodeConnector(new Uri(outputPort))
                                        .build())
                                .build())
                        .build());
            }

            orderId = orderId +1;
        }

        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actionList)
                .build();

        // Wrap our Apply Action in an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder() //
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()//
                        .setApplyActions(applyActions) //
                        .build()) //
                .build();

        // Put our Instruction in a list of Instructions
        arpFlow
                .setMatch(match) //
                .setInstructions(new InstructionsBuilder() //
                        .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                        .build()) //
                .setPriority(priority) //
                .setBufferId(OFConstants.OFP_NO_BUFFER) //
                .setHardTimeout(flowHardTimeout) //
                .setIdleTimeout(flowIdleTimeout) //
                .setCookie(new FlowCookie(BigInteger.valueOf(cookieInt)))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        LOG.info("FLOW COOKIE: " + new FlowCookie(BigInteger.valueOf(cookieInt)));

        return arpFlow.build();
    }

    private static int getRandomNumberInRange(int min, int max) {

        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }

        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

    /**
     * Embeds a given flow into network using the SAL-Flow service API.
     * @param nodeInstanceId The unique InstanceIdentifier for the OpenFlow node.
     * @param tableInstanceId The unique InstanceIdentifier for the flow table on the OpenFlow node.
     * @param flowPath The unique InstanceIdentifier for the Flow structure of the flow.
     * @param flow The actual Flow structure containing the properties of the new Flow.
     * @return The Future structure indicating the success or failure of flow configuration.
     */
    public static Future<RpcResult<AddFlowOutput>> writeFlowToController(SalFlowService salFlowService, InstanceIdentifier<Node> nodeInstanceId,
                                                                         InstanceIdentifier<Table> tableInstanceId,
                                                                         InstanceIdentifier<Flow> flowPath,
                                                                         Flow flow) {
        LOG.info("Adding flow to node {}",nodeInstanceId.firstKeyOf(Node.class, NodeKey.class).getId().getValue());

        final AddFlowInputBuilder builder = new AddFlowInputBuilder(flow);
        builder.setNode(new NodeRef(nodeInstanceId));
        builder.setFlowRef(new FlowRef(flowPath));
        builder.setFlowTable(new FlowTableRef(tableInstanceId));
        builder.setTransactionUri(new Uri(flow.getId().getValue()));
        Future<RpcResult<AddFlowOutput>> future =  salFlowService.addFlow(builder.build());



        // wait to finish writing
        //while (!future.isDone()) {
        //}

        return future;
    }

    /**
     * Generates the output-to-controller Action structure.
     * @return The output-to-controller Action structure.
     */
    private static Action getSendToControllerAction() {
        return new ActionBuilder()
                .setOrder(0)
                .setKey(new ActionKey(0))
                .setAction(new OutputActionCaseBuilder()
                        .setOutputAction(new OutputActionBuilder()
                                .setMaxLength(0xffff)
                                .setOutputNodeConnector(new Uri(OutputPortValues.CONTROLLER.toString()))
                                .build())
                        .build())
                .build();
    }

    /**
     * Generates the output-to-controller Action structure.
     * @return The output-to-controller Action structure.
     */
    private static Action getSendToLocalAction() {
        return new ActionBuilder()
                .setOrder(0)
                .setKey(new ActionKey(0))
                .setAction(new OutputActionCaseBuilder()
                        .setOutputAction(new OutputActionBuilder()
                                .setMaxLength(0xffff)
                                .setOutputNodeConnector(new Uri(OutputPortValues.LOCAL.toString()))
                                .build())
                        .build())
                .build();
    }

    /**
     * Generates the output-to-NORMAL Action structure.
     * @return The output-to-NORMAL Action structure.
     */
    private static Action getSendToNormalAction() {
        return new ActionBuilder()
                .setOrder(0)
                .setKey(new ActionKey(0))
                .setAction(new OutputActionCaseBuilder()
                        .setOutputAction(new OutputActionBuilder()
                                .setMaxLength(0xffff)
                                .setOutputNodeConnector(new Uri(OutputPortValues.NORMAL.toString()))
                                .build())
                        .build())
                .build();
    }

    /**
     * Generates the structure for specifying the OpenFlow Drop output action.
     * @return The structure specifying the OpenFlow Drop action.
     */
    private Action getDropAction() {
        DropActionCase dropAction = new DropActionCaseBuilder().build();
        ActionBuilder actionBuilder = new ActionBuilder();
        actionBuilder.setAction(dropAction);
        return actionBuilder.build();
    }

    /**
     * Creates a Flow structure that matches the LLDP packets and forwards them to the controller.
     * @param tableId The table identifier for which the flow structure is to be applied.
     * @param priority The priority of the flow.
     * @return The generated flow structure.
     */
    public static Flow createLldpToControllerFlow(Short tableId, int priority) {

        Integer cookieInt = getRandomNumberInRange(1, 1000000000);

        // start building flow
        FlowBuilder lldpFlow = new FlowBuilder() //
                .setTableId(tableId) //
                .setFlowName("lldptocntrl");

        // use its own hash code for id.
        lldpFlow.setId(new FlowId(Long.toString(cookieInt)));

        // layer 2
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(new EthernetTypeBuilder()
                        .setType(new EtherType(Long.valueOf(LLDP_ETHER_TYPE))).build());

        Match match = new MatchBuilder()
                .setEthernetMatch(ethernetMatchBuilder.build())
                .build();

        // Create an Apply Action
        ApplyActions applyActions = new ApplyActionsBuilder().setAction(ImmutableList.of(getSendToControllerAction()))
                .build();

        // Wrap our Apply Action in an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder() //
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()//
                        .setApplyActions(applyActions) //
                        .build()) //
                .build();

        // Put our Instruction in a list of Instructions
        lldpFlow
                .setMatch(match) //
                .setInstructions(new InstructionsBuilder() //
                        .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                        .build()) //
                .setPriority(priority) //
                .setBufferId(OFConstants.OFP_NO_BUFFER) //
                .setHardTimeout(flowHardTimeout) //
                .setIdleTimeout(flowIdleTimeout) //
                .setCookie(new FlowCookie(BigInteger.valueOf(cookieInt)))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        LOG.info("FLOW COOKIE: " + new FlowCookie(BigInteger.valueOf(cookieInt)));

        return lldpFlow.build();
    }

    /**
     * Removes an OpenFlow flow based on given cookie and node ID.
     * @param nodeId The OpenFlow node on which the flow is configured.
     * @param cookiePath The cookie that the flow rules on this path are configured with
     * @return The Result of flow rule removal.
     */
    public static boolean removeOpenFlowFlow(SalFlowService salFlowService, FlowCookie cookiePath, String nodeId) {
        Flow removeFlow = populateFlowRemoveStructure(cookiePath);

        InstanceIdentifier<Flow> flowPath = InstanceIdentifier
                .builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(nodeId)))
                .augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(Short.parseShort("0")))
                .child(Flow.class, new FlowKey(removeFlow.getId())).build();

        final RemoveFlowInputBuilder builder = new RemoveFlowInputBuilder(removeFlow);
        final InstanceIdentifier<Table> tableInstanceId = flowPath
                .<Table> firstIdentifierOf(Table.class);
        final InstanceIdentifier<Node> nodeInstanceId = flowPath
                .<Node> firstIdentifierOf(Node.class);
        builder.setNode(new NodeRef(nodeInstanceId));
        builder.setFlowTable(new FlowTableRef(tableInstanceId));
        builder.setTransactionUri(new Uri(removeFlow.getId().getValue()));

        final RemoveFlowInput flow = builder.build();

        //LOG.info("SalFlowService {}", salFlowService);
        //LOG.info("About to remove flow (via SalFlowService) {}", flow.getCookie().getValue());


        ListenableFuture<RpcResult<RemoveFlowOutput>> resultJdk = JdkFutureAdapters
                .listenInPoolThread(salFlowService.removeFlow(flow));

            Futures.addCallback(resultJdk, new FutureCallback<RpcResult<RemoveFlowOutput>>() {
                @Override
                public void onSuccess(final RpcResult<RemoveFlowOutput> o) {
                    LOG.info("Flow removal " + cookiePath + "Successful outcome.");
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    LOG.error("Failure when removing flow with cookie " + cookiePath);
                    throwable.printStackTrace();
                }
            });

        // wait to finish writing
        //while (!resultJdk.isDone()) {}

        return !resultJdk.isCancelled() && resultJdk.isDone();
    }

    /**
     * Method which populates the flow structure passed on to device in removeOpenFlowFlow()
     * @param cookiePath Cookie ID that is active for this specific path
     * @return Flow structure parsable in MD-SAL format
     */
    private static Flow populateFlowRemoveStructure(FlowCookie cookiePath) {
        FlowBuilder flowBuilder = new FlowBuilder() //
                .setTableId((short) 0);

        flowBuilder
                .setBufferId(OFConstants.OFP_NO_BUFFER)
                .setCookie(cookiePath)
                .setCookieMask(new FlowCookie(BigInteger.valueOf(0xffffL)))
                .setFlags(new FlowModFlags(false, false, false, false, true))
                .setId(new FlowId(cookiePath.toString()));

        return flowBuilder.build();
    }

    /**
     * Creates a Flow structure that matches the ICMP packets and forwards them using the outputPort (normal or controller) action.
     * @param tableId The table identifier for which the flow structure is to be applied.
     * @param priority The priority of the flow.
     * @return The generated flow structure.
     */
    public static Flow createICMPFlow(Short tableId, int priority, String outputPort) {

        Integer cookieInt = getRandomNumberInRange(1, 1000000000);

        // start building flow
        FlowBuilder icmpFlow = new FlowBuilder() //
                .setTableId(tableId) //
                .setFlowName("icmpforward");

        // use its own hash code for id.
        icmpFlow.setId(new FlowId(Long.toString(icmpFlow.hashCode())));

        // layer 2
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(new EthernetTypeBuilder()
                        .setType(new EtherType(Long.valueOf(IPV4_ETHER_TYPE))).build());

        // layer 3
        //Ipv4MatchBuilder ipv4MatchBuilder = new Ipv4MatchBuilder()
        //        .setIpv4Destination(new Ipv4Prefix("255.255.255.255/32"));
        IpMatchBuilder ipMatchBuilder = new IpMatchBuilder()
                .setIpProtocol((short) 1);

        Match match = new MatchBuilder()
                .setEthernetMatch(ethernetMatchBuilder.build())
                //.setLayer3Match(ipv4MatchBuilder.build())
                .setIpMatch(ipMatchBuilder.build())
                .build();

        // Create an Apply Action
        List<Action> actionList = new ArrayList<Action>();

        if(outputPort.contains("normal"))
            actionList.add(getSendToNormalAction());
        else if (outputPort.contains("controller"))
            actionList.add(getSendToControllerAction());
        else
            actionList.add(new ActionBuilder()
                    .setAction(new OutputActionCaseBuilder()
                            .setOutputAction(new OutputActionBuilder()
                                    .setOutputNodeConnector(new Uri(outputPort))
                                    .build())
                            .build())
                    .build());

        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actionList)
                .build();

        // Wrap our Apply Action in an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder() //
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()//
                        .setApplyActions(applyActions) //
                        .build()) //
                .build();

        // Put our Instruction in a list of Instructions
        icmpFlow
                .setMatch(match) //
                .setInstructions(new InstructionsBuilder() //
                        .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                        .build()) //
                .setPriority(priority) //
                .setBufferId(OFConstants.OFP_NO_BUFFER) //
                .setHardTimeout(flowHardTimeout) //
                .setIdleTimeout(flowIdleTimeout) //
                .setCookie(new FlowCookie(BigInteger.valueOf(cookieInt)))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        LOG.info("ICMP FLOW COOKIE: " + new FlowCookie(BigInteger.valueOf(cookieInt)));


        return icmpFlow.build();
    }

    /**
     * Creates a Flow structure that matches the ICMP packets and forwards them using the outputPort (normal or controller) action.
     * @param tableId The table identifier for which the flow structure is to be applied.
     * @param priority The priority of the flow.
     * @return The generated flow structure.
     */
    public static Flow createICMPFlowForIPPair(Short tableId, int priority, IPPrefAddrInfo srcIPAddr, IPPrefAddrInfo dstIPAddr, List<String> outputPorts) {

        Integer cookieInt = getRandomNumberInRange(1, 1000000000);

        // start building flow
        FlowBuilder icmpFlow = new FlowBuilder() //
                .setTableId(tableId) //
                .setFlowName("icmpforward");

        // use its own hash code for id.
        icmpFlow.setId(new FlowId(Long.toString(icmpFlow.hashCode())));

        // layer 2
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(new EthernetTypeBuilder()
                        .setType(new EtherType(Long.valueOf(IPV4_ETHER_TYPE))).build());

        // layer 3
        Ipv4MatchBuilder ipv4MatchBuilder = new Ipv4MatchBuilder()
                .setIpv4Source(srcIPAddr.getIPv4Prefix())
                .setIpv4Destination(dstIPAddr.getIPv4Prefix());
        IpMatchBuilder ipMatchBuilder = new IpMatchBuilder()
                .setIpProtocol((short) 1);

        Match match = new MatchBuilder()
                .setEthernetMatch(ethernetMatchBuilder.build())
                .setLayer3Match(ipv4MatchBuilder.build())
                .setIpMatch(ipMatchBuilder.build())
                .build();

        // Create an Apply Action
        List<Action> actionList = new ArrayList<Action>();

        int orderId = 0;
        for(String outputPort: outputPorts) {
            if (outputPort.contains("normal"))
                actionList.add(getSendToNormalAction());
            else if (outputPort.contains("controller"))
                actionList.add(getSendToControllerAction());
            else
                actionList.add(new ActionBuilder()
                        .setOrder(orderId)
                        .setAction(new OutputActionCaseBuilder()
                                .setOutputAction(new OutputActionBuilder()
                                        .setOutputNodeConnector(new Uri(outputPort))
                                        .build())
                                .build())
                        .build());

            orderId++;
        }

        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actionList)
                .build();

        // Wrap our Apply Action in an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder() //
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()//
                        .setApplyActions(applyActions) //
                        .build()) //
                .build();

        // Put our Instruction in a list of Instructions
        icmpFlow.setMatch(match) //
                .setInstructions(new InstructionsBuilder() //
                        .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                        .build()) //
                .setPriority(priority) //
                .setBufferId(OFConstants.OFP_NO_BUFFER) //
                .setHardTimeout(flowHardTimeout) //
                .setIdleTimeout(flowIdleTimeout) //
                .setCookie(new FlowCookie(BigInteger.valueOf(cookieInt)))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        LOG.info("ICMP FLOW COOKIE: " + new FlowCookie(BigInteger.valueOf(cookieInt)));


        return icmpFlow.build();
    }


    public static Flow createDhcpFlow(Short tableId, boolean isDHCPv4, PortNumber sourcePort, PortNumber dstPort, int priority, String outputPort) {

        Integer cookieInt = getRandomNumberInRange(1, 1000000000);

        // start building flow
        FlowBuilder dhcpFlow = new FlowBuilder() //
                .setTableId(tableId) //
                .setFlowName("dhcpforward67");

        // use its own hash code for id.
        dhcpFlow.setId(new FlowId(Long.toString(cookieInt)));

        // layer 2
        EthernetTypeBuilder typeBuilder = new EthernetTypeBuilder();
        if(isDHCPv4)
            typeBuilder.setType(new EtherType(Long.valueOf(IPV4_ETHER_TYPE)));
        else if (!isDHCPv4)
            typeBuilder.setType(new EtherType(Long.valueOf(IPV6_ETHER_TYPE)));

        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(typeBuilder.build());

        // layer 3
        IpMatchBuilder ipMatchBuilder = new IpMatchBuilder()
                .setIpProtocol((short) 17);

        // layer 4
        UdpMatchBuilder udpMatchBuilder = null;
        if(sourcePort != null)
            udpMatchBuilder = new UdpMatchBuilder()
                    .setUdpSourcePort(sourcePort);
        if(dstPort != null)
            udpMatchBuilder = new UdpMatchBuilder()
                    .setUdpDestinationPort(dstPort);

        Match match = new MatchBuilder()
                .setEthernetMatch(ethernetMatchBuilder.build())
                .setIpMatch(ipMatchBuilder.build())
                .setLayer4Match(udpMatchBuilder.build())
                .build();

        // Create an Apply Action
        List<Action> actionList = new ArrayList<Action>();

        // TODO: Must be fixed to send DIRECTLY to Controller
        if(outputPort.contains("normal"))
            actionList.add(getSendToNormalAction());
        else if (outputPort.contains("controller"))
            actionList.add(getSendToControllerAction());
        else
            actionList.add(new ActionBuilder()
                    .setAction(new OutputActionCaseBuilder()
                            .setOutputAction(new OutputActionBuilder()
                                    .setOutputNodeConnector(new Uri(outputPort))
                                    .build())
                            .build())
                    .build());

        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actionList)
                .build();

        // Wrap our Apply Action in an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder() //
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()//
                        .setApplyActions(applyActions) //
                        .build()) //
                .build();


        // Put our Instruction in a list of Instructions
        dhcpFlow.setMatch(match) //
                .setInstructions(new InstructionsBuilder() //
                        .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                        .build()) //
                .setPriority(priority) //
                .setBufferId(OFConstants.OFP_NO_BUFFER) //
                .setHardTimeout(flowHardTimeout) //
                .setIdleTimeout(flowIdleTimeout) //
                .setCookie(new FlowCookie(BigInteger.valueOf(cookieInt)))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        LOG.info("DHCP FLOW COOKIE: " + new FlowCookie(BigInteger.valueOf(cookieInt)));

        return dhcpFlow.build();

    }

    /**
     * Creates a Flow structure that matches the SSH packets sent to destination port 22 and forwards them using the outputPort action.
     * @param tableId The table identifier for which the flow structure is to be applied.
     * @param priority The priority of the flow.
     * @return The generated flow structure.
     */
    public static Flow createSSHFlow(Short tableId, boolean isIpv6Enabled, PortNumber sourcePortMatch, PortNumber dstPortMatch, int priority, String outputPort) {

        Integer cookieInt = getRandomNumberInRange(1, 1000000000);

        // start building flow
        FlowBuilder sshFlow = new FlowBuilder() //
                .setTableId(tableId) //
                .setFlowName("sshforward");

        // use its own hash code for id.
        sshFlow.setId(new FlowId(Long.toString(cookieInt)));

        // layer 2
        EthernetTypeBuilder typeBuilder = new EthernetTypeBuilder();
        if(!isIpv6Enabled)
            typeBuilder.setType(new EtherType(Long.valueOf(IPV4_ETHER_TYPE)));
        else if (isIpv6Enabled)
            typeBuilder.setType(new EtherType(Long.valueOf(IPV6_ETHER_TYPE)));

        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(typeBuilder.build());

        // layer 3
        IpMatchBuilder ipMatchBuilder = new IpMatchBuilder()
                .setIpProtocol((short) 6);

        TcpMatchBuilder tcpMatchBuilder = null;

        // layer 4
        if(sourcePortMatch != null)
            tcpMatchBuilder = new TcpMatchBuilder()
                    .setTcpSourcePort(sourcePortMatch);
        if(dstPortMatch != null)
            tcpMatchBuilder = new TcpMatchBuilder()
                    .setTcpDestinationPort(dstPortMatch);

        Match match = new MatchBuilder()
                .setEthernetMatch(ethernetMatchBuilder.build())
                .setIpMatch(ipMatchBuilder.build())
                .setLayer4Match(tcpMatchBuilder.build())
                .build();

        // Create an Apply Action
        List<Action> actionList = new ArrayList<Action>();

        // TODO: Must be fixed to send DIRECTLY to Controller
        if(outputPort.contains("normal"))
            actionList.add(getSendToNormalAction());
        else if (outputPort.contains("controller"))
            actionList.add(getSendToControllerAction());
        else
            actionList.add(new ActionBuilder()
                    .setAction(new OutputActionCaseBuilder()
                            .setOutputAction(new OutputActionBuilder()
                                    .setOutputNodeConnector(new Uri(outputPort))
                                    .build())
                            .build())
                    .build());

        // Create an Apply Action
        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actionList)
                .build();

        // Wrap our Apply Action in an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder() //
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()//
                        .setApplyActions(applyActions) //
                        .build()) //
                .build();

        // Put our Instruction in a list of Instructions
        sshFlow.setMatch(match) //
                .setInstructions(new InstructionsBuilder() //
                        .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                        .build()) //
                .setPriority(priority) //
                .setBufferId(OFConstants.OFP_NO_BUFFER) //
                .setHardTimeout(flowHardTimeout) //
                .setIdleTimeout(flowIdleTimeout) //
                .setCookie(new FlowCookie(BigInteger.valueOf(cookieInt)))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        LOG.info("SSH FLOW COOKIE: " + new FlowCookie(BigInteger.valueOf(cookieInt)));

        return sshFlow.build();
    }


    /**
     * Creates an IPv4/IPv6 Flow with optional Match instance specified for Layer 4 (port numbers).
     * @param tableId
     * @param srcPort
     * @param srcIPAddr
     * @param dstPort
     * @param dstIpAddr
     * @param priority
     * @param outputPorts
     * @return
     */
    public static Flow createIPLayer4Flow(Short tableId, PortNumber srcPort, IPPrefAddrInfo srcIPAddr, PortNumber dstPort,
                                   IPPrefAddrInfo dstIpAddr, int priority, List<String> outputPorts) {

        Integer cookieInt = getRandomNumberInRange(1, 1000000000);

        // start building flow
        FlowBuilder ofFlow = new FlowBuilder() //
                .setTableId(tableId) //
                .setFlowName("openflowforward");

        // use its own hash code for id.
        ofFlow.setId(new FlowId(Long.toString(cookieInt)));

        // layer 2
        EthernetTypeBuilder typeBuilder = new EthernetTypeBuilder();
        if(srcIPAddr.isIpv4() || dstIpAddr.isIpv4())
            typeBuilder.setType(new EtherType(Long.valueOf(IPV4_ETHER_TYPE)));
        else if (srcIPAddr.isIpv6() || dstIpAddr.isIpv6())
            typeBuilder.setType(new EtherType(Long.valueOf(IPV6_ETHER_TYPE)));

        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(typeBuilder.build());

        IpMatchBuilder ipMatchBuilder = new IpMatchBuilder();

        // layer 3
        if(srcPort != null || dstPort !=null)
            ipMatchBuilder.setIpProtocol((short) 6);

        MatchBuilder matchBuilder = new MatchBuilder()
                .setEthernetMatch(ethernetMatchBuilder.build ())
                .setIpMatch(ipMatchBuilder.build());

        if (srcIPAddr.isIpv4() || dstIpAddr.isIpv4()) {
            Ipv4MatchBuilder ipv4MatchBuilder = new Ipv4MatchBuilder();
            if (srcIPAddr.getIPv4Prefix() != null)
                ipv4MatchBuilder.setIpv4Source(srcIPAddr.getIPv4Prefix());
            if (dstIpAddr.getIPv4Prefix() != null)
                ipv4MatchBuilder.setIpv4Destination(dstIpAddr.getIPv4Prefix());

            matchBuilder.setLayer3Match(ipv4MatchBuilder.build());
        } else if (srcIPAddr.isIpv6() || dstIpAddr.isIpv6()) {
            Ipv6MatchBuilder ipv6MatchBuilder = new Ipv6MatchBuilder();
            if (srcIPAddr.getIPv6Prefix() != null)
                ipv6MatchBuilder.setIpv6Source(srcIPAddr.getIPv6Prefix());
            if (dstIpAddr.getIPv6Prefix() != null)
                ipv6MatchBuilder.setIpv6Destination(dstIpAddr.getIPv6Prefix());

            matchBuilder.setLayer3Match(ipv6MatchBuilder.build());
        }


        // layer 4
        TcpMatchBuilder tcpMatchBuilder = new TcpMatchBuilder();
        if(srcPort != null)
            tcpMatchBuilder.setTcpSourcePort(srcPort);
        if(dstPort != null)
            tcpMatchBuilder.setTcpDestinationPort(dstPort);
        if(srcPort != null || dstPort !=null)
            matchBuilder.setLayer4Match(tcpMatchBuilder.build());

        Match match= matchBuilder.build();

        // Create an Apply Action
        List<Action> actionList = new ArrayList<Action>();

        int orderId = 0;
        // TODO: Must be fixed to send DIRECTLY to Controller
        for(String outputPort:outputPorts) {
            if (outputPort.contains("normal"))
                actionList.add(getSendToNormalAction());
            else if (outputPort.contains("controller"))
                actionList.add(getSendToControllerAction());
            else if (outputPort.contains("local"))
                actionList.add(getSendToLocalAction());
            else
                actionList.add(new ActionBuilder()
                        .setOrder(orderId)
                        .setAction(new OutputActionCaseBuilder()
                                .setOutputAction(new OutputActionBuilder()
                                        .setOutputNodeConnector(new Uri(outputPort))
                                        .build())
                                .build())
                        .build());

            orderId = orderId+1;
        }

        // Create an Apply Action
        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actionList)
                .build();

        // Wrap our Apply Action in an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder() //
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()//
                        .setApplyActions(applyActions) //
                        .build()) //
                .build();

        // Put our Instruction in a list of Instructions
        ofFlow.setMatch(match) //
                .setInstructions(new InstructionsBuilder() //
                        .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                        .build()) //
                .setPriority(priority) //
                .setBufferId(OFConstants.OFP_NO_BUFFER) //
                .setHardTimeout(flowHardTimeout) //
                .setIdleTimeout(flowIdleTimeout) //
                .setCookie(new FlowCookie(BigInteger.valueOf(cookieInt)))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        LOG.info("IPv4 FLOW COOKIE: " + new FlowCookie(BigInteger.valueOf(cookieInt)));

        return ofFlow.build();
    }

    /**
     * Creates an IPv4/IPv6 Flow with optional Match instance specified for Layer 4 (port numbers).
     * @param tableId
     * @param srcPort
     * @param srcIPAddr
     * @param dstPort
     * @param dstIpAddr
     * @param priority
     * @param inputPort
     * @param outputPorts
     * @param switchId
     * @return
     */
    public static Flow createIPLayer4FlowGeneral(Short tableId, PortNumber srcPort, IPPrefAddrInfo srcIPAddr, PortNumber dstPort,
                                                 IPPrefAddrInfo dstIpAddr, int priority, String inputPort, List<String> outputPorts, String switchId) {

        Integer cookieInt = getRandomNumberInRange(1, 1000000000);

        // start building flow
        FlowBuilder ofFlow = new FlowBuilder() //
                .setTableId(tableId) //
                .setFlowName("openflowforward");

        // use its own hash code for id.
        ofFlow.setId(new FlowId(Long.toString(cookieInt)));

        // new match builder
        MatchBuilder matchBuilder = new MatchBuilder();

        // phy layer
        if (inputPort != null) {
            NodeConnectorId nodeConnectorId;
            nodeConnectorId = new NodeConnectorId(switchId + ":" + inputPort);
            matchBuilder.setInPort(nodeConnectorId);
        }


        // layer 2
        EthernetTypeBuilder typeBuilder = new EthernetTypeBuilder();
        if(srcIPAddr.isIpv4() || dstIpAddr.isIpv4())
            typeBuilder.setType(new EtherType(Long.valueOf(IPV4_ETHER_TYPE)));
        else if (srcIPAddr.isIpv6() || dstIpAddr.isIpv6())
            typeBuilder.setType(new EtherType(Long.valueOf(IPV6_ETHER_TYPE)));

        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(typeBuilder.build());

        IpMatchBuilder ipMatchBuilder = new IpMatchBuilder();

        // layer 3
        if(srcPort != null || dstPort !=null)
            ipMatchBuilder.setIpProtocol((short) 6);


        matchBuilder.setEthernetMatch(ethernetMatchBuilder.build ())
                .setIpMatch(ipMatchBuilder.build());


        if (srcIPAddr.isIpv4() || dstIpAddr.isIpv4()) {
            Ipv4MatchBuilder ipv4MatchBuilder = new Ipv4MatchBuilder();
            if (srcIPAddr.getIPv4Prefix() != null)
                ipv4MatchBuilder.setIpv4Source(srcIPAddr.getIPv4Prefix());
            if (dstIpAddr.getIPv4Prefix() != null)
                ipv4MatchBuilder.setIpv4Destination(dstIpAddr.getIPv4Prefix());

            matchBuilder.setLayer3Match(ipv4MatchBuilder.build());
        } else if (srcIPAddr.isIpv6() || dstIpAddr.isIpv6()) {
            Ipv6MatchBuilder ipv6MatchBuilder = new Ipv6MatchBuilder();
            if (srcIPAddr.getIPv6Prefix() != null)
                ipv6MatchBuilder.setIpv6Source(srcIPAddr.getIPv6Prefix());
            if (dstIpAddr.getIPv6Prefix() != null)
                ipv6MatchBuilder.setIpv6Destination(dstIpAddr.getIPv6Prefix());

            matchBuilder.setLayer3Match(ipv6MatchBuilder.build());
        }

        // layer 4
        TcpMatchBuilder tcpMatchBuilder = new TcpMatchBuilder();
        if(srcPort != null)
            tcpMatchBuilder.setTcpSourcePort(srcPort);
        if(dstPort != null)
            tcpMatchBuilder.setTcpDestinationPort(dstPort);
        if(srcPort != null || dstPort !=null)
            matchBuilder.setLayer4Match(tcpMatchBuilder.build());

        Match match= matchBuilder.build();

        // Create an Apply Action
        List<Action> actionList = new ArrayList<Action>();

        int orderId = 0;
        // TODO: Must be fixed to send DIRECTLY to Controller
        for(String outputPort:outputPorts) {
            if (outputPort.contains("normal"))
                actionList.add(getSendToNormalAction());
            else if (outputPort.contains("controller"))
                actionList.add(getSendToControllerAction());
            else if (outputPort.contains("local"))
                actionList.add(getSendToLocalAction());
            else
                actionList.add(new ActionBuilder()
                        .setOrder(orderId)
                        .setAction(new OutputActionCaseBuilder()
                                .setOutputAction(new OutputActionBuilder()
                                        .setOutputNodeConnector(new Uri(outputPort))
                                        .build())
                                .build())
                        .build());

            orderId = orderId+1;
        }

        // Create an Apply Action
        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actionList)
                .build();

        // Wrap our Apply Action in an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder() //
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()//
                        .setApplyActions(applyActions) //
                        .build()) //
                .build();

        // Put our Instruction in a list of Instructions
        ofFlow.setMatch(match) //
                .setInstructions(new InstructionsBuilder() //
                        .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                        .build()) //
                .setPriority(priority) //
                .setBufferId(OFConstants.OFP_NO_BUFFER) //
                .setHardTimeout(flowHardTimeout) //
                .setIdleTimeout(flowIdleTimeout) //
                .setCookie(new FlowCookie(BigInteger.valueOf(cookieInt)))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        LOG.info("IPv4 FLOW COOKIE: " + new FlowCookie(BigInteger.valueOf(cookieInt)));

        return ofFlow.build();
    }

    /**
     * Creates a flow that matches only Ethernet type that is equal to ARP
     * @param tableId
     * @param priority
     * @param inputPort
     * @param outputPorts
     * @param switchId
     * @return
     */
    public static Flow createARPFlowGeneral(Short tableId, int priority, String inputPort, List<String> outputPorts, String switchId) {

        Integer cookieInt = getRandomNumberInRange(1, 1000000000);

        // start building flow
        FlowBuilder ofFlow = new FlowBuilder() //
                .setTableId(tableId) //
                .setFlowName("openflowforward");

        // use its own hash code for id.
        ofFlow.setId(new FlowId(Long.toString(cookieInt)));

        // new match builder
        MatchBuilder matchBuilder = new MatchBuilder();

        // phy layer
        if (inputPort != null) {
            NodeConnectorId nodeConnectorId;
            nodeConnectorId = new NodeConnectorId(switchId + ":" + inputPort);
            matchBuilder.setInPort(nodeConnectorId);
        }

        // layer 2 ARP matching
        EthernetTypeBuilder typeBuilder = new EthernetTypeBuilder();
        typeBuilder.setType(new EtherType(Long.valueOf(ARP_ETHER_TYPE)));
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(typeBuilder.build());

        matchBuilder.setEthernetMatch(ethernetMatchBuilder.build ());

        Match match= matchBuilder.build();

        // Create an Apply Action
        List<Action> actionList = new ArrayList<Action>();

        int orderId = 0;
        for(String outputPort:outputPorts) {
            if (outputPort.contains("normal"))
                actionList.add(getSendToNormalAction());
            else if (outputPort.contains("controller"))
                actionList.add(getSendToControllerAction());
            else if (outputPort.contains("local"))
                actionList.add(getSendToLocalAction());
            else
                actionList.add(new ActionBuilder()
                        .setOrder(orderId)
                        .setAction(new OutputActionCaseBuilder()
                                .setOutputAction(new OutputActionBuilder()
                                        .setOutputNodeConnector(new Uri(outputPort))
                                        .build())
                                .build())
                        .build());

            orderId = orderId+1;
        }

        // Create an Apply Action
        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actionList)
                .build();

        // Wrap our Apply Action in an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder() //
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()//
                        .setApplyActions(applyActions) //
                        .build()) //
                .build();

        // Put our Instruction in a list of Instructions
        ofFlow.setMatch(match) //
                .setInstructions(new InstructionsBuilder() //
                        .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                        .build()) //
                .setPriority(priority) //
                .setBufferId(OFConstants.OFP_NO_BUFFER) //
                .setHardTimeout(flowHardTimeout) //
                .setIdleTimeout(flowIdleTimeout) //
                .setCookie(new FlowCookie(BigInteger.valueOf(cookieInt)))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        LOG.info("ARP FLOW COOKIE: " + new FlowCookie(BigInteger.valueOf(cookieInt)));

        return ofFlow.build();
    }

    public static Flow createDhcpFlowMultipleOutputPortsOneInputPort(Short tableId, boolean isDHCPv4, PortNumber sourcePort, PortNumber dstPort,
                                                                     int priority, String inputPort, List<String> outputPorts, String switchId) {

        Integer cookieInt = getRandomNumberInRange(1, 1000000000);

        // start building flow
        FlowBuilder dhcpFlow = new FlowBuilder() //
                .setTableId(tableId) //
                .setFlowName("dhcpforwardmultipleoutputportsoneinputport");

        // use its own hash code for id.
        dhcpFlow.setId(new FlowId(Long.toString(cookieInt)));

        // new match builder
        MatchBuilder matchBuilder = new MatchBuilder();

        // phy layer
        if (inputPort != null) {
            NodeConnectorId nodeConnectorId;
            nodeConnectorId = new NodeConnectorId(switchId + ":" + inputPort);
            matchBuilder.setInPort(nodeConnectorId);
        }

        // layer 2
        EthernetTypeBuilder typeBuilder = new EthernetTypeBuilder();
        if(isDHCPv4)
            typeBuilder.setType(new EtherType(Long.valueOf(IPV4_ETHER_TYPE)));
        else if (!isDHCPv4)
            typeBuilder.setType(new EtherType(Long.valueOf(IPV6_ETHER_TYPE)));

        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(typeBuilder.build());

        // layer 3
        IpMatchBuilder ipMatchBuilder = new IpMatchBuilder()
                .setIpProtocol((short) 17);

        // layer 4
        UdpMatchBuilder udpMatchBuilder = null;
        if(sourcePort != null)
            udpMatchBuilder = new UdpMatchBuilder()
                    .setUdpSourcePort(sourcePort);
        if(dstPort != null)
            udpMatchBuilder = new UdpMatchBuilder()
                    .setUdpDestinationPort(dstPort);

        Match match = matchBuilder
                .setEthernetMatch(ethernetMatchBuilder.build())
                .setIpMatch(ipMatchBuilder.build())
                .setLayer4Match(udpMatchBuilder.build())
                .build();

        // Create an Apply Action
        List<Action> actionList = new ArrayList<Action>();

        // TODO: Must be fixed to send DIRECTLY to Controller
        int orderId = 0;
        for (String op: outputPorts) {
            if (op.contains("controller")){
                actionList.add(getSendToControllerAction());
            } else {
                actionList.add(new ActionBuilder()
                        .setOrder(orderId)
                        .setKey(new ActionKey(orderId))
                        .setAction(new OutputActionCaseBuilder()
                                .setOutputAction(new OutputActionBuilder()
                                        .setOutputNodeConnector(new Uri(op))
                                        .build())
                                .build())
                        .build());
            }

            orderId++;
        }

        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actionList)
                .build();

        // Wrap our Apply Action in an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder() //
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()//
                        .setApplyActions(applyActions) //
                        .build()) //
                .build();


        // Put our Instruction in a list of Instructions
        dhcpFlow.setMatch(match) //
                .setInstructions(new InstructionsBuilder() //
                        .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                        .build()) //
                .setPriority(priority) //
                .setBufferId(OFConstants.OFP_NO_BUFFER) //
                .setHardTimeout(flowHardTimeout) //
                .setIdleTimeout(flowIdleTimeout) //
                .setCookie(new FlowCookie(BigInteger.valueOf(cookieInt)))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        LOG.info("DHCP FLOW COOKIE: " + new FlowCookie(BigInteger.valueOf(cookieInt)));

        return dhcpFlow.build();

    }

    /**
     * Returns all NodeConnectors for the provided nodeId
     *
     * @param nodeId
     * @param dataBroker
     * @return
     */
    public static List<NodeConnector> getAllNodeConnectorsFromNode(String nodeId, DataBroker dataBroker) {

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> nodeInstanceIdentifier
                = InstanceIdentifierUtils.createNodePath(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId(nodeId));

        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node node = null;
        ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction();
        try {
            Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> nodeOptional = readOnlyTransaction
                    .read(LogicalDatastoreType.OPERATIONAL, nodeInstanceIdentifier).get();
            if (nodeOptional.isPresent()) {
                node = nodeOptional.get();
            }
        } catch (Exception e) {
            LOG.error("Error reading node {}", nodeInstanceIdentifier);
            readOnlyTransaction.close();
            throw new RuntimeException(
                    "Error reading from operational store, node : " + nodeInstanceIdentifier, e);
        }
        readOnlyTransaction.close();
        try {
            node.getNodeConnector();
        } catch (NullPointerException e) {
            LOG.warn("NodeConnectors still not available in DS for the node {}", nodeId);
            return null;
        }

        return node.getNodeConnector();
    }

    /**
     * Converts NodeId to Node for the given topology
     *
     * @param nodesId
     * @param topologyId
     * @param dataBroker
     * @return
     */
    public static List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node> getTopologyNodesFromTopologyNodeIds(List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId> nodesId, String topologyId, DataBroker dataBroker) {
        List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node> desiredNodes = new ArrayList<>();
        List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node> allNodes = getNetworkTopologyNodes(topologyId, dataBroker);
        for (org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node node: allNodes) {
            if (nodesId.contains(node.getNodeId())) {
                desiredNodes.add(node);
            }
        }

        return desiredNodes;
    }

    /**
     * Returns a list of all nodes stored in the NetworkTopology datastore
     *
     * @param topologyId
     * @return List<Node>
     */
    public static List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node> getNetworkTopologyNodes(String topologyId, DataBroker dataBroker) {

        InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                .generateTopologyInstanceIdentifier(topologyId);

        Topology topology = null;
        ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction();
        try {
            Optional<Topology> topologyOptional = readOnlyTransaction
                    .read(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier).get();
            if (topologyOptional.isPresent()) {
                topology = topologyOptional.get();
            }
        } catch (Exception e) {
            LOG.error("Error reading topology {}", topologyInstanceIdentifier);
            readOnlyTransaction.close();
            throw new RuntimeException(
                    "Error reading from operational store, topology : " + topologyInstanceIdentifier, e);
        }
        readOnlyTransaction.close();
        if (topology == null) {
            return null;
        }

        List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node> topologyNodes = topology.getNode();
        if (topologyNodes == null || topologyNodes.isEmpty()) {
            return null;
        }

        return topologyNodes;
    }


}
