package eu.virtuwind.bootstrappingmanager.alternative.dhcp.impl;

import org.apache.directory.server.dhcp.messages.HardwareAddress;

import java.net.InetAddress;
import java.util.Observable;
import java.util.Observer;
import java.lang.*;


/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 20.06.18
 */
public class DhcpState extends Observable implements Observer {

    private HardwareAddress macAddress;
    private InetAddress leasedIpAddress;
    private boolean discoveryProcessed = false;
    private boolean offerSent = false;
    private boolean requestProcessed = false;
    private boolean ackSent = false;
    private long timeStamp = -1;
    public static final String lock = "LOCK";

    DhcpState() {
        this.addObserver(this);
    }

    public boolean isMacAddressAvailable() {
        if (macAddress != null) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isLeasedIpAddressAvailable() {
        if (leasedIpAddress != null) {
            return true;
        } else {
            return false;
        }
    }

    public HardwareAddress getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(HardwareAddress macAddress) {
        this.macAddress = macAddress;
    }

    public InetAddress getLeasedIpAddress() {
        return leasedIpAddress;
    }

    public void setLeasedIpAddress(InetAddress leasedIpAddress) {
        this.leasedIpAddress = leasedIpAddress;
    }

    public boolean isDiscoveryProcessed() {
        return discoveryProcessed;
    }

    public void setDiscoveryProcessed(boolean discoveryProcessed) {
        this.discoveryProcessed = discoveryProcessed;
        setChanged();
        notifyObservers(this.discoveryProcessed);
    }

    public boolean isOfferSent() {
        return offerSent;
    }

    public void setOfferSent(boolean offerSent) {
        this.offerSent = offerSent;
        setChanged();
        notifyObservers(this.offerSent);
    }

    public boolean isRequestProcessed() {
        return requestProcessed;
    }

    public void setRequestProcessed(boolean requestProcessed) {
        this.requestProcessed = requestProcessed;
        setChanged();
        notifyObservers(this.requestProcessed);
    }

    public boolean isAckSent() {
        return ackSent;
    }

    public void setAckSent(boolean ackSent) {
        this.ackSent = ackSent;
        setChanged();
        notifyObservers(this.ackSent);
    }

    public boolean isLeasingDone() {
        if (isDiscoveryProcessed()
                && isOfferSent()
                && isRequestProcessed()
                && isAckSent()) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isTimeStampAvailable() {
        if (timeStamp != -1) {
            return true;
        } else {
            return false;
        }
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    @Override
    public void update(Observable observable, Object o) {
        // After each message processed or sent update the timestamp
        // if the client fails to process one of the messages after some TIMEOUT value it will be allowed to
        // tra gain with the DISCOVER message
        if (isDiscoveryProcessed() || isOfferSent() || isRequestProcessed() || isAckSent()) {
            timeStamp = System.currentTimeMillis();
        }
    }
}
