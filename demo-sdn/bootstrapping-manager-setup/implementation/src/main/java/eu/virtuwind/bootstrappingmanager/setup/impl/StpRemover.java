package eu.virtuwind.bootstrappingmanager.setup.impl;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.bootstrappingmanager.setup.impl.rev150722.modules.module.configuration.SetupImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that implements the STP removal methods and configuration
 * settings to establish SSH sessions to switches.
 */
public class StpRemover implements Runnable {
    // LOGGER
    private static final Logger LOG = LoggerFactory.getLogger(StpRemover.class);

    // SERVICE DEFINITIONS
    private DataBroker dataBroker;
    private SalFlowService salFlowService;

    // TIMINGS
    private short flowTableId;
    private int flowHardTimeout;
    private int flowIdleTimeout;
    private Long waitingPeriod;

    // Bootstrapping Logic
    private static boolean isIpv6Enabled = false;
    private static boolean rstpUsed = true;
    private static String flowReconfiguratorFlavour;

    // SSH configuration (has to be set externally)
    private static String sshUsername, sshPassword = null;
    private static final int sshPort = 22;

    private static String setEnv;
    private static String bridge;


    /**
     * Default constructor for the Runnable.
     * @param dataBroker The MD-SAL data broker service.
     * @param salFlowService The MD-SAL flow service.
     * @param flowTableId The flow table ID.
     * @param flowHardTimeout The flow rule timeout (hard).
     * @param flowIdleTimeout The flow rule timeout (idle).
     * @param waitingTime The waiting period before the (R)STP is disabled in the system.
     */
    public StpRemover(DataBroker dataBroker,
                      SalFlowService salFlowService,
                      short flowTableId,
                      int flowHardTimeout,
                      int flowIdleTimeout,
                      Long waitingTime) {
        this.dataBroker = dataBroker;
        this.salFlowService = salFlowService;
        this.flowTableId = flowTableId;
        this.flowHardTimeout = flowHardTimeout;
        this.flowIdleTimeout = flowIdleTimeout;
        this.waitingPeriod = waitingTime;
    }


    /**
     * Sets the configuration environment to one of the environments used for evaluation of the code in VirtuWind:
     *  1) Lab (for trial field testing using actual physical OpenFlow devices)
     *  2) Local (for local testing using in-band controlled emulated network)
     *  3) Any (for use with any emulated network, e.g. out-of-band SDN control network)
     * @param env The environment parameter.
     */
    public static void setConfigEnv(String env) {
        setEnv = env;
    }

    /**
     * Configures the default name used for referencing the bridge instance of the Open vSwitch realized on the OpenFlow switches.
     */
    public static void setBridge(String brName) {
        bridge = brName;
    }

    /**
     * Modify the existing SSH configuration (currently expected that every device is accessed using the same login data).
     * @param newSSHUsername The SSH username.
     * @param newSSHPassword The SSH password.
     */
    public static void changeDefaultSSHConfig(String newSSHUsername, String newSSHPassword)
    {
        sshUsername = newSSHUsername;
        sshPassword = newSSHPassword;

        LOG.info("SSH Configuration changed for all devices to: "
                + sshUsername + ":" + sshPassword);
    }

    /**
     * Configures which FlowReconfigurator flavour should be executed during the bootstrapping procedure
     * @param flowReconfiguratorFlavour
     */
    public static void setFlowReconfiguratorFlavour(String flowReconfiguratorFlavour) {
        StpRemover.flowReconfiguratorFlavour = flowReconfiguratorFlavour;
    }

    /**
     * Enables or disables the mode for IPv6 default flow cofnigurations
     * @param arg The boolean binary parameter.
     */
    public static void setIPv6Enabled(boolean arg) {
        isIpv6Enabled = arg;
    }

    /**
     * Sets or disables the RSTP flag (otherwise STP assumed)
     * @param arg The boolean binary parameter.
     */
    public static void setRSTPUsed(boolean arg) {
        rstpUsed = arg;
    }


    /**
     * The executor instance re-initiated every time a new switch joins the network.
     * The execution of the run() method ensures that every switch in the network has a disabled STP agent after
     * the waiting period is expired.
     */
    public void run() {

        // Wait a reasonable amount of time for all network devices to establish OpenFlow sessions with controllers
        try {
            LOG.info("(R-)STP Thread (Re-)Initiated!");
            Thread.sleep(waitingPeriod * 1000); // ms
        }
        catch(Exception e) {
            LOG.info("Thread failed to sleep(), probably intentionally. Returning.");
            return;
        }

        String stp_kw;
        if(rstpUsed)
            stp_kw = "rstp";
        else
            stp_kw = "stp";

        LOG.info("Waited reasonable amount of time for all network devices to establish OpenFlow sessions with controllers, " +
                "starting process of disabling (R-)STP");

        // Obtain list of nodes
        List<Node> nodeList = new ArrayList<>();
        InstanceIdentifier<Nodes> nodesIid = InstanceIdentifier.builder(Nodes.class).build();
        ReadOnlyTransaction nodesTransaction = dataBroker.newReadOnlyTransaction();

        boolean notFetched = true;
        while(notFetched) {
            try {
                CheckedFuture<Optional<Nodes>, ReadFailedException> nodesFuture = nodesTransaction
                        .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
                Optional<Nodes> nodesOptional = nodesFuture.checkedGet();

                notFetched = false;

                if (nodesOptional != null && nodesOptional.isPresent()) {
                    nodeList = nodesOptional.get().getNode();
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch list of nodes");
                notFetched = true;
            }
        }

        LOG.info("Successfully fetched a list of nodes.");

        FlowCapableNode flowCapableNode;
        String ip;

        int timeout = 10000; // ms
        for (int i = 0; i < nodeList.size(); i++) {
            if (nodeList.get(i).getId().getValue().contains("openflow")) {
                // Fetch IP address
                ip = "0.0.0.0"; // (no IP address)
                try {
                    flowCapableNode = nodeList.get(i).getAugmentation(FlowCapableNode.class);

                    if(!isIpv6Enabled)
                        ip = flowCapableNode.getIpAddress().getIpv4Address().getValue();
                    else if (isIpv6Enabled)
                        ip = flowCapableNode.getIpAddress().getIpv6Address().getValue();
                } catch (Exception e) {
                    LOG.error("Failed to fetch IP address for node {}", nodeList.get(i).getId().getValue());
                }
                LOG.info("Fetched IP address for node {}, IP = {}", nodeList.get(i).getId().getValue(), ip);

                AnSshConnector sshConnector = new AnSshConnector(ip, sshPort, sshUsername, sshPassword, timeout);

                boolean stpDisabled = false;

                while(!stpDisabled) {
                    try {
                        String stpDisablingOutput = "";
                        // Disable STP
                        sshConnector.open();
                        if (setEnv.equals("local"))
                            stpDisablingOutput = sshConnector.executeCommand("echo '" + sshPassword + "' | sudo -kS ovs-vsctl set Bridge " + bridge + " " + stp_kw + "_enable=false");
                        else if (setEnv.equals("lab"))
                            stpDisablingOutput = sshConnector.executeCommand("/ovs/bin/ovs-vsctl set Bridge " + bridge + " " + stp_kw + "_enable=false");

                        LOG.info("Attempted to disable {} on node {} via SSH", stp_kw, nodeList.get(i).getId().getValue());
                        sshConnector.close();
                        stpDisabled = true;
                    } catch (Exception e) {
                        LOG.error("Failed to disable {} on node {}, exception = {}", stp_kw, nodeList.get(i).getId().getValue(), e.getMessage());
                        try {
                            sshConnector.close();
                        } catch (Exception e1) {
                            LOG.error("Failed while closing the SSH connector.");
                            LOG.error("Exception thrown when connecting to switch " + ip + " for {} command.", stp_kw);
                        }

                        try {
                            Thread.sleep(500);
                        } catch (Exception e2) {
                            LOG.error("Exception thrown during sleep after failed {} disabling.", stp_kw);
                        }
                    }
                }

                boolean stpDisablingConfirmed = false;

                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                    LOG.error("Exception thrown during sleep between {} disabling and checking.", stp_kw);
                }

                while (!stpDisablingConfirmed) {
                    try {
                        String output = "";
                        // Check STP was disabled
                        sshConnector.open();

                        if (setEnv.equals("local"))
                            output = sshConnector.executeCommand("echo '" + sshPassword + "' | sudo -kS ovs-vsctl get Bridge " + bridge + " " + stp_kw + "_enable");
                        else if (setEnv.equals("lab"))
                            output = sshConnector.executeCommand("/ovs/bin/ovs-vsctl get Bridge " + bridge + " " + stp_kw + "_enable");

                        LOG.info("Received {}_enable={} from node {} via SSH", stp_kw, output, nodeList.get(i).getId().getValue());

                        sshConnector.close();
                        stpDisablingConfirmed = true;
                    } catch (Exception e) {
                        LOG.error("Failed to check {} on node {}, exception = {}", stp_kw, nodeList.get(i).getId().getValue(), e.getMessage());
                        try {
                            sshConnector.close();
                        } catch (Exception e1) {
                            LOG.error("Failed while closing the SSH connector.");
                            LOG.error("Exception thrown when connecting to switch " + ip + " for {} command.", stp_kw);
                        }

                        try {
                            Thread.sleep(500);
                        } catch (Exception e2) {
                            LOG.error("Exception thrown during sleep after failed {} checking.", stp_kw);
                        }
                    }
                }

                LOG.info("Successfully disabled R-STP for node {}", nodeList.get(i).getId().getValue());

                try {
                    Thread.sleep(500); // ms //TODO: CHECK TUNING
                } catch (Exception e) {
                    LOG.error("Exception thrown during sleep after SSH configuration.");
                    return;
                }
            }
        }

        LOG.info("Successfully disabled (R-)STP on all nodes.");

        /** ### Starts the flow reconfiguration in a separate thread ### **/

        /**
         * Based on the configuration enum starts different FlowReconfigurator flavours
         * if/else here just to know what has been started
         */
        if (flowReconfiguratorFlavour.equals(FlowReconfigurator.ONEDISJOINTPATHFOREACHCONTROLLER)) {
            LOG.info("ONEDISJOINTPATHFOREACHCONTROLLER chosen");
            LOG.info("Starting FlowReconfigurator thread");
            Thread c = new Thread(FlowReconfigurator.getInstance(flowReconfiguratorFlavour));
            c.start();
        } else if (flowReconfiguratorFlavour.equals(FlowReconfigurator.TWODISJOINTPATHSFOREACHCONTROLLER)) {
            LOG.info("TWODISJOINTPATHFOREACHCONTROLLER chosen");
            LOG.info("Starting FlowReconfigurator thread");
            Thread c = new Thread(FlowReconfigurator.getInstance(flowReconfiguratorFlavour));
            c.start();
        } else {
            LOG.error("flowReconfiguratorFlavour configuration has either not been initialized or it has been initialized with" +
                    "unexisting option!!!");
        }

    }
}
