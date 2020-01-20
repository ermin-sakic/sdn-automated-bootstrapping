package eu.virtuwind.bootstrappingmanager.alternative.dhcp.impl;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 17.05.18
 */
public class MACAddressRemover implements Runnable {

    private String macAddressToRemove;
    private String choice;

    public MACAddressRemover(String macAddressToRemove, String choice) {

        this.macAddressToRemove = macAddressToRemove;
        this.choice = choice;
    }

    @Override
    public void run() {
        synchronized (CustomisableLeaseManagerDhcpService.lock) {
            if (choice.contains("OFFER")) {
                CustomisableLeaseManagerDhcpService.getOfferAlreadySentInsideDiscoveryPeriod().remove(macAddressToRemove);
            } else if (choice.contains("ACK")) {
                CustomisableLeaseManagerDhcpService.getAckAlreadySentInsideDiscoveryPeriod().remove(macAddressToRemove);
            }
        }
    }
}
