package fr.dgac.ivy;

/**
 * this interface specifies the methods of an PingCallback
 *
 * @author	Yannick Jestin
 * @author	<a href="http://www.tls.cena.fr/products/ivy/">http://www.tls.cena.fr/products/ivy/</a>
 *
 * Changelog:
 * 1.2.12
 */

public interface PingCallback {
  /**
   * invoked when a Pong is received
   * @param elapsedTime the elapsed time in milliseconds between the ping and
   * the pong
   */
  void pongReceived(IvyClient ic,int elapsedTime);
}
