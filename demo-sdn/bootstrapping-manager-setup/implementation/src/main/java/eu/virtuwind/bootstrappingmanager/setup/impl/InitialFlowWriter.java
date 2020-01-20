package eu.virtuwind.bootstrappingmanager.setup.impl;

import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.IPPrefAddrInfo;
import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.InitialFlowUtils;
import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.SyncLock;
import eu.virtuwind.registryhandler.impl.BootstrappingRegistryImpl;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingDatastore;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.ControllerIpListSlave;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static eu.virtuwind.bootstrappingmanager.setup.impl.utilities.InitialFlowUtils.writeFlowToController;


/**
 * Adds a set of default flows, configured on a newly discovered switch after an OpenFlow session has been initiated.
 * Registers as ODL Inventory listener so that it can add flows once a new node i.e. switch is added.
 */
public class InitialFlowWriter implements ClusteredDataTreeChangeListener<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(InitialFlowWriter.class);

    //private final ExecutorService initialFlowExecutor = Executors.newCachedThreadPool();
    private final ScheduledThreadPoolExecutor executorScheduler = new ScheduledThreadPoolExecutor(20);
    private final SalFlowService salFlowService;
    private short flowTableId;
    private DataBroker dataBroker;
    public static AtomicLong flowIdInc = new AtomicLong();
    private static boolean isLeader = false;
    private static boolean bootstrappingDone = false;
    private static ArrayList<String> ctlList = new ArrayList<String>();
    private static final String LOCK = new StringBuilder("LOCK!").toString();
    private boolean isIpv6Enabled = false;
    private String discoveryIPPrefix;
    private String controlPlanePrefix;
    private static Thread threadDisableInBand = null;
    private static boolean hasOwner = false;
    private static boolean flagSetupViaNBI = false;

    /**
     * Constructor for the InitialFlowWriter instance that sets the SAL-flow service dependency.
     *
     * @param salFlowService The MD-SAL wired SAL-flow service.
     */
    public InitialFlowWriter(SalFlowService salFlowService, DataBroker dataBrokerService) {
        this.salFlowService = salFlowService;
        this.dataBroker = dataBrokerService;
    }

    void setDiscoveryIPPrefix(String addr) {
        discoveryIPPrefix = addr;
    }

    void setControlPlanePrefix(String prefix) {
        controlPlanePrefix = prefix;
    }

    public static void setBootstrappingDone() {
        bootstrappingDone = true;
    }

    /**
     * Enables or disables the mode for IPv6 default flow cofnigurations
     *
     * @param arg The boolean binary parameter.
     */
    void setIPv6Enabled(boolean arg) {
        isIpv6Enabled = arg;
    }

    /**
     * Sets the flow table identifier to which the default flows are applied whenever a new switch is discovered.
     *
     * @param flowTableId The flow table ID - default: 0.
     */
    public void setFlowTableId(short flowTableId) {
        this.flowTableId = flowTableId;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Node>> collection) {
        synchronized (this) {
            for (DataTreeModification<Node> change : collection) {
                DataObjectModification<Node> mod = change.getRootNode();
                switch (mod.getModificationType()) {
                    case DELETE:
                        LOG.info("Deleted node {}", mod.getDataBefore().getId().getValue());
                        LOG.info(" Delete node after data {} ", mod.getDataAfter());
                        break;
                    case SUBTREE_MODIFIED:
                        break; // TODO: Fix at some point
                    case WRITE:
                        while (!hasOwner) {
                            sleep_some_time(5);
                        }

                        if (mod.getDataBefore() == null) {
                            Node fetchedDS = mod.getDataAfter();
                            LOG.info("Node configuration updated remotely");

                            if (!isLeader)
                                LOG.info("Data changed but not a leader...");

                            if (isLeader && fetchedDS != null && bootstrappingDone == false) {
                                LOG.info("Initially configuring node with NORMAL OF rules: " + fetchedDS.getId().getValue());
                                /*
                                    Trying to extract IP directly form fetchedDS throws NullPointerExceptions sometimes
                                    For that reason contact DS to fetch IP
                                 */
                                String IP = null;
                                while (IP == null) {
                                    IP = InitialFlowUtils.getNodeIPAddress(fetchedDS, dataBroker);
                                    sleep_some_time(100);
                                }
                                LOG.info("Node IP address: {}",  IP);
                                executorScheduler.execute(new InitialFlowWriterProcessor(InitialFlowUtils.getNodeInstanceId(fetchedDS), IP));
                                LOG.info("Finished initially configuring the node: " + fetchedDS.getId().getValue());
                            }
                        }
                        break;
                    default:
                        LOG.info("UNHANDLED: Node configuration updated remotely");
                        throw new IllegalArgumentException("Unhandled node modification type " + mod.getModificationType());
                }
            }
        }
    }

    static void setCtlList(ArrayList<String> controllerList) {
        ctlList = controllerList;
    }

    private void sleep_some_time(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * A private class to process the node updated event in separate thread. Allows to release the
     * thread that invoked the data node updated event. Avoids any thread lock it may cause.
     */
    private class InitialFlowWriterProcessor implements Runnable {
        InstanceIdentifier<?> nodeId = null;
        String nodeIPAddress;

        InitialFlowWriterProcessor(InstanceIdentifier<?> nodeId, String nodeIPAddress) {

            this.nodeId = nodeId;
            this.nodeIPAddress = nodeIPAddress;
        }

        @Override
        public synchronized void run() {
            if (nodeId == null) {
                return;
            }

            if (Node.class.isAssignableFrom(nodeId.getTargetType())) {
                InstanceIdentifier<Node> topoNodeId = (InstanceIdentifier<Node>) nodeId;
                if (topoNodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue().contains("openflow:")) {
                    LOG.info("Data changed but waiting on LOCK to be available!");
                    synchronized (LOCK) {
                        // TO DO: maybe artificial timeout to be sure that other node has installed rules
                        //sleep_some_time(200); // does not help
                        addInitialFlows(topoNodeId, nodeIPAddress);
                    }
                }
            }

        }

        /**
         * Adds the defaults flows to the node with ID nodeId.
         *
         * @param nodeId The node to write the flows on.
         */
        public void addInitialFlows(InstanceIdentifier<Node> nodeId, String nodeIP) {

            LOG.info("adding initial flows to node {} with IP {} ", nodeId.firstKeyOf(Node.class).getId().getValue(), nodeIP);

            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeId, flowTableId);

            // In case no controllers are provided by default in the provider, check the datastore content (manually
            // specified using the northbound interface
            //boolean flagSetupViaNBI = false; // was false
            LOG.info("ctlList.size() is {}", ctlList.size());
            if (ctlList.size() == 0) {
                // Retrieve the controller IP lists
                BootstrappingDatastore datastore = BootstrappingRegistryImpl.getInstance().readBootstrappingConfigurationFromDataStore();

                assert datastore != null;
                ctlList.add(datastore.getControllerIpMaster());

                List<ControllerIpListSlave> slaveCtlIpAddr = datastore.getControllerIpListSlave();

                for (ControllerIpListSlave ctlIp : slaveCtlIpAddr)
                    ctlList.add(ctlIp.getIpAddr());

                flagSetupViaNBI = true;
            }

            //DOES NOT HELP
            // Add 35 flows rules (total occupany 1 flow rule) just to ensure the connection between controller and switches is stable.
            /*for (int i = 0; i < 35; i++) {
                ArrayList<String> oPorts = new ArrayList<>();
                oPorts.add("normal");

                // also send all other unmatched IP packets to the controller (might not be needed)
                InstanceIdentifier<Flow> flowId7 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                IPPrefAddrInfo srcAddrInfo = new IPPrefAddrInfo();
                IPPrefAddrInfo dstAddrInfo = new IPPrefAddrInfo();


                LOG.info("Setting exemplary flows to flush the pipe.");
                if (!isIpv6Enabled) {
                    dstAddrInfo.setIpv4Prefix(new Ipv4Prefix(discoveryIPPrefix));
                    Flow f7 = InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcAddrInfo, new PortNumber(3322), dstAddrInfo, 1, oPorts);
                    InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), f7.getCookie());
                    writeFlowToController(salFlowService, nodeId, tableId, flowId7, f7);
                } else if (isIpv6Enabled) { // Still set the IPv4 Discovey Prefix as currently utilized discovery method (arping) only works with that
                    dstAddrInfo.setIpv4Prefix(new Ipv4Prefix(discoveryIPPrefix));
                    Flow f7 = InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcAddrInfo, new PortNumber(3322), dstAddrInfo, 1, oPorts);
                    InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), f7.getCookie());
                    writeFlowToController(salFlowService, nodeId, tableId, flowId7, f7);
                }
                // Wait until the OpenFlow connection is established
                sleep_some_time(50);
            }
            */


            LOG.info("Setting OpenFlow flows.");
            // 2 rules for each controller
            /*for (String ctl : ctlList) {
                setOpenFlowRulesToExactMatches(nodeId, tableId, ctl);
            }
            */
            // aggregating rules based only on the of tcp port
            InstanceIdentifier<Flow> flowId6 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
            InstanceIdentifier<Flow> flowId7 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

            List<String> oPorts = new ArrayList<String>();
            oPorts.add("normal");

            Flow f6 = null;
            Flow f7 = null;

            IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
            IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo(); // leave intentionally empty
            srcPrefix.setIpv4Prefix(new Ipv4Prefix(controlPlanePrefix));
            dstPrefix.setIpv4Prefix(new Ipv4Prefix(controlPlanePrefix));


            f6 = InitialFlowUtils.createIPLayer4Flow(flowTableId, new PortNumber(6633), srcPrefix, null, dstPrefix, 100, oPorts);
            f7 = InitialFlowUtils.createIPLayer4Flow(flowTableId, null, dstPrefix, new PortNumber(6633), srcPrefix, 100, oPorts);

            InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), f6.getCookie());
            InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), f7.getCookie());

            writeFlowToController(salFlowService, nodeId, tableId, flowId6, f6);
            writeFlowToController(salFlowService, nodeId, tableId, flowId7, f7);

            LOG.info("Setting LLDP flows.");
            //add LLDP-to-Controller flows
            InstanceIdentifier<Flow> flowId = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
            writeFlowToController(salFlowService, nodeId, tableId, flowId, InitialFlowUtils.createLldpToControllerFlow(flowTableId, 100));

            // Wait until the OpenFlow connection is established
            //sleep_some_time(150);

            LOG.info("Setting DHCP flows.");
            // ADD DHCP Port 68/546 flow (Port used by the Client)
            if (!isIpv6Enabled) {
                InstanceIdentifier<Flow> flowId2 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                Flow flow2 = InitialFlowUtils
                        .createDhcpFlow(flowTableId, true, new PortNumber(68), null, 100, "normal");
                writeFlowToController(salFlowService, nodeId, tableId, flowId2, flow2);

                InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), flow2.getCookie());

                /*
                InstanceIdentifier<Flow> flowId21 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                writeFlowToController(salFlowService, nodeId, tableId, flowId21, InitialFlowUtils
                        .createDhcpFlow(flowTableId, true, null, new PortNumber(68), 100, "normal"));
                 */
            } else if (isIpv6Enabled) {
                InstanceIdentifier<Flow> flowId2 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                writeFlowToController(salFlowService, nodeId, tableId, flowId2, InitialFlowUtils
                        .createDhcpFlow(flowTableId, false, new PortNumber(546), null, 100, "normal"));
                InstanceIdentifier<Flow> flowId21 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                writeFlowToController(salFlowService, nodeId, tableId, flowId2, InitialFlowUtils
                        .createDhcpFlow(flowTableId, false, null, new PortNumber(546), 100, "normal"));
            }
            // Wait until the OpenFlow connection is established
            //sleep_some_time(150);


            LOG.info("Setting even more DHCP flows.");
            // ADD DHCP Port 67/547 flows (Destination port number of the server)
            if (!isIpv6Enabled) {
                InstanceIdentifier<Flow> flowId3 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                Flow flow3 = InitialFlowUtils
                        .createDhcpFlow(flowTableId, true, new PortNumber(67), null,
                                100, "normal");
                writeFlowToController(salFlowService, nodeId, tableId, flowId3, flow3);

                InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), flow3.getCookie());

                /*
                InstanceIdentifier<Flow> flowId31 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                writeFlowToController(salFlowService, nodeId, tableId, flowId31, InitialFlowUtils
                        .createDhcpFlow(flowTableId, true, null, new PortNumber(67),
                                100, "normal"));
                */
            } else if (isIpv6Enabled) {
                InstanceIdentifier<Flow> flowId3 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                writeFlowToController(salFlowService, nodeId, tableId, flowId3, InitialFlowUtils
                        .createDhcpFlow(flowTableId, false, new PortNumber(547), null,
                                100, "normal"));
                InstanceIdentifier<Flow> flowId31 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                writeFlowToController(salFlowService, nodeId, tableId, flowId3, InitialFlowUtils
                        .createDhcpFlow(flowTableId, false, null, new PortNumber(547),
                                100, "normal"));
            }

            // Wait until the OpenFlow connection is established
            //sleep_some_time(150);

            LOG.info("Setting SSH flows.");
            // ADD THE SSH PORT 22 FLOW (outputs on NORMAL)
            InstanceIdentifier<Flow> flowId4 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
            Flow flow4 = InitialFlowUtils.createSSHFlow(flowTableId, isIpv6Enabled, new PortNumber(22),
                    null, 100, "normal");
            writeFlowToController(salFlowService, nodeId, tableId, flowId4, flow4);

            InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), flow4.getCookie());

            // Wait until the OpenFlow connection is established
            //sleep_some_time(150);

            // ADD THE SSH PORT 22 FLOW (outputs on NORMAL)
            InstanceIdentifier<Flow> flowId5 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
            Flow flow5 = InitialFlowUtils.createSSHFlow(flowTableId, isIpv6Enabled, null,
                    new PortNumber(22), 100, "normal");
            writeFlowToController(salFlowService, nodeId, tableId, flowId5, flow5);

            InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), flow5.getCookie());


            // Wait until the OpenFlow connection is established
            //sleep_some_time(150);

            oPorts = new ArrayList<>();
            oPorts.add("controller");

            LOG.info("Setting ARP/NDP flows used for self-discovery.");
            // ADD THE ARP/NDP FLOW USED FOR CONTROLLER SELF-DISCOVERY
            if (!isIpv6Enabled) {
                //add ARP flow with DST prefix
                InstanceIdentifier<Flow> flowId9 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                Flow f9 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 102, new Ipv4Prefix(discoveryIPPrefix), null, oPorts);
                //InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), f9.getCookie());

                writeFlowToController(salFlowService, nodeId, tableId, flowId9, f9);

                // Wait until the OpenFlow connection is established
                //sleep_some_time(150);

                //add ARP flow with SRC prefix
                /*
                InstanceIdentifier<Flow> flowId11 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                Flow f11 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 102, null, new Ipv4Prefix(discoveryIPPrefix), oPorts);
                InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), f11.getCookie());

                writeFlowToController(salFlowService, nodeId, tableId, flowId11, f11);
                */
            } else if (isIpv6Enabled) {
                // TODO: Handle a fixed-subnet IPv6 solicitation messages here for the purpose of controller discovery
            }

            // Wait until the OpenFlow connection is established
            //sleep_some_time(150);

            LOG.info("Setting ARP/NDP flows used for switch discovery.");
            // ADD THE ARP/NDP FLOW USED FOR SWITCH DISCOVERY
            oPorts = new ArrayList<>();
            oPorts.add("normal");

            if (!isIpv6Enabled) {
                InstanceIdentifier<Flow> flowIdtest1 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                Flow ftest1 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 101, new Ipv4Prefix(controlPlanePrefix), null, oPorts);
                InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), ftest1.getCookie());

                writeFlowToController(salFlowService, nodeId, tableId, flowIdtest1, ftest1);

                LOG.info("Debug: Embedded up to half...");

                // Wait until the OpenFlow connection is established
                //sleep_some_time(150);

                //add ARP flow with SRC prefix
                InstanceIdentifier<Flow> flowIdtest2 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                Flow ftest2 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 101, null, new Ipv4Prefix(controlPlanePrefix), oPorts);
                InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), ftest2.getCookie());

                writeFlowToController(salFlowService, nodeId, tableId, flowIdtest2, ftest2);
            } else if (isIpv6Enabled) {
                // TODO: Handle NDP switch-sourced and switch-destined IPv6 solicitation messages here?
            }

            // Wait until the OpenFlow connection is established
            //sleep_some_time(150);


            LOG.info("Setting ARP/NDP flows used for controller discovery.");
            // ADD THE ARP/NDP FLOW USED FOR CONTROLLER DISCOVERY
            oPorts = new ArrayList<>();
            oPorts.add("normal");
            if (!isIpv6Enabled) {
                for (String ctl : ctlList) {
                    InstanceIdentifier<Flow> flowId8 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                    InstanceIdentifier<Flow> flowId10 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

                    oPorts = new ArrayList<>();
                    oPorts.add("normal");
                    Flow f8 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 100, new Ipv4Prefix(ctl + "/32"), null, oPorts);
                    Flow f10 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 100, null, new Ipv4Prefix(ctl + "/32"), oPorts);

                    InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), f8.getCookie());
                    InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), f10.getCookie());


                    writeFlowToController(salFlowService, nodeId, tableId, flowId8, f8);
                    writeFlowToController(salFlowService, nodeId, tableId, flowId10, f10);
                }
            } else if (isIpv6Enabled) {
                // TODO: Handle NDP controller-sourced and controller-destined IPv6 solicitation messages here?
            }

            LOG.info("Setting flows used for any unmatched flows.");
            // ADD THE FLOW USED FOR ANY SENDING ANY UNMATCHED PACKETS TO THE CONTROLLER
            InstanceIdentifier<Flow> flowId21 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
            srcPrefix = new IPPrefAddrInfo(); // leave intentionally empty
            dstPrefix = new IPPrefAddrInfo(); // leave intentionally empty
            // Threads stop here, but unnecessary rule
            //writeFlowToController(salFlowService, nodeId, tableId, flowId21, InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcPrefix, null, dstPrefix, 2, oPorts));
            //add ARPFlow for all unmatched subnets (might not be needed)
            //InstanceIdentifier<Flow> flowId16 = getFlowInstanceId(tableId);
            //writeFlowToController(nodeId, tableId, flowId16, InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 99, null, null, "controller"));

            LOG.info("Setting controller-to-controller flows.");
            // add controller-to-controller flows (2*N controller flow combinations)
            // Add flows to and from each slave and master controller
            // priority 110 to facilitate measurements
            // (N chooses 2) times 2 flow rules
            oPorts = new ArrayList<String>();
            oPorts.add("normal");
            for (String ctlAddr1 : ctlList) {
                // Add flows to and from each slave and all other slave controllers
                for (String ctlAddr2 : ctlList) {
                    if (!ctlAddr1.equals(ctlAddr2)) {
                        IPPrefAddrInfo srcAddrInfo = new IPPrefAddrInfo();
                        IPPrefAddrInfo dstAddrInfo = new IPPrefAddrInfo();
                        srcAddrInfo.setIpv4Prefix(new Ipv4Prefix(ctlAddr1 + "/32"));
                        dstAddrInfo.setIpv4Prefix(new Ipv4Prefix(ctlAddr2 + "/32"));
                        InstanceIdentifier<Flow> flowC2CId = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                        Flow flowC2C = InitialFlowUtils.createIPLayer4Flow(flowTableId, null, srcAddrInfo, null, dstAddrInfo, 100, oPorts);
                        writeFlowToController(salFlowService, nodeId, tableId, flowC2CId, flowC2C);

                        InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), flowC2C.getCookie());

                    }
                }
            }
            // to facilitate measurements we consider that this rule is installed as the last one
            // and we measure in a switch when this rule is installed
            oPorts = new ArrayList<String>();
            oPorts.add("controller");

            LOG.info("Setting ARP/NDP flows used for unmatched flows.");
            // ADD THE ARP/NDP FLOW USED FOR ANY OTHER APPLICATIONS IN THE SYSTEM
            if (!isIpv6Enabled) {
                //add ARPFlow for all unmatched subnets (might not be needed)
                // used for measurements; do not forget to subtract 1 from the flow table size !!!
                InstanceIdentifier<Flow> flowId20 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
                Flow flow20 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 5, null, null, oPorts);
                writeFlowToController(salFlowService, nodeId, tableId, flowId20, flow20);

                InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), flow20.getCookie());
            } else if (isIpv6Enabled) {
                // TODO: Handle NDP IPv6 solicitation messages here?
            }

            LOG.info("Flag setup via NBI: " + flagSetupViaNBI);


            LOG.info("InitialFlowWriter for {} has finished.", nodeIP);

            /**
             * Notify  ConfNBI or ConfAutoWithSSH thread to disable in-band rules
             */
            synchronized (SyncLock.threadSync.get(nodeIP)) {
                SyncLock.threadSync.get(nodeIP).setCondVariable(true);
                LOG.info("Notifying ConfNBI or ConfAutoWithSSH to disable in-band rules for " + nodeIP);
                SyncLock.threadSync.get(nodeIP).notify();
            }
            LOG.info("InitialFlowWriter ended for node {}: {}", nodeId.firstKeyOf(Node.class).getId().getValue(), nodeIP);



            // when controller IPs provided to the switch externaly
            /*
            if (!flagSetupViaNBI) {
                ConfigureNewOpenFlowNodeAuto confOFnode = new ConfigureNewOpenFlowNodeAuto();
                if (threadDisableInBand != null && threadDisableInBand.isAlive()) {
                    threadDisableInBand.interrupt();
                    threadDisableInBand = null;
                }

                LOG.info("Starting OFCONF thread.");
                threadDisableInBand = new Thread(confOFnode);
                threadDisableInBand.start();

            }
            */

            /** ####### After the thread has finished, initiate the timer for RSTP disable commands ######## **/
            //reinitiateSTPDisable(35500);

        }


        public void setOpenFlowRulesToExactMatches(InstanceIdentifier<Node> nodeId, InstanceIdentifier<Table> tableId, String ctlAddr) {
            InstanceIdentifier<Flow> flowId6 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());
            InstanceIdentifier<Flow> flowId7 = InitialFlowUtils.getFlowInstanceId(tableId, flowIdInc.getAndIncrement());

            List<String> oPorts = new ArrayList<String>();
            oPorts.add("normal");

            Flow f6 = null;
            Flow f7 = null;

            IPPrefAddrInfo srcPrefix = new IPPrefAddrInfo();
            IPPrefAddrInfo dstPrefix = new IPPrefAddrInfo(); // leave intentionally empty
            srcPrefix.setIpv4Prefix(new Ipv4Prefix(ctlAddr + "/32"));

            f6 = InitialFlowUtils.createIPLayer4Flow(flowTableId, new PortNumber(6633), srcPrefix, null, dstPrefix, 100, oPorts);
            f7 = InitialFlowUtils.createIPLayer4Flow(flowTableId, null, dstPrefix, new PortNumber(6633), srcPrefix, 100, oPorts);

            InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), f6.getCookie());
            InitialFlowUtils.addRemovableFlowCookies(nodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue(), f7.getCookie());

            writeFlowToController(salFlowService, nodeId, tableId, flowId6, f6);
            writeFlowToController(salFlowService, nodeId, tableId, flowId7, f7);
        }

        /**
         * Simple wrapper of the writeFlowToController that tries to install the given rule until it succeeds
         *
         * @param salFlowService
         * @param nodeInstanceId
         * @param tableInstanceId
         * @param flowPath
         * @param flow
         */
        private void writeFlowToControllerWrapper(SalFlowService salFlowService, InstanceIdentifier<Node> nodeInstanceId,
                                                  InstanceIdentifier<Table> tableInstanceId,
                                                  InstanceIdentifier<Flow> flowPath,
                                                  Flow flow,
                                                  String nodeIP) {
            boolean isFlowAddedSuccessfully = false;
            Future<RpcResult<AddFlowOutput>> result = null;

            while (!isFlowAddedSuccessfully) {

                LOG.info("Trying to add initial flow rules to {}", nodeIP);

                result = writeFlowToController(salFlowService, nodeInstanceId, tableInstanceId, flowPath, flow);

                LOG.info("result.isDone()={}", result.isDone());
                LOG.info("result.isCancelled()={}", result.isCancelled());

                /**
                 * UNFORTUNATELY isDone never returns True even though a rule is installed in a switch!!!
                 */
                if (result.isDone()) {
                    try {
                        isFlowAddedSuccessfully = result.get().isSuccessful();
                    } catch (InterruptedException e) {
                        LOG.warn("Unable to read future result of the writeFlowToController");
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        LOG.warn("Unable to read future result of the writeFlowToController");
                        e.printStackTrace();
                    }
                }

                sleep_some_time(100);
            }

            try {
                LOG.info("Flow was added successfully: {}", result.get().getResult().toString());
            } catch (InterruptedException e) {
                LOG.error("This should not be printed!!!");
                e.printStackTrace();
            } catch (ExecutionException e) {
                LOG.error("This should not be printed!!!");
                e.printStackTrace();
            }
        }
    }


    /**
     * Implements the reaction to cluster leadership changes.
     *
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("flow-reconfig ownership change logged: " + ownershipChange);

        if (ownershipChange.hasOwner()) {
            LOG.info("This system now has an owner.");
            hasOwner = true;
        }
        if (ownershipChange.isOwner()) {
            LOG.info("This node is set as the flow-reconfig leader.");
            setLeader();
        } else if (ownershipChange.isOwner()) {
            LOG.info("This node is set as the flow-reconfig follower.");
            setFollower();
        }
    }

    /**
     * Confirms this instance is the current cluster leader.
     */
    public static void setLeader() {
        isLeader = true;
    }

    /**
     * Sets this instance as a cluster follower.
     */
    public static void setFollower() {
        isLeader = false;
    }


    /**
     * The method will block until the String checkString is contained
     * as part of the output of the remote command defined by checkCommand.
     *
     * @param ip           The SSH Connector IP.
     * @param command      The SSH command to be executed.
     * @param checkCommand The SSH check-command.
     * @param checkString  The SSH check-string.
     */
    public void blockSshExecuteCommandUntilCheck(String ip, String command,
                                                 String checkCommand, String checkString) {
        String output = "";
        LOG.info("IN-BAND DISABLING BEFORE WHILE");
        while (!output.contains(checkString)) {
            AnSshConnector sshConnector = new AnSshConnector(ip, 22, "admin", "admin", 5000);

            try {
                sshExecuteSimple(sshConnector, command);
                LOG.info("IN-BAND DISABLING INSIDE WHILE");
                // Wait a bit until the configuration is made successfully remotely.
                Thread.sleep(500); // ms
                //TODO CHECK IF TOO SHORT and if needed!

                //AnSshConnector sshConnector = new AnSshConnector(ip, SSH_PORT, SSH_TIMEOUT);

                if (!sshConnector.isOpen())
                    sshConnector.open();
                output = sshConnector.executeCommand(checkCommand);
                sshConnector.close();
                LOG.info("Executed COMMAND={} and CHECKCOMMAND={}, and CHECKSTRING={}, RESPONSE={}",
                        command, checkCommand, checkString, output);
            } catch (Exception e) {
                LOG.info(e.getMessage());
                e.printStackTrace();
                try {
                    sshConnector.close();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        }
    }


    /**
     * This method executes the command specified, using a given AnSshConnector instance.
     *
     * @param sshConnector The SSH connector.
     * @param command      The SSH command to be executed.
     */
    public void sshExecuteSimple(AnSshConnector sshConnector, String command) throws Exception {
        if (!sshConnector.isOpen())
            sshConnector.open();
        sshConnector.executeCommand(command);
    }

}

