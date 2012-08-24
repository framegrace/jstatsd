package org.javagamesfactory.nioservers;

import java.net.*;

/**
 * Contains all the metadata representing a single server instance
 * <P>
 * It's not clear why this has its own interface; may be deprecated in the future
 * in favour of using the only known implementation (ServerInfo) instead; see that
 * class for all method documentation
 * 
 * @see ServerInfo
 */
public interface iServerInfo
{
	String getName();
	InetAddress getAddress();
	InetSocketAddress getSocketAddress();
	public int getNumConnectedChannels();
	public void setNumConnectedChannels( int numConnectedChannels );
}