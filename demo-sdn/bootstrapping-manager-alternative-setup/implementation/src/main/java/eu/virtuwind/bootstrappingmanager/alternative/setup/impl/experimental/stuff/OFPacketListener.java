package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 07.05.18
 */

import org.opendaylight.controller.liblldp.Ethernet;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.liblldp.Packet;
import org.opendaylight.controller.liblldp.PacketException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Arrays;


public class OFPacketListener implements PacketProcessingListener {

    public static final short ETHERNET_TYPE_VLAN = (short) 0x8100;
    public static final short ETHERNET_TYPE_IPv4 = (short) 0x0800;
    public static final short IP_PROTOCOL_NUMBER_TCP = (short) 6;
    private static final short ETHERNET_TYPE_OFFSET = 12;
    private static final short IP_PROTOCOL_FIELD_OFFSET = ETHERNET_TYPE_OFFSET + 11;
    private static final short IP_IHL_OFFSET = 14;
    private static final short TCP_SRC_PORT_OFFSET = 34;
    private static final short TCP_DST_PORT_OFFSET = 36;
    private static final short OF_OFFSET = 40;
    private static final short IP_IHL_MASK = (short) 0x00ff;
    private static final short ETHERNET_VLAN_OFFSET = ETHERNET_TYPE_OFFSET + 4;
    /**
     * start position of source MAC address in array.
     */
    private static final int SRC_MAC_START_POSITION = 6;

    /**
     * end position of source MAC address in array.
     */
    private static final int SRC_MAC_END_POSITION = 12;
    /**
     * size of MAC address in octets (6*8 = 48 bits).
     */
    private static final int MAC_ADDRESS_SIZE = 6;



    private static final Logger LOG = LoggerFactory.getLogger(OFPacketListener.class);
    private final DataBroker dataBroker;

    public OFPacketListener(DataBroker dataBroker) {
        super();
        this.dataBroker = dataBroker;
    }

    @Override
    public void onPacketReceived(PacketReceived packetReceived) {

        String ingressPort = packetReceived.getIngress().getValue().toString();
        String packetMatch = packetReceived.getMatch().toString();


        //LOG.debug("PR: Packet received!!");
        //LOG.debug("PR: ingressPort: {}", ingressPort);
        //LOG.debug("PR: matching: {}", packetMatch);

        byte[] data = packetReceived.getPayload();

        Ethernet res = new Ethernet();


        try {

            Packet pkt = res.deserialize(data, 0, data.length * NetUtils.NumBitsInAByte);
            if (isOpenFlow(data)) {
                byte[] srcMacRaw = extractSrcMac(data);
                MacAddress srcMac = rawMacToMac(srcMacRaw);
                LOG.debug("PR: ingressPort: {}", ingressPort);
                LOG.debug("PR: OF packet received from the node {}", srcMac.toString());
            }

        } catch (PacketException e) {
            LOG.warn("PR: Failed to decode a packet!", e);
            e.printStackTrace();
            return;
        }

    }

    private static boolean isOpenFlow(final byte[] packet) {
        if (Objects.isNull(packet)) {
            return false;
        }

        ByteBuffer bb = ByteBuffer.wrap(packet);

        short ethernetType = bb.getShort(ETHERNET_TYPE_OFFSET);
        LOG.debug("PR: EtherType -> {}",  String.format("0x%04X", ethernetType));
        if (ethernetType == ETHERNET_TYPE_VLAN) {
            ethernetType = bb.getShort(ETHERNET_VLAN_OFFSET);
        }

        if (ethernetType == ETHERNET_TYPE_IPv4) {

            // assuming IPv4 correctly assembled
            char protocol = bb.getChar(IP_PROTOCOL_FIELD_OFFSET);
            LOG.debug("PR: Protocol -> {}", protocol);
            if (protocol == IP_PROTOCOL_NUMBER_TCP) {
                short IHL = (short) (bb.getShort(IP_IHL_OFFSET) & (IP_IHL_MASK));
                LOG.debug("PR: IHL -> {}", protocol);
                if (IHL <= 5) {
                    LOG.debug("PR: No IP options.");
                    short tcpDstPort = bb.getShort(TCP_DST_PORT_OFFSET);
                    short tcpSrcPort = bb.getShort(TCP_SRC_PORT_OFFSET);
                    LOG.debug("PR: TCP dst port -> {}", tcpDstPort);
                    LOG.debug("PR: TCP src port -> {}", tcpSrcPort);

                    if ((tcpDstPort == 6633) || (tcpSrcPort == 6633)) {
                        LOG.debug("PR: OF traffic");
                        char ofVersion = bb.getChar(OF_OFFSET);
                        char ofMessageType = bb.getChar(OF_OFFSET + 1);

                        LOG.debug("PR: OF Message type {} with OF version {} received", ofMessageType, ofVersion);
                        if (ofMessageType == 0)
                            return true;
                    }
                }
            } else {
                return false;
            }

        } else {
            // currently not working with IPv6
            return false;
        }

        return false;

    }

    private static byte[] extractSrcMac(final byte[] payload) {
        return Arrays.copyOfRange(payload, SRC_MAC_START_POSITION, SRC_MAC_END_POSITION);
    }

    /**
     * Converts a raw MAC bytes to a MacAddress.
     *
     * @param rawMac the raw bytes
     * @return {@link MacAddress} wrapping string value, baked upon binary MAC address
     */
    public static MacAddress rawMacToMac(final byte[] rawMac) {
        MacAddress mac = null;
        if (rawMac != null && rawMac.length == MAC_ADDRESS_SIZE) {
            StringBuilder sb = new StringBuilder();
            for (byte octet : rawMac) {
                sb.append(String.format(":%02X", octet));
            }
            mac = new MacAddress(sb.substring(1));
        }
        return mac;
    }


}