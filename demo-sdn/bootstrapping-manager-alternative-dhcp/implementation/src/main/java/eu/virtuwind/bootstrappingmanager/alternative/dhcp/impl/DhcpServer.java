package eu.virtuwind.bootstrappingmanager.alternative.dhcp.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.apache.directory.server.dhcp.io.DhcpInterfaceManager;
import org.apache.directory.server.dhcp.netty.DhcpHandler;
import org.apache.directory.server.dhcp.service.DhcpService;

import java.io.IOException;

/**
 * DHCPv4 netty-based server
 */
public class DhcpServer extends DhcpInterfaceManager {
    private final DhcpService service;
    private final int port;
    private Channel channel;

    /**
     * DhcpServer constructor which takes the port numbers and the DhcpService definition as.s parameter
     * @param service An extension of LeaseManagerDhcpService that specifies the default behavior on
     *                observation of specific client messages (e.g. DHCPDISCOVER, DHCPREQUEST, DHCPRELEASE etc.)
     * @param port The port number on which the DHCP server will bind (default: 67).
     */
    public DhcpServer(DhcpService service, int port) {
        this.service = service;
        this.port = port;
    }

    /**
     * Executes the DHCP server on a given port with a given service handler (DhcpService).
     * @param eventloopGroup Netty.io worker thread group dependency.
     * @throws IOException
     * @throws InterruptedException
     */
    public void start(EventLoopGroup eventloopGroup) throws IOException, InterruptedException {
        super.start();
        Bootstrap b = new Bootstrap();
        b.group(eventloopGroup);
        b.channel(NioDatagramChannel.class);
        b.option(ChannelOption.SO_BROADCAST, true);
        b.handler(new DhcpHandler(service, this));
        channel = b.bind(port).sync().channel();
    }

    /**
     * Stops the currently executing DHCP server.
     * @throws IOException
     * @throws InterruptedException
     */
    public void stop() throws IOException, InterruptedException {
        if (channel != null) {
            channel.close().sync();
            channel = null;
        }
        super.stop();
    }
}
