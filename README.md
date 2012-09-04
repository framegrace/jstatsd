jstatsd
=======

Simple Etsy statsd implementation in Java plus a simple graphite proxy.
I made this to be able to send probes to a centralized graphite on firewalled environments.

 * All servers are NIO, using apache's MINA networking library. 
 * The client uses a connection pool to allow reliable and scalable data sending. I've used a simple library made by The Distributed Computing Laboratory, at Emory University. Kudos to them.
 * All the jstatsd statistical aggregations are the java transconded version of the original Etsy's statsd. So you don't depend on my rusty stats skills.
 * All jstatsd commands are supported but without sampling.
 * The backend code doesn't have any kind of queuing in place for now. So you depend on the connection pool and a backend that can digest all the data in time. For now, when client timeouts, you lost the data.
 * Future plans for queuing on disconnection. I think I have a nice and simple idea on how to do it, which will allow even more flexibility. Stay tuned.
 * Future plans to allow multiple backends tied to multiple ports, to create a "Graphite router". Other ideas like cloning and load-balancing can also be done. This is all supported by carbon-cache (But not very well documented), so maybe is overkill, but I think will be fun to code.

Usage
=====

You can configure the server using different methods, all based on java Properties.

The paremeters themselves are (With the default values) :

    jstatsd.UdpPort=8200  // Udp port to listen to
    jstatsd.GraphiteHost=localhost // Graphite (Carbon) host to send the metrics
    jstatsd.GraphitePort=9200 // Graphite (Carbon) port to send the metrics
    jstatsd.FlushInterval=10 // Statsd flush interval (in seconds)
    jstatsd.GraphiteProxyPort=9210 // Graphite proxy port to listen to
    jstatsd.debug=No // Enable debug, other than "yes" disabled. If enabled, flushes to stdout instead of backend.

You can use them on command line, like this:

java -Djstatsd.UdpPort=8025 -Djstatsd.GraphiteHost=localhost -Djstatsd.GraphitePort=2003 -Djstatsd.FlushInterval=10 -jar jstatsd.jar

Or you can use a config file (Java properties file, the example above is Ok) like this:

java -Djstatsd.config=/etc/jstastd/config.cnf -jar jstatsd.jar

The defaults are a jstatsd.conf inside the jar, on the top level classpath. You can change them and repackage if you want and have a self contained pre-configured install.

The precedence of the configuration is:
 
  * Command line definitions (override everything)
  * Config file (override internal defaults)
  * jar internal defaults.

Install
=======

Quick and simple: just download dist/jstatsd.jar and run it (Using above examples).

I include the Netbeans project if you want to mess with it, improvements/bugfixes encouraged.


