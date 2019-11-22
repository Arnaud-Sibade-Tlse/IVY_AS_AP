package newIvy;

import fr.dgac.ivy.Ivy;
import fr.dgac.ivy.IvyApplicationListener;
import fr.dgac.ivy.IvyException;
import fr.dgac.ivy.IvyMessageListener;
import fr.irit.elipse.enseignement.isia.PaletteGraphique;
import fr.irit.ens.$1reco.$1Learning;
import fr.irit.ens.$1reco.$1Recognizer;
import fr.irit.diamant.ivy.viewer.Visionneur;

import IvyImpl.MyIvyApplicationListener;
import IvyImpl.MyIvyMessageListener;

import static newIvy.Constants.CLEAN_UI;

public class IvyBusMain {

    public static void main(String[] args) {

        PaletteGraphique palette = new PaletteGraphique(
            "127.255.255:2010",
            0,
            0,
            500,
            500);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        IvyApplicationListener ivyApplicationListener = new MyIvyApplicationListener();
        Ivy ivy = new Ivy(
            "Arnaud",
            CLEAN_UI,
            ivyApplicationListener);

        try {
            ivy.start("127.255.255:2010");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            IvyMessageListener ivyMessageListener = new MyIvyMessageListener(ivy);
            ivy.bindMsg("OneDollar (.*)", ivyMessageListener);
            ivy.bindMsg("sra5 (.*)", ivyMessageListener);
            ivy.bindMsg("Palette:MouseMoved (.*)", ivyMessageListener);

            System.out.println("Finished");

        } catch (IvyException e) {
            e.printStackTrace();
        }

    }
}
