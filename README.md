jstatsd
=======

Simple Etsy statsd implementation in Java

Usage
=====

All parameters are set using system vars, like this:

java -Djstatsd.UdpPort=8025 -Djstatsd.GraphiteHost=localhost -Djstatsd.GraphitePort=2003 -Djstatsd.FlushInterval=10 -jar jstatsd.jar

Parameters
 * jstatsd.UdpPort: Udp port to listen to. Default: 8025
 * jstatsd.GraphiteHost: Graphite (Carbon) host to send the metrics. Default: localhost
 * jstatsd.GraphitePort: Graphite (Carbon) port to send the metrics. Default: 2003
 * jstatsd.FlushInterval: Metric flush to graphite interval.  Default: 10 
 * jstatsd.Debug: If initialized to any valie, will flush to stdout instead to grahpite. 
