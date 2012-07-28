/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author marc
 */
public class Bucket {
    HashMap<String, LinkedList<Long>> TimeHashes=new HashMap<String, LinkedList<Long>>();
    HashMap<String, Long> CountHashes=new HashMap< String, Long>();
    HashMap<String, LinkedList<Long>> GaugeHashes=new HashMap<String, LinkedList<Long>>();

    public long getLastFlush() {
        return lastFlush;
    }
    public HashMap<String, LinkedList<Long>> getTimeHashes() {
        return TimeHashes;
    }
    
    public HashMap<String, LinkedList<Long>> getGaugeHashes() {
        return GaugeHashes;
    }

    public HashMap<String, Long> getCountHashes() {
        return CountHashes;
    }    
    
    public void cleanup() {
        TimeHashes = new HashMap<String, LinkedList<Long>>();
        CountHashes = new HashMap< String, Long>();
        GaugeHashes = new HashMap<String, LinkedList<Long>>();
    }

    public void setFlushTime(long currentFlush) {
        lastFlush=currentFlush;
    }
    
    public enum Type { ms, c, g };
    
    long lastFlush = System.currentTimeMillis();

    public void add(String name, long value, String type) {
        add(name, value, Type.valueOf(type));
    }
    
    public void add(String name, long value, Type type) {
        HashMap<String, LinkedList<Long>> currentHash;
        switch (type) {
            case ms:
            case g:
                if (type == Type.ms) {
                    currentHash = TimeHashes;
                } else {
                    currentHash = GaugeHashes;
                }
                LinkedList<Long> lst;
                synchronized (currentHash) {
                    lst = currentHash.get(name);
                    if (lst == null) {
                        lst = new LinkedList<Long>();
                        currentHash.put(name, lst);
                    }
                }
                lst.add(value);
                Logger.getLogger(Jstatsd.class.getName()).log(Level.FINEST, "Type:"+type+" #bukets:"+currentHash.size()+" Bucket \""+name+"\" size:"+lst.size());
                break;
            case c:
                synchronized (CountHashes) {
                    if (!CountHashes.containsKey(name)) {
                        CountHashes.put(name, value);
                    } else {
                        CountHashes.put(name, CountHashes.get(name).longValue() + value);
                    }
                }
                Logger.getLogger(Jstatsd.class.getName()).log(Level.FINEST, "Type:"+type+" #bukets:"+CountHashes.size()+" Bucket \""+name+"\" value:"+CountHashes.get(name).longValue());
                break;
            default:
        }
    }
}
