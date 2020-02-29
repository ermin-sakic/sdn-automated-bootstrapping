package eu.virtuwind.bootstrappingmanager.setup.impl;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SetupProvider.class);

    /**
     * Empty constructor, currently unused.
     */
    public SetupProvider() {}

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("SetupProvider Session Initiated");

    }

    @Override
    public void close() throws Exception {
        LOG.info("SetupProvider Closed");
    }

}
