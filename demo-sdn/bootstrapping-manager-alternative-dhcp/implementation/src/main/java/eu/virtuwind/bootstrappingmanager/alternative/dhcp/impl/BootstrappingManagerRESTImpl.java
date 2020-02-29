package eu.virtuwind.bootstrappingmanager.alternative.dhcp.impl;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.ConfigureNewOpenFlowNodeNBI;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;
import eu.virtuwind.registryhandler.impl.BootstrappingRegistryImpl;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.*;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingDatastore;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingDatastoreBuilder;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.BindDhcpInterface;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.BindDhcpInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.DhcpAddrRange;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.DhcpAddrRangeBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Implements the REST-enabled configuration of Bootstrapping
 * Manager. Allows specification of subnet configuration, cont-
 * roller IP addresses, interface to listen on  and similar.
 */
public class BootstrappingManagerRESTImpl implements BootstrappingmanagerAlternativeDhcpService {
    private static final Logger logger = LoggerFactory.getLogger(BootstrappingManagerRESTImpl.class);
    private static BootstrappingManagerRESTImpl instance;
    private static boolean configurationSetFlag = false;
    private static boolean isLeader = false;

    /**
     * Default constructor - Intentionally kept empty.
     */
    BootstrappingManagerRESTImpl(){
    }

    /**
     * Return a singleton instance of the BootstrappingManagerRESTImpl.
     * @return Singleton instance of the implementation.
     */
    public static BootstrappingManagerRESTImpl getInstance()
    {
        if(instance == null)
            instance = new BootstrappingManagerRESTImpl();

        return instance;
    }

    /**
     * Accepts a bootstrapping configuration that results in an overwritten existing configuration or
     * new configuration in case of no previous configuration. Furthermore starts the LeaseManager and DHCPServer
     * implementations based on the given set of server configurations.
     * @param input Contains DHCP server parameters, such as the interface to bind on
     *              the IP addresses of master and slave controllers, the DHCP address
     *              range to lease the IP addresses from, and the SSH login data to make
     *              modification of switch configurations.     *
     * @return Result of the configuration modification indicating success or failure.
     */
    @Override
    public Future<RpcResult<BootstrappingConfigurationModifyOutput>> bootstrappingConfigurationModify(BootstrappingConfigurationModifyInput input) {
        logger.info("Initiated modification of bootstrapping configuration.");

        BootstrappingConfigurationModifyOutputBuilder outputBuilder = new BootstrappingConfigurationModifyOutputBuilder();

        List<org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017
                .bootstrapping.datastore.ControllerIpListSlave> listOfControllerIps =
                new ArrayList<org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.ControllerIpListSlave>();

        org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping
                .datastore.ControllerIpListSlaveBuilder controllerIpListSlave =
                new org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping
                        .rev161017.bootstrapping.datastore.ControllerIpListSlaveBuilder();

        List<BindDhcpInterface> listOfDhcpInterfaces = new ArrayList<>();
        BindDhcpInterfaceBuilder bindDhcpInterfaceBuilder = new BindDhcpInterfaceBuilder();

        List<DhcpAddrRange> listOfDhcpAddrRanges = new ArrayList<>();
        DhcpAddrRangeBuilder dhcpAddrRangeBuilder = new DhcpAddrRangeBuilder();

        // Slave controller spec is optional
        if(input.getControllerIpListSlave()!=null)
            for(org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.modify.input.ControllerIpListSlave ipListSlaveEntry:input.getControllerIpListSlave())
                listOfControllerIps.add(controllerIpListSlave.setIpAddr(ipListSlaveEntry.getIpAddr()).build());

        // Transform the list of DHCP Interfaces to internal datastore representation
        if(input.getBindDhcpInterface()!=null)
            for(org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.modify.input.BindDhcpInterface dhcpInterface:input.getBindDhcpInterface())
                listOfDhcpInterfaces.add(bindDhcpInterfaceBuilder.setInterfaceName(dhcpInterface.getInterfaceName()).build());
        else return RpcResultBuilder.success(outputBuilder.setResponse("Failed - missing interface list specification!").build()).buildFuture();

        // Transform the list of DHCP Range specifications for internal data-store representation
        if(input.getDhcpRanges()!=null) {
            for(org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.modify.input.DhcpRanges dhcpRange:input.getDhcpRanges())

                listOfDhcpAddrRanges.add(
                        dhcpAddrRangeBuilder
                                .setDhcpAddrRangeBegin(dhcpRange.getDhcpAddrRangeBegin())
                                .setDhcpAddrRangeEnd(dhcpRange.getDhcpAddrRangeEnd())
                                .build()
                );
        }
        else return RpcResultBuilder.success
                (outputBuilder.setResponse("Failed - missing DHCP Range list specification!").build()).buildFuture();

        BootstrappingDatastoreBuilder bootstrappingDatastore = new BootstrappingDatastoreBuilder()
                .setBindDhcpInterface(listOfDhcpInterfaces)
                .setControllerIpListSlave(listOfControllerIps)
                .setControllerIpMaster(input.getControllerIpMaster())
                .setDhcpAddrRange(listOfDhcpAddrRanges)
                .setSshPassword(input.getSshPassword())
                .setSshUsername(input.getSshUsername());


        boolean success = BootstrappingRegistryImpl.getInstance()
                .writeModifiedBootstrappingConfigurationToDataStore(bootstrappingDatastore);

        BootstrappingConfigurationModifyOutput output =
                new BootstrappingConfigurationModifyOutputBuilder()
                        .setResponse(String.valueOf(success)).build();

        org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.modify.input.DhcpRanges myDhcpAddrRange = null;
        org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.modify.input.BindDhcpInterface myDhcpBindInterface = null;
        if(HostUtilities.isIPAvailableLocally(input.getControllerIpMaster())) {
            myDhcpAddrRange = input.getDhcpRanges().get(0);
            myDhcpBindInterface = input.getBindDhcpInterface().get(0);

            logger.info("IP available locally, selecting the range: " + myDhcpAddrRange.getDhcpAddrRangeBegin() + " to " + myDhcpAddrRange.getDhcpAddrRangeEnd());
        }
        else if (!HostUtilities.isIPAvailableLocally(input.getControllerIpMaster())) {
            for(org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.modify.input.ControllerIpListSlave ip:input.getControllerIpListSlave()) {
                if(HostUtilities.isIPAvailableLocally(ip.getIpAddr())) {
                    myDhcpAddrRange = input.getDhcpRanges().get(input.getControllerIpListSlave().indexOf(ip)+1);
                    myDhcpBindInterface = input.getBindDhcpInterface().get(input.getControllerIpListSlave().indexOf(ip)+1);

                    logger.info("IP available locally, selecting the range: " + myDhcpAddrRange.getDhcpAddrRangeBegin() + " to " + myDhcpAddrRange.getDhcpAddrRangeEnd());
                }
            }
        }

        if(myDhcpAddrRange==null)
            return RpcResultBuilder.success
                    (outputBuilder.setResponse("Failed - DHCP address ranges not matching with the controller IPs.")
                            .build()).buildFuture();
        if(myDhcpBindInterface==null)
            return RpcResultBuilder.success
                    (outputBuilder.setResponse("Failed - DHCP bind interface not matching with the controller IPs.")
                            .build()).buildFuture();

        // Stop any running ExampleLeaseManager instances
        ExampleLeaseManagerModule.stopCurrentELMInstance();

        // Stop any running DHCPServer instances
        DhcpModule.stopDHCPServer();

        BootstrappingDatastore currentDatastoreConfig =
                BootstrappingRegistryImpl.getInstance().readBootstrappingConfigurationFromDataStore();

        // Add the ELM configuration to the params of ExampleLeaseManager
        ExampleLeaseManagerModule.initiateELMInstance(myDhcpAddrRange.getDhcpAddrRangeBegin(), myDhcpAddrRange.getDhcpAddrRangeEnd());

        // Slave controller spec is optional
        List<String> listOfStringSlaveCIPs = new ArrayList<>();
        if(currentDatastoreConfig.getControllerIpListSlave()!=null) {
            for (org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping
                    .rev161017.bootstrapping.datastore.ControllerIpListSlave ip :
                    currentDatastoreConfig.getControllerIpListSlave())
                listOfStringSlaveCIPs.add(ip.getIpAddr());
        }

        //Add the default SSH/OpenFlow configuration
        ConfigureNewOpenFlowNodeNBI.changeDefaultConfig(currentDatastoreConfig.getControllerIpMaster(), listOfStringSlaveCIPs,
                currentDatastoreConfig.getSshUsername(), currentDatastoreConfig.getSshPassword());

        if(isLeader) {
            DhcpModule.instantiateDHCPServerWithAllParameters(myDhcpBindInterface.getInterfaceName());
        }
            // Add the DHCP configuration to the params of DHCPServer

        return RpcResultBuilder.success(output).buildFuture();
    }

    /**
     * Applies a new bootstrapping configuration in the datastore. Overwrites any existing configurations.
     * Furthermore starts the LeaseManager and DHCPServer implementations based on the given set of server
     * configurations.
     * @param input Contains DHCP server parameters, such as the interface to bind on
     *              the IP addresses of master and slave controllers, the DHCP address
     *              range to lease the IP addresses from, and the SSH login data to make
     *              modification of switch configurations.
     * @return      Result of the new configuration indicating success or failure.
     */
    @Override
    public Future<RpcResult<BootstrappingConfigurationInputOutput>> bootstrappingConfigurationInput(BootstrappingConfigurationInputInput input) {
        BootstrappingConfigurationInputOutputBuilder outputBuilder = new BootstrappingConfigurationInputOutputBuilder();

        logger.info("Initiated initial configuration of bootstrapping configuration.");
        // TODO: Requires proper input validation

        List<org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.ControllerIpListSlave> listOfControllerIps
                = new ArrayList<org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.ControllerIpListSlave>();
        org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.ControllerIpListSlaveBuilder controllerIpListSlave
                = new org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.bootstrapping.datastore.ControllerIpListSlaveBuilder();

        List<BindDhcpInterface> listOfDhcpInterfaces = new ArrayList<>();
        BindDhcpInterfaceBuilder bindDhcpInterfaceBuilder = new BindDhcpInterfaceBuilder();

        List<DhcpAddrRange> listOfDhcpAddrRanges = new ArrayList<>();
        DhcpAddrRangeBuilder dhcpAddrRangeBuilder = new DhcpAddrRangeBuilder();

        // Slave controller spec is optional
        if(input.getControllerIpListSlave()!=null)
            for(org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.input.input.ControllerIpListSlave ipListSlaveEntry:input.getControllerIpListSlave())
                listOfControllerIps.add(controllerIpListSlave.setIpAddr(ipListSlaveEntry.getIpAddr()).build());

        // Transform the list of DHCP Interfaces to internal datastore representation
        if(input.getBindDhcpInterface()!=null)
            for(org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.input.input.BindDhcpInterface dhcpInterface:input.getBindDhcpInterface())
                listOfDhcpInterfaces.add(bindDhcpInterfaceBuilder.setInterfaceName(dhcpInterface.getInterfaceName()).build());
        else return RpcResultBuilder.success(outputBuilder.setResponse("Failed - missing interface list specification!").build()).buildFuture();

        // Transform the list of DHCP Range specifications for internal data-store representation
        if(input.getDhcpRanges()!=null) {
            for(org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.
                    dhcp.rev161210.bootstrapping.configuration.input.input.DhcpRanges dhcpRange:input.getDhcpRanges())

                listOfDhcpAddrRanges.add(
                        dhcpAddrRangeBuilder
                                .setDhcpAddrRangeBegin(dhcpRange.getDhcpAddrRangeBegin())
                                .setDhcpAddrRangeEnd(dhcpRange.getDhcpAddrRangeEnd())
                                .build()
                );
        }
        else return RpcResultBuilder.success
                (outputBuilder.setResponse("Failed - missing DHCP Range list specification!").build()).buildFuture();

        BootstrappingDatastoreBuilder bootstrappingDatastore = new BootstrappingDatastoreBuilder()
                .setBindDhcpInterface(listOfDhcpInterfaces)
                .setControllerIpListSlave(listOfControllerIps)
                .setControllerIpMaster(input.getControllerIpMaster())
                .setDhcpAddrRange(listOfDhcpAddrRanges)
                .setSshPassword(input.getSshPassword())
                .setSshUsername(input.getSshUsername());

        boolean success = BootstrappingRegistryImpl.getInstance().writeBootstrappingConfigurationToDataStore(bootstrappingDatastore);

        BootstrappingConfigurationInputOutput output = new BootstrappingConfigurationInputOutputBuilder()
                .setResponse(String.valueOf(success)).build();

        org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.input.input.DhcpRanges myDhcpAddrRange = null;
        org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.input.input.BindDhcpInterface myDhcpBindInterface = null;
        if(HostUtilities.isIPAvailableLocally(input.getControllerIpMaster())) {
            myDhcpAddrRange = input.getDhcpRanges().get(0);
            myDhcpBindInterface = input.getBindDhcpInterface().get(0);

            logger.info("IP available locally, selecting the range: " + myDhcpAddrRange.getDhcpAddrRangeBegin() + " to " + myDhcpAddrRange.getDhcpAddrRangeEnd()
                    + " and listen on the interface: " + myDhcpBindInterface);
        }
        else if (!HostUtilities.isIPAvailableLocally(input.getControllerIpMaster())) {
            for(org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.input.input.ControllerIpListSlave ip:input.getControllerIpListSlave()) {
                if(HostUtilities.isIPAvailableLocally(ip.getIpAddr())) {
                    myDhcpAddrRange = input.getDhcpRanges().get(input.getControllerIpListSlave().indexOf(ip)+1);
                    myDhcpBindInterface = input.getBindDhcpInterface().get(input.getControllerIpListSlave().indexOf(ip)+1);

                    logger.info("IP available locally, selecting the range: " + myDhcpAddrRange.getDhcpAddrRangeBegin() + " to " + myDhcpAddrRange.getDhcpAddrRangeEnd()
                            + " and listen on the interface: " + myDhcpBindInterface);
                }
            }
        }

        if(myDhcpAddrRange==null)
            return RpcResultBuilder.success
                    (outputBuilder.setResponse("Failed - DHCP address ranges not matching with the controller IPs.")
                            .build()).buildFuture();
        if(myDhcpBindInterface==null)
            return RpcResultBuilder.success
                    (outputBuilder.setResponse("Failed - DHCP bind interface not matching with the controller IPs.")
                            .build()).buildFuture();

        configurationSetFlag = true;

        // Add the ELM configuration to the params of ExampleLeaseManager
        ExampleLeaseManagerModule.initiateELMInstance(myDhcpAddrRange.getDhcpAddrRangeBegin(), myDhcpAddrRange.getDhcpAddrRangeEnd());

        // Slave controller spec is optional
        List<String> listOfStringSlaveCIPs = new ArrayList<>();
        if(input.getControllerIpListSlave()!=null) {
            for (org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.input.input.ControllerIpListSlave ip : input.getControllerIpListSlave())
                listOfStringSlaveCIPs.add(ip.getIpAddr());
        }

        //Add the default SSH/OpenFlow configuration
        ConfigureNewOpenFlowNodeNBI.changeDefaultConfig(input.getControllerIpMaster(), listOfStringSlaveCIPs, input.getSshUsername(), input.getSshPassword());

        ArrayList ctlList = new ArrayList<String>();
        ctlList.add(input.getControllerIpMaster());
        for(org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.bootstrapping.configuration.input.input.ControllerIpListSlave ip:input.getControllerIpListSlave()) {
            ctlList.add(ip.getIpAddr());
        }

        // Only if elected as leader, start the DHCP server on this instance
        if(isLeader) {
            // Add the DHCP configuration to the params of DHCPServer
            DhcpModule.instantiateDHCPServerWithAllParameters(myDhcpBindInterface.getInterfaceName());
        }

        //FlowReconfigurator.setCtlList(ctlList);

        return RpcResultBuilder.success(output).buildFuture();
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

    static boolean wasConfigurationSetAlready() {
        return configurationSetFlag;
    }
}
