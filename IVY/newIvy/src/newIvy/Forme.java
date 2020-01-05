package newIvy;

public class Forme {
	private String formeCommande;
	private String couleur;
	private int x, y;
	private int longueur, hauteur;
	
	public Forme() {
		this.setFormeCommande("Rectangle");
		this.setX(0);
		this.setY(0);
		this.setCouleur("Rouge");
		this.setLongueur(200);
		this.setHauteur(100);
	}
	
	public String toString() {
		String f =  "Palette:Creer" +this.getFormeCommande()+ 
		        " x=" + this.getX() +
		        " y=" + this.getY() +
		        " longueur="+getLongueur()+ " hauteur="+getHauteur()+" " +
		        "couleurFond=" + this.getCouleur() +
		        " couleurContour="+this.getCouleur();
		return f;
	}

	public String getFormeCommande() {
		return formeCommande;
	}

	public void setFormeCommande(String formeCommande) {
		this.formeCommande = formeCommande;
	}

	public String getCouleur() {
		return couleur;
	}

	public void setCouleur(String couleur) {
		this.couleur = couleur;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getLongueur() {
		return longueur;
	}

	public void setLongueur(int longueur) {
		this.longueur = longueur;
	}

	public int getHauteur() {
		return hauteur;
	}

	public void setHauteur(int hauteur) {
		this.hauteur = hauteur;
	}
}
