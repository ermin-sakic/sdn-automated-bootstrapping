package eu.virtuwind.nbi.impl;

import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NbiModule extends eu.virtuwind.nbi.impl.AbstractNbiModule {
	private static final Logger LOG = LoggerFactory.getLogger(NbiModule.class);

	public NbiModule(
			org.opendaylight.controller.config.api.ModuleIdentifier identifier,
			org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
		super(identifier, dependencyResolver);
	}

	public NbiModule(
			org.opendaylight.controller.config.api.ModuleIdentifier identifier,
			org.opendaylight.controller.config.api.DependencyResolver dependencyResolver,
			NbiModule oldModule, AutoCloseable oldInstance) {
		super(identifier, dependencyResolver, oldModule, oldInstance);
	}

	@Override
	public void customValidation() {
		// add custom validation form module attributes here.
	}

	@Override
	public AutoCloseable createInstance() {
		LOG.info("NETSOFT: NBI Module Starting...");

		RpcProviderRegistry rpcProviderRegistry = getRpcRegistryDependency();

		NbiProvider provider = new NbiProvider(rpcProviderRegistry);

		LOG.info("NETSOFT: NBI Module Started...");

		return provider;
	}
}
