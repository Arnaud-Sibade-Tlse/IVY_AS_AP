package IvyImpl;

import fr.dgac.ivy.Ivy;
import fr.dgac.ivy.IvyClient;
import fr.dgac.ivy.IvyException;
import fr.dgac.ivy.IvyMessageListener;
import newIvy.Forme;

import static newIvy.Constants.*;

public class MyIvyMessageListener implements IvyMessageListener {

    private Ivy ivy;
    private Forme forme;
    
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
                        this.forme = new Forme();
                        this.forme.setFormeCommande("Palette:CreerRectangle");
                        this.forme.setLongueur(100);
                        this.forme.setHauteur(100);
                        break;
                    case "Text=carre":
                    	this.forme = new Forme();
                    	this.forme.setFormeCommande("Palette:CreerRectangle");
                    	this.forme.setLongueur(100);
                    	this.forme.setHauteur(100);
                    	break;
                    case "Reco=rectangle":
                    	this.forme = new Forme();
                    	this.forme.setFormeCommande("Palette:CreerRectangle");
                    	this.forme.setLongueur(100);
                    	this.forme.setHauteur(100);
                    	break;
                    case "Text=rectangle":
                    	this.forme = new Forme();
                    	this.forme.setFormeCommande("Palette:CreerRectangle");
                    	this.forme.setLongueur(100);
                    	this.forme.setHauteur(100);
                    	break;
                    case "Reco=elipse":
                    	this.forme = new Forme();
                    	this.forme.setFormeCommande("Palette:CreerCercle");
                    	this.forme.setLongueur(200);
                    	this.forme.setHauteur(100);
                    	break;
                    case "Text=elipse":
                    	this.forme = new Forme();
                    	this.forme.setFormeCommande("Palette:CreerCercle");
                    	this.forme.setLongueur(200);
                    	this.forme.setHauteur(100);
                    	break;
                    case "Text=vert":
                    	this.forme.setCouleur("green");
                    	break;
                    case "Text=jaune":
                    	this.forme.setCouleur("yellow");
                    	break;
                    case "Text=rouge":
                    	this.forme.setCouleur("red");
                    	break;
                    case "Text=noir":
                    	this.forme.setCouleur("black");
                    	break;
                    case "Text=bleu":
                    	this.forme.setCouleur("blue");
                    	break;
                    case "Text=blanc":
                    	this.forme.setCouleur("white");
                    	break;
                    case "Text=netoyer la palette":
                    	this.ivy.sendMsg(CLEAN_UI);
                    	break;
                    case "Text=supprimer":
                    	this.ivy.sendMsg(CLEAN_UI);
                    	break;
                    case "Text=valider":
                    	this.ivy.sendMsg(forme.toString());
                    	forme=null;
                    	break;
                    case "Text=ici":
                        this.forme.setX(100);
                        this.forme.setY(100);
                        break;
                    default:
                    	break;
                }
            } catch (IvyException e) {
                e.printStackTrace();
            }
        }
    }
}

