package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.address.node.connector.Addresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.HostNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Contains some random host utilities (e.g. parsing the IP address on the host interfaces.
 */
public class HostUtilities {
    private static final Logger LOG = LoggerFactory.getLogger(HostUtilities.class);
    private static InterfaceConfiguration myODLListeningIf = null; // Stores the interface spec for this particular cluster node
    private static DataBroker dataBroker;
    private static String topologyId;


    /**
     * Set dataBroker in SetupModule
     *
     * @param dataBroker
     */
    public static void setDataBroker(DataBroker dataBroker) {
        HostUtilities.dataBroker = dataBroker;
    }

    /**
     * Set topologyId in SetupModule
     *
     * @param topologyId
     */
    public static void setTopologyId(String topologyId) {
        HostUtilities.topologyId = topologyId;
    }


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
                        LOG.debug("IPAddress" + inetAddress + " located on interface "
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
     * Returns a HostNode from the topology DS if it finds one with ipAddr assigned
     *
     * @param ipAddr
     * @return
     */
    public static HostNode getHostNodeByIp(String ipAddr) {
        InstanceIdentifier<Topology> nwTopoIid =  InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
        ReadOnlyTransaction nodesTransaction = dataBroker.newReadOnlyTransaction();

        boolean notFetched = true;
        while(notFetched) {
            try {
                CheckedFuture<Optional<Topology>, ReadFailedException> nwTopo = nodesTransaction
                        .read(LogicalDatastoreType.OPERATIONAL, nwTopoIid);
                Optional<Topology> nwTopoOptional = nwTopo.checkedGet();

                notFetched = false;

                List<Node> nodesList = null;
                if (nwTopoOptional != null && nwTopoOptional.isPresent())
                    nodesList = nwTopoOptional.get().getNode();

                for(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node node:nodesList){
                    if(node.getNodeId().getValue().contains("host")) {
                        HostNode hostNode = node.getAugmentation(HostNode.class);

                        for (Addresses listAddr : hostNode.getAddresses()) {
                            if (listAddr.getIp().getIpv4Address().toString().contains(ipAddr)) {
                                LOG.debug("ipAddr:({}) getIPv4Addr:({})", ipAddr, listAddr.getIp().getIpv4Address().getValue());
                                LOG.info("Successfully fetched the HostNode: " + hostNode.toString());
                                return hostNode;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch list of nodes with Exception " + e.getMessage());
                notFetched = true;
            }
        }

        return null;
    }

    /**
     * Returns a HostNode IP from the topology DS if it finds one with nodeId assigned
     *
     * @param nodeId
     * @return
     */
    public static String getHostNodeIpById(String nodeId) {
        InstanceIdentifier<Topology> nwTopoIid =  InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
        ReadOnlyTransaction nodesTransaction = dataBroker.newReadOnlyTransaction();

        boolean notFetched = true;
        while(notFetched) {
            try {
                CheckedFuture<Optional<Topology>, ReadFailedException> nwTopo = nodesTransaction
                        .read(LogicalDatastoreType.OPERATIONAL, nwTopoIid);
                Optional<Topology> nwTopoOptional = nwTopo.checkedGet();

                notFetched = false;

                List<Node> nodesList = null;
                if (nwTopoOptional != null && nwTopoOptional.isPresent())
                    nodesList = nwTopoOptional.get().getNode();

                for(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node node:nodesList){
                    if(node.getNodeId().getValue().contains("host")) {
                        HostNode hostNode = node.getAugmentation(HostNode.class);

                        if (hostNode.getAttachmentPoints().get(0).getCorrespondingTp().getValue().equals(nodeId)) {
                            String hostNodeIp = hostNode.getAddresses().get(0).getIp().getIpv4Address().getValue();
                            LOG.info("Successfully fetched an IP addredd for the HostNode {}: ", nodeId, hostNodeIp);
                            return hostNodeIp;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch list of nodes with Exception " + e.getMessage());
                notFetched = true;
            }
        }

        return null;
    }

    /**
     * Returns a HostNode from the topology DS if it finds one with id assigned
     *
     * @param nodeId
     * @return
     */
    public static HostNode getHostNodeById(String nodeId) {
        InstanceIdentifier<Topology> nwTopoIid =  InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
        ReadOnlyTransaction nodesTransaction = dataBroker.newReadOnlyTransaction();

        boolean notFetched = true;
        while(notFetched) {
            try {
                CheckedFuture<Optional<Topology>, ReadFailedException> nwTopo = nodesTransaction
                        .read(LogicalDatastoreType.OPERATIONAL, nwTopoIid);
                Optional<Topology> nwTopoOptional = nwTopo.checkedGet();

                notFetched = false;

                List<Node> nodesList = null;
                if (nwTopoOptional != null && nwTopoOptional.isPresent())
                    nodesList = nwTopoOptional.get().getNode();

                for(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node node:nodesList){
                    if(node.getNodeId().getValue().contains("host")) {
                        HostNode hostNode = node.getAugmentation(HostNode.class);

                        if (hostNode.getAttachmentPoints().get(0).getCorrespondingTp().getValue().equals(nodeId)) {
                            LOG.info("Successfully fetched the HostNode: " + hostNode.toString());
                            return hostNode;

                        }

                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch list of nodes with Exception " + e.getMessage());
                notFetched = true;
            }
        }

        return null;
    }

    /**
     * Returns the local interface configuration. Currently assumes a single interface
     * .
     *
     * @param ipAddressesODL
     * @return The InterfaceConfiguration object that contains the specific of the resulting interface.
     */
    public static InterfaceConfiguration returnMyIfConfig(ArrayList<String> ipAddressesODL) {
        if(myODLListeningIf != null) {
            LOG.debug("InterfaceConfiguration: IPAddress " + myODLListeningIf.getIpAddr() + " located on interface "
                    + myODLListeningIf.getIf());
            return myODLListeningIf;
        }
        else {
            for (String ctlIppAddr: ipAddressesODL) {
                if (isIPAvailableLocally(ctlIppAddr)){
                    LOG.debug("InterfaceConfiguration: IPAddress " + myODLListeningIf.getIpAddr() + " located on interface "
                            + myODLListeningIf.getIf());
                    return myODLListeningIf;
                }
            }
            LOG.error("InterfaceConfiguration: Something is very wrong. Interface configuration could not be found.");
            return null;
        }
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

        /**
         * Retrieves the MAC address related to this interface configuration in the String representation.
         * @return A String containing the MAC address.
         */
        public String getMacAddressString() {
            StringBuilder mac = new StringBuilder(18);
            for (byte b : macAddress) {
                if (mac.length() > 0)
                    mac.append(':');
                mac.append(String.format("%02x", b));
            }
            return mac.toString();
        }
    }

}

