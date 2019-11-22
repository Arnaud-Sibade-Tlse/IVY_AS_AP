package IvyImpl;

import fr.dgac.ivy.Ivy;
import fr.dgac.ivy.IvyClient;
import fr.dgac.ivy.IvyException;
import fr.dgac.ivy.IvyMessageListener;

import static newIvy.Constants.*;

public class MyIvyMessageListener implements IvyMessageListener {

    private Ivy ivy;

    public MyIvyMessageListener(Ivy ivy) {
        super();
        this.ivy = ivy;
    }

    @Override
    public void receive(IvyClient client, String[] args) {
        System.out.println("=== receive ===");
        if (args.length > 0) {
            String sansPoint = args[0].replace("... ", "");
            String[] recoVoc = sansPoint.split("Confidence=",10);

            try {
                switch (recoVoc[0]) {
                    case "Reco=carre":
                        this.ivy.sendMsg(SQUARE);
                        break;
                    case "Reco=bigT":
                    	this.ivy.sendMsg(BIG_T);
                    	break;
                    case "Text=vert":
                    	this.ivy.sendMsg(BIG_T);
                    	break;
                    default:
                    	this.ivy.sendMsg(CLEAN_UI);
                    	break;
                }
            } catch (IvyException e) {
                e.printStackTrace();
            }
        }
    }
}

