package org.javagamesfactory.nioservers;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

import org.apache.log4j.*;

/**
 * A simple blocking client class that uses NIO over TCP, making it easier to interface with / share code
 * with NIO servers you write.
 * <P>
 * NB: there is no reason this class could not be made to be non-blocking, but it was chosen to be
 * blocking more for convenience of writing game-clients, which are easier to write if you can
 * assume all IO is blocking-on-send.
 */
public class StringBasedClient
{
	/**
	 * The logger automatically logs ALL transmitted data as INFO, but will truncate to this many characters
	 * when displaying what it has sent - or will display complete strings if you set this to anything less
	 * than zero.
	 * <P>
	 * Default value is 60 chars
	 */
	public static int maximumMessagePrefixToLog = 60;
	/**
	 * Default size of maximum outgoing buffer - this limits the length of the longest single message you
	 * can possibly send with this class
	 */
	public static int defaultByteBufferSize = 2000;
	
	protected Logger logger;
	protected String title;
	protected iMessageProcessor messageProcessor;
	protected String serverHostname;
	protected int serverPort;
	protected SocketChannel sc;
	Selector selector;
	Charset charset = Charset.forName( "ISO-8859-1" );
	CharsetDecoder decoder = charset.newDecoder();
	CharsetEncoder encoder = charset.newEncoder();
	
	ByteBuffer bb;
	CharBuffer cb;
	
	/**
	 * Simple constructor - remember that you MUST also call the setServerHostname and setServerPort methods
	 * before calling connect
	 * 
	 * @see #setServerHostname(String)
	 * @see #setServerPort(int)
	 */
	public StringBasedClient()
	{
		this( defaultByteBufferSize );
	}
	
	/**
	 * Remember that you MUST also call the setServerHostname and setServerPort methods
	 * before calling connect
	 * 
	 * @see #setServerHostname(String)
	 * @see #setServerPort(int)
	 * 
	 * @param bufferSize size of outgoing buffer in bytes
	 */
	public StringBasedClient( int bufferSize )
	{
		super();
		logger = Logger.getLogger( getClass() );
		bb = ByteBuffer.allocate( bufferSize );
		cb = CharBuffer.allocate( bufferSize );
	}
	
	/**
	 * Fully configures the connector, so that you can safely immediately call connect
	 * 
	 * @see #connect()
	 * 
	 * @param h the hostname of the server to connect to
	 * @param p the port of the server to connect to
	 * @param bufferSize size of outgoing buffer in bytes
	 */
	public StringBasedClient( String h, int p, int bufferSize )
	{
		this( bufferSize );
		setServerHostname( h );
		setServerPort( p );
	}
	
	/**
	 * Confirms whether this connector has connected at the socket-level to the remote machine
	 * 
	 * @return true if the SocketChannel has connected, false if otherwise
	 */
	public boolean isConnected()
	{
		if( sc != null )
			return sc.isConnected();
		else
			return false;
	}
	
	/**
	 * Configures the hostname that the connector will connect to
	 * 
	 * @param s target hostname
	 */
	public void setServerHostname( String s )
	{
		serverHostname = s;
	}
	
	/**
	 * Configures the port that the connector will connect to
	 *  
	 * @param i port in range 0-65535
	 */
	public void setServerPort( int i )
	{
		serverPort = i;
	}
	
	/**
	 * Convenience for if you want to interpret a Connector e.g after an error has been thrown
	 * 
	 * @return the hostname you configured this class with
	 */
	public String getServerHostname()
	{
		return serverHostname;
	}
	
	/**
	 * Convenience for if you want to interpret a Connector e.g after an error has been thrown
	 * 
	 * @return the port you configured this class with
	 */
	public int getServerPort()
	{
		return serverPort;
	}
	
	/**
	 * Mainly for debugging - allows you to check exactly which remote machine this connector has connected to
	 * 
	 * @return the address of the remote server this connector has connected to, or null if it has not yet
	 * successfully completed a connect() call
	 * 
	 * @see #connect()
	 */
	public InetAddress getRemoteInetAddress()
	{
		if( sc != null
		&& sc.socket() != null )
			return sc.socket().getInetAddress();
		else
			return null;
	}
	
	/**
	 * Mainly for debugging - allows you to check exactly which remote machine this connector has connected to
	 * 
	 * @return the port of the remote server this connector has connected to, or -1 if it has not yet
	 * successfully completed a connect() call
	 * 
	 * @see #connect()
	 */
	public int getRemotePort()
	{
		if( sc != null
		&& sc.socket() != null )
			return sc.socket().getPort();
		else
			return -1;
	}
	
	/**
	 * Attempts to connect to the remote server, configure the TCP socket to disable Nagle's algorithm
	 * (to reduce latency), and start a slave thread to receive incoming messages.
	 * <P>
	 * Before calling this method you MUST have configured the server hostname and port
	 * 
	 * @see #setServerHostname(String)
	 * @see #setServerPort(int)
	 * 
	 * @throws Throwable if anything at all goes wrong (lots could go wrong here!)
	 */
	public void connect() throws Throwable
	{
		/*
		 * sc = SocketChannel.open( new InetSocketAddress( serverHostname, serverPort ));
		 * sc.configureBlocking(false); selector = Selector.open(); sc.register( selector,
		 * SelectionKey.OP_WRITE );
		 */

		sc = SocketChannel.open( new InetSocketAddress( serverHostname, serverPort ) );
		sc.socket().setTcpNoDelay( true ); // stop Nagling, send all data
		// immediately
		ResponseListener rl = new ResponseListener( title, messageProcessor, sc, charset );
		rl.start();
	}
	
	/**
	 * You should always manually call this method when you wish to disconnect from the server,
	 * as it will close down the internal Selector
	 */
	public void close()
	{
		try
		{
			sc.close();
		}
		catch( IOException e )
		{
			logger.error( "Failed to close SocketChannel in Connector; irrelevant, nothing we can do about it; stupid API implementation from Sun!", e );
		}
	}
	
	/**
	 * Blocking send messages to the server; not thread-safe!
	 * 
	 * @param message
	 * @throws IOException
	 */
	public void sendMessage( String message ) throws IOException
	{
		cb.clear();
		bb.clear();
		cb.append( message );
		cb.flip();
		bb.putInt( cb.remaining() );
		
		encoder.encode( cb, bb, true );
		
		bb.flip();
		int numWritten = sc.write( bb );
		
		String messageWritten = message;
		
		if( maximumMessagePrefixToLog > -1
		&& messageWritten.length() > maximumMessagePrefixToLog )
		{
			if( maximumMessagePrefixToLog > 9 )
				messageWritten = messageWritten.substring( 0, maximumMessagePrefixToLog - 3 ) + "...";
			else
				messageWritten = messageWritten.substring( 0, maximumMessagePrefixToLog );
		}
		logger.info( "[Connector:" + title + "]    wrote " + numWritten + " bytes (" + messageWritten + ")" );
	}
	
	/**
	 * For testing only - deliberately splits messages into two pieces and sends them separately - this
	 * is VERY VERY useful for testing whether a JVM on the other end using NIO is correctly merging
	 * packets into complete messages (NB: even a NIO server in blocking mode will still occasionally
	 * receive split messages due to the internet)
	 * 
	 * @param message
	 */
	protected void sendStaggeredMessage( String message ) throws IOException
	{
		cb.clear();
		bb.clear();
		cb.append( message.substring( 0, message.length() - 1 ) );
		cb.flip();
		bb.putInt( cb.remaining() + 1 );
		
		encoder.encode( cb, bb, true );
		
		bb.flip();
		{
			logger.debug( "[Connector:" + title + "]    cb: l=" + cb.limit() + ", p=" + cb.position() );
			logger.debug( "[Connector:" + title + "]    bb: l=" + bb.limit() + ", p=" + bb.position() );
		}
		int numWritten = sc.write( bb );
		logger.info( "[Connector:" + title + "]    wrote " + numWritten + " bytes" );
		
		try
		{
			Thread.sleep( 100 );
		}
		catch( InterruptedException e )
		{
		}
		
		cb.compact();
		bb.compact();
		cb.append( message.substring( message.length() - 1 ) );
		cb.flip();
		encoder.encode( cb, bb, true );
		bb.flip();
		numWritten = sc.write( bb );
		logger.info( "[Connector:" + title + "]    wrote " + numWritten + " bytes" );
	}
}