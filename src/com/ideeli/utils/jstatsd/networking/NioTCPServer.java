/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd.networking;

import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import org.javagamesfactory.nioservers.StringBasedServer;

/**
 *
 * @author marc
 */
public class NioTCPServer extends StringBasedServer {

    TCPConsumer consumer;
    /**
     * Delegates directly to super-constructor with same signature
     *
     * @param port
     * @throws UnknownHostException
     */
    public NioTCPServer(int port,TCPConsumer consumer) throws UnknownHostException {
        super(port);
        this.consumer=consumer;
    }

    @Override
    protected void processStringMessage(String message, SelectionKey key) throws ClosedChannelException {
        consumer.consumeTCP(getPort(),message);
    }

    @Override
    protected void keyCancelled(SelectionKey key) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void postSelect(long millisecondsSinceLastStarted) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
