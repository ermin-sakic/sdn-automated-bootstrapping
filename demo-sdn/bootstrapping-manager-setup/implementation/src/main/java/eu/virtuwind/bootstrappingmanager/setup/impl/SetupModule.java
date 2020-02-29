package eu.virtuwind.bootstrappingmanager.setup.impl;

import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.InitialFlowUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.setup.impl.rev150722.modules.module.configuration.SetupImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.NotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.awt.image.ImageWatched;

import java.util.ArrayList;

/**
 * The entry-point to the Bootstrapping setup implementation. Holds the required activator method and helper methods
 * to start new DHCP and stop existing running DHCP server instances.
 */
public class SetupModule extends eu.virtuwind.bootstrappingmanager.setup.impl.AbstractSetupModule {
    private static final Logger LOG = LoggerFactory.getLogger(SetupModule.class);
    private static DataBroker dataBrokerService = null;
    private static SalFlowService salFlowService = null;
    private NotificationProviderService notificationService;
    private static short dropAllFlowTableId;
    private static Integer dropAllFlowHardTimeout, dropAllFlowIdleTimeout;
    private static Thread t = null;

    public SetupModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public SetupModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, eu.virtuwind.bootstrappingmanager.setup.impl.SetupModule oldModule, AutoCloseable oldInstance) {
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


        ArrayList<String> ctlList = new ArrayList<String>();
        if(getControllerIp1() != null)
            ctlList.add(getControllerIp1());
        if(getControllerIp2() != null)
            ctlList.add(getControllerIp2());
        if(getControllerIp3() != null)
            ctlList.add(getControllerIp3());

        FlowReconfigurator.setCtlList(ctlList);
        InitialFlowWriter.setCtlList(ctlList);

        SetupProvider provider = new SetupProvider();
        ConfigureNewOpenFlowNodeNBI.setCtlList(ctlList);
        ConfigureNewOpenFlowNodeAuto.setControllerList(ctlList);
        ConfigureNewOpenFlowNodeAuto.setDataBroker(dataBrokerService);
        ConfigureNewOpenFlowNodeAutoWithSSH.setCtlList(ctlList);

        // Setup Initial Flow Writer
        InitialFlowWriter initialFlowWriter = new InitialFlowWriter(salFlowService, dataBrokerService);


        new FlowReconfigurator(dataBrokerService, salFlowService, (short) 0, 0);
        /*
        if (getFlowReconfigurator() != null) {
            FlowReconfigurator.getInstance(getFlowReconfigurator());
        }
        else {
            FlowReconfigurator.getInstance(SetupImpl.FlowReconfigurator.TWODISJOINTPATHSFOREACHCONTROLLER);
        }
        */

        // Setup NetworkExtensionManager
        NetworkExtensionManager networkExtensionManager = new NetworkExtensionManager(dataBrokerService, salFlowService);

        // Setup NetworkExtensionTreeUpdate
        NetworkExtensionTreeUpdate networkExtensionTreeUpdate = new NetworkExtensionTreeUpdate();

        // For the time being, use only IPv4 and assume rstp availability
        if(getRstpUsed() != null)
            StpRemover.setRSTPUsed(getRstpUsed());

        if(getArpingPath() != null)
            FlowReconfigurator.setArpingPath(getArpingPath());

        if(getArpingDiscoveryPrefix() != null)
            initialFlowWriter.setDiscoveryIPPrefix(getArpingDiscoveryPrefix());

        if(getCpPrefix() != null) {
            initialFlowWriter.setControlPlanePrefix(getCpPrefix());
            networkExtensionManager.setCpNetworkPrefix(getCpPrefix());
        }

        if(getEnvName() != null)
            StpRemover.setConfigEnv(getEnvName());

        if(getBridgeName() != null)
            StpRemover.setBridge(getBridgeName());

        if(getDropallFlowTableId() != null && getDropallFlowHardTimeout()!=null &&  getDropallFlowIdleTimeout()!= null) {
            dropAllFlowTableId = getDropallFlowTableId();
            dropAllFlowHardTimeout = getDropallFlowHardTimeout();
            dropAllFlowIdleTimeout = getDropallFlowIdleTimeout();
            initialFlowWriter.setFlowTableId(getDropallFlowTableId());
            InitialFlowUtils.setFlowIdleTimeout(getDropallFlowIdleTimeout());
            InitialFlowUtils.setFlowHardTimeout(getDropallFlowHardTimeout());
            networkExtensionManager.setFlowTableId(getDropallFlowTableId());
        }

        if(getIpv6Enabled() != null) {
            ConfigureNewOpenFlowNodeNBI.setIPv6Enabled(getIpv6Enabled());
            ConfigureNewOpenFlowNodeAutoWithSSH.setIPv6Enabled(getIpv6Enabled());
            ConfigureNewOpenFlowNodeAuto.setIPv6Enabled(getIpv6Enabled());
            StpRemover.setIPv6Enabled(getIpv6Enabled());
            FlowReconfigurator.setIPv6Enabled(getIpv6Enabled());
            initialFlowWriter.setIPv6Enabled(getIpv6Enabled());
        }

        if(getSshUsername() != null && getSshPassword() != null) {
            StpRemover.changeDefaultSSHConfig(getSshUsername(), getSshPassword());
            ConfigureNewOpenFlowNodeNBI.setSSHConfiguration(getSshUsername(), getSshPassword());
            ConfigureNewOpenFlowNodeAutoWithSSH.setSSHConfiguration(getSshUsername(), getSshPassword());
            ConfigureNewOpenFlowNodeAuto.setSSHConfiguration(getSshUsername(), getSshPassword());
        }

        if (getFlowReconfigurator() != null) {
            StpRemover.setFlowReconfiguratorFlavour(getFlowReconfigurator());
        }

        if (getWaitBeforeDisableStp() != null) {
            ConfigureNewOpenFlowNodeAutoWithSSH.setWaitBeforeDisableSTPTimeout(getWaitBeforeDisableStp());
        }

        // enable/disable NEM via XML config
        if (getNetworkExtensionEnabled() != null) {
            NetworkExtensionManager.setNemEnabled(getNetworkExtensionEnabled());
        }

        // enable/disable ICMP rules via XML config
        if (getIcmpEnabled() != null) {
            FlowReconfigurator.setICMPEnabled(getIcmpEnabled());
        }


        InstanceIdentifier<Node> nodeInstance = InstanceIdentifier.builder(Nodes.class).child(Node.class).build();

        final DataTreeIdentifier<Node> treeId = new DataTreeIdentifier<Node>(
                LogicalDatastoreType.OPERATIONAL, nodeInstance);
        dataBrokerService.registerDataTreeChangeListener(treeId, initialFlowWriter);

        // Listen for change on NodeConnector in NetworkExtensionManager
        InstanceIdentifier<NodeConnector> nodeConnectorInstance = InstanceIdentifier.builder(Nodes.class).child(Node.class).child(NodeConnector.class).build();
        final DataTreeIdentifier<NodeConnector> nodeConnectorId = new DataTreeIdentifier<>(
                LogicalDatastoreType.OPERATIONAL, nodeConnectorInstance);
        dataBrokerService.registerDataTreeChangeListener(nodeConnectorId, networkExtensionManager);
        notificationService.registerNotificationListener(networkExtensionManager);


        // Listen for change on Link in NetworkExtensionTreeUpdate
        InstanceIdentifier<Link> linkInstanceIdentifier = InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId("flow:1"))).child(Link.class).build();
        final DataTreeIdentifier<Link> linkDataTreeIdentifier = new DataTreeIdentifier<>(
                LogicalDatastoreType.OPERATIONAL, linkInstanceIdentifier);
        dataBrokerService.registerDataTreeChangeListener(linkDataTreeIdentifier, networkExtensionTreeUpdate);

        LOG.info("SetupModule finished, returning provider...");

        return provider;
    }

    /**
     * Starts a scheduled thread that disables the STP agent on each of the underlying switches after an expiration
     * of scheduled timeout of 65 seconds since the time the last remaining switch has connected to controller.
     * @param waitingPeriodBeforeSTPDisable The waiting period before STP is disabled on all the nodes - default preset
     *                                      to 65 seconds - maximum time between arrivals of two DHCPDISCOVER packets.
     */
    public static void reinitiateSTPDisable(Long waitingPeriodBeforeSTPDisable)
    {
        if(t!=null && t.isAlive()) {
            t.interrupt();
            t = null;
        }

        StpRemover stpRemover = new StpRemover(dataBrokerService,
                salFlowService, dropAllFlowTableId, dropAllFlowHardTimeout,
                dropAllFlowIdleTimeout, waitingPeriodBeforeSTPDisable);

        t = new Thread(stpRemover);
        t.start();
    }
}
