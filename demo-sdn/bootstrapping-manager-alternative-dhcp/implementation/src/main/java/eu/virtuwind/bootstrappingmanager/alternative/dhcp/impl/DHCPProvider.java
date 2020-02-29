package eu.virtuwind.bootstrappingmanager.alternative.dhcp.impl;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DHCPProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DHCPProvider.class);

    /**
     * Empty constructor, currently unused.
     */
    public DHCPProvider() {}

    @Override
    public void onSessionInitiated(BindingAwareBroker.ProviderContext session) {
        LOG.info("DHCPProvider Session Initiated");

    }

    @Override
    public void close() throws Exception {
        LOG.info("DHCPProvider Closed");
    }

}

