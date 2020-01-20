package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.AnSshConnector;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.SyncLock;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
    private static final int SSH_TIMEOUT = 5000;
    private String deviceIp;
    private static boolean isIpv6Enabled = false;
    private static List<String> inBandDisabledNodes = new ArrayList<>();
    // delivered from SetupModule
    private static DataBroker db = null;



    //TODO: THIS CLASS WAS NOT TESTED SINCE ALL THE MEASUREMENTS WERE CCONFIGURED TO USE AUTO MODE IN XML
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

    /**
     * Default Constructor
     */
    public ConfigureNewOpenFlowNodeNBI(DataBroker db) {
        this.db = db;
    }

    /**
     * Constructor that initializes the IP address of the OpenFlow node on which
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

        //synchronized (ConfigureNewOpenFlowNodeNBI.class) {

            LOG.info("Starting SSH thread for device IP {}", deviceIp);

            // wait for DHCP ACK
            try { Thread.sleep(200); } catch(Exception e) { LOG.error(e.getMessage()); }

            /*
            *   Create a new lock object for this thread pair
            *   Does not need to be synchronized since the entire run() is atomic
            *   if not put synchronized around to avoid race conditions
            */
            synchronized (ConfigureNewOpenFlowNodeNBI.class) {
                SyncLock.threadSync.put(deviceIp, new SyncLock());
            }

            /**
             ######### Start remotely a script to execute a continous local re-addition of the controller-to-controller flows
             **/
            /*
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
            */

            /**
             ######### Configure the remote list of controllers to include the master and the slave controllers
             **/
            // Set controller IPs and generate the SSH command
            StringBuilder controllerListCommand = new StringBuilder();
            controllerListCommand.append("echo '" + sshPassword + "' | sudo -kS ovs-vsctl set-controller br100" );
            controllerListCommand.append(" tcp:" + preferredControllerIp + ":6633"); // Add preferred controller
            for(String slaveContrIp:slaveControllerIPs) // Add all other controllers
                controllerListCommand.append(" tcp:" + slaveContrIp + ":6633");


            LOG.info("Sending the following command to node " + this.deviceIp + ": " + controllerListCommand.toString());
            blockSshExecuteCommandUntilCheck(deviceIp, controllerListCommand.toString(),
                    "echo '" + sshPassword + "' | sudo -kS ovs-vsctl get-controller br100", preferredControllerIp);
            LOG.info("Delivered controller IPs to {} via SSH", deviceIp);

            LOG.info("Controller IP address provided checked!!");
            synchronized (SyncLock.threadSync.get(this.deviceIp)) {
                SyncLock.threadSync.get(this.deviceIp).setCondVariable(true);
                LOG.info("Notifying InitialFlowWriter to change the state of the switch " + this.deviceIp);
                SyncLock.threadSync.get(this.deviceIp).notify();
            }

        //}

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
        // if node not available
        // normally a new IP address has been leased to the node and
        // therefore the old one becomes unavailable
        // for that reason use MAX number of attempts in order to avoid an infinite  loop
        Integer MAX_ATTEMPTS = 10;
        Integer attemptCounter = 0;
        while (!output.contains(checkString) && (attemptCounter <= MAX_ATTEMPTS)) {
            AnSshConnector sshConnector = new AnSshConnector(ip, SSH_PORT, sshUsername, sshPassword, SSH_TIMEOUT);
            attemptCounter++;
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

}







