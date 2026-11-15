package fr.cobblepulse.battlepass.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dossier d'un joueur pour la saison en cours (data/players/[uuid].json ou table cp_bp_players). */
public final class PlayerData {
    public String uuid;
    public String name = "";
    /** Saison a laquelle appartient cette progression (remise a zero automatique au changement de saison). */
    public int saison;
    public long points;
    public boolean premium;
    /** Origine du premium : commande, achat, luckperms. */
    public String premiumSource = "";
    public long premiumDepuis;
    /** Paliers reclames : "g:5" (gratuit) ou "p:5" (premium). */
    public Set<String> reclames = new LinkedHashSet<>();
    /** Progression des quetes : "q|2026-11-15|capture_10" -> valeur. */
    public Map<String, Long> progression = new HashMap<>();
    /** Quetes terminees (memes cles). */
    public Set<String> terminees = new LinkedHashSet<>();
    /** Connexion quotidienne : dernier jour credite (aaaa-mm-jj), serie en cours, total de jours. */
    public String dernierJour = "";
    public int serie;
    public int joursConnexion;
    public int meilleureSerie;
    /** Paliers dont l'annonce/le titre a deja ete fait. */
    public int dernierPalierAnnonce;

    // ---- Chasses
    /** Chasses solo en cours (difficulte -> chasse). */
    public Map<String, HuntInstance> chasses = new HashMap<>();
    /** Apercu de la prochaine chasse solo (difficulte -> chasse). */
    public Map<String, HuntInstance> apercus = new HashMap<>();
    /** Fin de recharge par difficulte (ms). */
    public Map<String, Long> recharges = new HashMap<>();
    /** Chasses globales deja remises : "[id de la vague]:[index]". */
    public Set<String> globalesFaites = new LinkedHashSet<>();
    /** Captures recentes : uuid du Pokemon -> heure de capture (pour la regle "capture apres le debut"). */
    public Map<String, Long> captures = new LinkedHashMap<>();
    /** Pokemon deja remis (un Pokemon ne sert qu'une fois). */
    public Set<String> pokemonRemis = new LinkedHashSet<>();
    public int chassesTerminees;

    // ---- Statistiques de saison
    public long pointsQuetes;
    public long pointsConnexion;
    public long pointsChasses;
    public long pointsStaff;
    public int quetesTerminees;

    public PlayerData() {}

    public PlayerData(String uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
    }

    public void normalize() {
        if (name == null) name = "";
        if (premiumSource == null) premiumSource = "";
        if (reclames == null) reclames = new LinkedHashSet<>();
        if (progression == null) progression = new HashMap<>();
        if (terminees == null) terminees = new LinkedHashSet<>();
        if (dernierJour == null) dernierJour = "";
        if (chasses == null) chasses = new HashMap<>();
        if (apercus == null) apercus = new HashMap<>();
        if (recharges == null) recharges = new HashMap<>();
        if (globalesFaites == null) globalesFaites = new LinkedHashSet<>();
        if (captures == null) captures = new LinkedHashMap<>();
        if (pokemonRemis == null) pokemonRemis = new LinkedHashSet<>();
        chasses.values().removeIf(h -> h == null);
        apercus.values().removeIf(h -> h == null);
        chasses.values().forEach(HuntInstance::normalize);
        apercus.values().forEach(HuntInstance::normalize);
        trim();
    }

    /** Limite la taille des historiques (captures, Pokemon remis). */
    public void trim() {
        while (captures.size() > 300) {
            String first = captures.keySet().iterator().next();
            captures.remove(first);
        }
        while (pokemonRemis.size() > 500) {
            String first = pokemonRemis.iterator().next();
            pokemonRemis.remove(first);
        }
        while (globalesFaites.size() > 200) {
            String first = globalesFaites.iterator().next();
            globalesFaites.remove(first);
        }
    }

    /** Ne garde que la progression des periodes encore actives. */
    public void prune(Set<String> activePrefixes) {
        List<String> drop = new ArrayList<>();
        for (String k : progression.keySet()) if (!matches(k, activePrefixes)) drop.add(k);
        drop.forEach(progression::remove);
        terminees.removeIf(k -> !matches(k, activePrefixes));
    }

    private static boolean matches(String key, Set<String> prefixes) {
        for (String p : prefixes) if (key.startsWith(p)) return true;
        return false;
    }

    /**
     * Remet a zero la progression de la saison (points, paliers, quetes, connexion, chasses).
     * Le premium n'est retire que si {@code keepPremium} est faux.
     */
    public void resetProgress(boolean keepPremium) {
        points = 0;
        reclames.clear();
        progression.clear();
        terminees.clear();
        dernierJour = "";
        serie = 0;
        joursConnexion = 0;
        meilleureSerie = 0;
        dernierPalierAnnonce = 0;
        chasses.clear();
        apercus.clear();
        recharges.clear();
        globalesFaites.clear();
        chassesTerminees = 0;
        pointsQuetes = 0;
        pointsConnexion = 0;
        pointsChasses = 0;
        pointsStaff = 0;
        quetesTerminees = 0;
        if (!keepPremium) {
            premium = false;
            premiumSource = "";
            premiumDepuis = 0;
        }
    }
}
