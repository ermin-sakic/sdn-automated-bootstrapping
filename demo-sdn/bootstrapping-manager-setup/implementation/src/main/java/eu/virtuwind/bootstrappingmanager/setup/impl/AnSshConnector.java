package eu.virtuwind.bootstrappingmanager.setup.impl;

import com.jcraft.jsch.*;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * An SSH connector class that provides the basic configuration options for new SSH connections.
 * Also provides the methods to execute remote commands on the remote SSH servers.
 */
public class AnSshConnector implements AutoCloseable {

    // Default logger
    private static final Logger LOG = Logger.getLogger(AnSshConnector.class);

    // Constants
    private static final String STRICT_HOSTKEY_CHECKIN_KEY = "StrictHostKeyChecking";
    private static final String STRICT_HOSTKEY_CHECKIN_VALUE = "no";
    private static final String CHANNEL_TYPE = "exec";

    // SSH server ip
    private String ip;

    // SSH server port
    private int port;

    // User login
    private String login;

    // User password
    private String password;

    // Connection timeout
    private int timeout;

    private Session session;
    private Channel channel;

    /**
     * Basic constructor to establish an SSH connector.
     *
     * @param ip The SSH server (OpenFlow node) IP
     * @param port The SSH server (OpenFlow node) port number
     * @param login The SSH server (OpenFlow node) user login
     * @param password The SSH server (OpenFlow node) user password
     * @param timeout The SSH connection timeout
     */
    public AnSshConnector(String ip, int port, String login, String password, int timeout) {
        super();
        this.ip = ip;
        this.port = port;
        this.login = login;
        this.password = password;
        this.timeout = timeout;
    }

    /**
     * Opens an SSH connection to a given server.
     *
     * @throws JSchException if an error is due to the SSH connection...
     * @throws IOException
     */
    public void open() throws Exception {

        // Prepare session
        final JSch jsch = new JSch();
        session = jsch.getSession(login, ip, port);
        session.setPassword(password);
        session.setTimeout(timeout);
        session.setConfig(STRICT_HOSTKEY_CHECKIN_KEY,
                STRICT_HOSTKEY_CHECKIN_VALUE);

        // Start a connection
        LOG.debug("-- Try to connect to the server " + ip + ":" + port
                + " with user " + login);
        try {
            session.connect();
        } catch (Exception e) {
            LOG.info(e.getMessage());
            LOG.error("Exception caught during connect call to " + this.ip + " - socket not established...");

            throw new Exception("Exception caught during connect call to client " + this.ip);
        }

        LOG.debug("-- Connexion OK");
        LOG.debug("-- Open SSH channel");
        channel = session.openChannel(CHANNEL_TYPE);
    }

    /**
     * Executes a command on the remote SSH server using an existing SSH connector.
     * @param command The command the be executed remotely.
     * @return The output of the remote command execution.
     */
    public String executeCommand(String command) throws Exception
    {
        LOG.debug("-- Open SSH channel");
        channel = session.openChannel(CHANNEL_TYPE);

        StringBuilder outputBuffer = new StringBuilder();

        ((ChannelExec)channel).setCommand(command);
        InputStream commandOutput = channel.getInputStream();
        channel.connect();
        LOG.debug("-- Open SSH channel OK");

        int readByte = commandOutput.read();

        while(readByte != 0xffffffff)
        {
            outputBuffer.append((char)readByte);
            readByte = commandOutput.read();
        }

        channel.disconnect();

        return outputBuffer.toString();
    }

    public boolean isOpen(){

        if (session!=null)
            return session.isConnected();
        else return false;
    }

    /*
     * Closes the SSH connector by a clean channel and session disconnect().
     */
    public void close() throws Exception {
        // Close channel
        if(channel!=null)
            channel.disconnect();
        // Close session
        if(session!=null)
            session.disconnect();
    }
}