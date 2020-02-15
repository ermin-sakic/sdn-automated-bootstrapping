package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.AnSshConnector;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.SyncLock;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Runnable containing the methods for executing a modification of controller lists in the OpenFlow switches.
 * The modification of the controller lists is partaken using the SSH connection to the switches which are to be modified.
 */
public class ConfigureNewOpenFlowNodeAuto implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigureNewOpenFlowNodeAuto.class);

    // Information about the SSH host - delivered via NBI in BootstrappingManagerRESTImpl
    private static String preferredControllerIp = new String();
    private static List<String> slaveControllerIPs = new ArrayList<String>();
    private static String sshUsername, sshPassword = null;
    private static ArrayList<String> ctlList = new ArrayList<String>();
    private static final int SSH_PORT = 22;
    private static final int SSH_TIMEOUT = 5000;
    private String deviceIp;
    private String deviceMacAddress;
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    private static Map<String, List<ConfigureNewOpenFlowNodeAuto>> sshThreadStore = Collections.synchronizedMap(new HashMap<>());

    private static Map<String, String> ipMacMappingStore = Collections.synchronizedMap(new HashMap<>());

    // delivered from SetupModule
    private static DataBroker db = null;

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
     * Get isRunning
     * @return
     */
    public Boolean getIsRunning() {
        return isRunning.get();
    }

    /**
     * Set isRunning
     * @param isRunning
     */
    public void setIsRunning(Boolean isRunning) {
        this.isRunning.set(isRunning);
    }

    public static String mapToMacAddress(String ip) {
        return ipMacMappingStore.get(ip);
    }

    /**
     * Sets ssh credentials
     * @param username
     * @param password
     */
    public static void setSSHConfiguration(String username, String password) {
        sshUsername = username;
        sshPassword = password;
    }

    /**
     * Default Constructor
     */
    public ConfigureNewOpenFlowNodeAuto(DataBroker db) {
        this.db = db;
    }

    /**
     * Constructor that initializes the IP address of the OpenFlow node on which
     * the controller list is to be configured.
     */
    public ConfigureNewOpenFlowNodeAuto(String ip, String mac) {
        deviceIp = ip;
        deviceMacAddress = mac;
        ipMacMappingStore.put(deviceIp, deviceMacAddress);
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

        /**
         * The problem is that the DHCP client responds to all duplicates,
         * which change its IP address mulitple times. Thus, occassionally
         * you can see how SSH fails because the previously leased IP
         * address used in that thread is replaced with the new one.
         * For that reason SSH socket complains.
         * Allowing multiple SSH threads for one switch increases
         * probability of success because normally 2-3 duplicates
         * are observed.
         * The number of duplicates depends on the used topology,
         * i.e. the built ill-tree that causes this issue.
         *
         * Trying to block duplicates in the DHCP server lead to
         * the abnormal behaviour of the server. The server started
         * to reply on the previously received DISCOVER messages
         * instead of the currently received. This behaviour is confirmed
         * via Wireshark and logging in the controller.
         * The solution to this issue was never found.
         *
         * Though, it is possible to initially configure DHCP server
         * via XML to block duplicates (custom implemented), the above
         * described behavior was so annoying and not understandable that
         * the hack was done in this class. But have in mind that is not also
         * perfect and it can fail also depending on the underlying topology.
         */
        if (!isConfigured()) {

            LOG.info("Starting SSH thread for node ({}, {})",
                    deviceIp, deviceMacAddress);

            if (sshThreadStore.get(deviceMacAddress) == null){
                sshThreadStore.put(deviceMacAddress, new LinkedList<>());
            }
            sshThreadStore.get(deviceMacAddress).add(this);

            // wait for DHCP ACK
            try {
                Thread.sleep(200);
            } catch (Exception e) {
                LOG.error(e.getMessage());
            }

            /**
             ######### Configure the remote list of controllers to include the master and the slave controllers
             **/
            // Set controller IPs and generate the SSH command
            StringBuilder controllerListCommand = new StringBuilder();
            controllerListCommand.append("echo '" + sshPassword + "' | sudo -kS ovs-vsctl set-controller br100");

            for (String contrIp : ctlList) {// Add all controllers}
                LOG.info("Controller identifier configured: {}", contrIp);
                controllerListCommand.append(" tcp:" + contrIp + ":6633");
            }

            LOG.info("Sending the following command to node ({}, {}): {}",  deviceIp, deviceMacAddress, controllerListCommand.toString());

            /*
             *   Create a new lock object for this thread pair
             *   threadSync is Collections.synchronizedMap
             */
            String attemptId = deviceIp + deviceMacAddress;
            SyncLock.threadSync.put(attemptId, new SyncLock());
            boolean controllerIdentifiersProvisioningResult = blockSshExecuteCommandUntilCheck(deviceIp, controllerListCommand.toString(),
                    "echo '" + sshPassword + "' | sudo -kS ovs-vsctl get-controller br100", ctlList.get(0));

            if (controllerIdentifiersProvisioningResult){
                LOG.info("Controller identifiers delivered to node ({}, {}) via SSH",  deviceIp, deviceMacAddress);
                /* TODO: What should we do after the SSH thread fails. That is the failed thread should not notify InitialFLowWriter!!!   */
                synchronized (SyncLock.threadSync.get(attemptId)) {
                    SyncLock.threadSync.get(attemptId).setCondVariable(true);
                    LOG.info("Notifying InitialFlowWriter to change the state of node ({}, {}) ",  deviceIp, deviceMacAddress);
                    SyncLock.threadSync.get(attemptId).notify();
                }
            } else {
                LOG.warn("Controller identifiers failed to be delivered to node ({}, {}) via SSH",  deviceIp, deviceMacAddress);
                SyncLock.threadSync.remove(attemptId);
            }
            LOG.info("Closing SSH thread for node ({}, {})",  deviceIp, deviceMacAddress);
        } else {
            LOG.warn("Node ({}, {}) already configured via SSH. Probably DHCP duplicate.",  deviceIp, deviceMacAddress);
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
    public boolean blockSshExecuteCommandUntilCheck(String ip, String command,
                                                    String checkCommand, String checkString) {

        String output = "";
        // if node not available
        // normally a new IP address has been leased to the node and
        // therefore the old one becomes unavailable

        while (isRunning.get()) {
            if (!output.contains(checkString)) {
                AnSshConnector sshConnector = new AnSshConnector(ip, SSH_PORT, sshUsername, sshPassword, SSH_TIMEOUT);
                try {
                    sshExecuteFireAndForget(sshConnector, command);

                    // TODO: Check whether too short or necessary at all!
                    // Wait a bit until the configuration is made successfully remotely.
                    Thread.sleep(500); // ms

                    output = sshExecuteWithResult(sshConnector, checkCommand);
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
            } else {
                /* Device is configured block any new attempts */
                for (ConfigureNewOpenFlowNodeAuto sshThread: sshThreadStore.get(deviceMacAddress)){
                    sshThread.setIsRunning(false);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * This method executes the command specified, using a given AnSshConnector instance.
     *
     * @param sshConnector The SSH connector.
     * @param command      The SSH command to be executed.
     */
    private void sshExecuteFireAndForget(AnSshConnector sshConnector, String command) throws Exception {
        if(!sshConnector.isOpen()) {
            sshConnector.open();
        }
        sshConnector.executeCommand(command);
    }

    /**
     * This method executes the command specified, using a given AnSshConnector instance.
     *
     * @param sshConnector The SSH connector.
     * @param command      The SSH command to be executed.
     * @return  command output
     */
    private String sshExecuteWithResult(AnSshConnector sshConnector, String command) throws Exception {
        if(!sshConnector.isOpen()) {
            sshConnector.open();
        }
        return sshConnector.executeCommand(command);
    }

    /**
     * Checks if device with deviceMacAddress has already been assigned with controllers' IPs
     * @return
     */
    private Boolean isConfigured() {
        List<ConfigureNewOpenFlowNodeAuto> sshThreads = sshThreadStore.get(deviceMacAddress);

        if (sshThreads == null) {
            return false;
        } else {
            for (ConfigureNewOpenFlowNodeAuto obj: sshThreads) {
                if (!obj.getIsRunning()){
                    return true;
                }
            }
        }
        return false;
    }

}







