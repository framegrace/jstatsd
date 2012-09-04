/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd.backends;

import com.ideeli.utils.jstatsd.Bucket;
import com.ideeli.utils.jstatsd.Jstatsd;
import com.ideeli.utils.jstatsd.networking.Connection;
import com.ideeli.utils.jstatsd.networking.ConnectionPool;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author marc
 */
public class GraphiteBackend implements Backend {

    TcpConfigData config;
    ConnectionPool pool;

    public GraphiteBackend(String host, int port) {
        config = new TcpConfigData(host, port);
    }

    /**
     *
     */
    @Override
    public void init() {
        pool = new ConnectionPool(getConfig().getHost(), getConfig().getPort());    
    }
    
    @Override
    public TcpConfigData getConfig() {
        return config;
    }
    
    @Override
    public void send(String message) throws IOException {
        Connection c;
        try {
            c = pool.getConnection();
        } catch (InterruptedException ex) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        System.out.println("Sendind data");
        try {
            OutputStreamWriter osm=new OutputStreamWriter(c.getSocket().getOutputStream());
            osm.write(message);
            osm.flush();
        } catch (IOException ex) {
            Logger.getLogger(GraphiteBackend.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            c.returnToPool();
        }
    }
    
    @Override
    public synchronized void flush(Bucket bucket) throws IOException  {
        Connection c;
        try {
            c = pool.getConnection();
        }  catch (InterruptedException ex) {
            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        System.out.println("Flushing buket " + bucket);
        try {
            flush(c.getSocket().getOutputStream(), bucket);
        } catch (IOException ex) {
            Logger.getLogger(GraphiteBackend.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            c.returnToPool();
        }
    }
    
    @Override
    public synchronized void flush(java.io.OutputStream out, Bucket bucket) {
        // Cleanup Hashes
        try {
            HashMap<String, Long> CountHashes = bucket.getCountHashes();
            HashMap<String, LinkedList<Long>> TimeHashes = bucket.getTimeHashes();
            HashMap<String, LinkedList<Long>> GaugeHashes = bucket.getGaugeHashes();

            long currentFlush = System.currentTimeMillis();
            long lastFlush = bucket.getLastFlush();

            Logger.getLogger(Jstatsd.class.getName()).log(Level.FINE, "Flushing... last {0} ms.", (currentFlush - lastFlush));
            double c_interval = (currentFlush - lastFlush) / 1000.0;
            OutputStreamWriter osm = new OutputStreamWriter(out);
            for (Map.Entry<String, Long> set : CountHashes.entrySet()) {
                double r = set.getValue();
                double rs = c_interval / r;
                osm.write("stats." + set.getKey() + " " + rs + " " + currentFlush + '\n');
                osm.write("stats_counts." + set.getKey() + " " + r + " " + currentFlush + '\n');
            }
            osm.flush();
            for (Map.Entry<String, LinkedList<Long>> set : GaugeHashes.entrySet()) {
                LinkedList<Long> lst = set.getValue();
                float d = 0.0f;
                for (Long l : lst) {
                    d += l.longValue();
                }
                long r = (long) (d / (float) lst.size());
                osm.write("stats.gauges." + set.getKey() + " " + r + " " + currentFlush + '\n');
            }
            for (Map.Entry<String, LinkedList<Long>> set : TimeHashes.entrySet()) {
                LinkedList<Long> lst = set.getValue();
                String key = set.getKey();
                int count = lst.size();
                Collections.sort(lst, null);
                long min = lst.get(0);
                long max = lst.get(count - 1);

                LinkedList<Long> cumulativeList = new LinkedList<Long>();
                cumulativeList.add(min);
                for (int i = 1; i < count; i++) {
                    cumulativeList.add(lst.get(i) + cumulativeList.get(i - 1));
                }

                long sum = min;
                long mean = min;
                long maxAtThreshold = max;

                double pct = 90.0;

                if (count > 1) {
                    int thresholdIndex = (int) Math.round(((100.0 - pct) / 100.0) * (double) count);
                    int numInThreshold = count - thresholdIndex;

                    maxAtThreshold = lst.get(numInThreshold - 1);
                    sum = cumulativeList.get(numInThreshold - 1);
                    mean = sum / numInThreshold;
                }
                String clean_pct = "" + (double) pct;
                clean_pct = clean_pct.replace('.', '_');
                osm.write("stats.timers." + key + ".mean_" + clean_pct + " " + mean + " " + currentFlush + "\n");
                osm.write("stats.timers." + key + ".upper_" + clean_pct + " " + maxAtThreshold + " " + currentFlush + "\n");
                osm.write("stats.timers." + key + ".sum_" + clean_pct + " " + sum + " " + currentFlush + "\n");

                sum = cumulativeList.get(count - 1);
                mean = sum / count;

                long sumOfDiffs = 0;
                for (int i = 0; i < count; i++) {
                    sumOfDiffs += (lst.get(i) - mean) * (lst.get(i) - mean);
                }
                double stddev = Math.sqrt(sumOfDiffs / count);

                osm.write("stats.timers." + key + ".std " + stddev + " " + currentFlush + "\n");
                osm.write("stats.timers." + key + ".upper " + max + " " + currentFlush + "\n");
                osm.write("stats.timers." + key + ".lower " + min + " " + currentFlush + "\n");
                osm.write("stats.timers." + key + ".count " + count + " " + currentFlush + "\n");
                osm.write("stats.timers." + key + ".sum " + sum + " " + currentFlush + "\n");
                osm.write("stats.timers." + key + ".mean " + mean + " " + currentFlush + "\n");
            }
            osm.flush();
            bucket.cleanup();
            bucket.setFlushTime(currentFlush);
        } catch (IOException ex) {
            Logger.getLogger(GraphiteBackend.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
