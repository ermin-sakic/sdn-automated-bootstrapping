package eu.virtuwind.resourcemanager.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;

public class ResourcemanagerModule extends eu.virtuwind.resourcemanager.impl.AbstractResourcemanagerModule {
    public ResourcemanagerModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public ResourcemanagerModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, ResourcemanagerModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        DataBroker dataBrokerService = getDataBrokerDependency();
        RpcProviderRegistry rpcProviderRegistry = getRpcRegistryDependency();
        NotificationProviderService notificationService = getNotificationServiceDependency();
        ResourceManagerProvider provider = new ResourceManagerProvider(dataBrokerService, rpcProviderRegistry, notificationService);

//        InstanceIdentifier<Link> linkInstance = InstanceIdentifier.builder(NetworkTopology.class)
//                .child(Topology.class, new TopologyKey(new TopologyId("flow:1"))).child(Link.class).build();
//        dataBrokerService.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL, linkInstance,
//                new TopologyListener(dataBrokerService, notificationService),
//                AsyncDataBroker.DataChangeScope.BASE);

        return provider;
    }


}
