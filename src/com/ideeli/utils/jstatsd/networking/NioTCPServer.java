/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ideeli.utils.jstatsd.networking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;



/**
 *
 * @author marc
 */
public class NioTCPServer extends IoHandlerAdapter {

    TCPConsumer consumer;
    int port;

    public NioTCPServer(int port,TCPConsumer consumer) {
        this.consumer=consumer;
        this.port=port;
    }

    public void init() throws IOException {
        IoAcceptor acceptor = new NioSocketAcceptor();

        acceptor.getFilterChain().addLast( "codec", new ProtocolCodecFilter( new TextLineCodecFactory( Charset.forName( "UTF-8" ))));

        acceptor.setHandler(  this );

        acceptor.getSessionConfig().setReadBufferSize( 2048 );
        acceptor.getSessionConfig().setIdleTime( IdleStatus.BOTH_IDLE, 10 );
        System.out.println("Binding: ");
        acceptor.bind( new InetSocketAddress(port) );
        System.out.println("Bound ");
    }
 
    @Override
    public void exceptionCaught( IoSession session, Throwable cause ) throws Exception
    {
        cause.printStackTrace();
    }
    
    @Override
    public void messageReceived( IoSession session, Object message ) throws Exception
    {
        String str = message.toString();
        consumer.consumeTCP(port, str);
    }
    
    @Override
    public void sessionIdle( IoSession session, IdleStatus status ) throws Exception
    {
        System.out.println( "IDLE " + session.getIdleCount( status ));
    }

}
