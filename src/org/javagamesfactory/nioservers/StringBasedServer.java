package org.javagamesfactory.nioservers;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

import org.apache.log4j.*;

import static org.javagamesfactory.nioservers.ServerState.*;

/**
 * Base class that provides the core of a non-blocking NIO-driven server which can send and receive
 * Strings seamlessly. It correctly handles partial/fragmented incoming and outgoing packets, etc.
 * <P>
 * There are two methods in this class you need to override when subclassing. The first is
 * keyCancelled which you MUST override, or else you will get data-errors / crashes because your
 * data structures will become corrupted whenever a user disconnects.
 * <P>
 * The second is processStringMessage, which is where all the incoming messages get sent to, and is
 * the primary way for you to respond to incoming messages / requests / etc.
 * <P>
 * There is a second Logger instance in this class, mapped to [classname].verbose, which gives VERY
 * verbose information when you put it into info or debug Level.
 * 
 * @see #processStringMessage(String, SelectionKey)
 * @see #keyCancelled(SelectionKey)
 * 
 */
public abstract class StringBasedServer implements Runnable
{
	int maximumMessagePrefixToLog = 40;
	/**
	 * Default size to create ALL incoming and outgoing buffers; this equates to the maximum size of
	 * message that can be sent or received.
	 * <P>
	 * NB: this variable is ONLY checked at construction time; you must alter it BEFORE constructing
	 * an instance of a server if you want it to have effect (or use the alternative constructor that
	 * allows you to specify a custom value for that instance only).
	 */
	public static int defaultByteBufferSize = 2000;
	int currentByteBufferSize;
	
	protected Logger logger, verboseLogger;
	Thread thread;
	protected ServerState status = INITIALIZING;
	protected Selector selector;
	Charset charset = Charset.forName( "ISO-8859-1" );
	CharsetDecoder decoder = charset.newDecoder();
	CharsetEncoder encoder = charset.newEncoder();
	ServerSocketChannel ssc;
	protected HashMap<SelectionKey, ByteBuffer> readByteBuffers;
	protected HashMap<SelectionKey, CharBuffer> readCharBuffers;
	protected HashMap<SelectionKey, Integer> messageLengths;
	protected HashMap<SelectionKey, ByteBuffer> writeByteBuffers;
	protected HashMap<SelectionKey, LinkedList<String>> pendingOutgoingMessages;
	protected HashMap<SelectionKey, LinkedList<ByteBuffer>> pendingOutgoingEncodedMessages;
	protected HashMap<SelectionKey, Boolean> readBufferIsEmpty;
	
	protected int port = -1, targetPort;
	protected iServerInfo myInfo;
	
	long currentSelectProcessingBegan = Integer.MAX_VALUE;
	long currentIsReadableProcessingBegan = Integer.MAX_VALUE;
	long lastPostProcessBegan = Integer.MAX_VALUE;
	protected LinkedList<SocketChannel> connectedChannels;
	
	/**
	 * Creates a server using the default byte buffer size
	 * 
	 * @see #defaultByteBufferSize
	 * 
	 * @param p port which this server should bind to
	 * @throws UnknownHostException
	 */
	public StringBasedServer( int p ) throws UnknownHostException
	{
		this( p, defaultByteBufferSize );
	}
	
	/**
	 * Creates a server with a custom byte-buffer size, and ignores the defaultByteBufferSize
	 * 
	 * @see #defaultByteBufferSize
	 * 
	 * @param p port which this server should bind to
	 * @param newBufferSize sets the size of all receive and send buffers, overriding the defaultByteBufferSize
	 * @throws UnknownHostException
	 */
	public StringBasedServer( int p, int newBufferSize ) throws UnknownHostException
	{
		logger = Logger.getLogger( getClass() );
		verboseLogger = Logger.getLogger( getClass().getName() + ".verbose" );
		targetPort = p;
		currentByteBufferSize = newBufferSize;
		
		readByteBuffers = new HashMap<SelectionKey, ByteBuffer>();
		readCharBuffers = new HashMap<SelectionKey, CharBuffer>();
		readBufferIsEmpty = new HashMap<SelectionKey, Boolean>();
		writeByteBuffers = new HashMap<SelectionKey, ByteBuffer>();
		messageLengths = new HashMap<SelectionKey, Integer>();
		
		pendingOutgoingEncodedMessages = new HashMap<SelectionKey, LinkedList<ByteBuffer>>();
		pendingOutgoingMessages = new HashMap<SelectionKey, LinkedList<String>>();
		
		connectedChannels = new LinkedList<SocketChannel>();
		InetAddress isa = InetAddress.getLocalHost();
		
		myInfo = new ServerInfo( "Nemo", isa, port );
	}
	
	/** Subclasses SHOULD override this method to process all incoming messages */
	protected abstract void processStringMessage( String message, SelectionKey key ) throws ClosedChannelException;
	
	/**
	 * Subclasses MUST override this method to remove data from local data structures whenever
	 * connections are dropped / closed
	 */
	protected abstract void keyCancelled( SelectionKey key );
	
	/**
	 * Core of the server; this method runs continuously once the server has started, and continues until
	 * a successful call is made to the stop method
	 * <P>
	 * You can check if this method is running by inspecting the status variable - if it is RUNNING, then
	 * this method is happily chugging away
	 * <P>
	 * This server is single-threaded, using a single NIO Selector to do all the work of accepting, reading
	 * from and writing to connected TCP channels.
	 * 
	 * @see #start()
	 * @see #stop()
	 * @see #getStatus()
	 */
	public void run()
	{
		synchronized( this )
		{
			if( status != STARTED )
			{
				logger.warn( "Thread started up, but server has been de-STARTED asynchronously (state == "+status.toString()+"); terminating thread immediately" );
				return;
			}
				
			
			try
			{
				info( "Binding to port " + targetPort + "..." );
				ssc = ServerSocketChannel.open();
				ssc.configureBlocking( false );
				ssc.socket().bind( new InetSocketAddress( targetPort ) );
				port = targetPort;
			}
			catch( BindException e )
			{
				status = OFFLINE;
				error( "Attempted to bind to port " + targetPort + " which is already in use; server going OFFLINE", e );
				return;
			}
			catch( IOException e )
			{
				status = OFFLINE;
				error( "Failed to open non-blocking server port = " + targetPort + "; server going OFFLINE", e );
				return;
			}
		}
		
		try
		{
			synchronized( this )
			{
				if( status != STARTED )
					return;
				
				selector = Selector.open();
				ssc.register( selector, SelectionKey.OP_ACCEPT );
				status = RUNNING;
			}
			
			while( thread != null )
			{
				// info( "["+( (thread == null)? "NULL" : thread.getName()
				// )+":"+port+"] Selecting...");
				selector.select( 1 );
				currentSelectProcessingBegan = System.currentTimeMillis();
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> iterator = keys.iterator();
				while( iterator.hasNext() )
				{
					SelectionKey key = null;
					debugVerbose( "next key..." );
					try
					{
						key = iterator.next();
						iterator.remove();
						
						if( key.isConnectable() )
						{
							infoVerbose( "  CONNECTABLE key; calling finishConnect" );
							SocketChannel sc = ((SocketChannel) key.channel());
							sc.finishConnect();
						}
						
						if( key.isAcceptable() )
						{
							infoVerbose( "  ACCEPTABLE key; accepting and adding new SocketChannel to selector for READ only" );
							SocketChannel sc = ((ServerSocketChannel) key.channel()).accept();
							sc.configureBlocking( false );
							sc.socket().setTcpNoDelay( true ); // stop Nagling, send all data immediately
							sc.register( selector, SelectionKey.OP_READ );
							
							connectedChannels.addLast( sc );
						}
						
						if( key.isReadable() )
						{
							currentIsReadableProcessingBegan = System.currentTimeMillis();
							infoVerbose( "  READABLE key" );
							
							try
							{
								String message = readIncomingMessageFromKey( key );
								
								if( message == null )
								{
									verboseLogger.debug( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "]   READABLE key, but incomplete message; storing for later" );
									continue;
								}
								long currentTime = System.currentTimeMillis();
								verboseLogger.info( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "]   Received message = \"" + message + "\" from key, reading time = " + (currentTime - currentIsReadableProcessingBegan) + ", selection time total so far = " + (currentTime - currentSelectProcessingBegan) );
								
								processStringMessage( message, key );
							}
							catch( IOException e )
							{
								info( "Cancelling READABLE key (" + key + ") that generated an IOException", e );
								key.cancel();
								keyCancelled( key );
								connectedChannels.remove( key.channel() );
								continue;
							}
						}
						
						if( key.isWritable() )
						{
							infoVerbose( "  WRITABLE key" );
							
							ByteBuffer bb = writeByteBuffers.get( key );
							if( bb == null )
							{
								/*
								 * Looks for a ready-to-send buffer, instantiating missing data structures
								 * as it goes That buffer is then put into the writeByteBuffers map IFF it
								 * is only partially sent this time
								 */
								LinkedList<ByteBuffer> pending = pendingOutgoingEncodedMessages.get( key );
								if( pending == null )
								{
									pending = new LinkedList<ByteBuffer>();
									pendingOutgoingEncodedMessages.put( key, pending );
								}
								bb = pending.removeFirst();
								
								if( bb == null )
									error( "No pending outgoing messages but key was marked writeable, key = " + key );
								else
									writeByteBuffers.put( key, bb );
							}
							
							int numWritten = ((WritableByteChannel) key.channel()).write( bb );
							infoVerbose( "   wrote " + numWritten + " bytes" );
							
							if( bb.remaining() > 0 )
							{
								debug( "   WRITEABLE: bb still has bytes remaining to write, so leaving it in" );
							}
							else
							{
								infoVerbose( "   WRITEABLE: write complete; killing buffer and de-registering for OP_WRITE" );
								writeByteBuffers.remove( key );
								bb = null;
								
								String messageWritten = pendingOutgoingMessages.get( key ).removeFirst();
								if( messageWritten.length() > maximumMessagePrefixToLog )
									messageWritten = messageWritten.substring( 0, maximumMessagePrefixToLog - 3 ) + "...";
								debug( "   WRITEABLE: finished message = " + messageWritten );
								
								// if there's no more pending messages, then de-register
								// the OP_WRITE
								if( pendingOutgoingEncodedMessages.get( key ).size() < 1 )
									key.channel().register( selector, SelectionKey.OP_READ );
							}
						}
					}
					catch( CancelledKeyException e )
					{
						logger.warn( "Attempted to WRITE to cancelled key (probably cancelled on READ in this loop) (" + key + "); ignoring", e );
						continue;
					}
					catch( IOException e )
					{
						info( "Cancelling WRITABLE key (" + key + ") that generated an IOException", e );
						key.cancel();
						keyCancelled( key );
						connectedChannels.remove( key.channel() );
						continue;
					}
					catch( Exception e )
					{
						logger.warn( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] Error handling key = " + key + ", continuing with selection for all other keys", e );
					}
					
					debugVerbose( "...key ended; keys left this run = " + iterator.hasNext() );
				}
				debugVerbose( "...last key ended" );
				
				long currentPostProcessBegan = System.currentTimeMillis();
				if( lastPostProcessBegan == Integer.MAX_VALUE )
					lastPostProcessBegan = currentPostProcessBegan;
				
				debugVerbose( "Starting postSelect..." );
				postSelect( currentPostProcessBegan - lastPostProcessBegan );
				
				lastPostProcessBegan = currentPostProcessBegan;
			}
		}
		catch( IOException e )
		{
			error( "Major crash on server!", e );
		}
		catch( Throwable e )
		{
			error( "Major crash on server!", e );
		}
		finally
		{
			try
			{
				selector.close();
			}
			catch( Throwable e )
			{
				error( "(server crashed or told to stop) ...but I failed on final attempt to close selector", e );
			}
			
			try
			{
				ssc.close();
			}
			catch( Throwable e )
			{
				error( "(server crashed or told to stop) ...but I failed on final attempt to close ServerSocketChannel", e );
			}
		}
	}
	
	/**
	 * Invoked every time a select completes, IRRESPECTIVE of whether there was any network data.
	 * <P>
	 * This is primarily useful for servers that wish to be purely singlethreaded and do all their
	 * local processing in the gap between performing successive selects (lets you avoid using
	 * synchronized blocks anywhere)
	 * 
	 * @param millisecondsSinceLastStarted the number of milliseconds since this method was last
	 *           called; i.e. the time at which it was INVOKED, not the time at which it RETURNED;
	 *           this is perfect for maintaining fixed-rate game loops
	 */
	protected abstract void postSelect( long millisecondsSinceLastStarted );
	
	/**
	 * This class keeps track of how many channels are currently connected, adding them
	 * every time a key is accepted, and removing them as soon as there is an I/O error
	 * or READ or WRITE
	 * 
	 * @return number of currently connected channels - may be slightly incorrect (gets
	 * updated only after each select operation takes place)
	 */
	protected int getNumberOfConnectedChannels()
	{
		return connectedChannels.size();
	}
	
	/**
	 * Primary means of sending a message to a particular client; subclasses should use this
	 * method for ALL outgoing messages
	 * <P>
	 * NB: this method is NOT as efficient as it could be - it neither recycles buffers, nor
	 * does it intelligently create them of variable size. All buffers are created at a single
	 * fixed size (default 2000 bytes), configurable by changing the byteBufferSize variable.
	 *  
	 * @param key the key representing the client to send to
	 * @param message the message to send
	 * @throws ClosedChannelException if the client is no longer connected
	 */
	protected void addMessageToKey( SelectionKey key, String message ) throws ClosedChannelException
	{
		if( message.length() > currentByteBufferSize )
			throw new IllegalArgumentException( "This method can only accept messages up to " + currentByteBufferSize + " bytes in length; you tried to send a message of " + message.length() + " bytes" );
		
		CharBuffer cb = CharBuffer.allocate( currentByteBufferSize );
		ByteBuffer bb = ByteBuffer.allocate( currentByteBufferSize );
		
		cb.append( message );
		cb.flip();
		bb.putInt( cb.remaining() );
		
		encoder.encode( cb, bb, true );
		bb.flip();
		
		/*
		 * Fetch the list of pending encoded messages for this key, and add this new message on to the
		 * end
		 */
		LinkedList<ByteBuffer> pendingEncodedMessages = pendingOutgoingEncodedMessages.get( key );
		if( pendingEncodedMessages == null )
		{
			pendingEncodedMessages = new LinkedList<ByteBuffer>();
			pendingOutgoingEncodedMessages.put( key, pendingEncodedMessages );
		}
		pendingEncodedMessages.addLast( bb );
		
		/*
		 * Fetch the list of pending messages for this key, and add this new message on to the end
		 */
		LinkedList<String> pendingMessages = pendingOutgoingMessages.get( key );
		if( pendingMessages == null )
		{
			pendingMessages = new LinkedList<String>();
			pendingOutgoingMessages.put( key, pendingMessages );
		}
		pendingMessages.addLast( message );
		
		/* Prepare the channel to write the message if it's not already ready */
		if( (key.interestOps() & SelectionKey.OP_WRITE) != SelectionKey.OP_WRITE )
			key.channel().register( selector, SelectionKey.OP_WRITE );
		selector.wakeup();
	}
	
	/**
	 * Allows you to have a look at what is due to be sent to any given client, but hasn't
	 * yet been sent - automatically keeps track of the message in non-bytes.
	 * 
	 * @param key client to peek at
	 * @return
	 */
	protected String peekOutgoingMessageQueueForKey( SelectionKey key )
	{
		LinkedList<ByteBuffer> pendingEncodedMessages = pendingOutgoingEncodedMessages.get( key );
		LinkedList<String> pendingMessages = pendingOutgoingMessages.get( key );
		
		StringBuffer sb = new StringBuffer();
		for( String string : pendingMessages )
		{
			String startOfString = string.substring( 0, Math.min( string.length(), 20 ) );
			sb.append( "["+startOfString+"...]\n" );
		}
		
		return sb.toString();
	}
	
	/**
	 * Convenience method for sending error messages to a client using simple XML format to easily extract
	 * what went wrong and what caused the error
	 * 
	 * @param key client to send to
	 * @param originalMessage the message that you received from the client that caused the error
	 * @param errorDescription human-readable description of the error
	 * @throws ClosedChannelException
	 */
	protected void addErrorToKey( SelectionKey key, String originalMessage, String errorDescription ) throws ClosedChannelException
	{
		addMessageToKey( key, "<error><command>" + originalMessage + "</command><description>" + errorDescription + "</description></error>" );
	}
	
	/**
	 * Starts the server; must be called or else the server won't do anything.
	 */
	public void start()
	{
		status = STARTED;
		thread = new Thread( this );
		thread.start();
		info( "Started" );
	}
	
	/**
	 * Stops the server - not immediately (attempts immediate stop, but the server will have to finish
	 * some processing and close down some native OS resources, which takes time)
	 * <P>
	 * NB: there are MANY stages to stopping the server (this method is over 50 lines of code already)
	 * to handle all the edge cases of parts of the server crashing while trying to stop. Be patient!
	 * Worst-case scenario, an inner loop pauses for 10 milliseconds and re-checks to see if it can
	 * terminate the server yet - but if this waiting happens, it will output INFO messages to warn
	 * you that it hasn't hung, it's just trying to shutdown and failing. 
	 * <P>
	 * NOTE: uses custom logger calls because the notion of which thread it the server is running in
	 * is less obvious inside this method!
	 */
	public void stop()
	{
		synchronized( this )
		{
			status = STOPPING;
			logger.info( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] Stopping..." );
			
			//System.err.println( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] called from: ...(sending to std err)" );
			//Thread.dumpStack();
		}
		
		Thread oldThread = null;
		if( selector == null )
		{
			// Selector never started, therefore thread stopped automatically
			// too...
			logger.info( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] Selector never started, so not stopping ANYTHING" );
		}
		else if( thread == null )
		{
			// Thread never finished starting, this is impossible if stop() was called from same thread as start()
			logger.info( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] Impossible situation - stop() was called BEFORE start() returned" );
			System.err.println( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] (Impossible situation) was called from: ...(sending to std err)" );
			Thread.dumpStack();
			if( true )
				throw new UnsupportedOperationException( "Impossible situation; stop() was called BEFORE start() returned" );
		}
		else
		{
			oldThread = thread;
			thread = null;
			selector.wakeup();
			
			oldThread.interrupt();
			
			boolean outputMessageYet = false;
			while( oldThread.isAlive() || selector.isOpen() )
			{
				try
				{
					Thread.sleep( 10 );
				}
				catch( InterruptedException e )
				{
					
				}
				
				if( !outputMessageYet )
				{
					outputMessageYet = true;
					logger.info( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] Waiting for thread name = " + oldThread.getName() + " to return isAlive = false (currently: " + oldThread.isAlive() + "), and for selector = " + selector + " to return isOpen = false (currently: " + selector.isOpen() + ")" );
				}
			}
			if( outputMessageYet )
				logger.info( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] ...complete. Thread = " + oldThread.getName() + " is no longer alive, and selector = " + selector + " is no longer open" );
		}
		status = STOPPED;
		
		logger.info( "[" + ((thread == null) ? ((oldThread == null) ? "NULL" : oldThread.getName()) : thread.getName()) + ":" + port + "] Stopped." );
	}
	
	/**
	 * The status variable tells you exactly what the internal state-machine of the server is currently
	 * doing (or trying to do)
	 * 
	 * @see ServerState
	 * 
	 * @return current status of the server
	 */
	public ServerState getStatus()
	{
		return status;
	}
	
	/**
	 * An intelligent read-from-bytebuffer-into-string method that seamlessly copes with
	 * partial reads.
	 * 
	 * NB: this server REQUIRES that all incoming messages to be preceded by a single int saying how many
	 * bytes are in the message, and assumes that you are using a 1-byte wide charset (i.e. it
	 * doesn't track number of bytes separately from number of chars - it will NOT WORK if you use
	 * multi-byte charsets!)
	 * 
	 * @param key
	 * @return null if the message is incomplete, the complete message otherwise
	 * @throws IOException
	 */
	protected String readIncomingMessageFromKey( SelectionKey key ) throws IOException
	{
		try
		{
			
			/*
			 * Fetch the buffers we were using on a previous partial read, or create new ones from
			 * scratch
			 */
			Boolean SunIsStupid = readBufferIsEmpty.get( key );
			if( SunIsStupid == null )
			{
				SunIsStupid = new Boolean( true );
				readBufferIsEmpty.put( key, SunIsStupid );
			}
			boolean bufferIsEmpty = SunIsStupid.booleanValue();
			
			ByteBuffer bb = readByteBuffers.get( key );
			CharBuffer cb = readCharBuffers.get( key );
			if( bb == null )
			{
				bb = ByteBuffer.allocate( currentByteBufferSize );
				readByteBuffers.put( key, bb );
			}
			if( cb == null )
			{
				cb = CharBuffer.allocate( currentByteBufferSize );
				readCharBuffers.put( key, cb );
			}
			
			/*
			 * Read the data from the channel, and establish how many bytes length the total message
			 * should be
			 */
			int bytesRead = -1;
			if( bufferIsEmpty )
			{
				bytesRead = ((ReadableByteChannel) key.channel()).read( bb );
			}
			bb.flip();
			Integer bytesToRead = messageLengths.get( key );
			if( bytesToRead == null || bytesToRead.intValue() < 0 )
			{
				if( bytesRead < 4 ) // there is not enough data in the buffer to
				// read an "int" (4 bytes)
				{
					// de-flip the bb, let it append data on next run
					
					bb.position( bb.limit() );
					bb.limit( bb.capacity() );
					
					return null;
				}
				bytesToRead = bb.getInt();
				messageLengths.put( key, bytesToRead );
			}
			
			int excessBytesRead = bb.remaining() - bytesToRead;
			
			if( excessBytesRead > 0 )
			{
				bb.limit( bb.limit() - excessBytesRead );
			}
			
			/*
			 * Decode all new data in the bytebuffer appending it to the old data in the charbuffer
			 */
			debug( "  READABLE key, bytes to read = " + bytesToRead );
			infoVerbose( "  READABLE key, pre-decode: " + bytesRead + " bytes, bb (l=" + bb.limit() + ", p=" + bb.position() + ", rem = " + bb.remaining() + "), cb (l=" + cb.limit() + ", p=" + cb.position() + ", rem = " + cb.remaining() );
			decoder.decode( bb, cb, true );
			
			infoVerbose( "  READABLE key, post-decode: " + bytesRead + " bytes, bb (l=" + bb.limit() + ", p=" + bb.position() + ", rem = " + bb.remaining() + "), cb (l=" + cb.limit() + ", p=" + cb.position() + ", rem = " + cb.remaining() );
			cb.flip();
			debug( "  READABLE key, " + bytesRead + " bytes, contents (l=" + cb.limit() + ", p=" + cb.position() + ", rem = " + cb.remaining() + ") = \"" + cb.toString() + "\"" );
			
			if( cb.remaining() < bytesToRead )
			{
				debug( "  Missing " + (bytesToRead - cb.remaining()) + " bytes" );
				cb.position( cb.limit() );
				cb.limit( cb.capacity() );
				bb.clear();
				
				return null; // message is incomplete so far
			}
			else if( excessBytesRead > 0 )
			{
				bb.limit( bb.position() + excessBytesRead );
				bb.compact();
				
				SunIsStupid = new Boolean( false );
				
				String result = cb.toString();
				cb.clear();
				messageLengths.put( key, -1 );
				
				return result;
			}
			else
			{
				String result = cb.toString();
				// reset all the buffers etc!
				bb.clear();
				cb.clear();
				messageLengths.put( key, -1 );
				
				return result;
			}
			
		}
		finally
		{
			
		}
	}
	
	/**
	 * The port that this server is bound to
	 * 
	 * @return the port that this server is bound to, or -1 if not yet bound successfully
	 */
	public int getPort()
	{
		return port;
	}
	
	/**
	 * Works out the thread and port and prepends them to the log message before logging it to the
	 * standard logger
	 * 
	 * @param s
	 */
	protected void debug( String s )
	{
		logger.debug( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] " + s );
	}
	
	/**
	 * Works out the thread and port and prepends them to the log message before logging it to the
	 * standard logger
	 * 
	 * @param s
	 */
	protected void debugVerbose( String s )
	{
		verboseLogger.debug( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] " + s );
	}
	
	/**
	 * Works out the thread and port and prepends them to the log message before logging it to the
	 * standard logger
	 * 
	 * @param s
	 */
	protected void info( String s )
	{
		logger.info( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] " + s );
	}
	
	/**
	 * Works out the thread and port and prepends them to the log message before logging it to the
	 * standard logger
	 * 
	 * @param s
	 */
	protected void infoVerbose( String s )
	{
		verboseLogger.info( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] " + s );
	}
	
	/**
	 * Works out the thread and port and prepends them to the log message before logging it to the
	 * standard logger
	 * 
	 * @param s
	 */
	protected void info( String s, Throwable t )
	{
		logger.info( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] " + s, t );
	}
	
	/**
	 * Works out the thread and port and prepends them to the log message before logging it to the
	 * standard logger
	 * 
	 * @param s
	 */
	protected void error( String s )
	{
		logger.error( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] " + s );
	}
	
	/**
	 * Works out the thread and port and prepends them to the log message before logging it to the
	 * standard logger
	 * 
	 * @param s
	 */
	protected void error( String s, Throwable t )
	{
		logger.error( "[" + ((thread == null) ? "NULL" : thread.getName()) + ":" + port + "] " + s, t );
	}
}