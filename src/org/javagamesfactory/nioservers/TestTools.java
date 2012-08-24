package org.javagamesfactory.nioservers;

import java.io.*;
import java.util.*;

/**
 * Simple utility methods used throughout the JUnit tests for this package
 */
public class TestTools
{
	/**
	 * Very low-level implementation of the client/server protocol that requires no helper classes to do
	 * the sending of a message - useful for testing servers without being reliant on working copy of
	 * helper classes
	 * 
	 * @param message message to send
	 * @param os raw outputstream to send to (yes, this method does NOT use NIO)
	 * @throws IOException
	 */
	public static void sendMessagePrecededByLengthAsInteger( String message, OutputStream os ) throws IOException
	{
		if( message.length() < 256 )
		{
			os.write( 0 );
			os.write( 0 );
			os.write( 0 );
			os.write( message.length() );
			os.write( message.getBytes() );
		}
		else
			throw new IllegalArgumentException( "Not yet implemented: sending messages longer than 255 bytes" );
	}
	
	/**
	 * Simple method to search a list of received messages for one that starts with the specified prefix,
	 * and then immediately return
	 * 
	 * @param search prefix to search for
	 * @param entries list of strings to search, one or more of which MAY start with the prefix
	 * @return null if no match found
	 */
	public static String findMessageStartsWith( String search, LinkedList<String> entries )
	{
		for( String string : entries )
		{
			if( string.startsWith( search ))
				return string;
		}
		
		return null;
	}
}
