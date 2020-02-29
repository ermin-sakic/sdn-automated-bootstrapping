package eu.virtuwind.bootstrappingmanager.dhcp.impl;

import com.google.common.io.BaseEncoding;
import com.google.common.net.InetAddresses;
import org.anarres.dhcp.common.address.NetworkAddress;
import org.anarres.dhcp.common.address.Subnet;
import org.apache.directory.server.dhcp.DhcpException;
import org.apache.directory.server.dhcp.io.DhcpRequestContext;
import org.apache.directory.server.dhcp.messages.DhcpMessage;
import org.apache.directory.server.dhcp.messages.HardwareAddress;
import org.apache.directory.server.dhcp.options.OptionsField;
import org.apache.directory.server.dhcp.options.dhcp.IpAddressLeaseTime;
import org.apache.directory.server.dhcp.options.dhcp.VendorClassIdentifier;
import org.apache.directory.server.dhcp.options.misc.VendorSpecificInformation;
import org.apache.directory.server.dhcp.service.manager.AbstractDynamicLeaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Example implementation of a lease manager. It provides IPs to the clients based on an initial
 * IP increasing the IP by one for every new client.
 */
public class ExampleLeaseManager extends AbstractDynamicLeaseManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExampleLeaseManager.class);

    private InetAddress ipAddrPoolStart;
    private InetAddress ipAddrPoolEnd;
    private InetAddress currentIpUsed;

    /**
     * The default constructor
     * @param ipAddrPoolStart
     * @param ipAddrPoolEnd
     */
    ExampleLeaseManager(String ipAddrPoolStart, String ipAddrPoolEnd) {
        try {
            this.ipAddrPoolStart = Inet4Address.getByName(ipAddrPoolStart);
            this.ipAddrPoolEnd = Inet4Address.getByName(ipAddrPoolEnd);
            this.currentIpUsed = this.ipAddrPoolStart;
        }
        catch (UnknownHostException e)
        { throw new IllegalArgumentException("Invalid default IP provided", e); }
    }

    /**
     * Allows for reconfiguration of the IPv4 address pool from which the ExampleLeaseManager will
     * select the IP addresses for the requesting clients.
     * @param ipAddrPoolStart
     * @param ipAddrPoolEnd
     *
     */
    public void configureIPAddr(String ipAddrPoolStart, String ipAddrPoolEnd)
    {
        try {
            this.ipAddrPoolStart = Inet4Address.getByName(ipAddrPoolStart);
            this.ipAddrPoolEnd = Inet4Address.getByName(ipAddrPoolEnd);
            this.currentIpUsed = this.ipAddrPoolStart;
        }
        catch (UnknownHostException e)
        { throw new IllegalArgumentException("Invalid new IP provided", e); }
    }

    /**
     * NOT IMPLEMENTED.
     */
    @Override protected InetAddress getFixedAddressFor(final HardwareAddress hardwareAddress) throws DhcpException {
        LOG.info("ExampleLeaseManager.getFixedAddressFor {}", hardwareAddress);
        return null;
    }

    /**
     * NOT IMPLEMENTED.
     */
    @Override protected Subnet getSubnetFor(final NetworkAddress networkAddress) throws DhcpException {
        LOG.info("ExampleLeaseManager.getSubnetFor {}", networkAddress);
        return null;
    }

    /**
     * Leases an InetAddress for the given HardwareAddress. Lock, retrieve current mapping from store.
     * If the InetAddress is unallocated OR allocated to the given HardwareAddress, return it. Else return null.
     * @param address The
     * @param hardwareAddress
     * @param ttl
     * @return
     * @throws Exception
     */
    @Override protected boolean leaseIp(final InetAddress address, final HardwareAddress hardwareAddress,
                                        final long ttl) throws Exception {
        LOG.info("ExampleLeaseManager.leaseIp {}, {}", address, hardwareAddress);
        return true;
    }

    /**
     * Leases an InetAddress for the given HardwareAddress. Lock, retrieve current mapping from store.
     * @param request The DHCPREQUEST message.
     * @param clientRequestedAddress The IP address requested by the client.
     * @param ttl Ignored.
     * @return The IPv4 address to be leased for a given client.
     * @throws Exception
     */
    @Override protected InetAddress leaseMac(final DhcpRequestContext context, final DhcpMessage request,
                                             final InetAddress clientRequestedAddress, final long ttl) throws Exception {
        if(currentIpUsed.getAddress() != ipAddrPoolEnd.getAddress()) {
            this.currentIpUsed = InetAddresses.increment(currentIpUsed);
            LOG.info("ExampleLeaseManager.leaseMac leasing: {}", currentIpUsed);
        }
        else LOG.error("Exhausted all the IPs, something went very wrong ... ");

        return this.currentIpUsed;
    }

    /**
     * Determine a lease to offer in response to a DHCPDISCOVER message.
     * When a server receives a DHCPDISCOVER message from a client, the server chooses a network address for the requesting client. If no address is available, the server may choose to report the problem to the system administrator. If an address is available, the new address SHOULD be chosen as follows:
     *
     * The client's current address as recorded in the client's current binding, ELSE
     * The client's previous address as recorded in the client's (now expired or released) binding, if that address is in the server's pool of available addresses and not already allocated, ELSE
     * The address requested in the 'Requested IP Address' option, if that address is valid and not already allocated, ELSE
     * A new address allocated from the server's pool of available addresses; the address is selected based on the subnet from which the message was received (if 'giaddr' is 0) or on the address of the relay agent that forwarded the message ('giaddr' when not 0).
     * @param context The Apache Server IO DHCP context.
     * @param request The DHCPDISCOVER message.
     * @param clientRequestedAddress The IPv4 address preferred by the client.
     * @param clientRequestedExpirySecs The timeout for request as specified by the client.
     * @return The augmented DHCPOFFER message containing the IPv4 address proposal for this particular client.
     * @throws DhcpException
     */
    @Override public DhcpMessage leaseOffer(final DhcpRequestContext context, final DhcpMessage request,
                                            final InetAddress clientRequestedAddress, final long clientRequestedExpirySecs) throws DhcpException {
        LOG.info("ExampleLeaseManager.leaseOffer request: {}, requested address: {}", request.getOptions(), clientRequestedAddress);
        request.getOptions().getStringOption(VendorClassIdentifier.class);

        final DhcpMessage dhcpMessage = super
                .leaseOffer(context, request, clientRequestedAddress, clientRequestedExpirySecs);

        // Add some options
        final OptionsField options = dhcpMessage.getOptions();
        options.setOption(VendorSpecificInformation.class, BaseEncoding.base16().decode("0B0410000002"));
        dhcpMessage.setOptions(options);
        LOG.info("ExampleLeaseManager.leaseOffer response: {}", dhcpMessage);
        return dhcpMessage;
    }

    @Override public void close() throws Exception {
        // No resources to close
    }
}
