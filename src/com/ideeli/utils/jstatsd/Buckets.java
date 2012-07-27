/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author marc
 */
public class Buckets {
    HashMap<String, LinkedList<Long>> TimeHashes=new HashMap<String, LinkedList<Long>>();
    HashMap<String, Long> CountHashes=new HashMap< String, Long>();
    HashMap<String, LinkedList<Long>> GaugeHashes=new HashMap<String, LinkedList<Long>>();
    
    public enum Type { ms, c, g };
    
    long lastFlush = System.currentTimeMillis();

    public void add(String name, long value, String type) {
        add(name, value, Type.valueOf(type));
    }
    
    public synchronized void add(String name, long value, Type type) {
        HashMap<String, LinkedList<Long>> currentHash;
        switch (type) {
            case ms:
            case g:
                if (type == Type.ms) {
                    currentHash = TimeHashes;
                } else {
                    currentHash = GaugeHashes;
                }
                LinkedList<Long> lst = currentHash.get(name);
                if (lst == null) {
                    lst = new LinkedList<Long>();
                    currentHash.put(name, lst);
                }
                lst.add(value);
                Logger.getLogger(Jstatsd.class.getName()).log(Level.FINEST, "Type:"+type+" #bukets:"+currentHash.size()+" Bucket \""+name+"\" size:"+lst.size());
                break;
            case c:
                if (!CountHashes.containsKey(name)) {
                    CountHashes.put(name, value);
                } else {
                    CountHashes.put(name, CountHashes.get(name).longValue() + value);
                }
                Logger.getLogger(Jstatsd.class.getName()).log(Level.FINEST, "Type:"+type+" #bukets:"+CountHashes.size()+" Bucket \""+name+"\" value:"+CountHashes.get(name).longValue());
                break;
            default:
        }
    }
    public synchronized void flush(java.io.OutputStream out) throws IOException {
        // Cleanup Hashes
        long currentFlush=System.currentTimeMillis();
        Logger.getLogger(Jstatsd.class.getName()).log(Level.FINE, "Flushing... last "+(currentFlush-lastFlush)+" ms.");
        double c_interval=(currentFlush-lastFlush)/1000.0;
        OutputStreamWriter osm=new OutputStreamWriter(out);
        for(Entry<String,Long> set: CountHashes.entrySet()) {
            double r=set.getValue();
            double rs=c_interval/r;
            osm.write("stats."+set.getKey()+" "+rs+" "+currentFlush+'\n');
            osm.write("stats_counts."+set.getKey()+" "+r+" "+currentFlush+'\n');
        }
        osm.flush();
        for(Entry<String,LinkedList<Long>> set: GaugeHashes.entrySet()) {
            LinkedList<Long> lst=set.getValue();
            float d=0.0f;
            for(Long l:lst) {
                d+=l.longValue();
            }
            long r=(long)(d/(float)lst.size());
            osm.write("stats.gauges."+set.getKey()+" "+r+" "+currentFlush+'\n');
        }
        for(Entry<String,LinkedList<Long>> set: TimeHashes.entrySet()) {
            LinkedList<Long> lst=set.getValue();
            String key=set.getKey();
            int count=lst.size();
            Collections.sort(lst, null);
            long min=lst.get(0);
            long max=lst.get(count-1);
            
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
                int thresholdIndex = (int)Math.round(((100.0 - pct) / 100.0) * (double)count);
                int numInThreshold = count - thresholdIndex;

                maxAtThreshold = lst.get(numInThreshold - 1);
                sum = cumulativeList.get(numInThreshold - 1);
                mean = sum / numInThreshold;
            }
            String clean_pct = "" + (double)pct;
            clean_pct=clean_pct.replace('.', '_');
            osm.write("stats.timers." + key + ".mean_"  + clean_pct + " " + mean+ " " + currentFlush + "\n");
            osm.write("stats.timers." + key + ".upper_" + clean_pct + " " + maxAtThreshold + " " + currentFlush + "\n");
            osm.write("stats.timers." + key + ".sum_" + clean_pct + " " + sum + " " + currentFlush + "\n");
            
            sum = cumulativeList.get(count-1);
            mean = sum / count;

            long sumOfDiffs = 0;
            for (int i = 0; i < count; i++) {
                sumOfDiffs += (lst.get(i) - mean) * (lst.get(i) - mean);
            }
            double stddev = Math.sqrt(sumOfDiffs / count);

            osm.write("stats.timers." + key + ".std " + stddev  + " " + currentFlush + "\n");
            osm.write("stats.timers." + key + ".upper " + max   + " " + currentFlush + "\n");
            osm.write("stats.timers." + key + ".lower " + min   + " " + currentFlush + "\n");
            osm.write("stats.timers." + key + ".count " + count + " " + currentFlush + "\n");
            osm.write("stats.timers." + key + ".sum " + sum  + " " + currentFlush + "\n");
            osm.write("stats.timers." + key + ".mean " + mean + " " + currentFlush + "\n");
        }
        
        osm.flush();
        TimeHashes=new HashMap<String, LinkedList<Long>>();
        CountHashes=new HashMap< String, Long>();
        GaugeHashes=new HashMap<String, LinkedList<Long>>();
        lastFlush=currentFlush;
    }
}
