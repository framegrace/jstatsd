package org.javagamesfactory.nioservers;

import java.net.*;
import java.nio.channels.*;

/**
 * This is the class that runs automatically if you run the jar-file as an executable
 * <P>
 * Starts a simple server, and a simple client, and sends the server a hello-world style
 * message every 5 seconds, while printing a summary of what is happening to std-out
 */
public class SimpleServerDemo extends StringBasedServer
{
	/**
	 * Delegates directly to super-constructor with same signature
	 * 
	 * @param p
	 * @throws UnknownHostException
	 */
	public SimpleServerDemo( int p ) throws UnknownHostException
	{
		super( p );
	}
	
	/**
	 * Empty method body
	 */
	protected void keyCancelled( SelectionKey key )
	{
	}
	
	/**
	 * Empty method body
	 */
	protected void postSelect( long millisecondsSinceLastStarted )
	{
	}
	
	/**
	 * Simply prints the message to std-out
	 */
	protected void processStringMessage( String message, SelectionKey key ) throws ClosedChannelException
	{
		System.out.println( "RECEIVED INCOMING MESSAGE: " + message );
	}
	
	/**
	 * Accepts one optional argument - the port to which the server should bind itself
	 * 
	 * @param args
	 * @throws Throwable
	 */
	public static void main( String[] args ) throws Throwable
	{
		SimpleServerDemo server = null;
		StringBasedClient connector = null;
		try
		{
			System.out.println( "Demo for simple string-based server\n-------------------------\n" );
			System.out.println( "Fires up a server on the specified port (or port 9999 if none specified)," );
			System.out.println( "   and then creates a simple client to connect to the server and send it" );
			System.out.println( "   a sequence of simple messages\n" );
			System.out.println( "Only purpose is to demonstrate you've got the necessary libraries etc" );
			System.out.println( "   installed and that your version of java will actually run this API" );
			
			int port = 9999;
			if( args.length > 0 )
			{
				try
				{
					port = Integer.parseInt( args[0] );
				}
				catch( NumberFormatException e )
				{
					System.out.println( "ERROR! The first argument must be a valid integer port; you supplied: " + args[0] );
					System.exit( -1 );
				}
			}
			
			server = new SimpleServerDemo( port );
			server.start();
			connector = new StringBasedClient( "localhost", port, 2000 );
			connector.connect();
			connector.sendMessage( "Hello, World!" );
			
			while( true )
			{
				Thread.sleep( 5000 );
				connector.sendMessage( "And another message: Hello, World!" );
			}
		}
		catch( NoClassDefFoundError e )
		{
			System.err.println( "ERROR! Failed to find a required class: " + e.getMessage() );
			e.printStackTrace();
		}
		finally
		{
			connector.close();
			server.stop();
		}
	}
}