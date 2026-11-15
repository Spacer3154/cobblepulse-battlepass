package fr.cobblepulse.battlepass.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reglages de la saison ({@code config/cobblepulse_battlepass/saison.json}), modifiables dans l'editeur graphique.
 * Les noms de champs sont ecrits en snake_case dans le fichier (ex. {@code pointsParPalier} -> {@code points_par_palier}).
 */
public final class PassConfig {
    /** Numero de la saison en cours (augmente a chaque nouvelle saison : la progression de tous les joueurs repart a zero). */
    public int saison = 1;
    public String nom = "Saison 1";
    /** false = passe ferme (maintenance) : aucune progression, aucune reclamation. */
    public boolean ouvert = true;
    /** Duree de la saison en jours (les grades LuckPerms expirent a la fin de la saison). */
    public int jours = 30;
    /** Debut de la saison (millisecondes epoch). 0 = demarre au prochain lancement du serveur. */
    public long debut;
    /** A la fin de la saison : "fermer" (le passe attend une nouvelle saison) ou "nouvelle_saison" (enchaine automatiquement). */
    public String finDeSaison = "fermer";
    public int paliers = 50;
    public int pointsParPalier = 1000;
    /** Cout de chaque palier (index 0 = palier 1). Vide ou 0 = {@link #pointsParPalier}. */
    public List<Integer> pointsParPalierListe = new ArrayList<>();
    /** Fuseau horaire des remises a zero (connexion, quetes quotidiennes et hebdomadaires). */
    public String fuseau = "Europe/Paris";
    /** Heure de la remise a zero quotidienne (0-23). */
    public int heureReset = 0;
    /** Jour de la remise a zero hebdomadaire (MONDAY ... SUNDAY). */
    public String jourResetHebdo = "MONDAY";
    /** "joueur" : chaque joueur a ses propres quetes tirees au sort ; "global" : les memes pour tout le monde. */
    public String selectionQuetes = "joueur";
    public int quetesQuotidiennes = 3;
    public int quetesQuotidiennesPremium = 1;
    public int quetesHebdomadaires = 5;
    public int quetesHebdomadairesPremium = 2;
    /** Bonus de points (en %) pour les joueurs premium, sur toutes les sources. */
    public int bonusPremiumPourcent = 0;
    /** Annonce dans le chat quand un joueur atteint ces paliers (vide = aucune). */
    public List<Integer> annoncePaliers = new ArrayList<>(List.of(25, 50));
    public Premium premium = new Premium();
    public Connexion connexion = new Connexion();
    public LuckPerms luckperms = new LuckPerms();
    public Etoiles etoiles = new Etoiles();

    public static final class Premium {
        /** false = piste premium desactivee (seule la piste gratuite existe). */
        public boolean actif = true;
        /** Achat en jeu : "aucun" (boutique du serveur / commande), "coins_jobs", "bp_battletower" ou "objet". */
        public String achatEnJeu = "aucun";
        public double prix = 0;
        /** Objet (ou #tag) demande quand achat_en_jeu = "objet". */
        public String objet = "minecraft:diamond";
    }

    public static final class Connexion {
        public boolean actif = true;
        /** Points gagnes a la premiere connexion de chaque jour. */
        public int points = 100;
        /** Bonus supplementaires selon la serie de jours consecutifs. */
        public Map<String, Integer> bonusSerie = new LinkedHashMap<>(Map.of("7", 500, "14", 1000, "28", 2500));
        /** true : un jour manque remet la serie a 1. */
        public boolean serieCasseeSiJourManque = true;
    }

    public static final class LuckPerms {
        /** Attribue les grades LuckPerms (temporaires, jusqu'a la fin de la saison). */
        public boolean actif = true;
        public String groupeGratuit = "passe_gratuit";
        public String groupePremium = "passe_premium";
        /** Poids des groupes crees automatiquement. */
        public int poids = 5;
    }

    public static final class Etoiles {
        /** Texte du placeholder %cobblepulse_battlepass:etoile% (codes &, &#RRGGBB et <#RRGGBB>texte</#>). */
        public String gratuit = "<#C8C8C8>☆</#> ";
        public String premium = "<#FFD24A>★</#> ";
    }

    public void normalize() {
        if (nom == null || nom.isBlank()) nom = "Saison " + saison;
        saison = Math.max(1, saison);
        jours = Math.max(1, Math.min(3650, jours));
        paliers = Math.max(1, Math.min(1000, paliers));
        pointsParPalier = Math.max(1, pointsParPalier);
        if (pointsParPalierListe == null) pointsParPalierListe = new ArrayList<>();
        heureReset = Math.max(0, Math.min(23, heureReset));
        if (fuseau == null || fuseau.isBlank()) fuseau = "Europe/Paris";
        if (jourResetHebdo == null || jourResetHebdo.isBlank()) jourResetHebdo = "MONDAY";
        if (selectionQuetes == null) selectionQuetes = "joueur";
        if (finDeSaison == null) finDeSaison = "fermer";
        quetesQuotidiennes = Math.max(0, quetesQuotidiennes);
        quetesQuotidiennesPremium = Math.max(0, quetesQuotidiennesPremium);
        quetesHebdomadaires = Math.max(0, quetesHebdomadaires);
        quetesHebdomadairesPremium = Math.max(0, quetesHebdomadairesPremium);
        if (annoncePaliers == null) annoncePaliers = new ArrayList<>();
        if (premium == null) premium = new Premium();
        if (premium.achatEnJeu == null) premium.achatEnJeu = "aucun";
        if (premium.objet == null) premium.objet = "minecraft:diamond";
        if (connexion == null) connexion = new Connexion();
        if (connexion.bonusSerie == null) connexion.bonusSerie = new LinkedHashMap<>();
        if (luckperms == null) luckperms = new LuckPerms();
        if (luckperms.groupeGratuit == null) luckperms.groupeGratuit = "passe_gratuit";
        if (luckperms.groupePremium == null) luckperms.groupePremium = "passe_premium";
        if (etoiles == null) etoiles = new Etoiles();
        if (etoiles.gratuit == null) etoiles.gratuit = "";
        if (etoiles.premium == null) etoiles.premium = "";
    }

    /** Cout (en points) du palier {@code tier} (1..paliers). */
    public int costOf(int tier) {
        if (tier >= 1 && tier <= pointsParPalierListe.size()) {
            Integer v = pointsParPalierListe.get(tier - 1);
            if (v != null && v > 0) return v;
        }
        return pointsParPalier;
    }

    /** Points cumules necessaires pour atteindre le palier {@code tier}. */
    public long totalFor(int tier) {
        long sum = 0;
        for (int t = 1; t <= Math.min(tier, paliers); t++) sum += costOf(t);
        return sum;
    }

    /** Palier atteint avec {@code points} (0..paliers). */
    public int tierFor(long points) {
        long sum = 0;
        int tier = 0;
        for (int t = 1; t <= paliers; t++) {
            sum += costOf(t);
            if (points >= sum) tier = t;
            else break;
        }
        return tier;
    }
}
