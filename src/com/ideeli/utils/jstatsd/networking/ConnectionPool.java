/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is the Emory Utilities.
 *
 * The Initial Developer of the Original Code is
 * The Distributed Computing Laboratory, Emory University.
 * Portions created by the Initial Developer are Copyright (C) 2002
 * the Initial Developer. All Rights Reserved.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package com.ideeli.utils.jstatsd.networking;

import com.ideeli.utils.jstatsd.Jstatsd;
import java.io.IOException;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages a pool of socket connections to a single network endpoint. Pooling
 * enables reusing connections for multiple, unrelated data transfers, and it
 * can be used to implement certain connection-based protocols like HTTP 1.1.
 * Additionally, pooling can aid in controlling network load - limiting the
 * maximum pool size causes excessive connection requests to be enqueued at the
 * client side. <p> The endpoint is represented by a host name and a port
 * number, as well as by an optional client socket factory, specified at the
 * construction time. Client requests connections, use them, then return them to
 * the pool. Clients should not close the socket associated with the connection
 * or use the socket after returning connection to the pool. Upon a request for
 * connection, the pool first tries to return a pre-existing idle one, creating
 * a new connection only if none is available. Request may block if pool size
 * limit is reached and all connections are in use. After being returned to the
 * pool, if connection idles for longer than its expiration timeout, it is
 * closed. <p> Example:
 *
 * <pre>
 * ConnectionPool pool = new ConnectionPool(host, port);
 * ...
 * Connection conn = pool.getConnection();
 * Socket socket = conn.getSocket();
 * try {
 *     socket.getOutputStream().write(0x00);
 *     ...
 *     conn.returnToPool();
 * }
 * catch (IOException e) {
 *     conn.close();
 * }
 * </pre>
 *
 * @author Dawid Kurzyniec
 * @author Tomasz Janiak
 * @version 1.0
 */
public class ConnectionPool {

    static final long DEFAULT_EXPIRATION_TIMEOUT = 15000;
    static final int DEFAULT_CAPACITY = 10;
    private final HashSet connections = new HashSet();
    private final String hostName;
    private final int port;
    private final RMIClientSocketFactory socketFactory;
    final long expirationTimeout;
    private final int capacity;

    /**
     * Creates a connection pool for a specified endpoint, using a default
     * TCP/IP socket factory, a default expiration timeout of 15 s, and a
     * default capacity of 10 connections.
     *
     * @param hostName remote host name
     * @param port remote port
     * @param socketFactory socket factory to use when creating new connections
     */
    public ConnectionPool(String hostName, int port) {
        this(hostName, port, null);
    }

    /**
     * Creates a connection pool for a specified endpoint, using specified
     * socket factory and a default expiration timeout of 15 s and a default
     * capacity of 10 connections.
     *
     * @param hostName remote host name
     * @param port remote port
     * @param socketFactory socket factory to use when creating new connections
     */
    public ConnectionPool(String hostName, int port,
            RMIClientSocketFactory socketFactory) {
        this(hostName, port, socketFactory,
                DEFAULT_EXPIRATION_TIMEOUT, DEFAULT_CAPACITY);
    }

    /**
     * Creates a connection pool for a specified endpoint, using specified
     * expiration timeout and capacity and a default TCP/IP socket factory.
     *
     * @param hostName remote host name
     * @param port remote port
     * @param expirationTimeout maximum connection idle time
     * @param capacity maximum number of active connections
     */
    public ConnectionPool(String hostName, int port,
            long expirationTimeout, int capacity) {
        this(hostName, port, null, expirationTimeout, capacity);
    }

    /**
     * Creates a connection pool for a specified endpoint, using specified
     * socket factory, expiration timeout, and capacity.
     *
     * @param hostName remote host name
     * @param port remote port
     * @param socketFactory socket factory to use when creating new connections
     * @param expirationTimeout maximum connection idle time
     * @param capacity maximum number of active connections
     */
    public ConnectionPool(
            String hostName,
            int port,
            RMIClientSocketFactory socketFactory,
            long expirationTimeout,
            int capacity) {
        Logger.getLogger(Jstatsd.class.getName()).log(Level.INFO, "Starting Graphite connection pool\nHostname: " + hostName + "\nPort: " + port);
        if (capacity <= 0 || expirationTimeout < 0) {
            throw new IllegalArgumentException();
        }
        this.hostName = hostName;
        this.port = port;
        this.socketFactory = socketFactory;
        this.expirationTimeout = expirationTimeout;
        this.capacity = capacity;
    }

    private Connection findConnection() {
        Connection result = null;
        for (Iterator iter = connections.iterator(); iter.hasNext();) {
            Connection conn = (Connection) iter.next();
            byte connStatus = conn.acquire();
            if (connStatus == Connection.READY) {
                result = conn;
                break;
            } else if (connStatus == Connection.CLOSED) {
                iter.remove();
            }
        }
        return result;
    }

    synchronized void notifyConnectionStateChanged() {
        notify();
    }

    /**
     * Requests a connection from the pool. If an existing idle connection is
     * found, it is returned. Otherwise, if pool capacity has not been reached,
     * new connection is created. Otherwise, the operation blocks until a
     * connection is available.
     *
     * @return a connection
     * @throws IOException if I/O error occurs
     * @throws InterruptedException if interrupted while waiting for a
     * connection
     */
    public synchronized Connection getConnection()
            throws IOException, InterruptedException {
        // make sure that connection pooling does not circumvent security
        // policy by allowing unauthorized clients to use network sockets
        checkConnectPermission();

        Connection conn = findConnection();
        while (conn == null) {
            if (connections.size() == capacity) {
                wait();
                conn = findConnection();
            } else {
                Socket socket = socketFactory != null
                        ? socketFactory.createSocket(hostName, port)
                        : new Socket(hostName, port);
                conn = new Connection(socket, this);
                connections.add(conn);
            }
        }
        return conn;
    }

    private void checkConnectPermission() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkConnect(hostName, port);
        }
    }
}
