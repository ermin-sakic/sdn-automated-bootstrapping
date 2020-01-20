package eu.virtuwind.bootstrappingmanager.alternative.dhcp.impl;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.*;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.NetworkGraphService;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.TopologyLinkDataChangeHandler;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.lldpspeaker.LLDPSpeakerAll;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.lldpspeaker.LLDPSpeakerEdge;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.ScheduledThreadPoolExecutorWrapper;
import io.netty.channel.EventLoopGroup;
import org.anarres.dhcp.common.address.InterfaceAddress;
import org.apache.directory.server.dhcp.service.DhcpService;
import org.apache.directory.server.dhcp.service.manager.LeaseManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.*;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.alternative.dhcp.rev161210.BootstrappingmanagerAlternativeDhcpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The entry-point to the Bootstrapping-Manager DHCP implementation. Holds the required activator method and helper methods
 * to start new DHCP and stop existing running DHCP server instances.
 */
public class DhcpModule extends eu.virtuwind.bootstrappingmanager.alternative.dhcp.impl.AbstractDhcpModule {
    private static final Logger LOG = LoggerFactory.getLogger(DhcpModule.class);
    private static LeaseManager leasemanager;
    private static EventLoopGroup workerThreadDependency;
    private static List<DefaultOption> defaultOption;
    private static  DhcpServer dhcpServer = null;

    // EOS Stuff
    private static InstanceEntityOwnershipListener instanceEntityOwnershipListener = null;
    private static final String ENTITY_TYPE = "BOOTSTRAPPING-PROVIDER";
    private static EntityOwnershipCandidateRegistration registration;
    private static EntityOwnershipService eosService = null;

    // configuration stuff
    private static DHCPAutoConfigurator dhcpAutoConfigurator = null;
    private static DhcpServerConfig dhcpServerConfig;

    // DhcpStateStoreExpiryManager scheduler
    private static ScheduledThreadPoolExecutorWrapper dhcpStateStoreExpiryManagerScheduler = new ScheduledThreadPoolExecutorWrapper(1);


    public DhcpModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public DhcpModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, eu.virtuwind.bootstrappingmanager.alternative.dhcp.impl.DhcpModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {}

    /**
     * Creates a new instance of the DHCP Module. Resolves all necessary dependencies and wires the RPC
     * registrations of BootstrappingmanagerDhcpService implementation with the MD-SAL.
     * @return The AutoCloseable of the instantiated provider (can be ignored for the time being).
     */
    @Override
    public java.lang.AutoCloseable createInstance() {
        LOG.debug("Starting DHCP server on port {}", getPort());

        RpcProviderRegistry rpcProviderRegistry = getRpcRegistryDependency();

        rpcProviderRegistry.addRpcImplementation(BootstrappingmanagerAlternativeDhcpService.class,
                BootstrappingManagerRESTImpl.getInstance());

        eosService = getEntityOwnershipServiceDependency();
        leasemanager  = getLeaseManagerDependency();
        workerThreadDependency = getWorkerThreadGroupDependency();
        defaultOption = getDefaultOption();
        dhcpServerConfig = getDhcpServerConfig();

        // ## Registers the EOS, so to allow for notifications related to node failures ##//
        initiateEoS();

        AutoCloseable autoCloseable = new DHCPProvider();

        // config the expected discovery period
        CustomisableLeaseManagerDhcpService.setExpectedDiscoveryPeriod(dhcpServerConfig.getExpectedDiscoveryPeriod());
        // config duplication mode
        CustomisableLeaseManagerDhcpService.setDuplicationProtectionMode(dhcpServerConfig.getDhcpDuplicationProtection());

        // check for the configuration mode
        String configMode = dhcpServerConfig.getDhcpConfigMode();

        DataBroker dataBrokerService = getDataBrokerDependency();
        DHCPConfigurationListener dhcpConfigurationListener = new DHCPConfigurationListener(dataBrokerService);


        if (configMode.equals("auto")) {
            // start DHCP server automatically
            LOG.info("Auto mode config for the DHCP server selected.");
            CustomisableLeaseManagerDhcpService.setConfigMode("auto");
            dhcpAutoConfigurator = new DHCPAutoConfigurator(dataBrokerService);
            dhcpAutoConfigurator.autoConfigureDHCP(getDhcpServerConfig());
        } else if(configMode.equals("viaREST")) {
            LOG.info("viaREST mode config for the DHCP server selected");
            CustomisableLeaseManagerDhcpService.setConfigMode("viaREST");
        } else {
            LOG.warn("Non-existing DHCP config mode selected.");
        }

        if (dhcpServerConfig.getDhcpDuplicationProtection()) {
            // Initiate dhcpStateStoreExpiryManagerScheduler
            dhcpStateStoreExpiryManagerScheduler.scheduleAtFixedRate(new DhcpStateStoreExpiryManager(), 0L, 1000L, TimeUnit.MILLISECONDS);
        }

        LOG.info("createInstance() in DhcpModule finished. Exiting...");
        return autoCloseable;
    }

    /**
     * Instantiates a new DHCP server instance.
     * @param bindDHCPInterface The host interface name on which DHCP server binds, listens to DHCP
     *                          requests and communicates leases to the clients.
     */
    public static void instantiateDHCPServerWithAllParameters(String bindDHCPInterface)
    {
        LOG.info("Starting the server with specified params");
        List<String> interfaces = new ArrayList<>();
        interfaces.add(bindDHCPInterface);

        try {
            startServer(67, interfaces, leasemanager, defaultOption, workerThreadDependency);
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Stops an existing instance of a DHCP server.
     * @return Boolean flag indicating the success of executed action.
     */
    public static boolean stopDHCPServer()
    {
        LOG.info("Initiated stopping the existing instance of a DHCP server.");
        try {
            dhcpServer.stop();
            return true;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Starts a new instance of a DHCP server.
     * @param port The port number on which the DHCP server is to be started. Set to 67 by default. Requires
     *             superuser rights to instantiate as the standard protected port is used.
     * @param networkInterfaces The list of network interfaces which the DHCP server listens on.
     * @param manager The lease manager assigned to this DHCP server instance - governs the IP addresses assigned
     *                to its clients.
     * @param options Additional options sent as a part of DHCP response.
     * @param eventLoopGroup Netty.io worker thread group dependency.
     * @return The AutoCloseable which overrides the close() method with clean disable of the DHCP server.
     */
    private static AutoCloseable startServer(final Integer port, final List<String> networkInterfaces,
                                             final LeaseManager manager, final List<DefaultOption> options,
                                             EventLoopGroup eventLoopGroup) {
        if (dhcpServer == null) {
            final DhcpService dhcpService = new CustomisableLeaseManagerDhcpService(manager, options);

            try {
                dhcpServer = new DhcpServer(dhcpService, port);
            } catch (IllegalArgumentException e) {
                LOG.error("DHCP server on port {} failed to decode option arguments {}", port, e.toString());
                throw new IllegalStateException(e);
            }

            // Localhost required special setting
            if (networkInterfaces.contains("lo") || networkInterfaces.isEmpty()) {
                try {
                    dhcpServer.addInterface(InterfaceAddress.forString("127.0.0.1"));
                } catch (Exception e) {
                    LOG.error("DHCP server on port {} failed to add network interface {}", port, e.toString());
                    throw new IllegalStateException(e);
                }
            }

            // Bind here to pre-configured network interfaces
            try {
                dhcpServer.addInterfaces(new Predicate<NetworkInterface>() {

                    public boolean apply(final NetworkInterface input) {
                        return (networkInterfaces.contains(input.getName()) || networkInterfaces.isEmpty());
                    }
                });
            } catch (Exception e) {
                LOG.error("DHCP server on port {} failed to add network interfaces: {}", port, e.toString());
                throw new IllegalStateException(e);
            }

            // Start the server
            try {
                dhcpServer.start(eventLoopGroup);
                LOG.info("DHCP server on port {} started", port);

                return new AutoCloseable() {
                    @Override
                    public void close() throws Exception {
                        LOG.debug("Stopping DHCP server on port {}", port);
                        dhcpServer.stop();
                        LOG.info("DHCP server on port {} stopped", port);
                    }
                };
            } catch (Exception e) {
                LOG.error("DHCP server on port {} failed to start");
                throw new IllegalStateException(e);
            }
        }
        else throw new IllegalStateException("DHCP Server already started!");
    }


    /**
     * Implements the registration of all bootstrapping topics so to allow for notification
     * on every signalled cluster ownership update.
      */
    public static void initiateEoS() {
        //Register listener for entityOwnership changes
        instanceEntityOwnershipListener =
                new InstanceEntityOwnershipListener(eosService);

        Entity instanceEntity = new Entity(ENTITY_TYPE, ENTITY_TYPE);
        try {
            Optional<EntityOwnershipState> ownershipStateOpt = eosService.getOwnershipState(instanceEntity);
            registration = eosService.registerCandidate(instanceEntity);
            if (ownershipStateOpt.isPresent()) {
                EntityOwnershipState ownershipState = ownershipStateOpt.get();
                if (ownershipState.hasOwner() && !ownershipState.isOwner()) {
                    LOG.info("This instance was elected a FOLLOWER for topic" + ENTITY_TYPE.toString());
                    InitialFlowWriter.setFollower();
                    BootstrappingManagerRESTImpl.setFollower();
                    FlowReconfiguratorForResilienceStateListener.setFollower();
                    DHCPConfigurationListener.setFollower();
                    DHCPAutoConfigurator.setFollower();
                    //DHCPAutoConfigurator.hasOwner = true;
                    ControllerSelfDiscovery.setFollower();
                    InitialOFRulesPhaseI.setFollower();
                    InitialOFRulesPhaseII.setFollower();
                    LLDPSpeakerEdge.setFollower();
                    LLDPSpeakerAll.setFollower();
                    TopologyLinkDataChangeHandler.setFollower();
                    InitialOFRulesTrigger.setFollower();
                    NetworkGraphService.setFollower();
                    NetworkExtensionManager.setFollower();

                }
                else if (ownershipState.hasOwner() && ownershipState.isOwner()) {
                    LOG.info("This instance was elected a LEADER for topic" + ENTITY_TYPE.toString());

                    InitialFlowWriter.setLeader();
                    BootstrappingManagerRESTImpl.setLeader();
                    FlowReconfiguratorForResilienceStateListener.setLeader();
                    DHCPConfigurationListener.setLeader();
                    DHCPAutoConfigurator.setLeader();
                    //DHCPAutoConfigurator.hasOwner = true;
                    ControllerSelfDiscovery.setLeader();
                    InitialOFRulesPhaseI.setLeader();
                    InitialOFRulesPhaseII.setLeader();
                    LLDPSpeakerEdge.setLeader();
                    LLDPSpeakerAll.setLeader();
                    TopologyLinkDataChangeHandler.setLeader();
                    InitialOFRulesTrigger.setLeader();
                    NetworkGraphService.setLeader();
                    NetworkExtensionManager.setLeader();
                }
            }
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.warn("This entity {} was already "
                    + "registered for ownership", instanceEntity, e);
        }
    }

    /**
     * Implements the ownership service listener so to allow for proxying the notification about cluster state updates.
     */
    private static class InstanceEntityOwnershipListener implements EntityOwnershipListener {
        private EntityOwnershipListenerRegistration listenerRegistration;

        InstanceEntityOwnershipListener(EntityOwnershipService entityOwnershipService) {
            listenerRegistration = entityOwnershipService.registerListener(ENTITY_TYPE, this);
        }

        public void close() {
            this.listenerRegistration.close();
        }

        @Override
        public void ownershipChanged(EntityOwnershipChange ownershipChange) {
            LOG.info("OwnershipChange logged! --> Transferring event to FlowReconfiguration, DHCPServer instances, " +
                    "FlowReconfiguratorForResilienceStateListener, ... .");
            LOG.info("OWNERSHIP-CHANGED");
            InitialFlowWriter.handleOwnershipChange(ownershipChange);
            FlowReconfiguratorForResilienceStateListener.handleOwnershipChange(ownershipChange);
            BootstrappingManagerRESTImpl.handleOwnershipChange(ownershipChange);
            DHCPConfigurationListener.handleOwnershipChange(ownershipChange);
            DHCPAutoConfigurator.handleOwnershipChange(ownershipChange);
            ControllerSelfDiscovery.handleOwnershipChange(ownershipChange);
            InitialOFRulesPhaseI.handleOwnershipChange(ownershipChange);
            InitialOFRulesPhaseII.handleOwnershipChange(ownershipChange);
            LLDPSpeakerEdge.handleOwnershipChange(ownershipChange);
            LLDPSpeakerAll.handleOwnershipChange(ownershipChange);
            TopologyLinkDataChangeHandler.handleOwnershipChange(ownershipChange);
            InitialOFRulesTrigger.handleOwnershipChange(ownershipChange);
            NetworkGraphService.handleOwnershipChange(ownershipChange);
            NetworkExtensionManager.handleOwnershipChange(ownershipChange);
        }
    }
}
