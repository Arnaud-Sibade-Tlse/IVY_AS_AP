package newIvy;

public final class Constants {

    private Constants() {
        // restrict instantiation
    }

    public static final String CLEAN_UI=(
        "Palette:CreerRectangle " +
        "x=0 " +
        "y=0 " +
        "longueur=496 " +
        "hauteur=466 " +
        "couleurFond=white " +
        "couleurContour=white"
    );

    public static final String SQUARE=(
        "Palette:CreerRectangle " +
        "x=200 " +
        "y=250 " +
        "couleurFond=255:0:255 " +
        "couleurContour=0:255:0"
    );
    
    public static final String BIG_T=(
            "Palette:CreerRectangle " +
            "x=00 " +
            "y=0 " +
            "longueur=50 "+
            "hauteur=500 "+
            "couleurFond=green " +
            "couleurContour=0:255:0 ,"
        );

    public static final String Elipse=(
            "Palette:CreerElipse " +
            "x=00 " +
            "y=0 " +
            "longueur=50 "+
            "hauteur=500 "+
            "couleurFond=blue "
         );
    
    public static final String suprimer=("Palette:Creer");
    
    public static final String deplacer=("Palette:Creer");
    
}
