/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd;

import com.ideeli.utils.jstatsd.backends.Backend;
import com.ideeli.utils.jstatsd.backends.GraphiteBackend;
import com.ideeli.utils.jstatsd.networking.ASyncUDPSrv;
import com.ideeli.utils.jstatsd.networking.Connection;
import com.ideeli.utils.jstatsd.networking.ConnectionPool;
import com.ideeli.utils.jstatsd.networking.UDPConsumer;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author marc
 */
public class Jstatsd implements UDPConsumer {

    ConnectionPool pool;
    String BackendHost;
    int BakendPort;
    private int UDPPort;
    private long delay;
    // Bucket double buffer to avoid locking
    private Bucket[] bucket = new Bucket[2];
    int currentBucket=0;
    Object bucketLock=new Object();
    
    boolean debug = false;
    // Using only one bucket for now.
    // Interface prepared to allow multiple ones in the future
    private Backend backend;
    Timer scheduler = new Timer("Flush scheduler.");
    ASyncUDPSrv srvr;

    public Jstatsd(int UDPPort, String TCPHost, int TCPPort, int seconds) {
        this.BackendHost = TCPHost;
        this.BakendPort = TCPPort;
        this.UDPPort = UDPPort;
        this.delay = seconds * 1000;
        bucket[0] = new Bucket();
        bucket[1] = new Bucket();
    }

    public void setDebug(boolean debug) {
        if (debug) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.WARNING, "Debug enabled");
        }
        this.debug = debug;
    }

    public void init() {
        initNeworking();
        initScheduler();
    }

    void initNeworking() {
        // This may look stupid now, but will make easier
        // to have more than one backend in the future.
        backend = new GraphiteBackend(BackendHost, BakendPort);
        pool = new ConnectionPool(backend.getConfig().getHost(), backend.getConfig().getPort());
        srvr = new ASyncUDPSrv(UDPPort, this);
        srvr.start();
    }

    void initScheduler() {
        scheduler.schedule(new TimerTask() {
            @Override
            public void run() {
                int oldBucket=currentBucket;
                // Make entries to write on the other Bucket
                synchronized(bucketLock) {
                    currentBucket=(currentBucket+1)%2;
                }
                if (debug) {
                    System.out.println("Flushing buket "+oldBucket);
                    backend.flush(System.out, bucket[oldBucket]);
                    return;
                }
                Connection c;
                try {
                    c = pool.getConnection();
                } catch (IOException ex) {
                    Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                } catch (InterruptedException ex) {
                    Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                }
                try {
                    System.out.println("Flushing buket "+oldBucket);
                    backend.flush(c.getSocket().getOutputStream(),bucket[oldBucket]);
                } catch (IOException ex) {
                    Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    c.returnToPool();
                }
            }
        }, delay, delay);
    }

    public void shutDown() {
        if (srvr != null) {
            srvr.stop();
        }
    }
    Pattern p = Pattern.compile("^([^:]+):(\\d+)\\|(g|c|ms)$");

    @Override
    public void consume(String data) {
        long value;
        Matcher m = p.matcher(data);
        try {
            boolean b = m.matches();
            value = new Long(m.group(2)).longValue();
        } catch (IllegalStateException ex) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.WARNING, "Malformed input: " + data, ex);
            return;
        } catch (NumberFormatException ex) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.WARNING, "Number format exception: " + data, ex);
            return;
        }
        Bucket bucketToUse;
        synchronized(bucketLock) {
            bucketToUse=bucket[currentBucket];
        }
        System.out.println("Adding to bucket "+currentBucket);
        bucketToUse.add(m.group(1), value, m.group(3));
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        int Interval = 0;
        int UdpPort = 0;
        String GraphiteHost;
        int GraphitePort = 0;
        boolean debug;

        try {
            UdpPort = new Integer(System.getProperty("jstatsd.UdpPort", "8025"));
        } catch (NumberFormatException e) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Invalid UdpPort: " + System.getProperty("jstatsd.UdpPort"));
            System.exit(1);
        }

        GraphiteHost = System.getProperty("jstatsd.GraphiteHost", "localhost");

        try {
            GraphitePort = new Integer(System.getProperty("jstatsd.GraphitePort", "2003"));
        } catch (NumberFormatException e) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Invalid GraphitePort: " + System.getProperty("jstatsd.GraphitePort"));
            System.exit(1);
        }
        try {
            Interval = new Integer(System.getProperty("jstatsd.FlushInterval", "10"));
        } catch (NumberFormatException e) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Invalid FlushInterval: " + System.getProperty("jstatsd.FlushInterval"));
            System.exit(1);
        }
        debug = !(System.getProperty("jstatsd.Debug") == null);

        final Jstatsd app = new Jstatsd(UdpPort, GraphiteHost, GraphitePort, Interval);
        app.setDebug(debug);
        app.init();
        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                app.shutDown();
            }
        });
    }
}
