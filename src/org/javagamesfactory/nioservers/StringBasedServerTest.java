package org.javagamesfactory.nioservers;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

import junit.framework.*;

import org.apache.log4j.*;
import org.apache.log4j.xml.*;

/**
 * Comprehensive set of Unit Tests for the StringBasedServer class
 */
public class StringBasedServerTest extends TestCase
{
	DummyStringBasedServer sbs;
	int port = 5000;
	String serverHostname = "localhost";
	
	public StringBasedServerTest()
	{
		DOMConfigurator.configure("log4j.xml");
	}
	
	protected void setUp() throws Exception
	{
		sbs = new DummyStringBasedServer( port );
		sbs.logger.setLevel(Level.DEBUG);
		sbs.start();
	}

	protected void tearDown() throws Exception
	{
		sbs.stop();
	}
	
	/**
	 * Checks that not only can you START a server, but when you stop it, it does actually stop -
	 * which it tests by immediately starting a new instance on the same port (which will fail if
	 * the first instance incompletely stopped)
	 * 
	 * @throws Throwable
	 */
	public void testStartStop() throws Throwable
	{
		StringBasedServer privatesbs = new DummyStringBasedServer( port+999 );
		privatesbs.start();
		assertEquals( "Server has started", ServerState.STARTED, privatesbs.getStatus() );
		Thread.sleep( 100 );
		assertEquals( "Server is running", ServerState.RUNNING, privatesbs.getStatus() );
		privatesbs.stop();
		assertEquals( "Server has stopped", ServerState.STOPPED, privatesbs.getStatus() );
		
		privatesbs = new DummyStringBasedServer( port+999 );
		privatesbs.start();
		assertEquals( "Server has started", ServerState.STARTED, privatesbs.getStatus() );
		Thread.sleep( 100 );
		assertEquals( "Server is running", ServerState.RUNNING, privatesbs.getStatus() );
		privatesbs.stop();
		assertEquals( "Server has stopped", ServerState.STOPPED, privatesbs.getStatus() );
	}
	
	
	
	public void testConnect() throws Throwable
	{
		Socket sc = new Socket( serverHostname, port );
		
		assertTrue( "connected to remote server OK", sc.isConnected() );
		
		sc.close();
	}
	
	public void testReceiveMessage() throws Throwable
	{
		Socket sc = new Socket( serverHostname, port );
		
		OutputStream os = sc.getOutputStream();
		InputStream is = sc.getInputStream();
		
		String message = "Hello!";
		
		TestTools.sendMessagePrecededByLengthAsInteger( message, os );
		
		Thread.sleep( 10 );
		
		assertEquals( "Received my hello message", message, sbs.receivedMessages.getFirst());
	}
	
	public void testSendHandshakeToServerInTwoPieces() throws Throwable
	{
			StringBasedClient connector = new StringBasedClient();
			connector.setServerHostname( serverHostname );
			connector.setServerPort( port );
			connector.connect();
			
			String loginMessage = "<login><username>hahahaha</username></login>";
			connector.sendStaggeredMessage( loginMessage );
			
			Thread.sleep(1000);
			
			assertEquals( "Server read correct final message from client", loginMessage, sbs.receivedMessages.get( 0 ) );
	}
}

class DummyStringBasedServer extends StringBasedServer
{
	LinkedList<String> receivedMessages;
	
	public DummyStringBasedServer( int p ) throws UnknownHostException
	{
		super( p );
		receivedMessages = new LinkedList<String>();
	}
	
	@Override protected void postSelect( long millisecondsSinceLastStarted )
	{
	}
	
	@Override protected void keyCancelled( SelectionKey key )
	{
	}
	
	@Override protected void processStringMessage( String message, SelectionKey key ) throws ClosedChannelException
	{
		receivedMessages.addLast(message);
	}
}