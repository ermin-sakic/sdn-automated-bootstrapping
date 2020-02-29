package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.InitialFlowWriter;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.IPPrefAddrInfo;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 30.08.18
 */

/**
 * Utility function that installs tree rules to the provided switchId and the set of corresponding treePorts.
 */
public class InstallTreeRulesInTheSwitch implements Runnable {

    private static short flowTableId;
    private static SalFlowService salFlowService;
    private static String cpNetworkPrefix;
    private static NetworkGraphService networkGraphService;

    private NodeId switchId;
    private List<String> treePorts;

    public InstallTreeRulesInTheSwitch(NodeId switchId, List<String> treePorts){
        this.switchId = switchId;
        this.treePorts = treePorts;
    }

    public static void setFlowTableId(short flowTableId) {
        InstallTreeRulesInTheSwitch.flowTableId = flowTableId;
    }

    public static void setSalFlowService(SalFlowService salFlowService) {
        InstallTreeRulesInTheSwitch.salFlowService = salFlowService;
    }

    public static void setCpNetworkPrefix(String cpNetworkPrefix) {
        InstallTreeRulesInTheSwitch.cpNetworkPrefix = cpNetworkPrefix;
    }

    public static void setNetworkGraphService(NetworkGraphService networkGraphService) {
        InstallTreeRulesInTheSwitch.networkGraphService = networkGraphService;
    }

    @Override
    public void run() {

        IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
        IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo();
        // remember rules for later eventual removal
        List<FlowCookie> rulesToRemember = new ArrayList<>();

        for (String inPort : treePorts) {
            List<String> outPorts = new ArrayList<>(treePorts);
            outPorts.remove(inPort);

            InstanceIdentifier<Node> nodeId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, new NodeKey(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId(switchId))).build();
            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeId, flowTableId);

            InstanceIdentifier<Flow> flowIdFromController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            InstanceIdentifier<Flow> flowIdToController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            // OF traffic for the controller broadcast
            srcPrefix = new IPPrefAddrInfo();
            dstPrefix = new IPPrefAddrInfo(); // leave intentionally empty
            srcPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));
            dstPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));


            Flow flowToController = InitialFlowUtils.createIPLayer4FlowGeneral(flowTableId, null, srcPrefix, new PortNumber(6633), dstPrefix, 99, inPort, outPorts, switchId.getValue());
            rulesToRemember.add(flowToController.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdToController, flowToController);

            // OF traffic for other nodes from the controller broadcast
            srcPrefix = new IPPrefAddrInfo();
            dstPrefix = new IPPrefAddrInfo();
            srcPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));
            dstPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));
            Flow flowFromController = InitialFlowUtils.createIPLayer4FlowGeneral(flowTableId, new PortNumber(6633), srcPrefix, null, dstPrefix, 99, inPort, outPorts, switchId.getValue());

            rulesToRemember.add(flowFromController.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdFromController, flowFromController);

            // DHCP DISCOVER and REQUEST traffic for the controller broadcast
            InstanceIdentifier<Flow> flowIdDiscReq = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            InstanceIdentifier<Flow> flowIdOfferAck = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow flowDiscReq = null;
            Flow flowOfferAck = null;

            flowDiscReq = InitialFlowUtils.createDhcpFlowMultipleOutputPortsOneInputPort(flowTableId, true, new PortNumber(68), new PortNumber(67), 99, inPort, outPorts, switchId.getValue());
            rulesToRemember.add(flowDiscReq.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdDiscReq, flowDiscReq);

            // DHCP OFFER and ACK traffic for other nodes from the controller broadcast
            flowOfferAck = InitialFlowUtils.createDhcpFlowMultipleOutputPortsOneInputPort(flowTableId, true, new PortNumber(67), new PortNumber(68), 99, inPort, outPorts, switchId.getValue());
            rulesToRemember.add(flowOfferAck.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdOfferAck, flowOfferAck);

            InstanceIdentifier<Flow> flowIdSSHToController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            InstanceIdentifier<Flow> flowIdSSHFromController = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow flowSSHToController = null;
            Flow flowSSHFromController = null;

            // SSH traffic for the controller broadcast
            srcPrefix = new IPPrefAddrInfo();
            srcPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));
            dstPrefix = new IPPrefAddrInfo();
            dstPrefix.setIpv4Prefix(new Ipv4Prefix(cpNetworkPrefix));

            flowSSHToController = InitialFlowUtils.createIPLayer4FlowGeneral(flowTableId, new PortNumber(22), srcPrefix, null, dstPrefix, 99, inPort, outPorts, switchId.getValue());
            rulesToRemember.add(flowSSHToController.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdSSHToController, flowSSHToController);

            // SSH traffic for other nodes from the controller broadcast
            flowSSHFromController = InitialFlowUtils.createIPLayer4FlowGeneral(flowTableId, null, srcPrefix, new PortNumber(22), dstPrefix, 99, inPort, outPorts, switchId.getValue());
            rulesToRemember.add(flowSSHFromController.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdSSHFromController, flowSSHFromController);

            InstanceIdentifier<Flow> flowIdGeneralARP = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow flowSSHGeneralARP = null;

            // ARP traffic general both requests and replies
            flowSSHGeneralARP = InitialFlowUtils.createARPFlowGeneral(flowTableId, 99, inPort, outPorts, switchId.getValue());
            rulesToRemember.add(flowSSHGeneralARP.getCookie());
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowIdGeneralARP, flowSSHGeneralARP);

            // remember phaseII rules for this switch
            InitialFlowUtils.removableFlowCookiesPhaseII.put(switchId.getValue(), rulesToRemember);
        }
    }
}
