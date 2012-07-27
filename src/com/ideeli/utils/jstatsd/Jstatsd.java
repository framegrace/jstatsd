/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd;

import com.ideeli.utils.jstatsd.networking.ASyncUDPSrv;
import com.ideeli.utils.jstatsd.networking.Connection;
import com.ideeli.utils.jstatsd.networking.ConnectionPool;
import com.ideeli.utils.jstatsd.networking.UDPConsumer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
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
    
    private Buckets bucketStorage=new Buckets();
    Timer scheduler=new Timer("Flush scheduler.");
    
    ASyncUDPSrv srvr;
    Pattern p = Pattern.compile("^([^:]+):(\\d+)\\|(g|c|ms)$");
    
    public Jstatsd(int UDPPort,String TCPHost,int TCPPort,int seconds) {
        this.BackendHost=TCPHost;
        this.BakendPort=TCPPort;
        this.UDPPort=UDPPort;
        this.delay=seconds*1000;
    }
    
    public void initNeworking() {
        pool = new ConnectionPool(BackendHost, BakendPort);
        srvr = new ASyncUDPSrv(UDPPort, this);
        srvr.start();
        scheduler.schedule(new TimerTask() {
            public void run() {
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
                    bucketStorage.flush(c.getSocket().getOutputStream());
                    bucketStorage.flush(System.out);
                } catch (IOException ex) {
                    Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    c.returnToPool();
                }
            }
        }, delay, delay);
    }
    public void shutDown() {
       if (srvr!=null) {
            srvr.stop();
        }
    }
    
    public void consume(String data) {
        long value;
        Matcher m = p.matcher(data);
        try {
            boolean b = m.matches();
            value=new Long(m.group(2)).longValue();
        } catch(IllegalStateException ex) {
             Logger.getLogger(Jstatsd.class.getName()).log(Level.WARNING, "Malformed input: "+data, ex);
            return;
        } catch(NumberFormatException ex) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.WARNING, "Number format exception: "+data, ex);
            return;
        }
        bucketStorage.add(m.group(1),value,m.group(3));
    }
    

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        int Interval=0;
        int UdpPort=0;
        String GraphiteHost;
        int GraphitePort=0;
        
        try {
            UdpPort=new Integer(System.getProperty("jstatsd.UdpPort", "8025"));
        } catch (NumberFormatException e) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE,"Invalid UdpPort: "+System.getProperty("jstatsd.UdpPort"));
            System.exit(1);
        }
        
        GraphiteHost=System.getProperty("jstatsd.GraphiteHost", "localhost");
        
        try {
            GraphitePort=new Integer(System.getProperty("jstatsd.GraphitePort", "2003"));
        } catch (NumberFormatException e) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE,"Invalid GraphitePort: "+System.getProperty("jstatsd.GraphitePort"));
            System.exit(1);
        }
        try {
            Interval=new Integer(System.getProperty("jstatsd.FlushInterval", "10"));
        } catch (NumberFormatException e) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE,"Invalid FlushInterval: "+System.getProperty("jstatsd.FlushInterval"));
            System.exit(1);
        }
        
        final Jstatsd app=new Jstatsd(UdpPort,GraphiteHost,GraphitePort,Interval);
        app.initNeworking();
        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                app.shutDown();
            }
        });
    }
}
