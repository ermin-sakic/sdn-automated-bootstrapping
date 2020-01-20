package eu.virtuwind.bootstrappingmanager.setup.impl;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A Runnable containing the methods for executing a modification of controller lists in the OpenFlow switches.
 * The modification of the controller lists is partaken using the SSH connection to the switches which are to be modified.
 */
public class ConfigureNewOpenFlowNodeAuto implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigureNewOpenFlowNodeAuto.class);

    // Information about the SSH host - delivered via NBI in BootstrappingManagerRESTImpl
    private static ArrayList<String> controllerIpAddresses;
    private static String sshUsername, sshPassword = null;
    private static final int SSH_PORT = 22;
    private static final int SSH_TIMEOUT = 4000;
    private static DataBroker dataBroker;
    private static boolean isIpv6Enabled = false;

    public static void setControllerList(ArrayList<String> ctlList) {
        controllerIpAddresses = ctlList;
    }

    public static void setSSHConfiguration(String username, String password) {
        sshUsername = username;
        sshPassword = password;
    }

    /**
     * Default constructor that initializes the IP address of the OpenFlow node on which
     * the controller list is to be configured.
     */
    public ConfigureNewOpenFlowNodeAuto() {
        //this.deviceIp = ip;
    }

    /**
     * Enables or disables the mode for IPv6 default flow cofnigurations
     * @param arg The boolean binary parameter.
     */
    public static void setIPv6Enabled(boolean arg) {
        isIpv6Enabled = arg;
    }

    public static void setDataBroker(DataBroker db) {
        dataBroker = db;
    }

    /**
     * The main run() method which executes a configuration of the controller lists in a given OpenFlow switch.
     * The Runnable is executed in a separate thread for each newly connected switch.
     */
    public void run() {

        synchronized (ConfigureNewOpenFlowNodeAuto.class) {
            try {
                LOG.warn("Now sleeping for 18 seconds, waiting on other config threads to finish.");
                Thread.sleep(18000);
            } catch (Exception e) {
                LOG.warn("Sleep interrupted, probably fine as other node ongoing new reconfigs.");
                return;
            }

            InitialFlowWriter.setBootstrappingDone();

            // Obtain list of nodes
            List<Node> nodeList = new ArrayList<>();
            InstanceIdentifier<Nodes> nodesIid = InstanceIdentifier.builder(Nodes.class).build();
            ReadOnlyTransaction nodesTransaction = dataBroker.newReadOnlyTransaction();

            boolean notFetched = true;
            while (notFetched) {
                try {
                    CheckedFuture<Optional<Nodes>, ReadFailedException> nodesFuture = nodesTransaction
                            .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
                    com.google.common.base.Optional<Nodes> nodesOptional = nodesFuture.checkedGet();

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

            for (int i = 0; i < nodeList.size(); i++) {
                if (nodeList.get(i).getId().getValue().contains("openflow")) {
                    LOG.info("Handling OpenFlow node " + nodeList.get(i).getId().getValue());

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
                        continue;
                    }
                    LOG.info("Fetched IP address for node {}, IP = {}", nodeList.get(i).getId().getValue(), ip);

                    LOG.info("Starting SSH thread for device IP {}", ip);


                    /**
                     ######### Disables the in-band flow rules in the OVS so that we can override them with our own OpenFlow rules.
                     **/

                    String inBandConfigCommand = null, inBandConfigCheckCommand = null;

                    inBandConfigCommand = "echo '" + sshPassword + "' | sudo -kS ovs-vsctl set Bridge br100 other-config:disable-in-band=true";
                    inBandConfigCheckCommand = "echo '" + sshPassword + "' | sudo -kS ovs-vsctl get Bridge br100 other-config:disable-in-band";

                    LOG.info("Attempted to disable default in-band-mode via SSH");
                    blockSshExecuteCommandUntilCheck(ip, inBandConfigCommand, inBandConfigCheckCommand, "true");

                    LOG.info("Closing SSH thread for device IP {}", ip);
                }
            }

            /** ####### After the thread has finished, initiate the timer for RSTP disable commands ######## **/
            SetupModule.reinitiateSTPDisable(new Long(35));
        }
    }

    /**
     * The method will block until the String checkString is contained
     * as part of the output of the remote command defined by checkCommand.
     *
     * @param ip           The SSH Connector IP.
     * @param command      The SSH command to be executed.
     * @param checkCommand The SSH check-command.
     * @param checkString  The SSH check-string.
     */
    public void blockSshExecuteCommandUntilCheck(String ip, String command,
                                                 String checkCommand, String checkString) {
        String output = "";
        while (!output.contains(checkString)) {
            AnSshConnector sshConnector = new AnSshConnector(ip, SSH_PORT, sshUsername, sshPassword, SSH_TIMEOUT);

            try {
                sshExecuteSimple(sshConnector, command);

                // Wait a bit until the configuration is made successfully remotely.
                Thread.sleep(500); // ms
                //TODO CHECK IF TOO SHORT and if needed!

                //AnSshConnector sshConnector = new AnSshConnector(ip, SSH_PORT, SSH_TIMEOUT);

                if (!sshConnector.isOpen())
                    sshConnector.open();
                output = sshConnector.executeCommand(checkCommand);
                sshConnector.close();

                LOG.info("Executed COMMAND={} and CHECKCOMMAND={}, and CHECKSTRING={}, RESPONSE={}",
                        command, checkCommand, checkString, output);
            } catch (Exception e) {
                LOG.error(e.getMessage());
                //e.printStackTrace();
                try {
                    sshConnector.close();
                } catch (Exception e1) {
                    LOG.error(e1.getMessage());
                }
            }
        }
    }

    /**
     * This method executes the command specified, using a given AnSshConnector instance.
     *
     * @param sshConnector The SSH connector.
     * @param command      The SSH command to be executed.
     */
    public void sshExecuteSimple(AnSshConnector sshConnector, String command) throws Exception {
        if(!sshConnector.isOpen())
            sshConnector.open();
        sshConnector.executeCommand(command);
        //sshConnector.close();
    }
}







