package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.IPPrefAddrInfo;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.SyncLock;
import eu.virtuwind.registryhandler.impl.BootstrappingRegistryImpl;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.openflowplugin.api.openflow.OpenFlowPluginProvider;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingDatastore;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.ControllerIpListSlave;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.SwitchBootstrappingAugmentation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import eu.virtuwind.registryhandler.impl.BootstrappingSwitchStateImpl;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingState;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingStateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adds a set of default flows, configured on a newly discovered switch after an OpenFlow session has been initiated.
 * Registers as ODL Inventory listener so that it can add flows once a new node i.e. switch is added.
 */
public class InitialFlowWriter implements ClusteredDataTreeChangeListener<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(InitialFlowWriter.class);

    private DataBroker dataBroker;
    public static AtomicLong flowIdInc = new AtomicLong();
    private static boolean isLeader = false;
    private static boolean bootstrappingDone = false;
    private static ArrayList<String> ctlList = new ArrayList<String>();
    private static boolean hasOwner = false;
    // specified using the northbound interface
    private static boolean flagSetupViaNBI = false;
    // keep track of the nodes that already were this state
    public static List<String> initialOFSessionEstablishedDoneNodes = new ArrayList<>();
    // saves the last known state of the failed switch
    private static HashMap<String, SwitchBootsrappingState.State> failedSwitches = new HashMap<>();

    InitialFlowWriter(DataBroker dataBroker){
        this.dataBroker = dataBroker;
    }

    public static void setBootstrappingDone() {
        bootstrappingDone = true;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Node>> collection) {
            for (DataTreeModification<Node> change : collection) {
                DataObjectModification<Node> mod = change.getRootNode();
                switch (mod.getModificationType()) {
                    case DELETE:
                        //TODO: Extend logger to see when these weird situations happen
                        if (mod.getDataBefore() != null) {
                            LOG.warn("Deleted node {} was in state: {}",
                                    mod.getDataBefore().getId().getValue(),
                                    mod.getDataBefore().getAugmentation(SwitchBootstrappingAugmentation.class)
                                            .getSwitchBootsrappingState().getState().getName());
                            failedSwitches.put(mod.getDataBefore().getId().getValue(),
                                    mod.getDataBefore().getAugmentation(SwitchBootstrappingAugmentation.class)
                                            .getSwitchBootsrappingState().getState());
                        } else {
                            LOG.warn("Deleted node -> before data equals null");
                        }
                        LOG.info(" Deleted after data {} ", mod.getDataAfter());
                        break;
                    case SUBTREE_MODIFIED:
                        break; // TODO: Fix at some point
                    case WRITE:
                        LOG.info("New node written in the DS.");
                        while(!hasOwner) { sleep_some_time(5); }

                        if (mod.getDataBefore() == null) {
                            Node fetchedDS = mod.getDataAfter();
                            LOG.info("Node configuration updated remotely");

                            if (!isLeader)
                                LOG.info("Data changed but not a leader...");

                            if (isLeader && fetchedDS != null && bootstrappingDone == false) {

                                InstanceIdentifier<Node> nodeInstanceIdentifier = InitialFlowUtils.getNodeInstanceId(fetchedDS);
                                if(nodeInstanceIdentifier == null) {return;}

                                if (Node.class.isAssignableFrom(nodeInstanceIdentifier.getTargetType())) {
                                    InstanceIdentifier<Node> topoNodeId = (InstanceIdentifier<Node>) nodeInstanceIdentifier;
                                    if (topoNodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue().contains("openflow:")) {

                                        //--------------------------------------------------------------------------------
                                        LOG.info("New OF capable node discovered: {}", fetchedDS.getKey().getId().getValue());
                                        String nodeIPAddress = "";
                                        // In case that a node has changed an IP address the new node in the datastore tree
                                        // will be created and the old data will be deleted, therefore, return if no
                                        // IP available after 10 attempts
                                        Integer attemptCounter = 0;
                                        while(nodeIPAddress.equals("") && attemptCounter<=10) {
                                            attemptCounter++;
                                            sleep_some_time(100);
                                            nodeIPAddress =  InitialFlowUtils.getNodeIPAddress(fetchedDS, dataBroker);
                                        }
                                        if(attemptCounter == 10) {
                                            LOG.warn("Could not fetch an IP address for the node {}", fetchedDS.getId().getValue());
                                            return;
                                        }
                                        LOG.info("IP address of the new node is {}", nodeIPAddress);
                                        /****************************************************************************/
                                        BootstrappingSwitchStateImpl stateManager = null;
                                        while (stateManager == null) {
                                            try {
                                                stateManager =  BootstrappingSwitchStateImpl.getInstance();
                                            } catch (NullPointerException e) {
                                                LOG.warn("stateManager instance currently not available!!!");
                                            }
                                        }
                                        String attemptId = nodeIPAddress + ConfigureNewOpenFlowNodeAuto.mapToMacAddress(nodeIPAddress);
                                        synchronized (SyncLock.threadSync.get(attemptId)) {
                                            try {
                                                while(!SyncLock.threadSync.get(attemptId).isCondVariable()){
                                                    LOG.info("Waiting for ConfAuto thread of " + nodeIPAddress + " to finish.");
                                                    SyncLock.threadSync.get(attemptId).wait();
                                                }
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        synchronized (this) {
                                            stateManager.writeBootstrappingSwitchState(fetchedDS.getId(), new SwitchBootsrappingStateBuilder()
                                                    .setState(SwitchBootsrappingState.State.OFSESSIONESTABLISHED)
                                                    .build());
                                            initialOFSessionEstablishedDoneNodes.add(fetchedDS.getKey().getId().getValue());
                                        }

                                        /****************************************************************************/

                                    }
                                }

                            }
                        }
                        break;
                    default:
                        LOG.info("UNHANDLED: Node configuration updated remotely");
                        throw new IllegalArgumentException("Unhandled node modification type " + mod.getModificationType());
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
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("InitialFlowWriter ownership change logged: " + ownershipChange);

        if(ownershipChange.hasOwner()) {
            LOG.info("This system now has an owner.");
            hasOwner = true;
        } else {
            LOG.info("This system still does not have an owner.");
        }
        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as InitialFlowWriter leader.");
            setLeader();
        }
        else if(!ownershipChange.isOwner()) {
            LOG.info("This node is set as the InitialFlowWriter follower.");
            setFollower();
        }
    }

    /**
     * Confirms this instance is the current cluster leader.
     */
    public static void setLeader() {isLeader = true; }

    /**
     * Sets this instance as a cluster follower.
     */
    public static void setFollower() {isLeader = false;}
}

