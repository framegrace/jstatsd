/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd.backends;

/**
 *
 * @author marc
 */
public class TcpConfigData {

    String host;
    int port;

    public TcpConfigData(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
