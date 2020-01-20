package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;

public class IPPrefAddrInfo {
    private Ipv4Prefix ipv4Prefix = null;
    private Ipv6Prefix ipv6Prefix = null;

    private boolean isIpv6 = false;
    private boolean isIpv4 = false;

    public void setIpv4Prefix(Ipv4Prefix prefix) {
        isIpv4 = true;

        ipv4Prefix = prefix;
    }

    public void setIpv6Prefix(Ipv6Prefix prefix) {
        isIpv6 = true;

        ipv6Prefix = prefix;
    }

    public Ipv6Prefix getIPv6Prefix() {
        if(isIpv6)
            return ipv6Prefix;
        else return null;
    }

    public Ipv4Prefix getIPv4Prefix() {
        if(isIpv4)
            return ipv4Prefix;
        else return null;
    }

    public boolean isIpv6() {
        return isIpv6;
    }

    public boolean isIpv4() {
        return isIpv4;
    }

    public void setIpv4(boolean ipv4) { isIpv4 = ipv4; }
    public void setIpv6(boolean ipv6) { isIpv6 = ipv6; }

}
