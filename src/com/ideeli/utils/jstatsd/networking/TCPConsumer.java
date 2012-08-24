/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd.networking;

/**
 *
 * @author marc
 */
public interface TCPConsumer {

    public void consumeTCP(int port,String data);
}
