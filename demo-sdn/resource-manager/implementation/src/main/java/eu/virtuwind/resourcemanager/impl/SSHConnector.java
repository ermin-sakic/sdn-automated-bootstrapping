package eu.virtuwind.resourcemanager.impl;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * Class to provide SSH connection to remote host and execute a given command
 * Uses JSCH Library
 */
public class SSHConnector {

   // main for testing only
    public static void main(String[] args) {

        //SSHConnector connector =  new SSHConnector();

        //System.out.println(connector.connectTossh("name", "password","192.168.0.0",22,"ifconfig"));

    }


    /**
     * Method to connect to a remote host using SSH and execute a given command
     * @param username - username of remote hots
     * @param password - password of the remote host
     * @param targetIp - Ip address of the remote host
     * @param port - port to connect on, usually 22
     * @param commandToExecute - Command to execute on remote host
     * @return - result of the executed command in String
     */
    public static String connectTossh(String username, String password, String targetIp, int port, String commandToExecute) {

        try {

            JSch jsch = new JSch();
            Session session = jsch.getSession(username, targetIp, port);
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();

            //open channel
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            //set the command and connect
            channel.setCommand(commandToExecute);
            channel.connect();

            //read the input back from channel
            BufferedReader in = new BufferedReader(new InputStreamReader(channel.getInputStream()));

            // string to be returned
            String result = "";
            String msg = null;

            //keep adding to result each line of response
            while ((msg = in.readLine()) != null) {
                result  += msg;
            }


            int exitStatus = channel.getExitStatus();
            channel.disconnect();
            session.disconnect();
            //some debugging
            if(exitStatus < 0){
                System.out.println("Done, but exit status not set!");
            }
            else if(exitStatus > 0){
                System.out.println("Done, but with error!");
            }
            else{
                System.out.println("Done!");
            }

            in.close();
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return "Exception";
        }

    }



}
