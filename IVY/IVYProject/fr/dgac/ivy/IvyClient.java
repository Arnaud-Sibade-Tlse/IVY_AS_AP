/**
 * the peers on the bus.
 *
 * @author	Yannick Jestin
 * @author	<a href="http://www.tls.cena.fr/products/ivy/">http://www.tls.cena.fr/products/ivy/</a>
 *
 * each time a connexion is made with a remote peer, the regexp are exchanged
 * once ready, a ready message is sent, and then we can send messages, 
 * die messages, direct messages, add or remove regexps, or quit. A thread is
 * created for each remote client.
 *
 *  CHANGELOG:
 *  1.2.12
 *  - Ping and Pong are back ...
 *  1.2.8
 *  - no CheckRegexp anymore
 *  - synchronized(regexps) pour le match et le getParen():
 *    quoting http://jakarta.apache.org/regexp/apidocs/org/apache/regexp/RE.html  ,
 *    However, RE and RECompiler are not threadsafe (for efficiency reasons,
 *    and because requiring thread safety in this class is deemed to be a rare
 *    requirement), so you will need to construct a separate compiler or
 *    matcher object for each thread (unless you do thread synchronization
 *    yourself)
 *  - reintroduces bugs for multibus connexions. I can't fix a cross
 *    implementation bug.
 *  1.2.6
 *  - major cleanup to handle simultaneous connections, e.g., between two
 *    busses  within the same process ( AsyncAPI test is very stressful )
 *    I made an assymetric processing to elect the client that should
 *    disconnect based on the socket ports ... might work...
 *  - jakarta regexp are not meant to be threadsafe, so for match() and
 *    compile() must be enclaused in a synchronized block
 *  - now sends back an error message when an incorrect regexp is sent
 *    the message is supposed to be readable
 *  - sendMsg has no more async parameter
 *  1.2.5:
 *  - no more java ping pong
 *  1.2.5:
 *  - use org.apache.regexp instead of gnu-regexp
 *    http://jakarta.apache.org/regexp/apidocs/
 *  1.2.4:
 *  - sendBuffer goes synchronized
 *  - sendMsg now has a async parameter, allowing the use of threads to
 *    delegate the sending of messages
 *  - API change, IvyException raised when \n or \0x3 are present in bus.sendMsg()
 *  - breaks the connexion with faulty Ivy clients (either protocol or invalid
 *    regexps, closes bug J007 (CM))
 *  - sendDie now always requires a reason
 *  - invokes the disconnect applicationListeners at the end of the run()
 *    loop.
 *    closes Bug J006 (YJ)
 *  - changed the access of some functions ( sendRegexp, etc ) to protected
 *  1.2.3:
 *  - silently stops on InterruptedIOException.
 *  - direct Messages
 *  - deals with early stops during readline
 *  1.2.2:
 *  - cleared a bug causing the CPU to be eating when a remote client left the
 *    bus. closes Damien Figarol bug reported on december, 2002. It is handled
 *    in the readline() thread
 *  1.2.1:
 *  - removes a NullPointerException when stops pinging on a pinger that
 *    wasn't even started
 *  1.0.12:
 *  - introducing a Ping and Pong in the protocol, in order to detect the loss of
 *    connection faster. Enabled through the use of -DIVY_PING variable only
 *    the PINGTIMEOUT value in milliseconds allows me to have a status of the
 *    socket guaranteed after this timeout
 *  - right handling of IOExceptions in sendBuffer, the Client is removed from
 *    the bus
 *  - sendDie goes public, so does sendDie(String)
 *  - appName visibility changed from private to protected
 *  1.0.10:
 *  - removed the timeout bug eating all the CPU resources
 */
package fr.dgac.ivy ;
import java.lang.Thread;
import java.net.*;
import java.io.*;
import java.util.*;
import org.apache.regexp.*;

public class IvyClient implements Runnable {
    
  /* the protocol magic numbers */
  final static int Bye = 0;	/* end of the peer */
  final static int AddRegexp = 1;/* the peer adds a regexp */
  final static int Msg = 2 ;	/* the peer sends a message */
  final static int Error = 3; 	/* error message */
  final static int DelRegexp = 4;/* the peer removes one of his regex */
  final static int EndRegexp = 5;/* no more regexp in the handshake */
  final static int SchizoToken = 6; /* avoid race condition in concurrent connexions, aka BeginRegexp in other implementations */
  final static int DirectMsg = 7;/* the peer sends a direct message */
  final static int Die = 8;  /* the peer wants us to quit */
  final static int Ping = 9;  // from outer space
  final static int Pong = 10;  
  final static String MESSAGE_TERMINATOR = "\n"; /* the next protocol will use \r */
  final static String StartArg = "\u0002";/* begin of arguments */
  final static String EndArg = "\u0003"; /* end of arguments */
  final static String escape ="\u001A";
  final static char escapeChar = escape.charAt(0);
  final static char endArgChar = EndArg.charAt(0);
  final static char newLineChar = '\n';

  // private variables
  private final static int MAXPONGCALLBACKS = 10;
  private static int pingSerial = 0;
  private static Integer csMutex=new Integer(0);
  private static int clientSerial=0;	/* an unique ID for each IvyClient */
  private Hashtable PingCallbacksTable = new Hashtable();
  
  private String messages_classes[] = null;
  private Ivy bus;
  private Socket socket;
  private BufferedReader in;
  private OutputStream out;
  private int remotePort=0;
  private volatile Thread clientThread;// volatile to ensure the quick communication
  private Integer clientKey;
  private boolean discCallbackPerformed = false;
  private String remoteHostname="unresolved";

  // protected variables
  String appName="none";
  Hashtable regexps = new Hashtable();
  Hashtable regexpsText = new Hashtable();
  static boolean debug = (System.getProperty("IVY_DEBUG")!=null) ;
  // int protocol;
  private boolean incoming;

  IvyClient() { }

  IvyClient(Ivy bus, Socket socket,int remotePort,boolean incoming) throws IOException {
    synchronized(csMutex) { clientKey=new Integer(clientSerial++); }
    this.bus = bus;
    this.remotePort = remotePort;
    this.incoming = incoming;
    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    out = socket.getOutputStream();
    incoming=(remotePort==0);
    traceDebug(((incoming)?"incoming":"outgoing")+" connection on "+socket);
    this.socket = socket;
    if (!incoming) {
      synchronized(bus) {
	bus.addHalf(this); // register a half connexion
	sendSchizo();
	// the registering (handShake) will take place at the reception of the regexps...
      }
    }
    remoteHostname = socket.getInetAddress().getHostName();
    clientThread = new Thread(this); // clientThread handles the incoming traffic
    clientThread.start();
  }

  // sends our ID, whether we initiated the connexion or not
  // the ID is the couple "host name,application Port", the host name
  // information is in the socket itself, the port is not known if we
  // initiate the connexion
  private void sendSchizo() throws IOException {
    traceDebug("sending our service port "+bus.applicationPort);
    Hashtable tosend=bus.selfIvyClient.regexpsText;
    sendString(SchizoToken,bus.applicationPort,bus.appName);
    for (Enumeration e = tosend.keys(); e.hasMoreElements(); ) {
      Integer ikey = (Integer)e.nextElement();
      sendRegexp(ikey.intValue(),(String)tosend.get(ikey));
    }
    sendString( EndRegexp,0,"");
  }

  synchronized private void handShake() throws IvyException {
    synchronized(bus) {
      bus.removeHalf(this);
      bus.addClient(this);
    }
  }

  public String toString() { return "IC["+clientKey+","+bus.getSerial()+"] "+bus.appName+":"+appName+":"+remotePort; }
  public String toStringExt() {
    return "client socket:"+socket+", remoteport:" + remotePort;
  }

  /**
   *  returns the name of the remote agent.
   */
  public String getApplicationName() { return appName ; }

  /**
   *  returns the host name of the remote agent.
   *  @since 1.2.7
   */
  public String getHostName() { return remoteHostname ; }

  /**
   *  allow an Ivy package class to access the list of regexps at a
   *  given time.
   *  perhaps we should implement a new IvyApplicationListener method to
   *  allow the notification of regexp addition and deletion
   *  The content is not modifyable because String are not mutable, and cannot
   *  be modified once they are create.
   *  @see IvyClient#getRegexpsArray() getRegexpsArray to get a String[] result
   */
  public Enumeration getRegexps() { return regexpsText.elements(); }

  /**
   *  allow an Ivy package class to access the list of regexps at a
   *  given time.
   *  @since 1.2.4
   */
  public String[] getRegexpsArray() {
    String[] s = new String[regexpsText.size()];
    int i=0;
    for (Enumeration e=getRegexps();e.hasMoreElements();)
      s[i++]=(String)e.nextElement();
    return s;
  }

  /**
   * sends a direct message to the peer
   * @param id the numeric value provided to the remote client
   * @param message the string that will be match-tested
   */
  public void sendDirectMsg(int id,String message) throws IvyException {
    if ( (message.indexOf(IvyClient.newLineChar)!=-1)||(message.indexOf(IvyClient.endArgChar)!=-1))
      throw new IvyException("newline character not allowed in Ivy messages");
    sendString(DirectMsg,id,message);
  }
  
  /* closes the connexion to the peer  */
  protected void close(boolean notify) throws IOException {
    traceDebug("closing connexion to "+appName);
    if (notify) sendBye("hasta la vista");
    stopListening();
    socket.close(); // TODO is it necessary ? trying to fix a deadlock
  }

  /**
   * asks the remote client to leave the bus.
   * @param  message the message that will be carried
   */
  public void sendDie(String message) {
    sendString(Die,0,message);
  }

  /**
   * triggers a Ping, and executes the callback
   * @param  pc the callback that will be triggerred (once) when the ponc is
   * received
   */
  public void ping(PingCallback pc) throws IvyException {
    PCHadd(pingSerial,pc);
    sendString(Ping,pingSerial++,"");
  }

  ///////////////////////////////////////////////////
  //
  // PROTECTED METHODS
  //
  ///////////////////////////////////////////////////

  static String decode(String s) { return s.replace(escapeChar,'\n'); }
  static String encode(String s) { return s.replace('\n',escapeChar); }
  Integer getClientKey() { return clientKey ; }
  protected void sendRegexp(int id,String regexp) {sendString(AddRegexp,id,regexp);}
  protected void delRegexp(int id) {sendString(DelRegexp,id,"");}

  protected int sendMsg(String message) {
    int count = 0;
    for (Enumeration e = regexps.keys();e.hasMoreElements();) {
      Integer key = (Integer)e.nextElement();
      RE regexp = (RE)regexps.get(key);
      synchronized (regexp) {
	// re.match fails sometimes when it is called concurrently ..
	// see 28412 on jakarta regexp bugzilla
	if (regexp.match(message))  {
	  count++; // match
	  sendResult(Msg,key,regexp);
	}
      }
    }
    return count;
  }

  ///////////////////////////////////////////////////
  //
  // PRIVATE METHODS
  //
  ///////////////////////////////////////////////////

  /* interrupt the listener thread */
  private void stopListening() {
    Thread t = clientThread;
    if (t==null) return; // we can be summoned to quit from two path at a time
    clientThread=null;
    t.interrupt();
  }

  /*
   * compares two peers the id is the couple (host,service port).
   * true if the peers are similar. This should not happen, it is bad
   */
  protected int compareTo(IvyClient clnt) {
    // return clnt.clientKey.compareTo(clientKey); // Wrong. it's random...
    return (clnt.socket.getPort()-socket.getLocalPort());
  }

  protected boolean equals(IvyClient clnt) {
    if (clnt==this) return false;
    // TODO go beyond the port number ! add some host processing, cf:
    // IvyWatcher ...
    if (remotePort==clnt.remotePort) return true;
    /*
       e.g.
       if (socket.getInetAddress()==null) return false;
       if (clnt.socket.getInetAddress()==null) return false;
       if (!socket.getInetAddress().equals(clnt.socket.getInetAddress())) return false;
    */
    return false;
  }

  /*
   * the code of the thread handling the incoming messages.
   */
  public void run() {
    traceDebug("Thread started");
    Thread thisThread = Thread.currentThread();
    String msg = null;
    try {
      traceDebug("connection established with "+
	  socket.getInetAddress().getHostName()+ ":"+socket.getPort());
    } catch (Exception ie) {
      traceDebug("Interrupted while resolving remote hostname");
    }
    while (clientThread==thisThread) {
      try {
	if ((msg=in.readLine()) != null ) {
	  if (clientThread!=thisThread) break; // early stop during readLine()
	  if (!newParseMsg(msg)) {
	    close(true);
	    break;
	  }
	} else {
	  traceDebug("readline null ! leaving the thead");
	  break;
	}
      } catch (IvyException ie) {
	traceDebug("caught an IvyException");
	ie.printStackTrace();
      } catch (InterruptedIOException ioe) {
	traceDebug("I have been interrupted. I'm about to leave my thread loop");
	if (thisThread!=clientThread) break;
      } catch (IOException e) {
	if (clientThread!=null) {
	  traceDebug("abnormally Disconnected from "+ socket.getInetAddress().getHostName()+":"+socket.getPort());
	}
	break;
      }
    }
    traceDebug("normally Disconnected from "+ appName);
    bus.removeClient(this);
    // invokes the disconnect applicationListeners
    if (!discCallbackPerformed) bus.clientDisconnects(this);
    discCallbackPerformed=true;
    traceDebug("Thread stopped");
  }

  protected synchronized void sendBuffer( String buffer ) throws IvyException {
    buffer += "\n";
    try {
      out.write(buffer.getBytes() );
      out.flush();
    } catch ( IOException e ) {
      traceDebug("I can't send my message to this client. He probably left");
      // first, I'm not a first class IvyClient any more
      bus.removeClient(this);
      // invokes the disconnect applicationListeners
      if (!discCallbackPerformed) bus.clientDisconnects(this);
      discCallbackPerformed=true;
      try {
        close(false);
      } catch (IOException ioe) {
	throw new IvyException("close failed"+ioe.getMessage());
      }
    }
  }

  private void sendString(int type, int id, String arg) {
    try {
      sendBuffer(type+" "+id+StartArg+arg);
    } catch (IvyException ie ) {
      System.err.println("received an exception: " + ie.getMessage());
      ie.printStackTrace();
    }
  }

  private void sendResult(int type,Integer id, RE regexp) {
    try {
      String buffer = type+" "+id+StartArg;
      for(int i=1;i<regexp.getParenCount();i++)
	buffer+=regexp.getParen(i)+EndArg;
      sendBuffer(buffer);
    } catch (IvyException ie ) {
      System.err.println("received an exception: " + ie.getMessage());
      ie.printStackTrace();
    } catch (StringIndexOutOfBoundsException sioobe) {
      System.out.println("arg: "+regexp.getParenCount()+" "+regexp);
      sioobe.printStackTrace();
    }
  }

  private String dumpHex(String s) {
    byte[] b = s.getBytes();
    String outDump = "";
    String zu = "\t";
    for (int i=0;i<b.length;i++) {
      char c = s.charAt(i);
      outDump+=((int)c) + " ";
      zu+= ((c>15) ? c : 'X')+" ";
    }
    outDump += zu;
    return outDump;
  }

  private String dumpMsg(String s) {
    String deb = " \""+s+"\" "+s.length()+" cars, ";
    for (int i=0;i<s.length();i++) {
      deb+= "["+s.charAt(i) + "]:" + (int)s.charAt(i) +", ";
    }
    return s;
  }

  protected boolean newParseMsg(String s) throws IvyException {
    if (s==null) throw new IvyException("null string to parse in protocol");
    byte[] b = s.getBytes();
    int from=0,to=0,msgType;
    Integer msgId;
    while ((to<b.length)&&(b[to]!=' ')) to++;
    // return false au lieu de throw
    if (to>=b.length) {
      System.out.println("Ivy protocol error from "+appName);
      return false;
    }
    try {
      msgType = Integer.parseInt(s.substring(from,to));
    } catch (NumberFormatException nfe) {
      System.out.println("Ivy protocol error on msgType from "+appName);
      return false;
    }
    from=to+1;
    while ((to<b.length)&&(b[to]!=2)) to++;
    if (to>=b.length) {
      System.out.println("Ivy protocol error from "+appName);
      return false;
    }
    try {
      msgId = new Integer(s.substring(from,to));
    } catch (NumberFormatException nfe) {
      System.out.println("Ivy protocol error from "+appName+" "+s.substring(from,to)+" is not a number");
      return false;
    }
    from=to+1;
    switch (msgType) {
      case Die:
	traceDebug("received die Message from " + appName);
	// first, I'm not a first class IvyClient any more
	bus.removeClient(this);
	// invokes the die applicationListeners
	String message=s.substring(from,b.length);
	bus.dieReceived(this,msgId.intValue(),message);
	// makes the bus die
	bus.stop();
	try {
	  close(false);
	} catch (IOException ioe) {
	  throw new IvyException(ioe.getMessage());
	}
	break;
      case Bye:
	// the peer quits
	traceDebug("received bye Message from "+appName);
	// first, I'm not a first class IvyClient any more
	bus.removeClient(this);
	// invokes the die applicationListeners
	if (!discCallbackPerformed) bus.clientDisconnects(this);
	discCallbackPerformed=true;
	try {
	  close(false);
	} catch (IOException ioe) {
	  throw new IvyException(ioe.getMessage());
	}
	break;
      case Pong:
        PCHget(msgId);
	break;
      case Ping:
	sendString(Pong,msgId.intValue(),"");
	break;
      case AddRegexp:
	String regexp=s.substring(from,b.length);
	if ( bus.CheckRegexp(regexp) ) {
	  try {
	    regexps.put(msgId,new RE(regexp));
	    regexpsText.put(msgId,regexp);
	    bus.regexpReceived(this,msgId.intValue(),regexp);
	  } catch (RESyntaxException e) {
	    // the remote client sent an invalid regexp !
	    traceDebug("invalid regexp sent by " +appName+" ("+regexp+"), I will ignore this regexp");
	    sendBuffer(Error+e.toString());
	  }
	} else {
	  // throw new IvyException("regexp Warning exp='"+regexp+"' can't match removing from "+appName);
	  traceDebug("Warning "+appName+" subscribes to '"+regexp+"', it can't match our message filter");
	  bus.regexpReceived(this,msgId.intValue(),regexp);
	}
	break;
      case DelRegexp:
	regexps.remove(msgId);
	String text=(String)regexpsText.remove(msgId);
	bus.regexpDeleted(this,msgId.intValue(),text);
	break;
      case EndRegexp:
	bus.clientConnects(this);
	if (bus.ready_message!=null) sendMsg(bus.ready_message);
	break;
      case Msg:
	Vector v = new Vector();
	while (to<b.length) {
	  while ( (to<b.length) && (b[to]!=3) ) to++;
	  if (to<b.length) {
	    v.addElement(decode(s.substring(from,to)));
	    to++;
	    from=to;
	  }
	}
	String[] tab = new String[v.size()];
	for (int i=0;i<v.size();i++) tab[i]=(String)v.elementAt(i);
	// for developpemnt purposes
	traceDebug(tab);
	bus.selfIvyClient.callCallback(this,msgId,tab);
	break;
      case Error:
	String error=s.substring(from,b.length);
	traceDebug("Error msg "+msgId+" "+error);
  	break;
      case SchizoToken: // aka BeginRegexp in other implementations, or MsgSync
	appName=s.substring(from,b.length);
	remotePort=msgId.intValue();
	traceDebug("the peer sent his service port: "+remotePort);
	if (incoming) {
	  // incoming connexion, I wait for his token to send him mine ...
	  synchronized(bus) {
	    try {
	      bus.addHalf(this);
	      sendSchizo();
	      handShake();
	    } catch (IOException ioe) {
	      throw new IvyException(ioe.toString());
	    }
	  }
	} else {
	  // outgoing connexion
	  // I already have sent him a token
	  handShake();
	}
	break;
      case DirectMsg: 
	String direct=s.substring(from,b.length);
	bus.directMessage( this, msgId.intValue(), direct );
	break;
      default:
        System.out.println("protocol error from "+appName+", unknown message type "+msgType);
	return false;
    }
    return true;
  }

  private void sendBye() {sendString(Bye,0,"");}
  private void sendBye(String message) {sendString(Bye,0,message);}

  private void traceDebug(String s){
    String app="noname";
    int serial=0;
    if (bus!=null) {
      serial=bus.getSerial();
      app=bus.appName;
    }
    if (debug) System.out.println("-->IvyClient["+clientKey+","+serial+"] "+app+" (remote "+appName+")<-- "+s);
  }

  private void traceDebug(String[] tab){
    String s = " string array " + tab.length + " elements: ";
    for (int i=0;i<tab.length;i++) s+="("+tab[i]+") ";
    traceDebug(s);
  }

  void PCHadd(int serial,PingCallback pc) {
    PingCallbacksTable.put(new Integer(serial),new PingCallbackHolder(pc));
    if (PingCallbacksTable.size()>MAXPONGCALLBACKS) {
      // more than MAXPONGCALLBACKS callbacks, we ought to limit to prevent a
      // memory leak
      // TODO remove the first
      Integer smallest=(Integer)new TreeSet(PingCallbacksTable.keySet()).first();
      PingCallbackHolder pch = (PingCallbackHolder)PingCallbacksTable.remove(smallest);
      System.err.println("no response from "+getApplicationName()+" to ping "+smallest+" after "+pch.age()+" ms, discarding");
    }
  }

  void PCHget(Integer serial) {
    PingCallbackHolder pc = (PingCallbackHolder)PingCallbacksTable.remove(serial);
    if (pc==null) {
      System.err.println("warning: pong received for a long lost callback");
      return;
    }
    pc.run();
  }

  private class PingCallbackHolder {
    PingCallback pc;
    long epoch;
    int age() { return (int)(System.currentTimeMillis()-epoch); }
    PingCallbackHolder(PingCallback pc) {
      this.pc=pc;
      epoch=System.currentTimeMillis();
    }
    void run() {
      pc.pongReceived(IvyClient.this,age());
    }
  }

}
