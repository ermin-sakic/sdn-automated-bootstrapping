package eu.virtuwind.bootstrappingmanager.setup.impl;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static eu.virtuwind.bootstrappingmanager.setup.impl.SetupModule.reinitiateSTPDisable;

/**
 * A Runnable containing the methods for executing a modification of controller lists in the OpenFlow switches.
 * The modification of the controller lists is partaken using the SSH connection to the switches which are to be modified.
 */
public class ConfigureNewOpenFlowNodeNBI implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigureNewOpenFlowNodeNBI.class);

    // Information about the SSH host - delivered via NBI in BootstrappingManagerRESTImpl
    private static String preferredControllerIp = new String();
    private static List<String> slaveControllerIPs = new ArrayList<String>();
    private static String sshUsername, sshPassword = null;
    private static ArrayList<String> ctlList = new ArrayList<String>();
    private static final int SSH_PORT = 22;
    private static final int SSH_TIMEOUT = 5000; // was 5000
    private String deviceIp;
    private static boolean isIpv6Enabled = false;

    // for thread synchronization
    public static HashMap<String, SyncDataObject> threadIdentifiers = new HashMap<String, SyncDataObject>();

    //TODO: Enable connecting to multiple controllers instead of just a single one.
    /**
     * Modify the existing SSH and controller IP configuration (currently expected that every device is accessed using the same login data).
     * @param newCIP The new controller IP of the master controller to which the switches will connect.
     * @param slaveControllers The set of slave controllers to which the switches should connect in
     *                         case of a failure of the master controller.
     * @param newSSHUsername The SSH username.
     * @param newSSHPassword The SSH password.
     */
    public static void changeDefaultConfig(String newCIP, List<String> slaveControllers, String newSSHUsername, String newSSHPassword)
    {
        preferredControllerIp = newCIP;
        slaveControllerIPs.addAll(slaveControllers);
        sshUsername = newSSHUsername;
        sshPassword = newSSHPassword;

        LOG.info("SSH/OpenFlow Configuration changed for all devices to: "
                + sshUsername + ":" + sshPassword + "@" + preferredControllerIp);
    }

    static void setCtlList(ArrayList<String> controllerList) {
        LOG.info("CtlList in ConfigureNewOpenFlowNodeNBI: {}", ctlList.toString());
        ctlList = controllerList;
    }

    /**
     * Enables or disables the mode for IPv6 default flow cofnigurations
     * @param arg The boolean binary parameter.
     */
    public static void setIPv6Enabled(boolean arg) {
        isIpv6Enabled = arg;
    }


    public static void setSSHConfiguration(String username, String password) {
        sshUsername = username;
        sshPassword = password;
    }

    public static String getSSHUsername() {
        return sshUsername;
    }

    public static String getSSHPassword() {
        return sshPassword;
    }

    /**
     * Default constructor that initializes the IP address of the OpenFlow node on which
     * the controller list is to be configured.
     */
    public ConfigureNewOpenFlowNodeNBI(String ip) {
        this.deviceIp = ip;
    }

    /**
     * Returns the configured/default controller IP
     * @return String containing the controller IP address.
     */
    public static String getPreferredControllerIP()
    {
        return preferredControllerIp;
    }

    private void sleep_some_time(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * The main run() method which executes a configuration of the controller lists in a given OpenFlow switch.
     * The Runnable is executed in a separate thread for each newly connected switch.
     */
    public void run() {

        synchronized (ConfigureNewOpenFlowNodeNBI.class) {

            LOG.info("Starting SSH thread for device IP {}", deviceIp);

            // wait for DHCP ACK
            try { Thread.sleep(200); } catch(Exception e) { LOG.error(e.getMessage()); }

            /**
             ######### Start remotely a script to execute a continous local re-addition of the controller-to-controller flows
             **/

            StringBuilder commandC2Cflows = new StringBuilder();
            if(slaveControllerIPs.size() > 0 || !preferredControllerIp.isEmpty()) {
                commandC2Cflows = new StringBuilder()
                        .append("echo '" + sshPassword + "' | sudo -kS /periodicExecScript.sh " + preferredControllerIp);

                for (String slaveCtlIp : slaveControllerIPs)
                    commandC2Cflows.append(" " + slaveCtlIp);
            } else {
                commandC2Cflows = new StringBuilder()
                        .append("echo '" + sshPassword + "' | sudo -kS /periodicExecScript.sh ");

                for(String ctlIp: ctlList)
                    commandC2Cflows.append(" " + ctlIp);
            }

            String checkCommand = "echo '" + sshPassword + "' | sudo -kS ps ax";
            String checkString = "add_c2c";
            blockSshExecuteCommandUntilCheck(deviceIp, commandC2Cflows.toString(), checkCommand, checkString);


            /**
             ######### Configure the remote list of controllers to include the master and the slave controllers
             **/
            // Set controller IPs and generate the SSH command
            StringBuilder controllerListCommand = new StringBuilder();
            controllerListCommand.append("echo '" + sshPassword + "' | sudo -kS ovs-vsctl set-controller br100" );
            controllerListCommand.append(" tcp:" + preferredControllerIp + ":6633"); // Add preferred controller
            for(String slaveContrIp:slaveControllerIPs) // Add all other controllers
                controllerListCommand.append(" tcp:" + slaveContrIp + ":6633");

            // When OF session is cerated a new node in the data store is added which triggers the
            // InitialFlowWriter which may lead to some race conditions between threads that execute this code and InitialFlowWriter
            // Wait until the OpenFlow connection is established
            sleep_some_time(2000);

            LOG.info("Sending the following command to node " + this.deviceIp + ": " + controllerListCommand.toString());
            blockSshExecuteCommandUntilCheck(deviceIp, controllerListCommand.toString(),
                    "echo '" + sshPassword + "' | sudo -kS ovs-vsctl get-controller br100", preferredControllerIp);
            LOG.info("Delivered controller IPs to {} via SSH", deviceIp);


            /**
             ######### Disables the in-band flow rules in the OVS so that we can override them with our own OpenFlow rules.
             **/

            String inBandConfigCommand = "echo '" + sshPassword + "' | sudo -kS ovs-vsctl set Bridge br100 other-config:disable-in-band=true";
            String inBandConfigCheckCommand = "echo '" + sshPassword + "' | sudo -kS ovs-vsctl get Bridge br100 other-config:disable-in-band";
            LOG.info("Attempted to disable default in-band-mode via SSH");
            blockSshExecuteCommandUntilCheck(deviceIp, inBandConfigCommand, inBandConfigCheckCommand, "true");


            /**
             ######### PROBLEM: Disabling the STP messes up the MAC-learning tables and the controllers lose connection indefinitely
             ######### This sets the fail-mode to secure so that the removal of STP in the second phase does not evict the flows configured
             ######### in the first phase
             #########
             ######### Disables the standalone mode and sets it to secure.
             **/
            /*String secureCommand = "echo '" + sshPassword + "' | sudo -kS ovs-vsctl set-fail-mode br100 secure";
            String secureCheckCommand = "echo '" + sshPassword + "' | sudo -kS ovs-vsctl show";
            LOG.info("Attempted to disable the standalone mode using SSH, setting to secure.");
            blockSshExecuteCommandUntilCheck(deviceIp, secureCommand, secureCheckCommand, "secure");*/
        }

        /** ####### After the thread has finished, initiate the timer for RSTP disable commands ######## **/
        reinitiateSTPDisable(new Long(35));

        LOG.info("Closing SSH thread for device IP {}", deviceIp);
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
                LOG.info(e.getMessage());
                e.printStackTrace();
                try {
                    sshConnector.close();
                } catch (Exception e1) {
                    e1.printStackTrace();
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
    }

    /**
     * This is a helper class for sync purposes
     */

    public class SyncDataObject {

        private Condition condVariable;
        private boolean condValue;
        private Lock lock;

        public SyncDataObject(Lock lock, boolean condValue) {
            this.lock = lock;
            this.condVariable = lock.newCondition();
            this.condValue = condValue;
        }

        public Condition getCondVariable() {
            return condVariable;
        }

        private void setCondVariable(Condition condVariable) {
            this.condVariable = condVariable;
        }

        public boolean isCondValue() {
            return condValue;
        }

        public void setCondValue(boolean condValue) {
            this.condValue = condValue;
        }

        public Lock getLock() {
            return lock;
        }

        public void setLock(Lock lock) {
            this.lock = lock;
        }
    }
}







