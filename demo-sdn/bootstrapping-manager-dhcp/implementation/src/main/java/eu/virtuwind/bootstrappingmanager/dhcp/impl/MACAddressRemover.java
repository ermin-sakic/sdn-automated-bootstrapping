package eu.virtuwind.bootstrappingmanager.dhcp.impl;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 17.05.18
 */
public class MACAddressRemover implements Runnable {

    private String macAddressToRemove;

    public MACAddressRemover(String macAddressToRemove) {
        this.macAddressToRemove = macAddressToRemove;
    }

    @Override
    public void run() {
        synchronized (CustomisableLeaseManagerDhcpService.lock) {
            CustomisableLeaseManagerDhcpService.getAckAlreadySentInsideDiscoveryPeriod().remove(macAddressToRemove);
        }
    }
}
