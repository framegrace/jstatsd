package org.javagamesfactory.nioservers;

/**
 * Clients that implement this can asynchronously receive messages from a remote server
 */
public interface iMessageProcessor
{
	/**
	 * Handles incoming messages that have been read from the SocketChannel and need to be processed
	 * locally
	 * 
	 * @param message whatever message was received from the remote server
	 */
	public void receiveMessage( String message );
}