package eu.virtuwind.registryhandler.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The entry-point to the RegistryHandler implementation. Holds the required activator method and helper methods
 * to write, read and modify data-stores specific to various other VirtuWind modules
 * (e.g. PathManager, SecurityManager, QoSOrchestrator etc.)
 */
public class RegistryHandlerModule extends eu.virtuwind.registryhandler.impl.AbstractRegistryHandlerModule {
    private static final Logger logger = LoggerFactory.getLogger(RegistryHandlerModule.class);


    public RegistryHandlerModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier,
                                 org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public RegistryHandlerModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier,
                                 org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, eu.virtuwind.registryhandler.impl.RegistryHandlerModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {}

    /**
     * Creates a new instance of the RegistryHandler Module. Resolves all necessary dependencies and wires the RPC
     * registrations for REST-based testing.
     * @return The AutoCloseable which overrides the close() method with custom clean-up procedure.
     */
    @Override
    public java.lang.AutoCloseable createInstance() {
        logger.info("Creating the Registry Handler Implementation Instance...");

        DataBroker dataBrokerService = getDataBrokerDependency();
        RpcProviderRegistry rpcProviderRegistry = getRpcRegistryDependency();
        RegistryHandlerProvider provider = new RegistryHandlerProvider(dataBrokerService, rpcProviderRegistry);

        logger.info("Creating the Registry Handler Implementation created...");

        return provider;
    }

}
