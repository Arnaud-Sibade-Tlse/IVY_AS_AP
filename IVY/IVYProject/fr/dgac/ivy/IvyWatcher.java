/**
 * IvyWatcher, A private Class for the Ivy rendezvous
 *
 * @author	Yannick Jestin
 * @author	Francois-Régis Colin
 * @author	<a href="http://www.tls.cena.fr/products/ivy/">http://www.tls.cena.fr/products/ivy/</a>
 *
 * (C) CENA
 *
 * right now, the rendez vous is either an UDP socket or a TCP multicast.
 * The watcher will answer to
 * each peer advertising its arrival on the bus. The intrinsics of Unix are so
 * that the broadcast is done using the same socket, which is not a good
 * thing.
 *
 * CHANGELOG:
 * 1.2.9:
 *  - added an application Id in the UDP broadcast. It seems to be ok with
 *  most implementations ( VERSION PORT APPID APPNAME \n) is compatible with (VERSION
 *  APPID). If I receive a broadcast with with the same TCP port number,
 *  I ignore the first and accept the new ones
 * 1.2.8:
 *  - alreadyBroadcasted was static, thus Ivy Agents within the same JVM used
 *    to share the list of agents already connected. A nasty bug.
 * 1.2.7:
 *  - better handling of multiple connexions from the same remote agent when
 *    there are different broadcast addresses ( introduced the alreadyBroadcasted
 *    function )
 * 1.2.6:
 *  - IOException now goes silent when we asked the bus to stop()
 *  - use a new buffer for each Datagram received, to prevent an old bug
 * 1.2.5:
 *  - getDomain now sends IvyException for malformed broadcast addresses
 *  - uses apache jakarta-regexp instead of gnu-regexp
 *  - throws an IvyException if the broadcast domain cannot be resolved
 * 1.2.4:
 *  - sends the broadcast before listening to the other's broadcasts.
 *    I can't wait for all the broadcast to be sent before starting the listen
 *    mode, otherwise another agent behaving likewise could be started
 *    meanwhile, and one would not "see" each other.
 *  - (REMOVED) allows the connexion from a remote host with the same port number
 *    it's too complicated to know if the packet is from ourselves...
 *  - deals with the protocol errors in a more efficient way. The goal is not
 *    to loose our connectivity because of a rude agent.
 *    fixes Bug J005 (YJ + JPI)
 * 1.2.3:
 *  - the packet sending is done in its own thread from now on (PacketSender)
 *    I don't care stopping it, since it can't be blocked.
 *  - checks whether I have been interrupted just after the receive (start()
 *  then stop() immediately).
 * 1.2.1:
 *  - can be Interrupted during the broadcast Send. I catch the
 *    and do nothing with it. InterruptedIOException
 *  - changed the fill character from 0 to 10, in order to prevent a nasty bug
 *    on Windows XP machines
 *  - fixed a NullPointerException while trying to stop a Thread before having
 *    created it.
 * 1.0.12:
 *  - setSoTimeout on socket
 *  - the broadcast reader Thread goes volatile
 * 1.0.10:
 *  - isInDomain() is wrong  in multicast. I've removed it
 *  - there was a remanence effect in the datagrampacket buffer. I clean it up after each message
 *  - cleaned up the getDomain() and getPort() code 
 *  - close message sends an interruption on all threads for a clean exit
 *  - removed the timeout bug eating all the CPU resources
 *  - now handles a Vector of broadcast listeners
 */
package fr.dgac.ivy ;
import java.lang.Thread;
import java.net.*;
import java.io.*;
import org.apache.regexp.*;
import java.util.Hashtable;

class IvyWatcher implements Runnable {
  private static boolean debug = (System.getProperty("IVY_DEBUG")!=null);
  private boolean isMulticastAddress = false;
  private boolean alreadyIgnored = false;
  private Ivy bus;			/* master bus controler */
  private DatagramSocket broadcast;	/* supervision socket */
  private InetAddress localhost,loopback;
  private String domainaddr;
  private int port;
  private volatile Thread listenThread = null;
  private InetAddress group;
  private static int serial=0;
  private int myserial=serial++;
  private String busWatcherId = null;

  /**
   * creates an Ivy watcher
   * @param bus the bus
   * @param net the domain
   */
  IvyWatcher(Ivy bus,String domainaddr,int port) throws IvyException {
    this.bus = bus;
    this.domainaddr=domainaddr;
    this.port=port;
    busWatcherId=bus.getWatcherId();
    listenThread = new Thread(this);
    // create the MulticastSocket
    try {
      group = InetAddress.getByName(domainaddr);
      broadcast = new MulticastSocket(port);
      if (group.isMulticastAddress()) {
	isMulticastAddress = true;
	((MulticastSocket)broadcast).joinGroup(group);
      } 
      broadcast.setSoTimeout(Ivy.TIMEOUTLENGTH);
      localhost=InetAddress.getLocalHost();
      loopback=InetAddress.getByName(null);
    } catch ( UnknownHostException uhe ) {
    } catch ( IOException e ) {
      throw new IvyException("IvyWatcher I/O error" + e );
    }
  }
  
  /**
   * the behaviour of each thread watching the UDP socket.
   */
  public void run() {
    traceDebug("Thread started"); // THREADDEBUG
    Thread thisThread=Thread.currentThread();
    traceDebug("beginning of a watcher Thread");
    InetAddress remotehost=null;
    try {
      int remotePort=0;
      while( listenThread==thisThread ) {
	try {
	  byte buf[] = new byte[256];
	  DatagramPacket packet=new DatagramPacket(buf,buf.length);
	  broadcast.receive(packet);
	  if (listenThread!=thisThread) break; // I was summoned to leave during the receive
	  String msg = new String(buf,0,packet.getLength());
	  String remotehostname=null;
	  try {
	    remotehost = packet.getAddress();
	    remotehostname = remotehost.getHostName();
	    RE re  = new RE("([0-9]*) ([0-9]*)");
	    if (!(re.match(msg))) {
	      System.err.println("Ignoring bad format broadcast from "+
		  remotehostname+":"+packet.getPort());
	      continue;
	    }
	    int version = Integer.parseInt(re.getParen(1));
	    if ( version < Ivy.PROTOCOLMINIMUM ) {
	      System.err.println("Ignoring bad format broadcast from "+
		  remotehostname+":"+packet.getPort()
		  +" protocol version "+remotehost+" we need "+Ivy.PROTOCOLMINIMUM+" minimum");
	      continue;
	    }
	    remotePort = Integer.parseInt(re.getParen(2));
	    if (bus.applicationPort==remotePort) { // if (same port number)
	      RE reId  = new RE("([0-9]*) ([0-9]*) ([^ ]*) (.*)");
	      if (reId.match(msg)&&(busWatcherId!=null)) {
	        traceDebug("there's an appId: "+reId.getParen(3));
		String otherId=reId.getParen(3);
		String otherName=reId.getParen(4);
	        if (busWatcherId.compareTo(otherId)==0) {
		  // same port #, same bus Id, It's me, I'm outta here
		  traceDebug("ignoring my own broadcast");
		  continue;
		} else {
		  // same port #, different bus Id, it's another agent
		  // implementing the Oh Soooo Cool watcherId undocumented
		  // unprotocolar Ivy add on 
		  traceDebug("accepting a broadcast from a same port by "+otherName);
		}
	      } else {
	        // there's no watcherId in the broacast. I fall back to a
		// crude strategy: I ignore the first broadcast with the same
		// port number, and accept the following ones
		if (alreadyIgnored) {
		  traceDebug("received another broadcast from "+ remotehostname+":"+packet.getPort()
		      +" on my port number ("+remotePort+"), it's probably someone else");
		} else {
		  alreadyIgnored=true;
		  traceDebug("ignoring a broadcast from "+ remotehostname+":"+packet.getPort()
		      +" on my port number ("+remotePort+"), it's probably me");
		  continue;
		}
	      }
	    } // end if (same port #)
	    traceDebug("broadcast accepted from " +remotehostname
	      +":"+packet.getPort()+", port:"+remotePort+", protocol version:"+version);
	    if (!alreadyBroadcasted(remotehost.toString(),remotePort)) {
	      traceDebug("no known agent originating from " + remotehost + ":" + remotePort);
	      try {
	        bus.createIvyClient(new Socket(remotehost,remotePort),remotePort,false);
	      } catch ( java.net.ConnectException jnc ) {
		traceDebug("cannot connect to "+remotehostname+":"+remotePort+", he probably stopped his bus");
	      }
	    } else {
	      traceDebug("there is already a request originating from " + remotehost + ":" + remotePort);
	    }
	  } catch (RESyntaxException ree) {
	    ree.printStackTrace();
	    System.exit(-1);
	  } catch (NumberFormatException nfe) {
	    System.err.println("Ignoring bad format broadcast from "+remotehostname);
	    continue;
	  } catch ( UnknownHostException e ) {
	    System.err.println("Unkonwn host "+remotehost +","+e.getMessage());
	  } catch ( IOException e) {
	    System.err.println("can't connect to "+remotehost+" port "+ remotePort+e.getMessage());
	    e.printStackTrace();
	  }
	} catch (InterruptedIOException jii ){
	  if (thisThread!=listenThread) { break ;}
	}
      } // while
    } catch (java.net.SocketException se ){
      if (thisThread==listenThread) {
	traceDebug("socket exception, continuing anyway on other Ivy domains "+se);
      }
    } catch (IOException ioe ){
      System.out.println("IO Exception, continuing anyway on other Ivy domains "+ioe);
    }
    traceDebug("Thread stopped"); // THREADDEBUG
  } 

  /**
   * stops the thread waiting on the broadcast socket
   */
  synchronized void stop() {
    traceDebug("begining stopping");
    Thread t = listenThread;
    listenThread=null;
    broadcast.close();
    if (t!=null) { t.interrupt(); } // it might not even have been created
    traceDebug("stopped");
  }

  private class PacketSender implements Runnable {
    // do I need multiple packetsenders ? Well, there is one PacketSender per
    // domain.
    DatagramPacket packet;
    String data;
    public PacketSender(String data) {
      this.data=data;
      packet=new DatagramPacket(data.getBytes(),data.length(),group,port);
      new Thread((PacketSender.this)).start();
    }
    public void run() {
      traceDebug("PacketSender thread started"); // THREADDEBUG
      try {
	broadcast.send(packet);
      } catch (InterruptedIOException e) {
	// somebody interrupts my IO. Thread, do nothing.
	System.out.println(e.bytesTransferred+" bytes transferred anyway, out of " + data.length());
	e.printStackTrace();
	traceDebug("IO interrupted during the broadcast. Do nothing");
      } catch ( IOException e ) {
	if (listenThread!=null) {
	   System.out.println("Broadcast Error " + e.getMessage()+" continuing anyway");
	   // cannot throw new IvyException in a run ...
	   e.printStackTrace();
	}
      }
      traceDebug("PacketSender thread stopped"); // THREADDEBUG
    }
  }

  synchronized void start() throws IvyException {
    // String hello = Ivy.PROTOCOLVERSION + " " + bus.applicationPort + "\n";
    String hello = Ivy.PROTOCOLVERSION + " " + bus.applicationPort + " "+busWatcherId+" "+bus.selfIvyClient.getApplicationName()+"\n";
    if (broadcast==null) throw new IvyException("IvyWatcher PacketSender null broadcast address");
    new PacketSender(hello); // notifies our arrival on each domain: protocol version + port
    listenThread.start();
  }

  /*
   * since 1.2.7 pre ....
   * went local instead of static ! fixed a nasty bug in 1.2.8
   * checks if there is already a broadcast received from the same address
   * on the same port
   *
   * regoes static ...
   */
  private static Hashtable alreadySocks=new Hashtable();
  private synchronized boolean alreadyBroadcasted(String s,int port) {
    // System.out.println("DEBUUUUUUUG " + s+ ":" + port);
    if (s==null) return false;
    Integer i = (Integer)alreadySocks.get(s);
    if (((i!=null)&&(i.compareTo(new Integer(port)))==0)) return true;
    alreadySocks.put(s,new Integer(port));
    return false;
  }

  /*
  private boolean isInDomain( InetAddress host ){
    return true;
   // TODO check if this function is useful. for now, it always returns true
   // deprecated since we use Multicast. How to check when we are in UDP
   // broadcast ?
   //
    byte rem_addr[] = host.getAddress();
    for ( int i = 0 ; i < domainaddrList.size(); i++ ) {
      byte addr[] = ((InetAddress)domainaddrList.elementAt(i)).getAddress();
      int j ;
      for (  j = 0 ; j < 4 ; j++  )
        if ( (addr[j] != -1) && (addr[j] != rem_addr[j]) ) break;
      if ( j == 4 ) {
        traceDebug( "host " + host + " is in domain\n" );
	return true;
      }
    }
    traceDebug( "host " + host + " Not in domain\n" );
    return false;
  }
  */

  static String getDomain(String net) throws IvyException {
    // System.out.println("debug: net=[" + net+ "]");
    int sep_index = net.lastIndexOf( ":" );
    if ( sep_index != -1 ) { net = net.substring(0,sep_index); }
    try {
      RE numbersPoint = new RE("([0-9]|\\.)+");
      if (!numbersPoint.match(net)) {
	// traceDebug("should only have numbers and point ? I won't add anything... " + net);
	return "127.255.255.255";
	// return net;
      }
      net += ".255.255.255";
      RE exp = new RE( "^(\\d+\\.\\d+\\.\\d+\\.\\d+).*");
      if (!exp.match(net)) {
	System.out.println("Bad broascat addr " + net);
	throw new IvyException("bad broadcast addr");
      }
      net=exp.getParen(1);
    } catch ( RESyntaxException e ){
      System.out.println(e);
      System.exit(0);
    }
    //System.out.println("next domain: "+net);
    return net;
  }

  static int getPort(String net) { // returns 0 if no port is set
    int sep_index = net.lastIndexOf( ":" );
    int port= ( sep_index == -1 ) ? 0 :Integer.parseInt( net.substring( sep_index +1 ));
    // System.out.println("net: ["+net+"]\nsep_index: "+sep_index+"\nport: "+port);
    //System.out.println("next port: "+port);
    return port;
  }

  private void traceDebug(String s){
    if (debug) System.out.println("-->IvyWatcher["+myserial+","+bus.getSerial()+"]<-- "+s);
  }
  
} // class IvyWatcher
