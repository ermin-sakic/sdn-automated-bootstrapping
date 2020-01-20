package eu.virtuwind.nbi.impl;

import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.nbi.model.rev160704.NbiService;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NbiProvider implements BindingAwareProvider, AutoCloseable {
	private static final Logger LOG = LoggerFactory
			.getLogger(NbiProvider.class);

	private BindingAwareBroker.RpcRegistration<NbiService> nbiService;

	public NbiProvider(RpcProviderRegistry rpcProviderRegistry) {
		nbiService = rpcProviderRegistry.addRpcImplementation(
				NbiService.class, new NbiImpl());
	}

	@Override
	public void onSessionInitiated(ProviderContext session) {
		LOG.info("NbiProvider Session Initiated");
	}

	@Override
	public void close() throws Exception {
		LOG.info("NbiProvider Closed");
	}
}
