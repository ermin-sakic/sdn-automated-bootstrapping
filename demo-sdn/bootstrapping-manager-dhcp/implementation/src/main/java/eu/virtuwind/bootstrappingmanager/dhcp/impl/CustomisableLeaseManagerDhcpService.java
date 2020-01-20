package eu.virtuwind.bootstrappingmanager.dhcp.impl;

import eu.virtuwind.bootstrappingmanager.setup.impl.utilities.ScheduledThreadPoolExecutorWrapper;
import eu.virtuwind.bootstrappingmanager.setup.impl.ConfigureNewOpenFlowNodeAutoWithSSH;
import eu.virtuwind.bootstrappingmanager.setup.impl.ConfigureNewOpenFlowNodeNBI;
import org.apache.directory.server.dhcp.DhcpException;
import org.apache.directory.server.dhcp.io.DhcpRequestContext;
import org.apache.directory.server.dhcp.messages.DhcpMessage;
import org.apache.directory.server.dhcp.options.DhcpOption;
import org.apache.directory.server.dhcp.options.OptionsField;
import org.apache.directory.server.dhcp.service.manager.LeaseManager;
import org.apache.directory.server.dhcp.service.manager.LeaseManagerDhcpService;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.dhcp.impl.rev161210.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Simple extension to LeaseManagerDhcpService that provides a set of predefined options
 */
public class CustomisableLeaseManagerDhcpService extends LeaseManagerDhcpService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomisableLeaseManagerDhcpService.class);

    private final OptionsField dhcpOfferOptionField;
    private final OptionsField dhcpAckOptionField;
    private final OptionsField dhcpNakOptionField;
    private static String configMode = "auto"; // default configurable in yang
    private static Long expectedDiscoveryPeriod = 1000L; // default configurable in yang
    private static List<String> ackAlreadySentInsideDiscoveryPeriod = new LinkedList<>();
    private static ScheduledThreadPoolExecutorWrapper executorScheduler = new ScheduledThreadPoolExecutorWrapper(20);
    public final static String lock = "LOCK";

    public CustomisableLeaseManagerDhcpService(LeaseManager manager, List<DefaultOption> options)
            throws IllegalArgumentException {
        super(manager);
        dhcpOfferOptionField = new OptionsField();
        dhcpAckOptionField = new OptionsField();
        dhcpNakOptionField = new OptionsField();
        implementOptions(options);
    }

    public static String getConfigMode() {
        return configMode;
    }

    public static void setConfigMode(String configMode) {
        CustomisableLeaseManagerDhcpService.configMode = configMode;
    }

    public static Long getExpectedDiscoveryPeriod() {
        return expectedDiscoveryPeriod;
    }

    public static void setExpectedDiscoveryPeriod(Long expectedDiscoveryPeriod) {
        CustomisableLeaseManagerDhcpService.expectedDiscoveryPeriod = expectedDiscoveryPeriod;
    }

    public static List<String> getAckAlreadySentInsideDiscoveryPeriod() {
        return ackAlreadySentInsideDiscoveryPeriod;
    }

    /**
     * Handles the DHCPDISCOVER packets. The DHCP server responds with a DHCPOFFER message.
     * The subnet parameter that enforces using the subnet specified by the server is currently hardcoded.
     * @param context The Apache Server IO DHCP context.
     * @param request The content of the DHCPDISCOVER message.
     * @return The created DHCPOFFER message with subnet-enforcement option.
     * @throws DhcpException
     */
    @Override
    protected DhcpMessage handleDISCOVER(DhcpRequestContext context, DhcpMessage request) throws DhcpException {
        LOG.trace("Creating DHCP DISCOVER message");
        DhcpMessage message = super.handleDISCOVER(context, request);
        if (message != null) {

            // Generate and add DHCP option for subnetMask
            // TODO: derive from host subnetMask
            byte tag = 1; // DHCP option tag for subnetMask
            String subnetMask = "FFFFFF00"; // 255.255.255.0
            CustomDhcpOption subnetMaskOption = new CustomDhcpOption(tag, subnetMask);
            dhcpOfferOptionField.add(subnetMaskOption);

            message.getOptions().addAll(dhcpOfferOptionField);
        }
        return message;
    }

    /**
     * Handles the DHCPREQUEST packet. THe DHCP server responds with a DHCPACK message.
     * The subnet parameter that enforces using the subnet specified by the server is currently hardcoded.
     * ADDED: ignore multiple requests from the same node inside one DHCP expected discovery interval
     * @param context The Apache Server IO DHCP context.
     * @param request The content of the DHCPACK message.
     * @return THe created DHCPACK message with subnet-enforcement option.
     * @throws DhcpException
     */
    @Override
    protected DhcpMessage handleREQUEST(DhcpRequestContext context, DhcpMessage request) throws DhcpException {
        LOG.trace("Creating DHCP REQUEST message");

        DhcpMessage message = super.handleREQUEST(context, request);
        String switchMAC = message.getHardwareAddress().getNativeRepresentation();
        synchronized (lock){
            if (ackAlreadySentInsideDiscoveryPeriod.contains(switchMAC)) {
                LOG.debug("Handling REQUEST: Duplicate requests received from MAC: {}", switchMAC);
                LOG.debug("Handling REQUEST: Dropping a duplicate.");
                message = null;
                return message;
            } else {
                LOG.debug("Handling REQUEST: OFFER IP:{} for the node with the MAC:{}", message.getAssignedClientAddress().getHostAddress(),
                        message.getHardwareAddress().getNativeRepresentation());
                ackAlreadySentInsideDiscoveryPeriod.add(switchMAC);
                executorScheduler.schedule(new MACAddressRemover(switchMAC), expectedDiscoveryPeriod, TimeUnit.MILLISECONDS);
            }
        }

        if (message != null) {

            LOG.debug("Modifying DCHP ACK");

            // Generate and add DHCP option for subnetMask
            // TODO: derive from host subnetMask
            byte tag = 1; // DHCP option tag for subnetMask
            String subnetMask = "FFFFFF00"; // 255.255.255.0
            CustomDhcpOption subnetMaskOption = new CustomDhcpOption(tag, subnetMask);
            dhcpAckOptionField.add(subnetMaskOption);

            message.getOptions().addAll(dhcpAckOptionField);

            // Create thread responsible for configuring OpenFlow switch with controller IP
            // TODO: only for switches?
            String ip = request.getCurrentClientAddress().toString();

            if (ip.equals("/0.0.0.0")) {// requesting address for the first time
                if (configMode.equals("auto")) {
                    LOG.info("Starting thread ConfigureNewOpenFlowNodeAutoWithSSH for the node with IP {}",
                            message.getAssignedClientAddress().getHostAddress());
                    ConfigureNewOpenFlowNodeAutoWithSSH confOFnode = new ConfigureNewOpenFlowNodeAutoWithSSH(message.getAssignedClientAddress().getHostAddress(),
                            message.getHardwareAddress().getNativeRepresentation());
                    Thread t = new Thread(confOFnode);
                    t.start();
                } else if (configMode.equals("viaREST")){
                    LOG.info("Starting thread ConfigureNewOpenFlowNodeNBI for the node with IP {}",
                            message.getAssignedClientAddress().getHostAddress());
                    ConfigureNewOpenFlowNodeNBI confOFnode = new ConfigureNewOpenFlowNodeNBI(message.getAssignedClientAddress().getHostAddress());
                    Thread t = new Thread(confOFnode);
                    t.start();
                } else {
                    LOG.warn("Non-existing DHCP config mode selected.");
                }
            }

        }
        return message;
    }

    /**
     * Handles the DHCPDECLINE message sent by the clients who decline a proposed specific IP address.
     * @param context The Apache Server IO DHCP context.
     * @param request The content of the DHCPDECLINE message.
     * @return The default implementation just ignores the DHCPDECLINE and returns a NULL DhcpMessage.
     * @throws DhcpException
     */
    @Override
    protected DhcpMessage handleDECLINE(DhcpRequestContext context, DhcpMessage request) throws DhcpException {
        LOG.trace("Creating DHCP DECLINE message");
        DhcpMessage message = super.handleDECLINE(context, request);
        if (message != null) {
            message.getOptions().addAll(dhcpNakOptionField);
        }
        return message;
    }

    /**
     * Handles the DHCPRELEASE message sent by the clients. We do not reassign previously assigned IP addresses to new clients.
     * @param context The Apache Server IO DHCP context.
     * @param request The content of the DHCPRELEASE message.
     * @return The default implementation just ignores the DHCPRELEASE and returns a NULL DhcpMessage.
     * @throws DhcpException
     */
    @Override
    protected DhcpMessage handleRELEASE(DhcpRequestContext context, DhcpMessage request) throws DhcpException {
        LOG.trace("Creating DHCP RELEASE message");
        return super.handleRELEASE(context, request);
    }

    protected void implementOptions(List<DefaultOption> options) {
        DhcpOption dhcpOption;
        MessageType scope;

        // Additional DHCP options are OPTIONAL!
        if(options!=null)
            for (DefaultOption o : options) {
                try {
                    dhcpOption = new CustomDhcpOption((byte) o.getId().intValue(), o.getValue());
                } catch (IllegalArgumentException e) {
                    LOG.warn("Failed to parse DHCP option {}, skipping implementation", o.getId());
                    continue;
                }
                scope = o.getScope();
                if (scope == MessageType.DHCPOFFER || scope == MessageType.ALL) {
                    dhcpOfferOptionField.add(dhcpOption);
                    LOG.debug("DHCP OFFER option {} implemented", o.getId());
                }
                if (scope == MessageType.DHCPACK || scope == MessageType.ALL) {
                    dhcpAckOptionField.add(dhcpOption);
                    LOG.debug("DHCP ACK option {} implemented", o.getId());
                }
                if (scope == MessageType.DHCPNAK || scope == MessageType.ALL) {
                    dhcpNakOptionField.add(dhcpOption);
                    LOG.debug("DHCP NAK option {} implemented", o.getId());
                }
            }
    }
}
