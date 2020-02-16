/**
  *
  * @filename ControllerSelfDiscovery.java
  *
  * @date 25.04.18
  *
  * @author Mirza Avdic
  *
  *
 */
package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;


import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.ScheduledThreadPoolExecutorWrapper;
import eu.virtuwind.registryhandler.impl.BootstrappingSwitchStateImpl;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingState;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Random;

/**
 * The class that implements the logic responsible for the controllers' self-discovery.
 */
public class ControllerSelfDiscovery  implements ClusteredDataTreeChangeListener<SwitchBootsrappingState>  {
    private static final Logger LOG = LoggerFactory.getLogger(ControllerSelfDiscovery.class);
    private static DataBroker db = null;
    private static SalFlowService salFlowService = null;
    private static short flowTableId;
    private static String arpingPath;
    private static String discoveryIPPrefix;
    private static String topologyId;
    private static String controllerIP;
    private static String bindingControllerInterface;
    private static ArrayList<String> ctlList;
    private static int execCounterLeader = 0;
    private static int execCounterFollower = 0;
    private static ScheduledThreadPoolExecutorWrapper executorScheduler = new ScheduledThreadPoolExecutorWrapper(5);
    private static boolean executorSchedulerStarted = false;
    private static Long DEFAULT_DISCOVERY_PERIOD = 1000L;
    private static boolean isDiscovered = false;
    public static final String LOCK = "LOCK";


    // set through the EOS
    private static boolean isLeader = false;

    /*  - keep track of the nodes that already were in this state
        - thread safe within one atomic operation
        - for iteration synchronized still have to be used
    */
    public static List<String> initialSelfDiscoveryRuleInstalledDoneNodes = Collections.synchronizedList(new ArrayList());
    public static List<String> initialSelfDiscoveryDoneNodes = Collections.synchronizedList(new ArrayList());



    public ControllerSelfDiscovery(DataBroker db, SalFlowService salFlowService) {
        this.db = db;
        this.salFlowService = salFlowService;
    }


    public static void setCtlList(ArrayList<String> ctlList) {
        ControllerSelfDiscovery.ctlList = ctlList;
    }

    public static void setArpingPath(String arpingPath) {
        ControllerSelfDiscovery.arpingPath = arpingPath;
    }

    public static void setFlowTableId(short flowTableId) {
        ControllerSelfDiscovery.flowTableId = flowTableId;
    }

    public static void setDiscoveryIPPrefix(String discoveryIPPrefix) {
        ControllerSelfDiscovery.discoveryIPPrefix = discoveryIPPrefix;
    }

    public static String getTopologyId() {
        return topologyId;
    }

    public static void setTopologyId(String topologyId) {
        ControllerSelfDiscovery.topologyId = topologyId;
    }

    public static boolean isIsDiscovered() {
        return isDiscovered;
    }

    public static void setIsDiscovered(boolean isDiscovered) {
        ControllerSelfDiscovery.isDiscovered = isDiscovered;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<SwitchBootsrappingState>> changes) {

        for (DataTreeModification<SwitchBootsrappingState> change : changes) {

            Integer threadId = new Random().nextInt(1000);

            // get a NodeId of the node that has changed
            NodeId switchId = change.getRootPath().getRootIdentifier().firstKeyOf(Node.class).getId();

            // get a state before the change -> can throw NullPointerException
            String previousState = null;
            try {
                previousState = change.getRootNode().getDataBefore().getState().getName();
                LOG.info(threadId + ": ControllerSelfDiscovery for node {} with the previous node state {}", switchId, previousState);

            } catch (NullPointerException e) {
                LOG.info(threadId + ": First time OF-SESSION-ESTABLISHED with node {}", switchId.getValue());
            }
            // get a state after the change has happened
            String afterState = change.getRootNode().getDataAfter().getState().getName();
            LOG.info(threadId + ": ControllerSelfDiscovery for node {} with the after state {}", switchId, afterState);

            // get controller IPs and the interfaces on which these IPs are binded
            HostUtilities.InterfaceConfiguration myIfConfig =
                    HostUtilities.returnMyIfConfig(ctlList);
            if (myIfConfig == null) {
                LOG.warn(threadId + ": Provided controller IP addresses are not found on this machine.");
                return;
            }
            controllerIP = myIfConfig.getIpAddr();
            bindingControllerInterface = myIfConfig.getIf();

            if (isLeader) {

                    // do self controller discovery -> only for the node directly connected to the controller
                 if ((execCounterLeader == 1)
                         && (afterState.equals(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYRULEINSTALLED.getName()))
                         && previousState.equals(SwitchBootsrappingState.State.OFSESSIONESTABLISHED.getName())) {

                    Thread executor = new Thread(new ControllerSelfDiscoveryExecutor(switchId));
                    executor.start();
                    try { // wait till the self-discovery has been done in order to change a switch state
                        executor.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    LOG.debug(threadId + ": ControllerSelfDiscoveryExecutor for master executed!!!");

                    // install self discovery rule to the switch connected to the leader controller
                } else if ((execCounterLeader == 0)
                         && (afterState.equals(SwitchBootsrappingState.State.OFSESSIONESTABLISHED.getName()))
                         && previousState == null) {
                     Thread executor = new Thread(new ControllerSelfDiscoveryRuleSpecificInstaller(switchId));
                     executor.start();
                     try { // wait till the self-discovery rule has been installed in order to change a switch state
                         executor.join();
                     } catch (InterruptedException e) {
                         e.printStackTrace();
                     }
                     synchronized (LOCK) {
                         BootstrappingSwitchStateImpl stateManager = BootstrappingSwitchStateImpl.getInstance();
                         stateManager.writeBootstrappingSwitchState(switchId, new SwitchBootsrappingStateBuilder()
                                 .setState(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYRULEINSTALLED)
                                 .build());
                     }
                     initialSelfDiscoveryRuleInstalledDoneNodes.add(switchId.getValue());

                 } else if ((execCounterLeader > 1)
                         && (afterState.equals(SwitchBootsrappingState.State.OFSESSIONESTABLISHED.getName()))
                         && previousState == null) {
                     // install Discovery rule on the next level switches
                     Thread executor = new Thread(new ControllerSelfDiscoveryRuleInstaller(switchId));
                     executor.start();

                     LOG.info(threadId + ": The state of the next level switches will be changed when this level finishes processing.");
                }
            } else {
                /* To discover slave controllers, probe each time when a switch reaches CONTROLLERSELFDISCOVERYRULEINSTALLED.
                    ARP broadcasting will be dropped on the switch that is connected to the  leader and in that way
                    we avoid wrong discovery at the switch connected to the leader.
                    It is possible that temporarily a controller is discovered on a wrong switch, but when the switch that is
                    connected to the controller reaches the state CONTROLLERSELFDISCOVERYRULEINSTALLED,
                    and the controllers probe the network again, this error will be fixed.
                */
                if (afterState.equals(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYRULEINSTALLED.getName())
                        && previousState.equals(SwitchBootsrappingState.State.OFSESSIONESTABLISHED.getName())) {
                    Thread executor = new Thread(new ControllerSelfDiscoveryExecutor(switchId));
                    executor.start();
                    try { // wait till the self-discovery arp is sent and the change state
                        executor.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    /*
                        In case that some of the controllers initially do not boot as fast as their peers
                        it prevents bootstrapping stalls due to the delayed appearance of controller links
                     */
                    if (!executorSchedulerStarted) {
                        executorScheduler.scheduleAtFixedRate(new ControllerSelfDiscoveryFollowerPeriodicExecutor(switchId), 0, 1000, TimeUnit.MILLISECONDS);
                        executorSchedulerStarted = true;
                    }
                    /*
                        Check if other controllers have already changed the switch state.
                        If yes do nothing, if no change the state to CONTROLLERSELFDISCOVERYDONE
                     */
                    synchronized (LOCK) {
                        String currentSwitchState = InitialFlowUtils.getSwitchBootstrappingState(switchId, db);
                        if (currentSwitchState.equals(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYRULEINSTALLED.getName())) {
                            BootstrappingSwitchStateImpl stateManager = BootstrappingSwitchStateImpl.getInstance();
                            stateManager.writeBootstrappingSwitchState(switchId, new SwitchBootsrappingStateBuilder()
                                    .setState(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYDONE)
                                    .build());

                            initialSelfDiscoveryDoneNodes.add(switchId.getValue());

                        } else {
                            LOG.info(threadId + ": Switch state already changed by other controller instance");
                        }
                    }

                }
            }
        }
    }


    private class  ControllerSelfDiscoveryExecutor implements Runnable {

        private NodeId switchId;

        ControllerSelfDiscoveryExecutor(NodeId switchId) {
            this.switchId = switchId;
        }

        @Override
        public void run() {

                LOG.info("CSD: Self-discovery arp injection to node {}", switchId);
                LOG.info("CSD: ARPING: {} {} {}", arpingPath, controllerIP, bindingControllerInterface);
                String arpingCommand = arpingPath + " " + controllerIP + " " + bindingControllerInterface;

                if (isLeader){ while (!isDiscovered) {
                    Runtime rt = Runtime.getRuntime();
                    try {
                        Process proc = rt.exec(arpingCommand);
                        proc.waitFor();
                        StringBuffer result = new StringBuffer();
                        BufferedReader reader =
                                new BufferedReader(new InputStreamReader(proc.getInputStream()));
                        String line = "";
                        while ((line = reader.readLine()) != null) {
                            result.append(line + "\n");
                        }
                        LOG.info("CSD: ARPING: " + result.toString());
                        sleep_some_time(100);


                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                // TODO: Handle better in case of an exception occurence
            } else {
                    // probe the network 5 times
                    for (int i = 0; i < 5; ++i) {
                        Runtime rt = Runtime.getRuntime();
                        try {
                            Process proc = rt.exec(arpingCommand);
                            proc.waitFor();
                            StringBuffer result = new StringBuffer();
                            BufferedReader reader =
                                    new BufferedReader(new InputStreamReader(proc.getInputStream()));
                            String line = "";
                            while ((line = reader.readLine()) != null) {
                                result.append(line + "\n");
                            }
                            LOG.info("CSD: ARPING: " + result.toString());
                            sleep_some_time(100);


                        } catch (Exception e) {
                            e.printStackTrace();
                    }
                }
                // TODO: Handle better in case of the exception occurence
            }

                execCounterLeader++;

        }
    }

    private class  ControllerSelfDiscoveryFollowerPeriodicExecutor implements Runnable {

        private NodeId switchId;

        ControllerSelfDiscoveryFollowerPeriodicExecutor(NodeId switchId) {
            this.switchId = switchId;
        }

        @Override
        public void run() {

            LOG.info("CSDF: Self-discovery arp injection to node {}", switchId);
            LOG.info("CSDF: ARPING: {} {} {}", arpingPath, controllerIP, bindingControllerInterface);
            String arpingCommand = arpingPath + " " + controllerIP + " " + bindingControllerInterface;

            if (!isDiscovered){
                Runtime rt = Runtime.getRuntime();
                try {
                    Process proc = rt.exec(arpingCommand);
                    proc.waitFor();
                    StringBuffer result = new StringBuffer();
                    BufferedReader reader =
                            new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    String line = "";
                    while ((line = reader.readLine()) != null) {
                        result.append(line + "\n");
                    }
                    LOG.info("CSDF: ARPING: " + result.toString());

                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else {
                executorScheduler.shutdown();
            }
        }
    }

    private class  ControllerSelfDiscoveryRuleSpecificInstaller implements Runnable {

        private NodeId switchId;

        ControllerSelfDiscoveryRuleSpecificInstaller(NodeId switchId) {
            this.switchId = switchId;
        }

        @Override
        public void run() {
            LOG.info("Self-Discovery rule for the master installed in the master switch {}", switchId.getValue());
            InstanceIdentifier<Node> nodeId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, new NodeKey(switchId)).build();
            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeId, flowTableId);
            ArrayList<String> oPorts = new ArrayList<>();
            oPorts.add("controller");

            //add ARP flow with DST prefix for the Master controller
            InstanceIdentifier<Flow> flowId1 = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow f1 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 102, new Ipv4Prefix(discoveryIPPrefix), new Ipv4Prefix(controllerIP + "/32"), oPorts);
            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowId1, f1);


            //add ARP flow with SRC prefix for the Master controller
            InstanceIdentifier<Flow> flowId2 = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow f2 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 102, new Ipv4Prefix(controllerIP + "/32"), new Ipv4Prefix(discoveryIPPrefix), oPorts);

            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowId2, f2);

            // Add the same rules for the slave controllers but drop their packets to avoid false controller node discovery
            oPorts = new ArrayList<>();
            LOG.info("Self-Discovery rule for the slaves installed in the master switch {}", switchId.getValue());
            for (String conIP: ctlList) {
                if (!conIP.equals(controllerIP)) {
                    //add ARP flow with DST prefix for the Slave controller
                    flowId1 = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                    f1 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 102, new Ipv4Prefix(discoveryIPPrefix), new Ipv4Prefix(conIP + "/32"), oPorts);
                    InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowId1, f1);

                    //add ARP flow with SRC prefix for the Slave controller
                    flowId2 = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
                    f2 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 102, new Ipv4Prefix(conIP + "/32"), new Ipv4Prefix(discoveryIPPrefix), oPorts);

                    InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowId2, f2);

                }
            }
            LOG.info("Self-Discovery rules installation has been completed");
            
            // increment execution number counter
            execCounterLeader++;

        }
    }

    private class  ControllerSelfDiscoveryRuleInstaller implements Runnable {

        private NodeId switchId;

        ControllerSelfDiscoveryRuleInstaller(NodeId switchId) {
            this.switchId = switchId;
        }

        @Override
        public void run() {

            LOG.info("Installing ControllerSelfDiscoveryRule to node {}", switchId.getValue());
            InstanceIdentifier<Node> nodeId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, new NodeKey(switchId)).build();
            InstanceIdentifier<Table> tableId = InitialFlowUtils.getTableInstanceId(nodeId, flowTableId);
            ArrayList<String> oPorts = new ArrayList<>();
            oPorts.add("controller");
            //add ARP flow with DST prefix
            InstanceIdentifier<Flow> flowId1 = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow f1 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 102, new Ipv4Prefix(discoveryIPPrefix), null, oPorts);

            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowId1, f1);

            //add ARP flow with SRC prefix
            InstanceIdentifier<Flow> flowId2 = InitialFlowUtils.getFlowInstanceId(tableId, InitialFlowWriter.flowIdInc.getAndIncrement());
            Flow f2 = InitialFlowUtils.createARPFlowWithMatchPair(flowTableId, 102, null, new Ipv4Prefix(discoveryIPPrefix), oPorts);

            InitialFlowUtils.writeFlowToController(salFlowService, nodeId, tableId, flowId2, f2);

            // increment execution number counter
            execCounterLeader++;

        }
    }

    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */

    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("ControllerSelfDiscovery ownership change logged: " + ownershipChange);

        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the ControllerSelfDiscovery leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the ControllerSelfDiscovery follower.");
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

    private void sleep_some_time(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
