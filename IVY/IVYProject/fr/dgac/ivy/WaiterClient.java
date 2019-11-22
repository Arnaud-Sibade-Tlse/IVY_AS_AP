/**
 * @author	Yannick Jestin
 * @author	<a href="http://www.tls.cena.fr/products/ivy/">http://www.tls.cena.fr/products/ivy/</a>
 *
 *  CHANGELOG:
 *  1.2.8:
 *    added a test during the waiting loop
 */

package fr.dgac.ivy ;
import java.util.Hashtable;

class WaiterClient extends IvyApplicationAdapter implements Runnable {
    private static final int INCREMENT = 100;
    int timeout;
    private IvyClient received=null;
    private boolean forever=false;
    private Thread t;
    String name;
    Hashtable clients;

    WaiterClient(String n,int timeout,Hashtable clients) {
      this.timeout=timeout;
      this.clients=clients;
      name=n;
      if (timeout<=0) forever=true;
      t=new Thread(this);
    }

    IvyClient waitForClient() {
      t.start();
      try { t.join(); } catch (InterruptedException ie) { return null; }
      return received;
    }

    public void run() {
      boolean encore=true;
      // System.out.println("DEV WaiterClient start");
      while (encore) {
	try {
	  if (INCREMENT>0) Thread.sleep(INCREMENT);
	  if (!forever) {
	    timeout-=INCREMENT;
	    if (timeout<=0) encore=false;
	  }
	} catch (InterruptedException ie) {
	  break;
	}
        if ((received=Ivy.alreadyThere(clients,name))!=null) break;
      }
      // System.out.println("DEV WaiterClient stop");
    }

    public void connect(fr.dgac.ivy.IvyClient client)  {
      if (name.compareTo(client.getApplicationName())!=0) return;
      received=client;
      t.interrupt();
    }
  }
