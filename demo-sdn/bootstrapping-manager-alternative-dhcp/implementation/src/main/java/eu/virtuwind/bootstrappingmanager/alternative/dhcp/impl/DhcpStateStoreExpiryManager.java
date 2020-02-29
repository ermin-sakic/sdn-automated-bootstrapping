package eu.virtuwind.bootstrappingmanager.alternative.dhcp.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 20.06.18
 */
public class DhcpStateStoreExpiryManager implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpStateStoreExpiryManager.class);

    /*
        A DhcpState is removed from the transactionsStateStore
        after it reaches the state LeasingDone and when the
        (current time - timestamp) > expiryTimeout
     */
    private long expiryTimeout = 5000L;

    @Override
    public void run() {
        synchronized (DhcpState.lock) {
            if (!CustomisableLeaseManagerDhcpService.transactionsStateStore.isEmpty()) {
                List<Integer> statesToRemove = new ArrayList<>();
                for (Map.Entry<Integer, DhcpState> state : CustomisableLeaseManagerDhcpService.transactionsStateStore.entrySet()) {
                    long currentTime = System.currentTimeMillis();
                    if (state.getValue().isTimeStampAvailable()) {
                        if ((currentTime - state.getValue().getTimeStamp()) > expiryTimeout) {
                            statesToRemove.add(state.getKey());
                            String leasedIp = new String("");
                            try {
                                 leasedIp = state.getValue().getLeasedIpAddress().getHostAddress();
                            } catch (NullPointerException e) {
                                LOG.warn("Ip address still not leased");
                            }
                            LOG.info("Transaction id {} state expired. Leased IP: {} Device MAC: {}",
                                    String.format("0x%x", state.getKey()),
                                    leasedIp,
                                    state.getValue().getMacAddress().getNativeRepresentation());
                        }
                    }
                }
                for (Integer transactionId: statesToRemove) {
                    CustomisableLeaseManagerDhcpService.transactionsStateStore.remove(transactionId);
                }
            }
        }
    }
}
