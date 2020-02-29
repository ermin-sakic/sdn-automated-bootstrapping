package eu.virtuwind.pathmanager.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.PathManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PathManagerModule extends eu.virtuwind.pathmanager.impl.AbstractPathManagerModule {
    private static final Logger logger = LoggerFactory.getLogger(PathManagerModule.class);

    public PathManagerModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public PathManagerModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, PathManagerModule oldModule, AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public AutoCloseable createInstance() {
        logger.info("Creating the Path Manager Instance...");

        DataBroker dataBrokerService = getDataBrokerDependency();
        RpcProviderRegistry rpcProviderRegistry = getRpcRegistryDependency();
        NotificationProviderService notificationService = getNotificationServiceDependency();
        PathManagerProvider provider = PathManager.getInstance(dataBrokerService, rpcProviderRegistry.getRpcService(SalFlowService.class));
        rpcProviderRegistry.addRpcImplementation(PathManagerService.class, (PathManager) provider);
        return provider;
    }

}
