package eu.virtuwind.bootstrappingmanager.setup.impl.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * Contains some random host utilities (e.g. parsing the IP address on the host interfaces.
 */
public class HostUtilities {
    private static final Logger LOG = LoggerFactory.getLogger(HostUtilities.class);
    private static InterfaceConfiguration myODLListeningIf = null; // Stores the interface spec for this particular cluster node

    /**
     * Checks if the given IPs is available on one of the local interfaces.
     * @param inetAddress The IP address that needs to be checked.
     * @return Boolean output.
     */
    public static boolean isIPAvailableLocally(String inetAddress) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // filters out 127.0.0.1 and inactive interfaces
                if (iface.isLoopback() || !iface.isUp())
                    continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while(addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String ip = addr.getHostAddress();

                    if (ip.contains(inetAddress)) {
                        LOG.info("IPAddress" + inetAddress + " located on interface "
                                + iface.getDisplayName() + " " + ip);

                        myODLListeningIf = new InterfaceConfiguration(iface.getDisplayName(),
                                ip, iface.getHardwareAddress());

                        return true;
                    }
                }
            }
            return false;
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the local interface configuration. Currently assumes a single interface and
     * that the IP configuration was determined previously once.
     *
     * @param ipAddressesODL
     * @return The InterfaceConfiguration object that contains the specific of the resulting interface.
     */
    public static InterfaceConfiguration returnMyIfConfig(ArrayList<String> ipAddressesODL) {
        if(myODLListeningIf != null) {
            LOG.info("InterfaceConfiguration: IPAddress " + myODLListeningIf.getIpAddr() + " located on interface "
                    + myODLListeningIf.getIf());
            return myODLListeningIf;
        }
        else //todo: find out the interface name
            LOG.error("InterfaceConfiguration: Something is very wrong. Interface configuration could not be found.");
        return null;
    }

    /** Simple interface configuration class to store the information related to a physical interface. **/
    public static class InterfaceConfiguration {
        String ifName;
        String ipAddress;
        byte[] macAddress;

        /**
         * Default constructor to create the InterfaceConfiguration object-
         * @param ifc The interface name.
         * @param ipA The IPv4 address assigned to this interface.
         * @param macA The MAC address specific to this interface.
         */
        InterfaceConfiguration(String ifc, String ipA, byte[] macA) {
            ifName = ifc;
            ipAddress = ipA;
            macAddress = macA;
        };

        /**
         * Retrieves the IP address.
         * @return IP address as a string object.
         */
        public String getIpAddr() {
            return ipAddress;
        }

        /** Retrieves the interface name.
         *
         * @return The network interface name.
         */
        public String getIf() {
            return ifName;
        }

        /**
         * Retrieves the MAC address related to this interface configuration.
         * @return A byte array containing the MAC address.
         */
        public byte[] getMacAddress() {
            return macAddress;
        }
    }
}

