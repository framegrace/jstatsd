package org.javagamesfactory.nioservers;

/**
 * Represents the core internal state of any StringBasedServer (or subclass)
 * <P>
 * All servers are INITIALIZING at the moment of constructor call, and remain in that state until
 * they are started. While attempting to start, the server is STARTED, until it either fails and
 * goes to OFFLINE, or succeeds and goes to RUNNING. Stopping a server immediately sends it to
 * STOPPING until it successfully reaches STOPPED, or fails and goes OFFLINE. 
 */
public enum ServerState
{
	INITIALIZING, STARTED, RUNNING, STOPPING, STOPPED, OFFLINE;
}