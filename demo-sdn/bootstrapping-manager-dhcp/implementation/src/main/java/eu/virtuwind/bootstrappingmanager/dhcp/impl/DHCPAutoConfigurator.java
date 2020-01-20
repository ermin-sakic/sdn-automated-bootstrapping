/**
  *
  * @filename DHCPAutoConfigurator.java
  *
  * @date 09.05.18
  *
  * @author Mirza Avdic
  *
  *
 */
package eu.virtuwind.bootstrappingmanager.dhcp.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DHCPAutoConfigurator {
    private static final Logger LOG = LoggerFactory.getLogger(DHCPAutoConfigurator.class);
    private static boolean isLeader = false;
    public static boolean hasOwner = false;

    /**
     * register listener to get Bootstrapping state add/removed event.
     */
    private final DataBroker broker;

    public DHCPAutoConfigurator(final DataBroker db) {
        broker = db;
    }


    public void autoConfigureDHCP(DhcpServerConfig autoConfig) {

        List<DhcpRanges> myDhcpAddrRange = autoConfig.getDhcpRanges();
        List<BindDhcpInterface> myDhcpBindInterfaces = autoConfig.getBindDhcpInterface();

        // see Listener and implement config validity checking
        // add multiple controllers possibility


        LOG.info("DHCPAutoConfig: IP available locally, selecting the range: " + myDhcpAddrRange.get(0).getDhcpAddrRangeBegin() + " to "
                    + myDhcpAddrRange.get(0).getDhcpAddrRangeEnd()
                    + " and listen on the interface: " + myDhcpBindInterfaces.get(0).getInterfaceName());



        // Add the ELM configuration to the params of ExampleLeaseManager

        ExampleLeaseManagerModule.initiateELMInstance( myDhcpAddrRange.get(0).getDhcpAddrRangeBegin(), myDhcpAddrRange.get(0).getDhcpAddrRangeEnd());

        // If a cluster leader, start the DHCP server locally
        while (!hasOwner) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            LOG.info("Waiting for the election process to finish.");
        }
        if(isLeader) {
            LOG.info("I am the leader!");
            DhcpModule.instantiateDHCPServerWithAllParameters(myDhcpBindInterfaces.get(0).getInterfaceName());
        }
    }



    /**
     * Implements the reaction to cluster leadership changes.
     * @param ownershipChange
     */
    public static void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        LOG.info("flow-reconfig ownership change logged: " + ownershipChange);

        LOG.info("Owner available: {}", ownershipChange.hasOwner());
        if (ownershipChange.hasOwner())
            hasOwner = true;
        LOG.info("IsOwner: {}", ownershipChange.isOwner());

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
}
