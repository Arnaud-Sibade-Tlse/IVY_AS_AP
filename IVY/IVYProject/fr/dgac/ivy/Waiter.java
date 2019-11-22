/**
 * @author	Yannick Jestin
 * @author	<a href="http://www.tls.cena.fr/products/ivy/">http://www.tls.cena.fr/products/ivy/</a>
 *
 *  CHANGELOG:
 *  1.2.8:
 *    no more import of java.util.*
 */

package fr.dgac.ivy ;

class Waiter implements Runnable, IvyMessageListener {
    private static final int INCREMENT = 100;
    int timeout;
    private IvyClient received=null;
    private boolean forever=false;
    private Thread t;

    public Waiter(int timeout) {
      this.timeout=timeout;
      if (timeout<=0) forever=true;
      t=new Thread(this);
    }

    public IvyClient waitFor() {
      t.start();
      try { t.join(); } catch (InterruptedException ie) { return null; }
      return received;
    }

    public void run() {
      boolean encore=true;
      // System.out.println("DEV Waiter start");
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
      }
      // System.out.println("DEV Waiter stop");
    }

    public void receive(IvyClient ic, String[] args) {
      received=ic;
      t.interrupt();
    }
  }
