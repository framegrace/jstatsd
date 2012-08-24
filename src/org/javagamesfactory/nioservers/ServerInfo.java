package org.javagamesfactory.nioservers;

import java.net.*;

/**
 * Contains all the metadata representing a single server instance
 */
public class ServerInfo implements iServerInfo
{
	InetAddress address;
	String name;
	int port;
	int numConnectedChannels;
	
	/**
	 * Creates a new record representing a server
	 * 
	 * @param n name of the server
	 * @param isa address the server is bound to
	 * @param p port the server is bound to
	 */
	public ServerInfo( String n, InetAddress isa, int p )
	{
		name = n;
		address = isa;
		port = p;
	}
	
	/**
	 * Number of connected clients
	 */
	public int getNumConnectedChannels()
	{
		return numConnectedChannels;
	}
	
	/**
	 * Update the number of connected clients (should be called by the server implementation automatically)
	 */
	public void setNumConnectedChannels( int numConnectedChannels )
	{
		this.numConnectedChannels = numConnectedChannels;
	}
	
	/**
	 * Address the server is bound to, without socket
	 */
	public InetAddress getAddress()
	{
		return address;
	}
	
	/**
	 * Address the server is bound to, including socket
	 */
	public InetSocketAddress getSocketAddress()
	{
		return new InetSocketAddress( address, port );
	}
	
	/**
	 * Human-readable name of this server
	 */
	public String getName()
	{
		return name;
	}
	
	/**
	 * A custom naming convention: "NAME @ ADDRESS:PORT"
	 */
	@Override public String toString()
	{
		return name + " @ " + address.getHostName() + ":" + port;
	}
}