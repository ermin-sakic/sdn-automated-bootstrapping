package eu.virtuwind.bootstrappingmanager.alternative.dhcp.impl;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.ConfigureNewOpenFlowNodeNBI;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
//import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingDatastore;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.BindDhcpInterface;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.ControllerIpListSlave;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.DhcpAddrRange;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by ermin on 07.04.17.
 */
public class DHCPConfigurationListener implements ClusteredDataTreeChangeListener<BootstrappingDatastore> {
    private static final Logger logger = LoggerFactory.getLogger(DHCPConfigurationListener.class);
    private static boolean isLeader = false;

    /**
     * register listener to get Bootstrapping state add/removed event.
     */
    private final DataBroker broker;

    public DHCPConfigurationListener(final DataBroker db) {
        broker = db;
        registerListener();
    }

    public void registerListener() {
        final DataTreeIdentifier<BootstrappingDatastore> treeId = new DataTreeIdentifier<>(
                LogicalDatastoreType.OPERATIONAL, getObservedPath());
        broker.registerDataTreeChangeListener(treeId, DHCPConfigurationListener.this);
        logger.info("DHCP Configuration Listener registration success");
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<BootstrappingDatastore>> changes) {
        logger.info("DHCPConfigurationListener onDataTreeChanged {} ", changes );
        for (DataTreeModification<BootstrappingDatastore> change : changes) {
            DataObjectModification<BootstrappingDatastore> mod = change.getRootNode();
            switch (mod.getModificationType()) {
                case DELETE:
                    logger.info(" Delete after data {} ", mod.getDataAfter());
                    break;
                case SUBTREE_MODIFIED:
                    break; // TODO: Fix at some point
                case WRITE:
                    if (mod.getDataBefore() == null) {
                        BootstrappingDatastore fetchedDS = mod.getDataAfter();
                        logger.info("BootstrappingDatastore configuration updated remotely: {} ", fetchedDS);
                        if(!BootstrappingManagerRESTImpl.getInstance().wasConfigurationSetAlready())
                            handleUpdatedDatastore(fetchedDS);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unhandled node modification type " + mod.getModificationType());
            }
        }
    }

    protected InstanceIdentifier<BootstrappingDatastore> getObservedPath() {
        return InstanceIdentifier.create(BootstrappingDatastore.class);
    }

    protected DHCPConfigurationListener getDataTreeChangeListener() {
        return this;
    }

    public void handleUpdatedDatastore(BootstrappingDatastore mod) {
        DhcpAddrRange myDhcpAddrRange = null;
        BindDhcpInterface myDhcpBindInterface = null;
        if(HostUtilities.isIPAvailableLocally(mod.getControllerIpMaster())) {
            myDhcpAddrRange = mod.getDhcpAddrRange().get(0);
            myDhcpBindInterface = mod.getBindDhcpInterface().get(0);

            logger.info("IP available locally, selecting the range: " + myDhcpAddrRange.getDhcpAddrRangeBegin() + " to " + myDhcpAddrRange.getDhcpAddrRangeEnd()
                    + " and listen on the interface: " + myDhcpBindInterface);
        }
        else if (!HostUtilities.isIPAvailableLocally(mod.getControllerIpMaster())) {
            for(ControllerIpListSlave ip:mod.getControllerIpListSlave()) {
                if(HostUtilities.isIPAvailableLocally(ip.getIpAddr())) {
                    myDhcpAddrRange = mod.getDhcpAddrRange().get(mod.getControllerIpListSlave().indexOf(ip)+1);
                    myDhcpBindInterface = mod.getBindDhcpInterface().get(mod.getControllerIpListSlave().indexOf(ip)+1);

                    logger.info("IP available locally, selecting the range: " + myDhcpAddrRange.getDhcpAddrRangeBegin() + " to " + myDhcpAddrRange.getDhcpAddrRangeEnd()
                            + " and listen on the interface: " + myDhcpBindInterface);
                }
            }
        }

        if(myDhcpAddrRange==null)
            logger.error("Failed - DHCP address ranges not matching with the controller IPs.");
        if(myDhcpBindInterface==null)
            logger.error("Failed - DHCP bind interface not matching with the controller IPs.");

        // Add the ELM configuration to the params of ExampleLeaseManager
        ExampleLeaseManagerModule.initiateELMInstance(myDhcpAddrRange.getDhcpAddrRangeBegin(), myDhcpAddrRange.getDhcpAddrRangeEnd());

        // Slave controller spec is optional
        List<String> listOfStringSlaveCIPs = new ArrayList<>();
        if(mod.getControllerIpListSlave()!=null) {
            for (ControllerIpListSlave ip : mod.getControllerIpListSlave())
                listOfStringSlaveCIPs.add(ip.getIpAddr());
        }

        //Add the default SSH/OpenFlow configuration
        ConfigureNewOpenFlowNodeNBI.changeDefaultConfig(mod.getControllerIpMaster(), listOfStringSlaveCIPs, mod.getSshUsername(), mod.getSshPassword());

        ArrayList ctlList = new ArrayList<String>();
        ctlList.add(mod.getControllerIpMaster());

        for(ControllerIpListSlave ip:mod.getControllerIpListSlave()) {
            ctlList.add(ip.getIpAddr());
        }

        // If a cluster leader, start the DHCP server locally
        if(isLeader) {
            DhcpModule.instantiateDHCPServerWithAllParameters(myDhcpBindInterface.getInterfaceName());
        }
    }

    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        logger.info("flow-reconfig ownership change logged: " + ownershipChange);

        if(ownershipChange.isOwner()) {
            logger.info("This node is set as the flow-reconfig leader.");
            setLeader();
        }
        else {
            logger.info("This node is set as the flow-reconfig follower.");
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
