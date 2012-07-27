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

import java.io.IOException;
import java.net.Socket;
import java.util.*;

/**
 * Single connection from pool of connections. Every connection has associated
 * connected socket and expiration timeout.
 *
 * @see ConnectionPool
 *
 * @author Dawid Kurzyniec
 * @author Tomasz Janiak
 * @version 1.0
 */
public class Connection {

    static class Reaper {
        private final HashSet connections = new HashSet();
        private final Reap reap = new Reap();
        private volatile long sleepTime = Long.MAX_VALUE;
        private Thread thread;

        class Reap implements Runnable {
            public void run() {
                do {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {}
                } while (doReap());
            }

            private boolean doReap() {
                boolean result = true;
                long currentTime = System.currentTimeMillis();
                List connToClose = new ArrayList();
                synchronized (Reaper.this) {
                    for (Iterator iter = connections.iterator(); iter.hasNext();) {
                        Connection conn = (Connection)iter.next();
                        if (conn.getCloseTime() > currentTime) continue;
                        switch (conn.acquire()) {
                            case USED:
                            case CLOSED:
                                break;
                            case READY:
                                connToClose.add(conn);
                        }
                        iter.remove();
                    }
                    if (connections.isEmpty()) {
                        thread = null;
                        result = false;
                    }
                }
                for (Iterator iter = connToClose.iterator(); iter.hasNext();) {
                    Connection conn = (Connection)iter.next();
                    conn.close();
                }
                return result;
            }
        }

        void registerConnection(Connection conn, long closeDelay) {
            if (conn.isClosed()) {
                return;
            }
            synchronized (this) {
                connections.add(conn);
                if (closeDelay < sleepTime) {
                    sleepTime = closeDelay;
                }
                if (thread == null) {
                    thread = new Thread(reap, "Reaper");
                    thread.setDaemon(true);
                    thread.start();
                }
            }
        }
    }

    static final byte USED = -1, CLOSED = 0, READY = 1;
    static final Reaper reaper = new Reaper();
    final ConnectionPool pool;
    final Socket socket;
    long expires = -1;

    Connection(Socket socket, ConnectionPool pool) {
        this.socket = socket;
        this.pool = pool;
    }

    /**
     * Returns this connection to its pool. This method should be called only
     * if it is safe to reuse the connection in the future; otherwise, the
     * connection should be {@link close closed}.
     * Connection may be not reusable if it is left by the preceding
     * operation in an inconsistent state, e.g. if the client failed to read
     * all the data written by the server etc. The precise meaning of a
     * consistent state is defined by a higher-level application protocol.
     * <p>
     * After returning the connection to the pool, client should not use
     * or close the socket associated with that connection.
     */
    public void returnToPool() {
        release(System.currentTimeMillis() + pool.expirationTimeout);

        Connection.reaper.registerConnection(this, pool.expirationTimeout);
        pool.notifyConnectionStateChanged();
    }

    /**
     * Closes this connection.
     */
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {}
        pool.notifyConnectionStateChanged();
    }

    /**
     * Returns the socket associated with this connection.
     * @return the socket associated with this connection
     */
    public synchronized Socket getSocket() {
        return socket;
    }

    /**
     * If returns READY, means turned into USED, and must be released;
     * otherwise, no-op
     *
     * @return state prior to acuire
     */
    synchronized byte acquire() {
        if (socket.isClosed()) {
            return CLOSED;
        } else if (expires == -1) {
            return USED;
        }
        expires = -1;
        return READY;
    }

    synchronized void release(long closeTime) {
        if (socket.isClosed()) {
            // no op
            return;
        }
        else if (expires == -1) {
            expires = closeTime;
        }
        else {
            throw new IllegalStateException("Not currently used");
        }
    }

    synchronized long getCloseTime() {
        return expires;
    }

    synchronized boolean isClosed() {
        return socket.isClosed();
    }

    synchronized boolean isUsed() {
        return expires == -1;
    }
}