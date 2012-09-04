/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd;

import com.ideeli.utils.jstatsd.backends.Backend;
import com.ideeli.utils.jstatsd.backends.GraphiteBackend;
import com.ideeli.utils.jstatsd.networking.NioTCPServer;
import com.ideeli.utils.jstatsd.networking.NioUDPServer;
import com.ideeli.utils.jstatsd.networking.TCPConsumer;
import com.ideeli.utils.jstatsd.networking.UDPConsumer;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Properties;
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
public class Jstatsd implements UDPConsumer, TCPConsumer {

    String BackendHost;
    int BackendPort;
    int ProxyPort;
    private int UDPPort;
    private long delay;
    // Bucket double buffer to avoid locking
    private Bucket[] bucket = new Bucket[2];
    int currentBucket = 0;
    final Object bucketLock = new Object();
    boolean debug = false;
    // Using only one bucket for now.
    // Interface prepared to allow multiple ones in the future
    private Backend backend;
    Timer scheduler = new Timer("Flush scheduler.");
    NioUDPServer udpsrvr;
    NioTCPServer tcpsrvr;

    public Jstatsd() {
        bucket[0] = new Bucket();
        bucket[1] = new Bucket();
    }

    public void setDebug(boolean debug) {
        if (debug) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.WARNING, "Debug enabled");
        }
        this.debug = debug;
    }

    public void init() throws UnknownHostException, IOException {
        validateAndLoadProperties();
        initNeworking();
        initScheduler();
    }

    void initNeworking() throws UnknownHostException, IOException {
        // This may look stupid now, but will make easier
        // to have more than one backend in the future.
        backend = new GraphiteBackend(BackendHost, BackendPort);
        udpsrvr = new NioUDPServer(UDPPort, this);
        tcpsrvr = new NioTCPServer(ProxyPort, this);
        backend.init();
        tcpsrvr.init();
        udpsrvr.init();
    }

    void initScheduler() {
        scheduler.schedule(new TimerTask() {
            @Override
            public void run() {
                int oldBucket = currentBucket;
                // Make entries to write on the other Bucket
                synchronized (bucketLock) {
                    currentBucket = (currentBucket + 1) % 2;
                }
                if (debug) {
                    System.out.println("Flushing buket " + oldBucket);
                    try {
                        backend.flush(System.out, bucket[oldBucket]);
                    } catch (IOException ex) {
                        Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return;
                }
                try {
                    backend.flush(bucket[oldBucket]);
                } catch (IOException ex) {
                    Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Backend disconnected using secondary storage.");
                    try {
                        backend.flush(System.out, bucket[oldBucket]);
                    } catch (IOException ex1) {
                        Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                }
            }
        }, delay, delay);
    }

    public void shutDown() {
    }
    Pattern p = Pattern.compile("^([^:]+):(\\d+)\\|(g|c|ms)$");

    @Override
    public void consumeTCP(int port, String data) {
        if (debug) {
            System.out.println("Proxy received: " + data);
        }
        try {
            backend.send(data);
        } catch (IOException ex) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Backend disconnected using secondary storage.");
            System.out.println(data);
        }
    }

    @Override
    public void consumeUDP(int port, String data) {
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
        synchronized (bucketLock) {
            bucketToUse = bucket[currentBucket];
        }
        bucketToUse.add(m.group(1), value, m.group(3));
    }

    void validateAndLoadProperties() {
        loadProperties();
        try {
            UDPPort = new Integer(System.getProperty("jstatsd.UdpPort"));
        } catch (NumberFormatException e) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Invalid UdpPort: {0}", System.getProperty("jstatsd.UdpPort"));
            System.exit(1);
        }

        BackendHost = System.getProperty("jstatsd.GraphiteHost", "localhost");

        try {
            BackendPort = new Integer(System.getProperty("jstatsd.GraphitePort"));
        } catch (NumberFormatException e) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Invalid GraphitePort: {0}", System.getProperty("jstatsd.GraphitePort"));
            System.exit(1);
        }
        try {
            delay = new Integer(System.getProperty("jstatsd.FlushInterval", "10"));
            delay *= 1000;
        } catch (NumberFormatException e) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Invalid FlushInterval: {0}", System.getProperty("jstatsd.FlushInterval"));
            System.exit(1);
        }
        try {
            ProxyPort = new Integer(System.getProperty("jstatsd.GraphiteProxyPort"));
        } catch (NumberFormatException e) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Invalid GraphiteProxyPort: {0}", System.getProperty("jstatsd.GraphitePort"));
            System.exit(1);
        }
        debug = System.getProperty("jstatsd.Debug")==null?false:System.getProperty("jstatsd.Debug").equals("Yes");
        setDebug(debug);
        Logger.getLogger(Jstatsd.class.getName()).log(Level.INFO, "UDPPort={0}", UDPPort);
        Logger.getLogger(Jstatsd.class.getName()).log(Level.INFO, "BackendHost={0}", BackendHost);
        Logger.getLogger(Jstatsd.class.getName()).log(Level.INFO, "BackendPort={0}", BackendPort);
        Logger.getLogger(Jstatsd.class.getName()).log(Level.INFO, "delay={0}", delay);
        Logger.getLogger(Jstatsd.class.getName()).log(Level.INFO, "ProxyPort={0}", ProxyPort);
        Logger.getLogger(Jstatsd.class.getName()).log(Level.INFO, "Debug={0}", debug);
        if (ProxyPort==BackendPort && (BackendHost.equals("localhost")||BackendHost.equals("127.0.0.1"))) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Proxy == Backend. This is sooooo wrong");
            System.exit(1);
        }
    }

    void loadProperties() {
        InputStream propFileStream = null;
        String ConfigFile;
        ConfigFile = System.getProperty("jstatsd.config");
        //Load the full current properties set
        Properties properties = new Properties(System.getProperties());
        // Store app properties, and remove from the default as they must take precedence.
        Properties appProps = new Properties();
        for (Enumeration e = properties.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            if (name.startsWith("jstatsd")) {
                appProps.setProperty(name, properties.getProperty(name));
                properties.remove(name);
            }
        }
        // Load as a resource First. This will set the defaults.
        propFileStream = getClass().getClassLoader().getResourceAsStream("jstatsd.conf");
        if (propFileStream == null) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Default Properties Missing: ");
            System.exit(1);
        }
        try {
            // Load default properties
            properties.load(propFileStream);
        } catch (IOException ex) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Default Properties Missing: ");
            System.exit(1);
        }

        // Then try the supplied config file a file
        if (ConfigFile != null) {
            try {
                propFileStream = new FileInputStream(ConfigFile);
                properties.load(propFileStream);
            } catch (IOException ex) {
                Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        for (Enumeration e = appProps.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            properties.setProperty(name, appProps.getProperty(name));
        }
        // set the system properties
        System.setProperties(properties);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws UnknownHostException, IOException {
        // TODO code application logic here
        int Interval = 0;
        int UdpPort = 0;
        String GraphiteHost;
        int GraphitePort = 0;
        boolean debug;

        final Jstatsd app = new Jstatsd();
        app.init();
//        final Thread mainThread = Thread.currentThread();
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            @Override
//            public void run() {
//                app.shutDown();
//            }
//        });
    }
}
