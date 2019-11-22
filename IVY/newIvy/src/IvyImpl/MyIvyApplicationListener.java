package IvyImpl;

import fr.dgac.ivy.IvyApplicationListener;
import fr.dgac.ivy.IvyClient;

public class MyIvyApplicationListener implements IvyApplicationListener {

    public MyIvyApplicationListener() {
        super();
    }

    @Override
    public void connect(IvyClient client) {
        System.out.println("=== connect ===");
        System.out.println(client);
    }

    @Override
    public void disconnect(IvyClient client) {
        System.out.println("=== disconnect ===");
        System.out.println(client);
    }

    @Override
    public void die(IvyClient client, int id, String msgarg) {
        System.out.println("=== die ===");
        System.out.println(client);
        System.out.println(id);
        System.out.println(msgarg);
    }

    @Override
    public void directMessage(IvyClient client, int id, String msgarg) {
        System.out.println("=== directMessage ===");
        System.out.println(client);
        System.out.println(id);
        System.out.println(msgarg);
    }
}
