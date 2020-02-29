package eu.virtuwind.bootstrappingmanager.setup.impl.utilities;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 03.08.18
 */

/**
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public abstract class PacketUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PacketUtils.class);

    /**
     * size of MAC address in octets (6*8 = 48 bits).
     */
    private static final int MAC_ADDRESS_SIZE = 6;

    /**
     * start position of destination MAC address in array.
     */
    private static final int DST_MAC_START_POSITION = 0;

    /**
     * end position of destination MAC address in array.
     */
    private static final int DST_MAC_END_POSITION = 6;

    /**
     * start position of source MAC address in array.
     */
    private static final int SRC_MAC_START_POSITION = 6;

    /**
     * end position of source MAC address in array.
     */
    private static final int SRC_MAC_END_POSITION = 12;

    /**
     * start position of ethernet type in array.
     */
    private static final int ETHER_TYPE_START_POSITION = 12;

    /**
     * end position of ethernet type in array.
     */
    private static final int ETHER_TYPE_END_POSITION = 14;

    /**
     * minimum packet size of DHCP (ACK)
     */
    private static final short MINIMUM_DHCP_SIZE = 304;

    /**
     * ethernet type ipv4
     */
    public static final short ETHERNET_TYPE_IPV4 = (short) 0x0800;

    /**
     * IPv4 version star position (reference for ip fields)
     */
    public static final int IPV4_VERSION_START_POSITION = ETHER_TYPE_END_POSITION;

    /**
     * start position of ip protocol field in array.
     */
    private static final int IPV4_PROTOCOL_START_POSITION = IPV4_VERSION_START_POSITION + 9;

    /**
     * size of IPv4 protocol header field (1B)
     */
    private static final int IPV4_PROTOCOL_FIELD_SIZE = 1;

    /**
     * end position of ip protocol field in array.
     */
    private static final int IPV4_PROTOCOL_END_POSITION = IPV4_PROTOCOL_START_POSITION + IPV4_PROTOCOL_FIELD_SIZE;

    /**
     * protocol number for UDP
     */
    private static final byte PROTOCOL_NUMBER_UDP = (byte) 17;

    /**
     * udp src port offset (assumption ip no options, only 20B length of the header)
     */
    private static final int UDP_SRC_PORT_START_POSITION = IPV4_VERSION_START_POSITION + 20;

    /**
     * udp src port field size
     */
    public static final int UDP_SRC_PORT_FIELD_SIZE = 2;

    /**
     * udp dst port start position
     */
    private static final int UDP_DST_PORT_START_POSITON = UDP_SRC_PORT_START_POSITION + UDP_SRC_PORT_FIELD_SIZE;

    /**
     * udp dst port field size
     */
    public static final int UDP_DST_PORT_FIELD_SIZE = 2;

    /**
     * DHCP header offset
     */
    public static final int DHCP_HEADER_START_POSITION = UDP_SRC_PORT_START_POSITION + 8;

    /**
     * offset of the client mac address in the dhcp header
     */
    public static final int DHCP_CHADDR_START_POSITION = DHCP_HEADER_START_POSITION + 28;

    private PacketUtils() {
        //prohibite to instantiate this class
    }

    /**
     * Extracts the destination MAC address.
     *
     * @param payload the payload bytes
     * @return destination MAC address
     */
    public static byte[] extractDstMac(final byte[] payload) {
        return Arrays.copyOfRange(payload, DST_MAC_START_POSITION, DST_MAC_END_POSITION);
    }

    /**
     * Extracts the source MAC address.
     *
     * @param payload the payload bytes
     * @return source MAC address
     */
    public static byte[] extractSrcMac(final byte[] payload) {
        return Arrays.copyOfRange(payload, SRC_MAC_START_POSITION, SRC_MAC_END_POSITION);
    }

    /**
     * Extracts the ethernet type.
     *
     * @param payload the payload bytes
     * @return source MAC address
     */
    public static byte[] extractEtherType(final byte[] payload) {
        return Arrays.copyOfRange(payload, ETHER_TYPE_START_POSITION, ETHER_TYPE_END_POSITION);
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

    /**
     * Gets Client Hardware Address from the DHCP message, if the message is not DHCP returns null
     * @param payload
     * @return
     */
    public static String getClientMACAddressFromDHCP(byte[] payload) {
        if (isDHCP(payload)) {
            return extractClientHardwareAddressFromDHCPPacket(payload);
        } else {
            return null;
        }
    }

    /**
     * Extracts Client Hardware Address from the DHCP message by accessing fixed offset in the packet
     *
     * @param payload
     * @return
     */
    private static String extractClientHardwareAddressFromDHCPPacket(byte[] payload) {
        final ByteBuffer bb = ByteBuffer.wrap(payload);
        byte op = bb.get(DHCP_HEADER_START_POSITION);
        LOG.info("DHCP opcode: {}", op);
        byte mac1 = bb.get(DHCP_CHADDR_START_POSITION);
        byte mac2 = bb.get(DHCP_CHADDR_START_POSITION + 1);
        byte mac3 = bb.get(DHCP_CHADDR_START_POSITION + 2);
        byte mac4 = bb.get(DHCP_CHADDR_START_POSITION + 3);
        byte mac5 = bb.get(DHCP_CHADDR_START_POSITION + 4);
        byte mac6 = bb.get(DHCP_CHADDR_START_POSITION + 5);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%02x", mac1));
        sb.append(":");
        sb.append(String.format("%02x", mac2));
        sb.append(":");
        sb.append(String.format("%02x", mac3));
        sb.append(":");
        sb.append(String.format("%02x", mac4));
        sb.append(":");
        sb.append(String.format("%02x", mac5));
        sb.append(":");
        sb.append(String.format("%02x", mac6));

        LOG.info("DHCP client address {}", sb.toString());
        return sb.toString();
    }

    /**
     * Checks if the provided Packet-In payload belongs to DHCP
     *
     * @param packet
     * @return
     */
    private static boolean isDHCP(final byte[] packet) {
        if (Objects.isNull(packet) || packet.length < MINIMUM_DHCP_SIZE) {
            return false;
        }

        final ByteBuffer bb = ByteBuffer.wrap(packet);

        short ethernetType = bb.getShort(ETHER_TYPE_START_POSITION);

        if (ethernetType == ETHERNET_TYPE_IPV4) {

            byte protocol = bb.get(IPV4_PROTOCOL_START_POSITION);
            if (protocol == PROTOCOL_NUMBER_UDP) {
                short srcPort = bb.getShort(UDP_SRC_PORT_START_POSITION);
                short dstPort = bb.getShort(UDP_DST_PORT_START_POSITON);

                if (((srcPort == (short) 68) && (dstPort == (short) 67))
                        || ((srcPort == (short) 67) && (dstPort == (short) 68))) {
                    return true;
                } else {
                    LOG.info("Not DHCP packet");
                    return false;
                }
            } else {
                LOG.info("Not UDP packet: {}", String.format("%02x", protocol));
                return false;
            }
        } else {
            LOG.info("Not IPv4 packet: {}", String.format("%04x", ethernetType));
            return false;
        }

    }
}