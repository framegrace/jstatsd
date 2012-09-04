/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd.networking;

/**
 *
 * @author marc
 */
public interface UDPConsumer {

    public void consumeUDP(int port,String data);
}
