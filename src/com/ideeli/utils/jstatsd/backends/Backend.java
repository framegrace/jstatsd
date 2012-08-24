/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd.backends;

import com.ideeli.utils.jstatsd.Bucket;
import java.io.IOException;

/**
 *
 * @author marc
 */
public interface Backend {

    /**
     *
     * @param out
     * @param bucket
     */
    public void flush(java.io.OutputStream out, Bucket bucket) throws IOException;
    public void flush(Bucket bucket) throws IOException;
    public void send(String message) throws IOException;
    public void init();

    public TcpConfigData getConfig();
}
