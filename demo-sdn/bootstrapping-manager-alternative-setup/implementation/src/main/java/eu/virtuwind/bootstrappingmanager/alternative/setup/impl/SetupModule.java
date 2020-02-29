package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;


import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff.*;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.*;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.lldpspeaker.LLDPSpeakerAll;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.lldpspeaker.LLDPSpeakerEdge;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.lldpspeaker.NodeConnectorInventoryEventTranslator;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.topologylldpdiscovery.LLDPActivator;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.topologylldpdiscovery.LLDPLinkAger;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.topologylldpdiscovery.LLDPDiscoveryListener;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.SwitchBootstrappingAugmentation;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.FlowTopologyDiscoveryListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;

//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.topology.lldp.discovery.config.rev160511.NonZeroUint32Type;


import org.opendaylight.yangtools.yang.binding.NotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * First class executed in the module (createInstance() method); initializes other classes with the config from the XML
 * file; registers listeners for certain events
 */
public class SetupModule extends eu.virtuwind.bootstrappingmanager.alternative.setup.impl.AbstractSetupModule {
    private static final Logger LOG = LoggerFactory.getLogger(SetupModule.class);
    private static DataBroker dataBrokerService = null;
    private static SalFlowService salFlowService = null;
    private static PacketProcessingService packetProcessingService = null;
    private static short dropAllFlowTableId;
    private static Integer dropAllFlowHardTimeout, dropAllFlowIdleTimeout;
    private static Thread t = null;
    private Registration listenerRegistration = null;
    private ListenerRegistration<NotificationListener> flowTopologyDiscoveryListenerRegistration;
    private ListenerRegistration<NotificationListener> lldpNotificationRegistration;
    private ListenerRegistration<NotificationListener> ofNotificationRegistration;
    private ListenerRegistration<NotificationListener> dhcpNotificationRegistration;
    private NotificationProviderService notificationService;
    private TopologyLinkDataChangeHandler topologyLinkDataChangeHandler;
    public static NetworkGraphService networkGraphService;
    private FlowTopologyDiscoveryListener flowTopologyDiscoveryListener = null;
    private LLDPDiscoveryListener lldpDiscoveryListener;
    private LLDPActivator lldpActivator;
    private LLDPLinkAger lldpLinkAger;
    private OFPacketListener ofPacketListener = null;
    private DHCPPacketInListener dhcpPacketInListener = null;
    private LLDPSpeakerEdge lldpSpeakerEdge;
    private LLDPSpeakerAll lldpSpeakerAll;
    private NodeConnectorInventoryEventTranslator nodeConnectorInventoryEventTranslator;

    static String TOPOLOGY_ID = "flow:1";


    public SetupModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public SetupModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, SetupModule oldModule, AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    /**
     * Creates a new instance of the SetupModule. Resolves all necessary dependencies and wires the
     * SAL-flow service necessary to implement a set of default flows on newly added OpenFlow switches.
     * @return The AutoCloseable which overrides the close() method with custom clean-up procedure.
     *
     * @return The AutoCloseable with a customized close() method
     */
    @Override
    public AutoCloseable createInstance() {
        dataBrokerService = getDataBrokerDependency();
        RpcProviderRegistry rpcProviderRegistry = getRpcRegistryDependency();
        salFlowService = rpcProviderRegistry.getRpcService(SalFlowService.class);
        notificationService = getNotificationServiceDependency();
        packetProcessingService = rpcProviderRegistry.getRpcService(PacketProcessingService.class);

        // Reading controller IP addresses from the config file (or yang default values)
        ArrayList<String> ctlList = new ArrayList<String>();
        if(getControllerIp1() != null)
            ctlList.add(getControllerIp1());
        if(getControllerIp2() != null)
            ctlList.add(getControllerIp2());
        if(getControllerIp3() != null)
            ctlList.add(getControllerIp3());

        // Providing controller IP addresses to classes that need them
        FlowReconfiguratorForResilienceStateListener.setCtlList(ctlList);
        InitialFlowWriter.setCtlList(ctlList);
        ControllerSelfDiscovery.setCtlList(ctlList);
        InitialOFRulesPhaseI.setCtlList(ctlList);
        InitialOFRulesPhaseII.setCtlList(ctlList);
        ConfigureNewOpenFlowNodeNBI.setCtlList(ctlList);
        ConfigureNewOpenFlowNodeAuto.setCtlList(ctlList);
        LLDPSpeakerEdge.setCtlList(ctlList);
        LLDPSpeakerAll.setCtlList(ctlList);
        InitialOFRulesTrigger.setCtlList(ctlList);
        ResiliencePathManagerImpl.setCtlList(ctlList);
        HopByHopTreeGraph.ctlIPs = ctlList;

        SetupProvider provider = new SetupProvider();

        // ResiliencePathManager setup
        ResiliencePathManagerImpl.setDataBroker(dataBrokerService);
        ResiliencePathManagerImpl.setSalFlowService(salFlowService);

        // HostUtilities setup
        HostUtilities.setDataBroker(dataBrokerService);

        // NetworkGraph implementation setup
        networkGraphService = new NetworkGraphService(dataBrokerService);

        // Setup Initial Flow Writer
        InitialFlowWriter initialFlowWriter = new InitialFlowWriter(dataBrokerService);

        // Setup Dummy State Listener
        DummyStateListener dŚL = new DummyStateListener(dataBrokerService);

        // Setup isolated Dummy State Listener
        DummyIsolatedStateListener dIŚL = new DummyIsolatedStateListener(dataBrokerService);

        // Setup isolated Dummy State Listener
        ControllerSelfDiscovery controllerSelfDiscovery = new ControllerSelfDiscovery(dataBrokerService, salFlowService);

        // Setup  Dummy Topology Listener
        DummyTopologyListener dTL = new DummyTopologyListener(dataBrokerService);

        // Setup  Dummy NodeConnector Listener
        DummyNodeConnectorListener dummyNodeConnectorListener = new DummyNodeConnectorListener(dataBrokerService);

        // Setup  Dummy Topology Link Listener
        DummyTopologyLinkListener dummyTopologyLinkListener = new DummyTopologyLinkListener(dataBrokerService);

        // Setup  SwitchesStateMonitoring
        SwitchesStateMonitoring switchesStateMonitoring = new SwitchesStateMonitoring(dataBrokerService);

        // Setup  InitialOFRulesTrigger Link Listener
        InitialOFRulesTrigger initialOFRulesTrigger = new InitialOFRulesTrigger();

        // Setup ConfigureNewOpenFlowNodeNBI
        ConfigureNewOpenFlowNodeNBI configureNewOpenFlowNodeNBI = new ConfigureNewOpenFlowNodeNBI(dataBrokerService);

        // Setup InitialOFRulesPhaseI
        InitialOFRulesPhaseI initialOFRulesPhaseI = new InitialOFRulesPhaseI(dataBrokerService, salFlowService);

        // Setup InitialOFRulesPhaseII
        InitialOFRulesPhaseII initialOFRulesPhaseII = new InitialOFRulesPhaseII(dataBrokerService, salFlowService, networkGraphService);

        // Setup MstChecker
        //MstChecker mstChecker = new MstChecker(dataBrokerService);

        // Setup TreeUtilities
        TreeUtils.setDataBroker(dataBrokerService, networkGraphService);

        // Setup FlowReconfiguratorForResilienceStateListener
        FlowReconfiguratorForResilienceStateListener flowReconfiguratorForResilienceStateListener =
                new FlowReconfiguratorForResilienceStateListener(dataBrokerService, salFlowService, (short) 0);

        // Setup InstallTreeRulesInTheSwitch
        InstallTreeRulesInTheSwitch.setSalFlowService(salFlowService);
        InstallTreeRulesInTheSwitch.setNetworkGraphService(networkGraphService);

        // Setup NetworkExtensionManager
        NetworkExtensionManager networkExtensionManager = new NetworkExtensionManager(dataBrokerService, salFlowService, networkGraphService);


        // Setup LLDPSpeakerEdge
        lldpSpeakerEdge = new LLDPSpeakerEdge(packetProcessingService, networkGraphService);
        // Setup LLDPSpeakerAll
        lldpSpeakerAll = new LLDPSpeakerAll(packetProcessingService);
        // Setup NodeConnectorInventoryEventTranslator and register lldpSpeakerEdge and lldpSpeakerAll as observers
        nodeConnectorInventoryEventTranslator = new NodeConnectorInventoryEventTranslator(dataBrokerService, lldpSpeakerEdge, lldpSpeakerAll);

        // Setup topologylldpdiscovery
        lldpLinkAger = new LLDPLinkAger(1000, 5000, notificationService);
        lldpDiscoveryListener = new LLDPDiscoveryListener(notificationService, lldpLinkAger);
        lldpDiscoveryListener.setDataBroker(dataBrokerService);
        lldpActivator = new LLDPActivator(notificationService, lldpDiscoveryListener);

        // Reading arping script path from the config file (or yang default values)
        if(getArpingPath() != null){
            ControllerSelfDiscovery.setArpingPath(getArpingPath());
        }

        // Reading arping discovery prefix from the config file (or yang default values)
        if(getArpingDiscoveryPrefix() != null) {
            ControllerSelfDiscovery.setDiscoveryIPPrefix(getArpingDiscoveryPrefix());
        }

        // Reading controller plane prefix from the config file (or yang default values)
        if(getCpPrefix() != null) {
            InitialOFRulesPhaseI.setCpNetworkPrefix(getCpPrefix());
            InitialOFRulesPhaseII.setCpNetworkPrefix(getCpPrefix());
            TopologyLinkDataChangeHandler.setCpNetworkPrefix(getCpPrefix());
            InstallTreeRulesInTheSwitch.setCpNetworkPrefix(getCpPrefix());
        }

        // Reading timeout and id config values from the config file (or yang default values)
        if(getDropallFlowTableId() != null && getDropallFlowHardTimeout()!=null &&  getDropallFlowIdleTimeout()!= null) {
            dropAllFlowTableId = getDropallFlowTableId();
            dropAllFlowHardTimeout = getDropallFlowHardTimeout();
            dropAllFlowIdleTimeout = getDropallFlowIdleTimeout();
            InitialFlowUtils.setFlowIdleTimeout(getDropallFlowIdleTimeout());
            InitialFlowUtils.setFlowHardTimeout(getDropallFlowHardTimeout());
            ControllerSelfDiscovery.setFlowTableId(getDropallFlowTableId());
            InitialOFRulesPhaseI.setFlowTableId(getDropallFlowTableId());
            ResiliencePathManagerImpl.setFlowTableId(getDropallFlowTableId());
            TopologyLinkDataChangeHandler.setFlowTableId(getDropallFlowTableId());
            InstallTreeRulesInTheSwitch.setFlowTableId(getDropallFlowTableId());
        }

        // Reading IPv6 config from the config file (or yang default values)
        if(getIpv6Enabled() != null) {
            ConfigureNewOpenFlowNodeNBI.setIPv6Enabled(getIpv6Enabled());
        }

        // Reading SSH config from the config file (or yang default values)
        if(getSshUsername() != null && getSshPassword() != null) {
            ConfigureNewOpenFlowNodeAuto.setSSHConfiguration(getSshUsername(), getSshPassword());
            ConfigureNewOpenFlowNodeNBI.setSSHConfiguration(getSshUsername(), getSshPassword());
            InitialOFRulesPhaseI.setSSHConfiguration(getSshUsername(), getSshPassword());
        }

        // Reading NextLevelRefreshersFlag
        if(getInitialOFRulesPhaseIIConfig() != null) {
            if (getInitialOFRulesPhaseIIConfig().getNextLevelTriggerRefreshersFiniteEnabled() != null)
                InitialOFRulesPhaseII.setNextLevelTriggerRefreshersFiniteEnabled(getInitialOFRulesPhaseIIConfig().getNextLevelTriggerRefreshersFiniteEnabled());
            if (getInitialOFRulesPhaseIIConfig().getNextLevelTriggerRefreshersFiniteMaxExec() != null)
                InitialOFRulesPhaseII.setMaxExecNum(getInitialOFRulesPhaseIIConfig().getNextLevelTriggerRefreshersFiniteMaxExec());
            if (getInitialOFRulesPhaseIIConfig().getNextLevelTriggerRefreshersPeriod() != null) {
                InitialOFRulesPhaseII.setDefaultMonitorRefreshPeriod(getInitialOFRulesPhaseIIConfig().getNextLevelTriggerRefreshersPeriod());
            }
        }

        // Reading the deafult topology-id
        if (getTopologyId() != null) {
            InitialFlowUtils.setTopologyId(getTopologyId());
            TOPOLOGY_ID = getTopologyId();
            ControllerSelfDiscovery.setTopologyId(getTopologyId());
            FlowReconfiguratorForResilienceStateListener.setTopologyId(getTopologyId());
            InitialOFRulesPhaseI.setTopologyId(getTopologyId());
            InitialOFRulesPhaseII.setTopologyId(getTopologyId());
            LLDPSpeakerEdge.setTopologyId(getTopologyId());
            TreeUtils.setTopologyId(getTopologyId());
            LLDPDiscoveryListener.setTopologyId(getTopologyId());
            ResiliencePathManagerImpl.setTopologyId(getTopologyId());
            HostUtilities.setTopologyId(getTopologyId());
            NetworkGraphService.setTopologyId(getTopologyId());
        }


        // enable/disable NEM via XML config
        if (getNetworkExtensionEnabled() != null) {
            NetworkExtensionManager.setNemEnabled(getNetworkExtensionEnabled());
            TopologyLinkDataChangeHandler.setNemEnabled(getNetworkExtensionEnabled());
        }


        // Creating necessary data store InstanceIdentifiers necessary for data change listener registration
        InstanceIdentifier<Node> nodeInstance = InstanceIdentifier.builder(Nodes.class).child(Node.class).build();
        InstanceIdentifier<FlowCapableNodeConnector> flowCapableNodeConnectorInstanceIdentifier = InstanceIdentifier.builder(Nodes.class).child(Node.class).child(NodeConnector.class).augmentation(FlowCapableNodeConnector.class).build();
        InstanceIdentifier<NodeConnector> nodeConnectorInstanceIdentifier = InstanceIdentifier.builder(Nodes.class).child(Node.class).child(NodeConnector.class).build();
        InstanceIdentifier<Topology> topologyInstance = InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class).build();
        InstanceIdentifier<Link> linkInstance = InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class).child(Link.class).build();
        InstanceIdentifier<SwitchBootsrappingState> swInstance = nodeInstance.builder().
                augmentation(SwitchBootstrappingAugmentation.class).child(SwitchBootsrappingState.class).build();


        final DataTreeIdentifier<Node> nodeDataTreeIdentifier = new DataTreeIdentifier<Node>(
                LogicalDatastoreType.OPERATIONAL, nodeInstance);
        final DataTreeIdentifier<FlowCapableNodeConnector> flowCapableNodeConnectorDataTreeIdentifier = new DataTreeIdentifier<>(
                LogicalDatastoreType.OPERATIONAL, flowCapableNodeConnectorInstanceIdentifier);
        final DataTreeIdentifier<NodeConnector> nodeConnectorDataTreeIdentifier = new DataTreeIdentifier<>(
                LogicalDatastoreType.OPERATIONAL, nodeConnectorInstanceIdentifier);
        final  DataTreeIdentifier<SwitchBootsrappingState> swId = new DataTreeIdentifier<>(
                LogicalDatastoreType.OPERATIONAL, swInstance);
        final  DataTreeIdentifier<Topology> topologyId = new DataTreeIdentifier<>(
                LogicalDatastoreType.OPERATIONAL, topologyInstance);
        final  DataTreeIdentifier<Link> linkId = new DataTreeIdentifier<>(
                LogicalDatastoreType.OPERATIONAL, linkInstance);

        // Listen for change on NodeConnector in NetworkExtensionManager
        dataBrokerService.registerDataTreeChangeListener(nodeConnectorDataTreeIdentifier, networkExtensionManager);

        // Registering data tree change listeners

        // Listen for change on any data store tree node
        dataBrokerService.registerDataTreeChangeListener(nodeDataTreeIdentifier, initialFlowWriter);
        //dataBrokerService.registerDataTreeChangeListener(nodeDataTreeIdentifier, dŚL);

        // Listen for change on NodeConnector
        // dataBrokerService.registerDataTreeChangeListener(flowCapableNodeConnectorDataTreeIdentifier, dummyNodeConnectorListener);

        // Listen for change on Topology nodes
        //dataBrokerService.registerDataTreeChangeListener(topologyId, dTL);

        // Listen for change on Topology Link nodes
        //dataBrokerService.registerDataTreeChangeListener(linkId, dummyTopologyLinkListener);
        //dataBrokerService.registerDataTreeChangeListener(linkId, flowReconfiguratorForResilienceLinkListener);
        dataBrokerService.registerDataTreeChangeListener(linkId, initialOFRulesTrigger);

        // Listen for change on SwitchBootstrappingState nodes
        //dataBrokerService.registerDataTreeChangeListener(swId, dIŚL);
        dataBrokerService.registerDataTreeChangeListener(swId, controllerSelfDiscovery);
        dataBrokerService.registerDataTreeChangeListener(swId, initialOFRulesPhaseI);
        dataBrokerService.registerDataTreeChangeListener(swId, initialOFRulesPhaseII);
        dataBrokerService.registerDataTreeChangeListener(swId, flowReconfiguratorForResilienceStateListener);
        dataBrokerService.registerDataTreeChangeListener(swId, switchesStateMonitoring);
        //dataBrokerService.registerDataTreeChangeListener(swId, mstChecker);

        // register Topology DataChangeListener
        // this class handles new discovered links and build the tree that is uses for broadcasting purposes
        //networkGraphService = new NetworkGraphService();
        this.topologyLinkDataChangeHandler = new TopologyLinkDataChangeHandler(dataBrokerService, salFlowService, networkGraphService);
        topologyLinkDataChangeHandler.setTopologyId(TOPOLOGY_ID); // import from config file extend yang with additional setup
        listenerRegistration = topologyLinkDataChangeHandler.registerAsDataChangeListener();

        // registering for notifications from openflowplugin LLDP based topology app
        //final TopologyKey key = new TopologyKey(new TopologyId(TOPOLOGY_ID));
        //final InstanceIdentifier<Topology> topologyPath = InstanceIdentifier.create(NetworkTopology.class)
        //        .child(Topology.class, key);
        //flowTopologyDiscoveryListener = new FlowCapableTopologyListener(topologyPath);
        //flowTopologyDiscoveryListenerRegistration = notificationService.registerNotificationListener(flowTopologyDiscoveryListener);

        //registering for LLDP packets
        //lldpDiscoveryListener = new LLDPDiscoveryListener();
        //lldpNotificationRegistration = notificationService.registerNotificationListener(lldpDiscoveryListener);

        // registering for OF packets
        //ofPacketListener = new OFPacketListener(dataBrokerService);
        //ofNotificationRegistration = notificationService.registerNotificationListener(ofPacketListener);

        // registering for DHCP packets
        dhcpPacketInListener = new DHCPPacketInListener();
        dhcpNotificationRegistration = notificationService.registerNotificationListener(dhcpPacketInListener);
        notificationService.registerNotificationListener(networkExtensionManager);


        LOG.info("SetupModule finished, returning provider...");

        return provider;
    }

}
