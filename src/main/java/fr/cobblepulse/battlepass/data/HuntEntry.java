package fr.cobblepulse.battlepass.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pokemon pouvant etre tire pour une chasse (d'apres CobbleHunts). */
public final class HuntEntry {
    /** Espece (nom, identifiant complet ou nom affiche ; fakemons acceptes). */
    public String espece = "";
    /** Nom de forme Cobblemon (vide = forme normale, "*" = n'importe laquelle). */
    public String forme = "";
    /** Aspects exiges (alolan, galarian...). */
    public List<String> aspects = new ArrayList<>();
    /** "" (indifferent), "male", "femelle" ou "aleatoire" (tire au sort a chaque chasse). */
    public String genre = "";
    /** "" (indifferente), une nature (adamant) ou "aleatoire". */
    public String nature = "";
    /** Nombre de statistiques (tirees au sort) dont l'IV doit atteindre le minimum de chasses.json. */
    public int ivParfaits;
    /** Poids du tirage. */
    public double poids = 1.0;

    public HuntEntry() {}

    public HuntEntry(String espece, String genre, String nature, int ivParfaits) {
        this.espece = espece;
        this.genre = genre;
        this.nature = nature;
        this.ivParfaits = ivParfaits;
    }

    public void normalize() {
        if (espece == null) espece = "";
        espece = espece.trim();
        if (forme == null) forme = "";
        if (aspects == null) aspects = new ArrayList<>();
        aspects.replaceAll(a -> a == null ? "" : a.trim().toLowerCase(Locale.ROOT));
        aspects.removeIf(String::isEmpty);
        if (genre == null) genre = "";
        genre = genre.trim().toLowerCase(Locale.ROOT);
        if (nature == null) nature = "";
        nature = nature.trim().toLowerCase(Locale.ROOT);
        ivParfaits = Math.max(0, Math.min(6, ivParfaits));
        if (poids <= 0) poids = 1.0;
    }
}
