package eu.virtuwind.bootstrappingmanager.dhcp.impl;

import com.google.common.io.BaseEncoding;
import org.apache.directory.server.dhcp.options.DhcpOption;

/**
 * Allows for specification of standardized (RFC2132) or custom DHCP options to be transmitted to the clients.
 */
public class CustomDhcpOption extends DhcpOption {

    private final byte tag;

    /**
     * Results in creation of a custom DHCP option.
     * @param tag DHCP option tag as per RFC2132
     * @param hexadecimalDataRepresentation Hexadecimal representation of the option value.
     * @throws IllegalArgumentException
     */
    public CustomDhcpOption(byte tag, String hexadecimalDataRepresentation) throws IllegalArgumentException {
        this.tag = tag;
        setData(BaseEncoding.base16().decode(hexadecimalDataRepresentation.toUpperCase()));
    }

    /**
     * Returns the tag assigned to this instance of the DHCP option.
     * @return The DHCP tag
     */
    @Override
    public byte getTag() {
        return tag;
    }

}
