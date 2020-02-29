package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;
import eu.virtuwind.registryhandler.impl.BootstrappingSwitchStateImpl;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingState;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingStateBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/*
    The Class that monitors whether the controllers are discovered in a network.
 */
public class InitialOFRulesTrigger implements ClusteredDataTreeChangeListener<Link> {

    private static final Logger LOG = LoggerFactory.getLogger(InitialOFRulesTrigger.class);
    private static ArrayList<String> ctlList = null;
    static boolean isAlreadyExecuted = false;
    // set through the EOS
    private static boolean isLeader = false;

    public static List<String> getCtlList() {
        return ctlList;
    }

    public static void setCtlList(ArrayList<String> ctlList) {
        InitialOFRulesTrigger.ctlList = ctlList;
    }


    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Link>> changes) {

        synchronized (this) {
            for (DataTreeModification<Link> change : changes) {

                //DataObjectModification.ModificationType modtype = change.getRootNode().getModificationType();
                DataObjectModification<Link> mod = change.getRootNode();

                if ((mod.getModificationType() == DataObjectModification.ModificationType.WRITE) && !isAlreadyExecuted
                        && isLeader) {
                    LOG.info("Self-Discovery done!");
                    try {
                        String source = change.getRootNode().getDataAfter().getSource().getSourceNode().getValue();
                        String destination = change.getRootNode().getDataAfter().getDestination().getDestNode().getValue();
                        boolean alreadySelfDiscoveryDoneDst;
                        boolean alreadySelfDiscoveryDoneSrc;
                        alreadySelfDiscoveryDoneDst = ControllerSelfDiscovery.initialSelfDiscoveryDoneNodes.contains(destination);
                        alreadySelfDiscoveryDoneSrc = ControllerSelfDiscovery.initialSelfDiscoveryDoneNodes.contains(source);

                        if (source.contains("host:")) {
                            // notify that self discovery is done for the leader
                            NodeId switchId = new NodeId(destination);
                            if (!alreadySelfDiscoveryDoneDst) {
                                BootstrappingSwitchStateImpl stateManager = BootstrappingSwitchStateImpl.getInstance();
                                stateManager.writeBootstrappingSwitchState(switchId, new SwitchBootsrappingStateBuilder()
                                        .setState(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYDONE)
                                        .build());
                                String currentState = stateManager.readBootstrappingSwitchStateName(switchId);
                                ControllerSelfDiscovery.initialSelfDiscoveryDoneNodes.add(switchId.getValue());
                                LOG.info("Change state of node {} to {}", switchId, currentState);

                            }
                            isAlreadyExecuted=true;
                            ControllerSelfDiscovery.setIsDiscovered(true);
                        } else if (destination.contains("host:")) {
                            // notify that self discovery is done for the leader
                            NodeId switchId = new NodeId(source);
                            if (!alreadySelfDiscoveryDoneSrc) {
                                BootstrappingSwitchStateImpl stateManager = BootstrappingSwitchStateImpl.getInstance();
                                stateManager.writeBootstrappingSwitchState(switchId, new SwitchBootsrappingStateBuilder()
                                        .setState(SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYDONE)
                                        .build());
                                ControllerSelfDiscovery.initialSelfDiscoveryDoneNodes.add(switchId.getValue());
                                String currentState = stateManager.readBootstrappingSwitchStateName(switchId);
                                LOG.info("Change state of node {} to {}", switchId, currentState);
                            }
                            isAlreadyExecuted=true;
                            ControllerSelfDiscovery.setIsDiscovered(true);
                        }
                    } catch (NullPointerException e) {
                        LOG.error("Source or Destination caused NullPointerException");
                    }

                } else if (!isLeader && (mod.getModificationType() == DataObjectModification.ModificationType.WRITE)) {
                    /*
                        Stops a periodic ControllerSelfDiscoveryFollowerPeriodicExecutor when a follower controller is
                        discovered.
                     */
                    try {
                        String source = change.getRootNode().getDataAfter().getSource().getSourceNode().getValue();
                        String destination = change.getRootNode().getDataAfter().getDestination().getDestNode().getValue();

                        HostUtilities.InterfaceConfiguration myIfConfig =
                                HostUtilities.returnMyIfConfig(ctlList);
                        if (myIfConfig == null) {
                            LOG.warn("Provided controller IP addresses are not found on this machine.");
                            return;
                        }
                        String instanceMAC = myIfConfig.getMacAddressString();
                        if (source.contains(instanceMAC) || destination.contains(instanceMAC)) {
                            ControllerSelfDiscovery.setIsDiscovered(true);
                        }
                    } catch (NullPointerException e) {
                        LOG.error("Source or Destination caused NullPointerException");
                    }
                }
            }

        }
    }

    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("InitialOFRulesTrigger ownership change logged: " + ownershipChange);

        if(ownershipChange.isOwner()) {
            LOG.info("This node is set as the InitialOFRulesTrigger leader.");
            setLeader();
        }
        else {
            LOG.info("This node is set as the InitialOFRulesTrigger follower.");
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
}
