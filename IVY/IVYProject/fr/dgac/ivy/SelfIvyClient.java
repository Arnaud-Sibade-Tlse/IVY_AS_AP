/**
 * A private Class for ourself on the bus
 *
 * @author	Yannick Jestin
 * @author	<a href="http://www.tls.cena.fr/products/ivy/">http://www.tls.cena.fr/products/ivy/</a>
 * @since 1.2.4
 *
 *  CHANGELOG:
 *  1.2.7:
 *    - fixes a bug on unbindMsg(String) ( closes Matthieu's burreport )
 *  1.2.6:
 *    - jakarta regexp are not threadsafe, adding extra synch blocks
 *  1.2.5:
 *    - uses apache regexp instead of gnu regexp
 *  1.2.4:
 *    - adds a the threaded option for callbacks
 *    - Matthieu's bugreport on unBindMsg()
 */

package fr.dgac.ivy ;
import java.util.*;
import org.apache.regexp.*;

class SelfIvyClient extends IvyClient {
    
  private Ivy bus;
  private static int serial=0;		/* an unique ID for each regexp */
  private Hashtable callbacks=new Hashtable();
  private Hashtable threadedFlag=new Hashtable();
  private boolean massThreaded=false;

  public void sendDirectMsg(int id,String message) {
    bus.directMessage(this,id,message);
  }
  public void sendDie(String message) { bus.dieReceived(this,0,message); }

  protected SelfIvyClient(Ivy bus,String appName) {
    this.bus=bus;
    // this.protocol=Ivy.PROTOCOLVERSION;
    this.appName=appName;
  }

  synchronized protected int bindMsg(String sregexp, IvyMessageListener callback, boolean threaded ) throws IvyException  {
    // creates a new binding (regexp,callback)
    try {
      RE re=new RE(sregexp);
      Integer key = new Integer(serial++);
      regexps.put(key,re);
      regexpsText.put(key,sregexp);
      callbacks.put(key,callback);
      threadedFlag.put(key,new Boolean(threaded));
      return key.intValue();
    } catch (RESyntaxException ree) {
      throw new IvyException("Invalid regexp " + sregexp);
    }
  }

  synchronized protected void unBindMsg(int id) throws IvyException {
    Integer key = new Integer(id);
    if ( ( regexps.remove(key) == null )
         || (regexpsText.remove(key) == null )
         || (callbacks.remove(key) == null )
         || (threadedFlag.remove(key) == null )
       )
    throw new IvyException("client wants to remove an unexistant regexp "+id);
  }

  // unbinds to the first regexp
  synchronized protected boolean unBindMsg(String re) {
    if (!regexpsText.contains(re)) return false;
    for (Enumeration e=regexpsText.keys();e.hasMoreElements();) {
      Integer k = (Integer)e.nextElement();
      if ( ((String)regexpsText.get(k)).compareTo(re) == 0) {
	try {
	  bus.unBindMsg(k.intValue());
	} catch (IvyException ie) {
	  return false;
	}
	return true;
      }
    }
    return false;
  }

  protected int sendSelfMsg(String message) {
    int count = 0;
    for (Enumeration e = regexps.keys();e.hasMoreElements();) {
      Integer key = (Integer)e.nextElement();
      RE regexp = (RE)regexps.get(key);
      String sre = (String)regexpsText.get(key);
      synchronized(regexp) {
	if (!regexp.match(message)) continue;
	count++;
	callCallback(this,key,toArgs(regexp));
      }
    }
    return count;
  }

  protected void callCallback(IvyClient client, Integer key, String[] tab) {
    IvyMessageListener callback=(IvyMessageListener)callbacks.get(key);
    if (callback==null) {
      traceDebug("Not regexp matching id "+key.intValue()+", it must have been unsubscribed concurrently");
      return; 
      // TODO check that nasty synchro issue, test suite: Request
    }
    Boolean b = (Boolean)threadedFlag.get(key);
    if (callback==null) {
      System.out.println("threadedFlag.get returns null for"+key.intValue()+", it must have been unsubscribed concurrently");
      return;
    }
    boolean threaded=b.booleanValue();
    if (!threaded) {
      // runs the callback in the same thread
      callback.receive(client, tab); // TODO tab can be faulty ?!
    } else {
      // starts a new Thread for each callback ... ( Async API )
      new Runner(callback,client,tab);
    }
  }

  private String[] toArgs(RE re) {
    String[] args = new String[re.getParenCount()-1];
    for(int sub=1;sub<re.getParenCount();sub++) {
      args[sub-1]=re.getParen(sub);
      if (bus.doProtectNewlines) args[sub-1]=decode(args[sub-1]);
    }
    return args;
  }

  public String toString() { return "IvyClient (ourself)"+bus.appName+":"+appName; }

  // a class to perform the threaded execution of each new message
  // this is an experimental feature introduced in 1.2.4
  class Runner implements Runnable {
    IvyMessageListener cb;
    IvyClient c;
    String[] args;
    private Thread t;
    public Runner(IvyMessageListener cb,IvyClient c,String[] a) {
      this.cb=cb;
      this.c=c;
      args=a;
      t=new Thread(Runner.this);
      bus.registerThread(t);
      t.start();
      bus.unRegisterThread(t);
    }
    public void run() { cb.receive(c,args); }
  } // class Runner

  private void traceDebug(String s){
    if (debug)
    System.out.println("-->SelfIvyClient "+bus.appName+":"+appName+"<-- "+s);
  }

}
/* EOF */
