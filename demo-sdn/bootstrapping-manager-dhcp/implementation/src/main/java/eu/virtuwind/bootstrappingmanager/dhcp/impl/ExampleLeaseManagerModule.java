package eu.virtuwind.bootstrappingmanager.dhcp.impl;

import org.opendaylight.controller.config.api.JmxAttributeValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The entry-point to the ExampleLeaseManager Module implementation. Holds the required activator method and helper methods
 * to start a new instance of ExampleLeaseManager and stop existing running ExampleLeaseManager instances.
 */
public class ExampleLeaseManagerModule extends eu.virtuwind.bootstrappingmanager.dhcp.impl.AbstractExampleLeaseManagerModule {
    private static final Logger LOG = LoggerFactory.getLogger(ExampleLeaseManagerModule.class);
    private static ExampleLeaseManager elmInstance = null;

    public ExampleLeaseManagerModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public ExampleLeaseManagerModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier,
                                     org.opendaylight.controller.config.api.DependencyResolver dependencyResolver,
                                     eu.virtuwind.bootstrappingmanager.dhcp.impl.ExampleLeaseManagerModule oldModule,
                                     AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        try {
            InetAddress.getByName(getIp());
        } catch (UnknownHostException e) {
            throw JmxAttributeValidationException.wrap(e, "Provided IP address is invalid", ipJmxAttribute);
        }
    }

    /**
     * Creates an instance of the ExampleLeaseManager with a specified default IPv4 address subnet.
     * @return The AutoCloseable with modified close() implementation.
     */
    @Override
    public AutoCloseable createInstance() {
        elmInstance  = new ExampleLeaseManager("0.0.0.0", "0.0.0.0");

        LOG.info("createInstance() in ELMModule finished. Exiting...");
        return elmInstance;
    }

    /**
     * Configures an instance of our ExampleLeaseManager implementation of the DHCP AbstractDynamicLeaseManager.
     * @param ipAddressRangeStart The IP address pool start from which this instance of the DHCP server will be
     *                       allowed to lease the IP addresses to clients.
     * @param ipAddressRangeEnd The IP address pool end from which this instance of the DHCP server will be
     *                       allowed to lease the IP addresses to clients.
     */
    public static void initiateELMInstance(String ipAddressRangeStart, String ipAddressRangeEnd) { elmInstance.configureIPAddr(ipAddressRangeStart, ipAddressRangeEnd); }

    /**
     * Stops a currently executing instance of the ExampleLeaseManager.
     */
    public static void stopCurrentELMInstance()
    {
        try {
            elmInstance.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
