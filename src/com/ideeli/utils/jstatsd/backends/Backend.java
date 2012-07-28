/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd.backends;

import com.ideeli.utils.jstatsd.Bucket;

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
    public void flush(java.io.OutputStream out, Bucket bucket);

    public TcpConfigData getConfig();
}
