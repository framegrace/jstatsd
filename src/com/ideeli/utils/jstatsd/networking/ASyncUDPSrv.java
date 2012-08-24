package com.ideeli.utils.jstatsd.networking;

import com.ideeli.utils.jstatsd.Jstatsd;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ASyncUDPSrv {

    static int BUF_SZ = 1024;
    ExecutorService exec = Executors.newCachedThreadPool();
    Selector selector;
    int Port;
    UDPConsumer consumer;

    public ASyncUDPSrv(int Port, UDPConsumer consumer) {
        this.Port = Port;
        this.consumer = consumer;
    }

    class Con {

        ByteBuffer req;
        SocketAddress sa;

        public Con() {
            req = ByteBuffer.allocate(BUF_SZ);
        }
    }

    public void start() {
        Thread UDPServerT;
        UDPServerT = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    selector = Selector.open();
                    DatagramChannel channel = DatagramChannel.open();
                    InetSocketAddress isa = new InetSocketAddress(Port);
                    channel.socket().bind(isa);
                    channel.configureBlocking(false);
                    SelectionKey clientKey = channel.register(selector, SelectionKey.OP_READ);
                    clientKey.attach(new Con());
                    while (!exec.isShutdown()) {
                        try {
                            selector.select();
                            Iterator selectedKeys = selector.selectedKeys().iterator();
                            while (selectedKeys.hasNext()) {
                                try {
                                    SelectionKey key = (SelectionKey) selectedKeys.next();
                                    selectedKeys.remove();

                                    if (!key.isValid()) {
                                        continue;
                                    }

                                    if (key.isReadable()) {
                                        read(key);
                                        key.interestOps(SelectionKey.OP_READ);
                                    }
                                } catch (IOException e) {
                                    Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "glitch, continuing... " + (e.getMessage() != null ? e.getMessage() : ""));
                                }
                            }
                        } catch (IOException e) {
                            Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "glitch, continuing... " + (e.getMessage() != null ? e.getMessage() : ""));
                        }
                    }
                    Logger.getLogger(Jstatsd.class.getName()).log(Level.INFO, "Executor Stopped");
                    selector.close();
                    Logger.getLogger(Jstatsd.class.getName()).log(Level.INFO, "UDP Selector closed.");

                } catch (IOException e) {
                    Logger.getLogger(Jstatsd.class.getName()).log(Level.SEVERE, "Network Error " + (e.getMessage() != null ? e.getMessage() : ""));
                }
            }
        });
        Logger.getLogger(Jstatsd.class.getName()).log(Level.INFO, "Starting UDP\nUDP Port: " + Port);
        UDPServerT.start();
    }

    private void read(SelectionKey key) throws IOException {
        DatagramChannel chan = (DatagramChannel) key.channel();
        Con con = (Con) key.attachment();
        con.sa = chan.receive(con.req);
        con.req.flip();
        byte[] bytestr = new byte[con.req.limit()];
        con.req.get(bytestr);
        con.req.clear();
        produce(new String(bytestr, "UTF-8"));
    }

    private void produce(final String result) {
        Runnable requestHandler;
        requestHandler = new Runnable() {
            @Override
            public void run() {
                consumer.consumeUDP(result);
            }
        };
        exec.execute(requestHandler);
    }

    public void stop() {
        exec.shutdown();
        // Wake up selector to give make loop exit
        if (selector != null) {
            selector.wakeup();
        }
    }
}
