package org.javagamesfactory.nioservers;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

import org.apache.log4j.*;

/**
 * FIXME: this class has the same bug that originally existed in the StringBasedServer - if it
 * receives fewer than 4 bytes in the first read of a message, it will NOT correctly handle that
 * (it needs the same workaround in SBS, i.e needs to check if the very first read reads fewer
 * than 4 bytes, and if so, ignore it, reset one buffer, advance the other, and try again)
 * <P>
 * Worker thread that listens to a SocketChannel, receives incoming messages, decodes them,
 * and then passes them on one-by-one as String instances to a specified other class of your
 * choice.
 * <P>
 * Implements a protocol of:
 * <blockquote>
 *  MSG_LENGTH_IN_BYTES + MESSAGE
 * </blockquote>
 * (there is no explicit delimiting of message start/end)
 */
public class ResponseListener implements Runnable
{
	public int bufferSize = 4000;
	
	Logger logger;
	String title;
	iMessageProcessor messageProcessor;
	CharsetDecoder decoder;
	Thread myThread = null;
	SocketChannel channel;
	boolean bufferIsEmpty;
	
	/**
	 * 
	 * @param t human-readable title of this listener, to make it easier to distinguish between log messages and debugging
	 * @param mp processor that will handle any messages that this instance receives and parses into strings
	 * @param sc the low-level channel to read messages from
	 * @param charset the encoding that the low-level bytes are in so that this instance can decode them into chars 
	 */
	public ResponseListener( String t, iMessageProcessor mp, SocketChannel sc, Charset charset )
	{
		logger = Logger.getLogger( getClass() );
		title = t;
		messageProcessor = mp;
		channel = sc;
		decoder = charset.newDecoder();
	}
	
	/**
	 * Starts a new thread for this listener
	 */
	public void start()
	{
		myThread = new Thread( this );
		myThread.start();
	}
	
	/**
	 * Continuously reads from the channel, with a blocking-read, until it receives a complete
	 * message, which it then passes to the message processor.
	 * <P>
	 * Format of messages is: [length-in-bytes] [message]
	 * <P>
	 * length is specified as a 32-bit integer, i.e. 4 bytes 
	 */
	public void run()
	{
		ByteBuffer bb = ByteBuffer.allocate( bufferSize );
		CharBuffer cb = CharBuffer.allocate( bufferSize ); // because ISO 8859 1 uses 1-byte chars
		
		bufferIsEmpty = true;
		while( myThread != null )
		//try
		{
			if( bufferIsEmpty )
			{
				try
				{
					int bytesRead = channel.read( bb ); // ignore the value of this because it includes the bb.getInt we remove
				}
				catch( IOException e )
				{
					logger.info( "["+title+"] Quitting because received IOException on my only channel", e );
					myThread = null;
					continue;
				}
			}
			bb.flip();
			int bytesToRead = bb.getInt();
			int excessBytesRead = bb.remaining() - bytesToRead;
			
			if( excessBytesRead > 0 )
			{
				bb.limit( bb.limit() - excessBytesRead );
			}
			else if( excessBytesRead < 0 )
			{
				// not enough bytes read!
				throw new UnsupportedOperationException( "["+title+"] this shouldn't have happened - partial read from server!" );
				/*
				// move the buffer's pointer to the start of the unused space, and free all the unused space
				bb.position( bb.limit() );
				bb.limit( bb.capacity() );
				*/
			}
			
			decoder.decode(bb, cb, true);
			cb.flip();
			logger.info( "["+title+"] MESSAGE RECEIVED: "+cb.toString());
			messageProcessor.receiveMessage(cb.toString());
			
			if( excessBytesRead > 0 )
			{
				// move the excess read stuff to the start of the buffer
				bb.limit( bb.position() + excessBytesRead );
				bb.compact();
				
				bufferIsEmpty = false;
			}
			else
			{
				bb.clear();
				bufferIsEmpty = true;
			}
			cb.clear();
		}
		/*catch( IOException e )
		{
			logger.error( "attempting to read response from remote channel", e );
		}*/
	}
}