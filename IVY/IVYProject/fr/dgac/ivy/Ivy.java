/**
 * a software bus package
 *
 * @author	Yannick Jestin
 * @author	<a href="http://www.tls.cena.fr/products/ivy/">http://www.tls.cena.fr/products/ivy/</a>
 *
 * (c) CENA 1998-2004
 *
 *<pre>
 *Ivy bus = new Ivy("Dummy agent","ready",null);
 *bus.bindMsg("(.*)",myMessageListener);
 *bus.start(getDomain(null));
 *</pre>
 *
 *  CHANGELOG:
 *  1.2.12:
 *    - directMessage goes protected
 *  1.2.9:
 *    - introducing setFilter()
 *    - introducing IVYRANGE in to allow the bus service socket to start on a
 *    specific port range ( think of firewalls ), using java -DIVYRANGE=4000-5000 e.g.
 *  1.2.8:
 *    - addclient and removeclient going synchronized
 *    - domainaddr goes protected in Domain ( gij compatibility )
 *    - checks if (Client)e.nextElement() each time we want to ...
 *    Multithreaded Enumerations ..., should fix [YJnul05]
 *    - added getDomainArgs(String,String[]) as a facility to parse the
 *    command line in search of a -b domain
 *    - added getWBUId(), un function returning a string ID to perform
 *    queries, computed strings look like IDTest0:1105029280616:1005891134
 *    - empties the watchers vector after a stop(), and handles the "stopped"
 *    better, FIXES FJ's bugreport stop/start
 *  1.2.7:
 *    - minor fixes for accessing static final values
 *  1.2.6:
 *    - added serial numbers for traceDebug
 *    - changed the semantic of -b a,b:port,c:otherport if no port is
 *      specified for a, it take the port from the next one. If none is
 *      specified, it takes DEFAULT_PORT
 *    - no more asynchronous sending of message ( async bind is ok though )
 *      because the tests are sooooo unsuccessful
 *    - use addElement/removeElement instead of add/remove is registering
 *      threads ( jdk1.1 backward compatibility )
 *  1.2.5:
 *    - protection of newlines
 *  1.2.4:
 *    - added an accessor for doSendToSelf
 *    - waitForMsg() and waitForClient() to make the synchronization with
 *      other Ivy agents easier
 *    - with the bindAsyncMsg() to subscribe and perform each callback in a
 *      new Thread
 *    - bindMsg(regexp,messagelistener,boolean) allow to subscribe with a
 *      synchrone/asynch exectution
 *    - API change, IvyException raised when \n or \0x3 are present in bus.sendMsg()
 *    - bindListener are now handled
 *    - removeApplicationListener can throw IvyException
 *    - bus.start(null) now starts on getDomain(null), first the IVYBUS
 *    property, then the DEFAULT_DOMAIN, 127:2010
 *    - bindMsg() now throws an IvyException if the regexp is invalid !!!
 *    BEWARE, this can impact lots of programs ! (fixes J007)
 *    - no more includes the "broadcasting on " in the domain(String) method
 *    - new function sendToSelf(boolean) allow us to send messages to
 *    ourselves
 *  1.2.3:
 *    - adds a IVYBUS property to propagate the domain once set. This way,
 *    children forked through Ivy java can inherit from the current value.
 *    - adds synchronized flags to allow early disconnexion
 *  1.2.2:
 *    added the String domains(String d) function, in order to display the
 *  domain list
 *  1.2.1:
 *    bus.start(null) now starts on DEFAULT_DOMAIN. ( correction 1.2.4 This was not true.)
 *    added the getDomains in order to correctly display the domain list
 *    checks if the serverThread exists before interrupting it
 *    no has unBindMsg(String)
 *  1.2.0:
 *    setSoTimeout is back on the server socket
 *    added a regression test main()
 *    clients is now a Hashtable. the deletion now works better
 *    getIvyClientsByName allows the research of IvyClient by name
 *    getDomain doesnt throw IvyException anymore
 *    removed the close() disconnect(IvyClient c). Fixes a big badaboum bug
 *    getDomain becomes public
 *    adding the sendToSelf feature
 *    fixed the printStackTrace upon closing of the ServerSocket after a close()
 */
package fr.dgac.ivy ;
import java.net.*;
import java.io.*;
import java.util.*;
import gnu.getopt.Getopt;
import org.apache.regexp.*;

public class Ivy implements Runnable {

  /**
   * the name of the application on the bus
   */
  String appName;
  /**
   * the protocol version number
   */
  public static final int PROTOCOLVERSION = 3 ;
  public static final int PROTOCOLMINIMUM = 3 ;
  /**
   * the port for the UDP rendez vous, if none is supplied
   */
  public static final int DEFAULT_PORT = 2010 ;
  /**
   * the domain for the UDP rendez vous
   */
  public static final String DEFAULT_DOMAIN = "127.255.255.255:"+DEFAULT_PORT;
  /**
   * the library version, useful for development purposes only, when java is
   * invoked with -DIVY_DEBUG
   */
  public static final String libVersion ="1.2.12";

  private boolean debug;
  private ServerSocket app;
  private Vector watchers = new Vector();
  private volatile Thread serverThread; // to ensure quick communication of the end
  private Hashtable clients = new Hashtable();
  private Hashtable half = new Hashtable();
  private Vector ivyApplicationListenerList = new Vector();
  private Vector ivyBindListenerList = new Vector();
  private Vector sendThreads = new Vector();
  private String[] filter = null;
  private boolean stopped=true;
  protected int applicationPort;	/* Application port number */
  protected  String ready_message = null;
  protected boolean doProtectNewlines = false ;
  private boolean doSendToSelf = false ;
  protected SelfIvyClient selfIvyClient ;
  public final static int TIMEOUTLENGTH = 1000;
  private static int serial=0;
  private int myserial=serial++;
  static long current = System.currentTimeMillis();
  private static java.util.Random generator = new java.util.Random(current*(serial+1));
  private String watcherId=null;
	
  /**
   * Readies the structures for the software bus connexion.
   *
   * All the dirty work is done un the start() method
   * @see #start
   * @param name The name of your Ivy agent on the software bus
   * @param message The hellow message you will send once ready
   * @param appcb A callback handling the notification of connexions and
   * disconnections, may be null
   */
  public Ivy(String name, String message, IvyApplicationListener appcb) {
    appName = name;
    ready_message =  message;
    debug = (System.getProperty("IVY_DEBUG")!=null);
    if ( appcb != null ) ivyApplicationListenerList.addElement( appcb );
    selfIvyClient=new SelfIvyClient(this,name);
  }

  /**
   * Waits for a message to be received
   *
   * @since 1.2.4
   * @param regexp the message we're waiting for to continue the main thread.
   * @param timeout in millisecond, 0 if infinite
   * @return the IvyClient who sent the message, or null if the timeout is
   * reached
   */
  public IvyClient waitForMsg(String regexp,int timeout) throws IvyException {
    Waiter w  = new Waiter(timeout);
    int re = bindMsg(regexp,w);
    IvyClient ic=w.waitFor();
    unBindMsg(re);
    return ic;
  }

  /**
   * Waits for an other IvyClient to join the bus
   *
   * @since 1.2.4
   * @param name the name of the client we're waiting for to continue the main thread.
   * @param timeout in millisecond, 0 if infinite
   * @return the first IvyClient with the name or null if the timeout is
   * reached
   */
  public IvyClient waitForClient(String name,int timeout) throws IvyException {
    IvyClient ic;
    if (name==null) throw new IvyException("null name given to waitForClient");
    // first check if client with the same name is on the bus
    if ((ic=alreadyThere(clients,name))!=null) return ic;
    // if not enter the waiting loop
    WaiterClient w  = new WaiterClient(name,timeout,clients);
    int i = addApplicationListener(w);
    ic=w.waitForClient();
    removeApplicationListener(i);
    return ic;
  }

  /*
   * since 1.2.8
   */
  static protected IvyClient alreadyThere(Hashtable c,String name) {
    IvyClient ic;
    for (Enumeration e=c.elements();e.hasMoreElements();) {
      try {
	ic = (IvyClient)e.nextElement();
      } catch (ArrayIndexOutOfBoundsException _ ) {
	return null; // with gij, it ... can happen
      }
      if ((ic!=null)&&(name.compareTo(ic.getApplicationName())==0)) return ic;
    }
    return null;
  }

  /**
   * connects the Ivy bus to a domain or list of domains.
   *
   * <li>One thread (IvyWatcher) for each traffic rendezvous (either UDP broadcast or TCPMulticast)
   * <li>One thread (serverThread/Ivy) to accept incoming connexions on server socket
   * <li>a thread for each IvyClient when the connexion has been done
   *
   * @param domainbus a domain of the form 10.0.0:1234, it is similar to the
   * netmask without the trailing .255. This will determine the meeting point
   * of the different applications. Right now, this is done with an UDP
   * broadcast. Beware of routing problems ! You can also use a comma
   * separated list of domains.
   *
   * 1.2.8: goes synchronized. I don't know if it's really useful
   *
   */
  public synchronized void start(String domainbus) throws IvyException {
    if (!stopped) throw new IvyException("cannot start a bus that's already started");
    stopped=false;
    if (domainbus==null) domainbus=getDomain(null);
    Properties sysProp = System.getProperties();
    sysProp.put("IVYBUS",domainbus);
    String range=(String)sysProp.get("IVYRANGE");
    RE rangeRE = new RE("(\\d+)-(\\d+)"); // tcp range min and max
    if ((range!=null)&&rangeRE.match(range)) {
      int rangeMin=Integer.parseInt(rangeRE.getParen(1));
      int rangeMax=Integer.parseInt(rangeRE.getParen(2));
      int index=rangeMin;
      traceDebug("trying to allocate a TCP port between "+rangeMin+" and "+rangeMax);
      boolean allocated=false;
      while (!allocated) try {
        if (index>rangeMax) throw new IvyException("no available port in IVYRANGE" + range );
	app = new ServerSocket(index);
	app.setSoTimeout(TIMEOUTLENGTH);
	applicationPort = app.getLocalPort();
	allocated=true;
      } catch (BindException e) {
        index++;
      } catch (IOException e) {
	throw new IvyException("can't open TCP service socket " + e );
      }
    }
    else try {
      app = new ServerSocket(0);
      app.setSoTimeout(TIMEOUTLENGTH);
      applicationPort = app.getLocalPort();
    } catch (IOException e) {
      throw new IvyException("can't open TCP service socket " + e );
    }
    // app.getInetAddress().getHostName()) is always 0.0.0.0
    traceDebug("lib: "+libVersion+" protocol: "+PROTOCOLVERSION+" TCP service open on port "+applicationPort);

    Domain[] d = parseDomains(domainbus);
    if (d.length==0) throw new IvyException("no domain found in "+domainbus);
    watcherId=getWBUId().replace(' ','*'); // no space in the watcherId
    // readies the rendezvous : an IvyWatcher (thread) per domain bus
    for (int index=0;index<d.length;index++)
      watchers.addElement(new IvyWatcher(this,d[index].domainaddr,d[index].port));
    serverThread = new Thread(this);
    serverThread.start();
    // sends the broadcasts and listen to incoming connexions
    for (int i=0;i<watchers.size();i++){ ((IvyWatcher)watchers.elementAt(i)).start(); }
  }

  public Domain[] parseDomains(String domainbus) {
    StringTokenizer st = new StringTokenizer(domainbus,",");
    Domain[] d = new Domain[st.countTokens()];
    int index=0;
    while  ( st.hasMoreTokens()) {
      String s = st.nextToken() ;
      try {
	d[index++]=new Domain(IvyWatcher.getDomain(s),IvyWatcher.getPort(s));
      } catch (IvyException ie) {
	// do nothing
	ie.printStackTrace();
      }
    }
    // fixes the port values ...
    int lastport = Ivy.DEFAULT_PORT;
    for (index--;index>=0;index--) {
      Domain dom=d[index];
      if (dom.port==0) dom.port=lastport;
      lastport=dom.port;
    }
    return d;
  }

  /**
   * disconnects from the Ivy bus
   */
  public void stop() {
    if (stopped) return;
    stopped=true;
    traceDebug("beginning stopping");
    try {
      // stopping the serverThread
      Thread t=serverThread;
      serverThread=null;
      if (t!=null) t.interrupt(); // The serverThread might be stopped even before having been created
      // System.out.println("IZZZ joining "+t);
      try { t.join(); } catch ( InterruptedException _ ) { }
      // TODO BUG avec gcj+kaffe, le close() reste pendu et ne rend pas la main
      app.close();
      // stopping the IvyWatchers
      for (int i=0;i<watchers.size();i++){ ((IvyWatcher)watchers.elementAt(i)).stop(); }
      watchers.clear();
      // stopping the remaining IvyClients
      for (Enumeration e=clients.elements();e.hasMoreElements();) {
	try {
	  IvyClient c = (IvyClient)e.nextElement();
	  if (c!=null) { c.close(true);removeClient(c); }
	} catch (ArrayIndexOutOfBoundsException _ ) {
	  continue;
	}
      }
    } catch (IOException e) {
     traceDebug("IOexception Stop ");
    }
    traceDebug("end stopping");
  }

  /**
   * Toggles the sending of messages to oneself, the remote client's
   * IvyMessageListeners are processed first, and ourself afterwards.
   * @param b true if you want to send the message to yourself. Default
   * is false
   * @since 1.2.4
   */
  public void sendToSelf(boolean b) {doSendToSelf=b;}

  /**
   * do I send messsages to myself ?
   * @since 1.2.4
   */
  public boolean isSendToSelf() {return doSendToSelf;}

  /**
   * returns our self IvyClient.
   * @since 1.2.4
   */
  public IvyClient getSelfIvyClient() {return selfIvyClient;}

  /**
   * Toggles the encoding/decoding of messages to prevent bugs related to the
   * presence of a "\n"
   * @param b true if you want to enforce encoding of newlines. Default
   * is false. Every receiver will have to decode newlines
   * @since 1.2.5
   * The default escape character is a ESC 0x1A
   */
  public void protectNewlines(boolean b) {doProtectNewlines=b;}

  /**
   * Performs a pattern matching according to everyone's regexps, and sends
   * the results to the relevant ivy agents.
   *
   * @param message A String which will be compared to the regular
   * expressions of the different clients
   * @return returns the number of messages actually sent
   */
  public int sendMsg(String message) throws IvyException {
    int count = 0;
    if (doProtectNewlines)	
      message=IvyClient.encode(message);
    else if ( (message.indexOf(IvyClient.newLineChar)!=-1)||(message.indexOf(IvyClient.endArgChar)!=-1))
      throw new IvyException("newline character not allowed in Ivy messages");
    for ( Enumeration e=clients.elements();e.hasMoreElements();) {
      try {
	IvyClient client = (IvyClient)e.nextElement();
	if (client!=null) count += client.sendMsg(message);
      } catch (ArrayIndexOutOfBoundsException _ ) {
	continue; // gij problem
      }
    }
    if (doSendToSelf) count+=selfIvyClient.sendSelfMsg(message);
    return count;
  }

  /**
   * Subscribes to a regular expression.
   *
   * The callback will be executed with
   * the saved parameters of the regexp as arguments when a message will sent
   * by another agent. A program <em>doesn't</em> receive its own messages.
   * <p>Example:
   * <br>the Ivy agent A performs <pre>b.bindMsg("^Hello (*)",cb);</pre>
   * <br>the Ivy agent B performs <pre>b2.sendMsg("Hello world");</pre>
   * <br>a thread in A will uun the callback cb with its second argument set
   * to a array of String, with one single element, "world"
   * @param sregexp a perl regular expression, groups are done with parenthesis
   * @param callback any objects implementing the IvyMessageListener
   * interface, on the AWT/Swing framework
   * @return the id of the regular expression
   */
  public int bindMsg(String sregexp, IvyMessageListener callback ) throws IvyException  {
    return bindMsg(sregexp,callback,false);
  }
    
  /**
   * Subscribes to a regular expression with asyncrhonous callback execution.
   *
   * Same as bindMsg, except that the callback will be executed in a separate
   * thread each time.
   * WARNING : there is no way to predict the order of execution
   * of the * callbacks, i.e. a message received might trigger a callback before
   * another one sent before
   *
   * @since 1.2.4
   * @param sregexp a perl compatible regular expression, groups are done with parenthesis
   * @param callback any objects implementing the IvyMessageListener
   * interface, on the AWT/Swing framework
   * @return the int ID of the regular expression.
   */
  public int bindAsyncMsg(String sregexp, IvyMessageListener callback ) throws IvyException  {
    return bindMsg(sregexp,callback,true);
  }

  /**
   * Subscribes to a regular expression.
   *
   * The callback will be executed with
   * the saved parameters of the regexp as arguments when a message will sent
   * by another agent. A program <em>doesn't</em> receive its own messages,
   * except if sendToSelf() is set to true.
   * <p>Example:
   * <br>the Ivy agent A performs <pre>b.bindMsg("^Hello (*)",cb);</pre>
   * <br>the Ivy agent B performs <pre>b2.sendMsg("Hello world");</pre>
   * <br>a thread in A will uun the callback cb with its second argument set
   * to a array of String, with one single element, "world"
   * @since 1.2.4
   * @param sregexp a perl regular expression, groups are done with parenthesis
   * @param callback any objects implementing the IvyMessageListener
   * interface, on the AWT/Swing framework
   * @param async  if true, each callback will be run in a separate thread,
   * default is false
   * @return the id of the regular expression
   */
  public int bindMsg(String sregexp, IvyMessageListener callback,boolean async ) throws IvyException {
    // adds the regexp to our collection in selfIvyClient
    int key=selfIvyClient.bindMsg(sregexp,callback,async);
    // notifies the other clients this new regexp
    for (Enumeration e=clients.elements();e.hasMoreElements();) {
      try {
	IvyClient c = (IvyClient)e.nextElement();
	if (c!=null) c.sendRegexp(key,sregexp);
      } catch (ArrayIndexOutOfBoundsException _ ) {
	continue; // gij problem
      }
    }
    return key;
  }

  /**
   * Subscribes to a regular expression for one time only, useful for
   * requests, in cunjunction with getWBUId()
   *
   * The callback will be executed once and only once, and the agent will
   * unsubscribe
   * @since 1.2.8
   * @param sregexp a perl regular expression, groups are done with parenthesis
   * @param callback any objects implementing the IvyMessageListener
   * interface, on the AWT/Swing framework
   * @return the id of the regular expression
   */
  public int bindMsgOnce(String sregexp, IvyMessageListener callback ) throws IvyException  {
    Once once = new Once(callback);
    int id = bindMsg(sregexp,once);
    once.setRegexpId(id);
    return id;
  }

  /**
   * unsubscribes a regular expression
   *
   * @param id the id of the regular expression, returned when it was bound
   */
  public void unBindMsg(int id) throws IvyException {
    selfIvyClient.unBindMsg(id);
    for (Enumeration e=clients.elements();e.hasMoreElements();) {
      try {
	IvyClient ic=(IvyClient)e.nextElement();
	if (ic!=null) ic.delRegexp(id );
      } catch (ArrayIndexOutOfBoundsException _ ) {
	continue;
      }

    }
  }

  /**
   * unsubscribes a regular expression
   *
   * @return a boolean, true if the regexp existed, false otherwise or
   * whenever an exception occured during unbinding
   * @param re the string for the regular expression
   */
  public boolean unBindMsg(String re) { return selfIvyClient.unBindMsg(re); }

  /**
   * adds a bind listener to a bus
   * @param callback is an object implementing the IvyBindListener interface
   * @return the id of the bind listener, useful if you wish to remove it later
   * @since 1.2.4
   */
  public int addBindListener(IvyBindListener callback){
    ivyBindListenerList.addElement(callback);
    return ivyBindListenerList.indexOf(callback);
  }

  /**
   * removes a bind listener
   * @param id the id of the bind listener to remove
   * @since 1.2.4
   */
  public void removeBindListener(int id) throws IvyException {
    try {
      ivyBindListenerList.removeElementAt(id);
    } catch (ArrayIndexOutOfBoundsException aie) {
      throw new IvyException(id+" is not a valid Id");
    }
  }

  /**
   * adds an application listener to a bus
   * @param callback is an object implementing the IvyApplicationListener
   * interface
   * @return the id of the application listener, useful if you wish to remove
   * it later
   */
  public int addApplicationListener(IvyApplicationListener callback){
    ivyApplicationListenerList.addElement(callback);
    return ivyApplicationListenerList.indexOf( callback );
  }

  /**
   * removes an application listener
   * @param id the id of the application listener to remove
   */
  public void removeApplicationListener(int id) throws IvyException {
    try {
      ivyApplicationListenerList.removeElementAt(id);
    } catch (ArrayIndexOutOfBoundsException aie) {
      throw new IvyException(id+" is not a valid Id");
    }
  }

  /**
   * sets the filter expression
   * @param filter the extensive list of strings beginning the messages
   * @since 1.2.9
   *
   * once this filter is set, when a client subscribes to a regexp of the
   * form "^dummystring...", there is a check against the filter list. If no
   * keyword is found to match, the binding is just ignored.
   */
  public void setFilter(String[] filter){ this.filter=filter; }

  /**
   * checks the "validity" of a regular expression if a filter has been set
   * @since 1.2.9
   * @param exp a string regular expression
   * must be synchronized ( RE is not threadsafe )
   */
  private static final RE bounded = new RE("^\\^([a-zA-Z0-9_-]+).*");
  public synchronized boolean CheckRegexp( String exp ) {
    if (filter==null) return true; // there's no message filter
    if (!bounded.match(exp)) return true; // the regexp is not bounded
    //System.out.println("the regexp is bounded, "+bounded.getParen(1));
    // else the regexp is bounded. The matching string *must* be in the filter
    for (int i=0;i<filter.length;i++) {
      String prems = bounded.getParen(1);
      // traceDebug(" classFilter ["+filter[i]+"] vs regexp ["+prems+"]");
      if (filter[i].compareTo(prems)==0) return true;
    }
    return false;
  }

  // a private class used by bindMsgOnce, to ensure that a callback will be
  // executed once, and only once
  private class Once implements IvyMessageListener {
    boolean received=false;
    int id=-1;
    IvyMessageListener callback=null;
    Once(IvyMessageListener callback){this.callback=callback;}
    void setRegexpId(int id){this.id=id;}
    public void receive(IvyClient ic,String[] args){
      synchronized(this) {
	// synchronized because it will most likely be called
	// concurrently, and I *do* want to ensure that it won't
	// execute twice
	if (received||(callback==null)||(id==-1)) return;
	received=true;
	try {Ivy.this.unBindMsg(id);} catch (IvyException ie) { ie.printStackTrace(); }
	callback.receive(ic,args);
      }
    }
  }
    
  /* invokes the application listeners upon arrival of a new Ivy client */
  protected void clientConnects(IvyClient client){
    for ( int i = 0 ; i < ivyApplicationListenerList.size(); i++ ) {
      ((IvyApplicationListener)ivyApplicationListenerList.elementAt(i)).connect(client);
    }
  }

  /* invokes the application listeners upon the departure of an Ivy client */
  protected void clientDisconnects(IvyClient client){
    for ( int i = 0 ; i < ivyApplicationListenerList.size(); i++ ) {
      ((IvyApplicationListener)ivyApplicationListenerList.elementAt(i)).disconnect(client);
    }
  }

  /* invokes the bind listeners */
  protected void regexpReceived(IvyClient client,int id,String sregexp){
    for ( int i = 0 ; i < ivyBindListenerList.size(); i++ ) {
      ((IvyBindListener)ivyBindListenerList.elementAt(i)).bindPerformed(client,id,sregexp);
    }
  }

  /* invokes the bind listeners */
  protected void regexpDeleted(IvyClient client,int id,String sregexp){
    for ( int i = 0 ; i < ivyBindListenerList.size(); i++ ) {
      ((IvyBindListener)ivyBindListenerList.elementAt(i)).unbindPerformed(client,id,sregexp);
    }
  }

  /*
   * invokes the application listeners when we are summoned to die
   * then stops
   */
  protected void dieReceived(IvyClient client, int id,String message){
    for ( int i=0 ;i<ivyApplicationListenerList.size();i++ ) {
      ((IvyApplicationListener)ivyApplicationListenerList.elementAt(i)).die(client,id,message);
    }
  }

  /* invokes the direct message callbacks */
  protected void directMessage( IvyClient client, int id,String msgarg ){
    for ( int i = 0 ; i < ivyApplicationListenerList.size(); i++ ) {
      ((IvyApplicationListener)ivyApplicationListenerList.elementAt(i)).directMessage(client,id, msgarg);
    }
  }

  /**
   * gives the (Vectored) list of IvyClient at a given instant
   */
  public Vector getIvyClients() {
    Vector v=new Vector();
    for (Enumeration e=clients.elements();e.hasMoreElements();) {
      try {
	IvyClient ic=(IvyClient)e.nextElement();
	if (ic!=null) v.addElement(ic);
      } catch (ArrayIndexOutOfBoundsException _) {
	continue;
      }
    }
    return v;
  }

  /**
   * gives a list of IvyClient with the name given in parameter
   *
   * @param name The name of the Ivy agent you're looking for
   */
  public Vector getIvyClientsByName(String name) {
    Vector v=new Vector();
    String icname;
    for (Enumeration e=clients.elements();e.hasMoreElements();) {
      try {
	IvyClient ic = (IvyClient)e.nextElement();
	if ( (ic==null)||((icname=ic.getApplicationName())==null) ) break;
	if (icname.compareTo(name)==0) v.addElement(ic);
      } catch (ArrayIndexOutOfBoundsException _ ) {
	continue;
      }
    }
    return v;
  }

  /**
   * returns the domain bus
   *
   * @param domainbus if non null, returns the argument
   * @return It returns domainbus, if non null,
   * otherwise it returns the IVYBUS property if non null, otherwise it
   * returns Ivy.DEFAULT_DOMAIN
   */
  public static String getDomain(String domainbus) {
    if ( domainbus == null ) domainbus = System.getProperty("IVYBUS");
    if ( domainbus == null ) domainbus = DEFAULT_DOMAIN;
    return domainbus;
  }

  /**
   * returns the domain bus
   *
   * @since 1.2.8
   * @param progname The name of your program, for error message
   * @param args the String[] of arguments passed to your main()
   * @return returns the domain bus, ascending priority : ivy default bus, IVY_BUS
   * property, -b domain on the command line
   */
  public static String getDomainArgs(String progname, String[] args) {
    Getopt opt = new Getopt(progname,args,"b:");
    int c;
    if ( ((c=opt.getopt())!=-1) && c=='b' ) return opt.getOptarg();
    return getDomain(null);
  }

  /**
   * returns a "wana be unique" ID to make requests on the bus
   *
   * @since 1.2.8
   * @return returns a string wich is meant to be noisy enough to be unique
   */
  public String getWBUId() {
    return "ID<"+appName+myserial+":"+nextId()+":"+generator.nextInt()+">";
  }
  private synchronized long nextId() { return current++; }


  /**
   * prints a human readable representation of the list of domains
   *
   * @since 1.2.9
   */
  public String domains(String toparse) {
    String s="";
    Ivy.Domain[] d = parseDomains(toparse);
    for (int index=0;index<d.length;index++) {
      s+=d[index].getDomainaddr()+":"+d[index].getPort()+" ";
    }
    return s;
  }
  /////////////////////////////////////////////////////////////////:
  //
  // Protected methods
  //
  /////////////////////////////////////////////////////////////////:

  protected IvyClient createIvyClient(Socket s,int port, boolean domachin) throws IOException {
    return new IvyClient(this,s,port,domachin);
  }

  protected synchronized void addClient(IvyClient c) {
    if (clients==null||c==null) return;
    synchronized (clients) {
      clients.put(c.getClientKey(),c);
      traceDebug("added "+c+" in clients: "+getClientNames(clients));
    }
  }

  protected synchronized void removeClient(IvyClient c) {
    synchronized (clients) {
      clients.remove(c.getClientKey());
      traceDebug("removed "+c+" from clients: "+getClientNames(clients));
    }
  }

  protected void addHalf(IvyClient c) {
    synchronized(half){half.put(c.getClientKey(),c);}
    traceDebug("added "+c+" in half: "+getClientNames(half));
  }

  protected void removeHalf(IvyClient c) {
    if (half==null||c==null) return;
    synchronized(half){half.remove(c.getClientKey());}
    traceDebug("removed "+c+" from half: "+getClientNames(half));
  }

  private boolean shouldIleave(IvyClient ic) {
    traceDebug("looking for "+ic+" in "+getClientNames(half)+" and "+getClientNames(clients));
    IvyClient peer=searchPeer(ic);
    if (peer==null) return false;
    boolean shoulda=peer.compareTo(ic)>0;
    traceDebug(ic+" "+ic.toStringExt()+((shoulda)?" must leave ":" must not leave"));
    traceDebug(peer+" "+peer.toStringExt()+((!shoulda)?" must leave ":" must not leave"));
    return shoulda;
  }

  private IvyClient searchPeer(IvyClient ic) {
    IvyClient peer;
    for (Enumeration e=half.elements();e.hasMoreElements();) {
      peer=(IvyClient)e.nextElement();
      if ((peer!=null)&&(peer.equals(ic))) return peer;
    }
    synchronized (clients) {
      for (Enumeration e=clients.elements();e.hasMoreElements();){
	peer=(IvyClient)e.nextElement();
	if ((peer!=null)&&(peer.equals(ic))) return peer;
      }
    }
    return null;
  }

  /*
   * the service socket thread reader main loop
   */
  public void run() {
    traceDebug("service thread started"); // THREADDEBUG
    Thread thisThread=Thread.currentThread();
    while(thisThread==serverThread){
      try {
        Socket socket = app.accept();
	if ((thisThread!=serverThread)||stopped) break; // early disconnexion
	createIvyClient(socket,0,true); // the peer called me
      } catch (InterruptedIOException ie) {
	// traceDebug("server socket was interrupted. good");
	if (thisThread!=serverThread) break;
      } catch( IOException e ) {
	if (serverThread==thisThread) {
          traceDebug("Error IvyServer exception:  "  +  e.getMessage());
	  System.out.println("Ivy server socket reader caught an exception " + e.getMessage());
	  System.out.println("this is probably a bug in your JVM ! (e.g. blackdown jdk1.1.8 linux)");
	  System.exit(0);
	} else {
	  traceDebug("my server socket has been closed");
	}
      }
    }
    traceDebug("service thread stopped"); // THREADDEBUG
  }

  protected String getWatcherId() { return watcherId ; }

  protected int getSerial() { return myserial ; }
  private void traceDebug(String s){
    if (debug) System.out.println("-->Ivy["+myserial+"]<-- "+s);
  }

  // stuff to guarantee that all the treads have left
  synchronized void registerThread(Thread t) { sendThreads.addElement(t); }
  synchronized void unRegisterThread(Thread t) { sendThreads.removeElement(t); }
  synchronized Thread getOneThread() {
    if (sendThreads.size()==0) return null;
    return (Thread) sendThreads.firstElement();
  }

  // a small private method for debbugging purposes
  private String getClientNames(Hashtable t) {
    String s = "(";
    for (Enumeration e=t.elements();e.hasMoreElements();){
      IvyClient ic = (IvyClient)e.nextElement();
      if (ic!=null) s+=ic.getApplicationName()+",";
    }
    return s+")";
  }

  private class Domain {
    String domainaddr;
    int port;
    public Domain(String domainaddr,int port) {this.domainaddr=domainaddr;this.port=port;}
    public String toString() {return domainaddr+":"+port;}
    public String getDomainaddr() { return domainaddr; }
    public int getPort() { return port; }
  }

  // test. Normally, running java fr.dgac.ivy.Ivy should stop in 2.3 seconds :)
  public static void main(String[] args) {
    Ivy bus = new Ivy("Test Unitaire","TU ready",null);
    try {
      bus.start(Ivy.getDomainArgs("IvyTest",args));
      System.out.println("waiting 5 seconds for a coucou");
      System.out.println(((bus.waitForMsg("^coucou",5000))!=null)?"coucou received":"coucou not received");
      System.out.println("waiting 5 seconds for IvyProbe");
      System.out.println(((bus.waitForClient("IVYPROBE",5000))!=null)?"Ivyprobe joined the bus":"nobody came");
      System.out.println("random values: "+bus.getWBUId()+", "+bus.getWBUId()+", "+bus.getWBUId());
      bus.stop();
    } catch (IvyException ie) {
      System.out.println("Ivy main test error");
      ie.printStackTrace();
    }
  }
 
} // class Ivy
