/**
 * IvyDaemon: simple TCP to Ivy relay.
 *
 * @author	Yannick Jestin
 * @author	<a href="http://www.tls.cena.fr/products/ivy/">http://www.tls.cena.fr/products/ivy/</a>
 *
 * This is a sample implementation of an Ivy Daemon, like ivyd
 * sends anonymous messages to an Ivy bus through a simple tcp socket,
 * line by line. The default port is 3456.
 *
 * (c) CENA
 *
 * changelog:
 *   1.2.8
 *    - goes into tools subpackage
 *   1.2.3
 *    - adds the traceDebug
 *    - uses the clientThread paradigm to programm the thread sync
 *    - invalid port number as a command line argument now stops the program
 *    - cleans up the code
 *    - adds a "quiet" option on the command line
 *   1.2.2
 *    - changes the setProperty to a backward compatible construct
 *   1.0.12
 *    - class goes public access !
 */
package fr.dgac.ivy.tools ;
import fr.dgac.ivy.* ;
import java.io.*;
import java.net.*;
import java.util.Properties ;
import gnu.getopt.Getopt;

public class IvyDaemon implements Runnable {


  private ServerSocket serviceSocket;
  private boolean isRunning=false;
  private static boolean debug = (System.getProperty("IVY_DEBUG")!=null) ;
  private volatile Thread clientThread;// volatile to ensure the quick communication
  private Ivy bus;

  public static int DEFAULT_SERVICE_PORT = 3456 ;
  public static final String DEFAULTNAME = "IvyDaemon";
  public static final String helpmsg = "usage: java fr.dgac.ivy.tools.IvyDaemon [options]\n\t-b BUS\tspecifies the Ivy bus domain\n\t-p\tport number, default "+DEFAULT_SERVICE_PORT+"\n\t-n ivyname (default "+DEFAULTNAME+")\n\t-q\tquiet, no tty output\n\t-d\tdebug\n\t-h\thelp\nListens on the TCP port, and sends each line read on the Ivy bus. It is useful to launch one Ivy Daemon and let scripts send their message on the bus.\n";

  private static String name = DEFAULTNAME;
  public static void main(String[] args) throws IvyException, IOException {
    Ivy bus;
    Getopt opt = new Getopt("IvyDaemon",args,"n:b:dqp:h");
    int c;
    int servicePort = DEFAULT_SERVICE_PORT;
    boolean quiet = false;
    String domain=Ivy.getDomain(null);
    while ((c = opt.getopt()) != -1) switch (c) {
    case 'n':
      name=opt.getOptarg();
      break;
    case 'b':
      domain=opt.getOptarg();
      break;
    case 'q':
      quiet=true;
      break;
    case 'd':
      Properties sysProp = System.getProperties();
      sysProp.put("IVY_DEBUG","yes");
      break;
    case 'p':
      String s="";
      try {
        servicePort = Integer.parseInt(s=opt.getOptarg());
      } catch (NumberFormatException nfe) {
        System.out.println("Invalid port number: " + s );
	System.exit(0);
      }
      break;
    case 'h':
    default:
      System.out.println(helpmsg);
      System.exit(0);
    }
    bus=new Ivy(name,name+" ready",null);
    if (!quiet) System.out.println("broadcasting on "+bus.domains(domain));
    bus.start(domain);
    if (!quiet) System.out.println("listening on "+servicePort);
    IvyDaemon d = new IvyDaemon(bus,servicePort);
  }

  public IvyDaemon(Ivy bus,int servicePort) throws IOException {
    this.bus=bus;
    serviceSocket = new ServerSocket(servicePort) ;
    clientThread=new Thread(this);
    clientThread.start();
  }

  /*
   * the service socket reader. 
   * it could be a thread, but as long as we've got one ....
   */
  public void run() {
    Thread thisThread = Thread.currentThread();
    traceDebug("Thread started");
    while ( clientThread==thisThread ) {
      try {
        new SubReader(serviceSocket.accept());
      } catch( IOException e ) {
	traceDebug("TCP socket reader caught an exception " + e.getMessage());
      }
    }
    traceDebug("Thread stopped");
  }

  class SubReader extends Thread {
    BufferedReader in;
    SubReader(Socket socket) throws IOException {
      in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
      start();
    }
    public void run() {
      traceDebug("Subreader Thread started");
      String msg = null;
      try {
	while (true) {
	  msg=in.readLine();
	  if (msg==null) break;
	  try {
	    bus.sendMsg(msg);
	  } catch (IvyException ie) {
	    System.out.println("incorrect characters whithin the message. Not sent");
	  }
	}
      } catch (IOException ioe) {
        traceDebug("Subreader exception ...");
	ioe.printStackTrace();
	System.exit(0);
      }
      traceDebug("Subreader Thread stopped");
    }
  }

  private static void traceDebug(String s){
    if (debug) System.out.println("-->IvyDaemon "+name+"<-- "+s);
  }

}
