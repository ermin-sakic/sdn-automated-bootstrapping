package eu.virtuwind.resourcemanager.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.opendaylight.openflowplugin.api.OFConstants;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Prefix;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;

//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetQueueActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.queue.action._case.SetQueueActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Instructions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflow.protocol.rev130731.multipart.reply.multipart.reply.body.multipart.reply.meter.config._case.multipart.reply.meter.config.MeterConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * class PathConfigurator to configure the paths by installing flows
 * privides methods to install the flows and remove the paths
 * {@link PathConfigurator}
 */
public class PathConfigurator {


    //Logger for debugging
    private static final Logger LOG = LoggerFactory.getLogger(PathConfigurator.class);

    //salflow service to add/remove  the flow in the network
    private SalFlowService salFlowService;

    //counter for the path ID, static to ensure uniqueness
    private static long counter = 0;

    //list of paths to store all the paths created
    private static List<Path> listofPaths = new ArrayList<Path>();

    //flow cookie which serves as unique id for flows
    private AtomicLong flowCookieInc = new AtomicLong(0x2a00000000000000L);

    /**
     * Constructor to initialise the SalFlowService to be able to add flows
     * @param  salFlowService {@link SalFlowService}
     */
    public PathConfigurator(SalFlowService salFlowService) {
        this.salFlowService = salFlowService;
    }


    /**
     * Method to create and send/add a flow/flows to the network and
     * return a Path ID in which the flows are stored
     * for them to be deleted/referenced in the future
     *
     * @param sourceIP  {@link Ipv4Prefix}        - Ip address of the source
     * @param destinationIP {@link Ipv4Prefix}    - IP address of the destination
     * @param sourcePort  {@link Integer}             - Port of the source
     * @param destPort    {@link Integer}             - Port of the destination
     * @param protocol    {@link Short}           - Protocol number such as 6 for TCP or 17 for UDP
     * @param physicalPathLinks {@link List<String>}- Links from which flows should be created
     * @param queuesOnPath {@link int[]}      - queues for each of the specific links
     * @param metershaping {{@link MeterConfig}}   -  meterconfiguration -- (ignored for first cycle, use null)
     * @return Long {@link Long} - Path IlD
     */
    public Long createAndSendFlow(Ipv4Prefix sourceIP, Ipv4Prefix destinationIP,
                                  Integer sourcePort, Integer destPort, Short protocol, List<String> physicalPathLinks, int[] queuesOnPath, MeterConfig metershaping) {
        if(physicalPathLinks.get(0).startsWith("host"))
            physicalPathLinks.remove(0);

        LOG.info("Physical path links on the path: " + physicalPathLinks.toString());

        List<Flow> flows = createFlows(sourceIP, destinationIP, sourcePort, destPort, protocol, physicalPathLinks, queuesOnPath);

        Path p = new Path(counter++, flows);

        listofPaths.add(p);


        for (int i = 0; i < physicalPathLinks.size(); i++) {

            //store the edge_switch name from the links
            String edge_switch = physicalPathLinks.get(i).split(":")[0] + ":" + physicalPathLinks.get(i).split(":")[1];

            //the flow which was created
            Flow createdFlow = flows.get(i);

            //add it to the map inside the path
            p.addToMap(createdFlow, edge_switch);

            //get the add input flow to add to sal
            final AddFlowInput flow = getAddFlowInputToSend(edge_switch, createdFlow);

            // add flow to sal
            salFlowService.addFlow(flow);


        }

        return p.getUniqueID();

    }

    /**
     * Method to create the addFlowInput for it to be added to sal flow service
     *
     * @param edge_switch {@link String} - the switch for the flow to be added on
     * @param createdFlow {@link Flow} - created flow to be added
     * @return  {@link AddFlowInput} - addflowinput to be added to salflow
     */
    private AddFlowInput getAddFlowInputToSend(String edge_switch, Flow createdFlow) {
        InstanceIdentifier<Flow> flowPath = InstanceIdentifier
                .builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(edge_switch)))
                .augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(createdFlow.getTableId()))
                .child(Flow.class, new FlowKey(createdFlow.getId())).build();


        final AddFlowInputBuilder builder = new AddFlowInputBuilder(createdFlow);
        final InstanceIdentifier<Table> tableInstanceId = flowPath
                .<Table>firstIdentifierOf(Table.class);
        final InstanceIdentifier<Node> nodeInstanceId = flowPath
                .<Node>firstIdentifierOf(Node.class);
        builder.setNode(new NodeRef(nodeInstanceId));
        builder.setFlowTable(new FlowTableRef(tableInstanceId));
        builder.setTransactionUri(new Uri(createdFlow.getId().getValue()));
        return builder.build();
    }

    /**
     * Method to create all the flows to be added to sal flow service
     *
     * @param sourceIP  {@link Ipv4Prefix}        - Ip address of the source
     * @param destinationIP {@link Ipv4Prefix}    - IP address of the destination
     * @param sourcePort  {@link Integer}             - Port of the source
     * @param destPort    {@link Integer}             - Port of the destination
     * @param protocol    {@link Short}           - Protocol number such as 6 for TCP or 17 for UDP
     * @param physicalPathLinks {@link List<String>}- Links from which flows should be created
     * @param queuesOnPath {@link int[]}
     * @return {@link List<Flow>} - list of created flows
     */

    private List<Flow> createFlows(Ipv4Prefix sourceIP, Ipv4Prefix destinationIP, Integer sourcePort, Integer destPort, Short protocol, List<String> physicalPathLinks, int[] queuesOnPath) {
        FlowBuilder flowBuilder = new FlowBuilder()
                .setTableId((short) 0)
                .setFlowName("random");

        //Flow ID
        flowBuilder.setId(new FlowId(Long.toString(flowBuilder.hashCode())));

        TcpMatchBuilder tcpMatchBuilder = new TcpMatchBuilder().setTcpSourcePort(new PortNumber(sourcePort)).setTcpDestinationPort(new PortNumber(destPort));
        IpMatchBuilder ipMatchBuilder = new IpMatchBuilder().setIpProtocol(protocol);
        Match match = new MatchBuilder()

                .setIpMatch(ipMatchBuilder.build())
                .setLayer4Match(tcpMatchBuilder.build())

                .setLayer3Match(new Ipv4MatchBuilder()
                        .setIpv4Source(sourceIP)
                        .setIpv4Destination(destinationIP)
                        .build())
                .setEthernetMatch(new EthernetMatchBuilder()
                        .setEthernetType(new EthernetTypeBuilder()
                                .setType(new EtherType(0x0800L))
                                .build())
                        .build())
                .build();

        ActionBuilder actionBuilder = new ActionBuilder();
        List<Action> actions = new ArrayList<Action>();


        List<List<Action>> listofactions = new ArrayList<>();

        for (int j = 0; j < queuesOnPath.length; ++j) {
            listofactions.add(new ArrayList<Action>());
        }

        for (int i = 0; i < queuesOnPath.length; i++) {

            Action queueAction = actionBuilder
                    .setOrder(0).setAction(new SetQueueActionCaseBuilder()
                            .setSetQueueAction(new SetQueueActionBuilder()
                                    .setQueueId(Long.parseLong(String.valueOf(queuesOnPath[0])))
                                    .build())
                            .build())

                    .build();


            Action outputNodeConnectorAction = actionBuilder
                    .setOrder(1).setAction(new OutputActionCaseBuilder()
                            .setOutputAction(new OutputActionBuilder()
                                    .setOutputNodeConnector(new Uri(physicalPathLinks.get(i).split("/")[0].split(":")[2]))
                                    .build())
                            .build())
                    .build();


            listofactions.get(i).add(queueAction);
            listofactions.get(i).add(outputNodeConnectorAction);

        }


        //ApplyActions

        List<Flow> flows = new ArrayList<>();

        for (int i = 0; i < listofactions.size(); i++) {

            ApplyActions applyActions = new ApplyActionsBuilder().setAction(listofactions.get(i)).build();

            //Instruction
            Instruction applyActionsInstruction = new InstructionBuilder() //
                    .setOrder(0).setInstruction(new ApplyActionsCaseBuilder()//
                            .setApplyActions(applyActions) //
                            .build()) //
                    .build();

            Instructions applyInstructions = new InstructionsBuilder()
                    .setInstruction(ImmutableList.of(applyActionsInstruction))
                    .build();


            flows.add(flowBuilder
                    .setMatch(match)
                    .setBufferId(OFConstants.OFP_NO_BUFFER)
                    .setInstructions(applyInstructions)
                    .setPriority(1000)
                    .setHardTimeout((int) 3000)
                    .setIdleTimeout((int) 3000)
                    .setCookie(new FlowCookie(BigInteger.valueOf(flowCookieInc.getAndIncrement())))
                    .setFlags(new FlowModFlags(false, false, false, false, false)).build());


        }
        return flows;
    }

    /**
     * Method to create and send the best embeded flow in the network without caring about the queues and not making a path for those flows
     *
     * @param sourceIP  {@link Ipv4Prefix}         - Ip address of source
     * @param destinationIP  {@link Ipv4Prefix}      - Ip address of destination
     * @param sourcePort    {@link Integer}      - Source port
     * @param destPort     {@link Integer}     - Destination Port
     * @param protocol     {@link Integer}     - Protocol number (TCP/UDP)
     * @param physicalPathLinks {@link List<String>} - Links in the topology
     * @param idletimeout   {@link Integer}    - idle timeout of the flows
     * @param hardtimeout   {@link Integer}    - hard timeout for the flows
     * @param metershaping   {@link MeterConfig}   - meterconfiguration (Ignored, use null)
     */

    public void bestembededflow(Ipv4Prefix sourceIP, Ipv4Prefix destinationIP,
                                Integer sourcePort, Integer destPort, Integer protocol, List<String> physicalPathLinks, int idletimeout, int hardtimeout, MeterConfig metershaping) {
        if(physicalPathLinks.get(0).startsWith("host"))
            physicalPathLinks.remove(0);

        LOG.info("Physical path links on the path: " + physicalPathLinks.toString());

        List<Flow> flows = createFlowsWithoutQueues(sourceIP, destinationIP, sourcePort, destPort, protocol, physicalPathLinks, idletimeout, hardtimeout);


        for (int i = 0; i < physicalPathLinks.size(); i++) {

            String edge_switch = physicalPathLinks.get(i).split(":")[0] + ":" + physicalPathLinks.get(i).split(":")[1];
            Flow createdFlow = flows.get(i);

            LOG.info("Pushing the flow on node {} using port no. {}", edge_switch, createdFlow.getOutPort());

            final AddFlowInput flow = getAddFlowInputToSend(edge_switch, createdFlow);

            // add flow to sal

            salFlowService.addFlow(flow);

        }


    }

    /**
     * Method to create flows without the queues and not making a path for such flows
     *
     * @param sourceIP  {@link Ipv4Prefix}         - Ip address of source
     * @param destinationIP  {@link Ipv4Prefix}    - Ip address of destination
     * @param sourcePort    {@link Integer}        - Source port
     * @param destPort     {@link Integer}         - Destination Port
     * @param protocol     {@link Integer}         - Protocol number (TCP/UDP)
     * @param physicalPathLinks {@link List<String>} - Links in the topology
     * @param idletimeout   {@link Integer}    - idle timeout of the flows
     * @param hardtimeout   {@link Integer}    - hard timeout for the flows
     * @return {@link List<Flow>} - list of created flows
     */


    private List<Flow> createFlowsWithoutQueues(Ipv4Prefix sourceIP, Ipv4Prefix destinationIP, Integer sourcePort, Integer destPort, Integer protocol, List<String> physicalPathLinks, int idletimeout, int hardtimeout) {
        FlowBuilder flowBuilder = new FlowBuilder()
                .setTableId((short) 0)
                .setFlowName("random");

        //Flow ID
        flowBuilder.setId(new FlowId(Long.toString(flowBuilder.hashCode())));

        TcpMatchBuilder tcpMatchBuilder = null;
        IpMatchBuilder ipMatchBuilder = null;

        if (sourcePort != null && destPort != null) {

            tcpMatchBuilder = new TcpMatchBuilder().setTcpSourcePort(new PortNumber(sourcePort)).setTcpDestinationPort(new PortNumber(destPort));



        } else {

            //values are null

            tcpMatchBuilder = new TcpMatchBuilder();
            //   ipMatchBuilder = new IpMatchBuilder();


        }

        if (protocol != null) {
            ipMatchBuilder = new IpMatchBuilder().setIpProtocol(protocol.shortValue());
        } else {
            ipMatchBuilder = new IpMatchBuilder();
        }

        Match match = new MatchBuilder()

                .setIpMatch(ipMatchBuilder.build())
                .setLayer4Match(tcpMatchBuilder.build())

                .setLayer3Match(new Ipv4MatchBuilder()
                        .setIpv4Source(sourceIP)
                        .setIpv4Destination(destinationIP)
                        .build())
                .setEthernetMatch(new EthernetMatchBuilder()
                        .setEthernetType(new EthernetTypeBuilder()
                                .setType(new EtherType(0x0800L))
                                .build())
                        .build())
                .build();
        ActionBuilder actionBuilder = new ActionBuilder();
        List<Action> actions = new ArrayList<Action>();

        //Actions
        //currently changing tos and sending to output connector


        List<List<Action>> listofactions = new ArrayList<>();

        for (int j = 0; j < physicalPathLinks.size(); ++j) {
            listofactions.add(new ArrayList<Action>());
        }

        for (int i = 0; i < physicalPathLinks.size(); i++) {
            LOG.info("Setting the output port to: " + physicalPathLinks.get(i).split(":")[2]);

            Action outputNodeConnectorAction = actionBuilder
                    .setOrder(1).setAction(new OutputActionCaseBuilder()
                            .setOutputAction(new OutputActionBuilder()
                                    .setOutputNodeConnector(new Uri(physicalPathLinks.get(i).split("/")[0].split(":")[2]))
                                    .build())
                            .build())
                    .build();


            listofactions.get(i).add(outputNodeConnectorAction);
        }
        LOG.info(listofactions.toString());

        //ApplyActions

        List<Flow> flows = new ArrayList<>();

        for (int i = 0; i < listofactions.size(); i++) {


            ApplyActions applyActions = new ApplyActionsBuilder().setAction(listofactions.get(i)).build();

            //Instruction
            Instruction applyActionsInstruction = new InstructionBuilder() //
                    .setOrder(0).setInstruction(new ApplyActionsCaseBuilder()//
                            .setApplyActions(applyActions) //
                            .build()) //
                    .build();

            Instructions applyInstructions = new InstructionsBuilder()
                    .setInstruction(ImmutableList.of(applyActionsInstruction))
                    .build();

            // Put our Instruction in a list of Instructions


            flows.add(flowBuilder
                    .setMatch(match)
                    .setBufferId(OFConstants.OFP_NO_BUFFER)
                    .setInstructions(applyInstructions)
                    .setPriority(1000)
                    .setHardTimeout(hardtimeout)
                    .setIdleTimeout(idletimeout)
                    .setCookie(new FlowCookie(BigInteger.valueOf(flowCookieInc.getAndIncrement())))
                    .setFlags(new FlowModFlags(false, false, false, false, false)).build());
        }
        LOG.info(flows.toString());

        return flows;
    }


    /**
     * Method to remove the path from the network given the path id
     * deletes all the flows within the path
     *
     * @param pathID {@link Long} - The path to delete
     * @return {@link Boolean} true if successfully removed all the paths, otherwise false.
     */

    public boolean removePath(long pathID) {

        //Found path will be stored in this
        Path pathFound = null;

        //find if a path with the path id exists
        for (Path p : listofPaths) {

            if (p.getUniqueID() == pathID) {

                pathFound = p;

            }

        }

        //ensuring that path is found
        if (pathFound != null) {

            //path found

            //Going through all the flows within the path to delete them one by one
            for (int i = 0; i < pathFound.getFlowlist().size(); i++) {


                Flow flowtoDelete = pathFound.getFlowlist().get(i);

                String edge_switch = pathFound.getFlowStringMap().get(flowtoDelete);

                //identify the flow
                InstanceIdentifier<Flow> flowPath = InstanceIdentifier
                        .builder(Nodes.class)
                        .child(Node.class, new NodeKey(new NodeId(edge_switch)))
                        .augmentation(FlowCapableNode.class)
                        .child(Table.class, new TableKey(flowtoDelete.getTableId()))
                        .child(Flow.class, new FlowKey(flowtoDelete.getId())).build();


                RemoveFlowInputBuilder b = new RemoveFlowInputBuilder(flowtoDelete);
                InstanceIdentifier<Table> tableInstanceId = flowPath
                        .<Table>firstIdentifierOf(Table.class);
                InstanceIdentifier<Node> nodeInstanceId = flowPath
                        .<Node>firstIdentifierOf(Node.class);
                b.setNode(new NodeRef(nodeInstanceId));
                b.setFlowTable(new FlowTableRef(tableInstanceId));
                b.setTransactionUri(new Uri(flowtoDelete.getId().getValue()));

                //the flow to remove
                RemoveFlowInput flow = b.build();

                // remove flow from sal
                salFlowService.removeFlow(flow);

            }


        } else {

            //no path found hence nothing to delete

            return false;
        }

        // all flows successfully removed
        return true;

    }
}