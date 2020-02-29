package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.ControllerSelfDiscovery;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.InitialOFRulesPhaseI;
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.HostNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static java.lang.Thread.sleep;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 08.06.18
 */
public class ControllerPair {

    private static final Logger LOG = LoggerFactory.getLogger(ControllerPair.class);

    private HostNode controller1 = null;
    private HostNode controller2 = null;
    private String controllerIp1 = null;
    private String controllerIp2 = null;

    public ControllerPair(String controllerIp1, String controllerIp2) {
        this.controllerIp1 = controllerIp1;
        this.controllerIp2 = controllerIp2;
        while (this.controller1 == null) {
            this.controller1 = HostUtilities.getHostNodeByIp(controllerIp1);
            sleep_some_time(50);
        }
        while (this.controller2 == null) {
            this.controller2 = HostUtilities.getHostNodeByIp(controllerIp2);
            sleep_some_time(50);
        }
    }

    public ControllerPair(NodeId controller1, NodeId controller2) {
        while (this.controller1 == null) {
            this.controller1 = HostUtilities.getHostNodeById(controller1.getValue());
            sleep_some_time(50);
        }
        while (this.controller2 == null) {
            this.controller2 = HostUtilities.getHostNodeById(controller2.getValue());
            sleep_some_time(50);
        }
        while (this.controllerIp1 == null) {
            this.controllerIp1 = this.controller1.getAddresses().get(0).getIp().getIpv4Address().getValue();
            sleep_some_time(50);
        }
        while (this.controllerIp2 == null) {
            this.controllerIp2 = this.controller2.getAddresses().get(0).getIp().getIpv4Address().getValue();
            sleep_some_time(50);
        }
    }



    public String getControllerIp1() {
        return controllerIp1;
    }

    public String getControllerIp2() {
        return controllerIp2;
    }

    public String getControllerId1() {

        String controllerId = null;
        // in multi controller environment necessary throws NullPointerExceptions due to the slow datastore access
        while (controllerId == null) {
            LOG.debug("Trying to fetch controllerId in the ControllerPair class!");
            controllerId = controller1.getAttachmentPoints().get(0).getCorrespondingTp().getValue();
            sleep_some_time(50);
        }
        return controllerId;
    }

    public String getControllerId2() {

        String controllerId = null;
        // in multi controller environment necessary throws NullPointerExceptions due to the slow datastore access
        while (controllerId == null) {
            LOG.debug("Trying to fetch controllerId in the ControllerPair class!");
            controllerId = controller2.getAttachmentPoints().get(0).getCorrespondingTp().getValue();
            sleep_some_time(50);
        }
        return controllerId;
    }


    public boolean equalsPair(Object o) { // checking if reverse is equal
        if(!equals(o)) {
            ControllerPair that = (ControllerPair) o;
            return Objects.equals(getControllerIp1(), that.getControllerIp2()) &&
                    Objects.equals(getControllerIp2(), that.getControllerIp1()) &&
                    Objects.equals(getControllerId1(), that.getControllerId2()) &&
                    Objects.equals(getControllerId2(), that.getControllerId1());
        } else {
            return true;
        }
    }


    @Override
    public String toString() {
        return controllerIp1 + '-' +  controllerIp2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ControllerPair)) return false;
        ControllerPair that = (ControllerPair) o;
        return (Objects.equals(getControllerId1(), that.getControllerId1()) &&
                Objects.equals(getControllerId2(), that.getControllerId2()) &&
                Objects.equals(getControllerIp1(), that.getControllerIp1()) &&
                Objects.equals(getControllerIp2(), that.getControllerIp2()));
    }

    @Override
    public int hashCode() {

        return Objects.hash(getControllerId1(), getControllerId2(), getControllerIp1(), getControllerIp2());
    }

    private void sleep_some_time(long milis) {
        try {
            sleep(milis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}


