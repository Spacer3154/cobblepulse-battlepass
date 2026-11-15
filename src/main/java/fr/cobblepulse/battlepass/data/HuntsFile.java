package fr.cobblepulse.battlepass.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chasses (chasses.json), d'apres CobbleHunts de Hysocs (LGPL-3.0) : chasses solo par difficulte et chasses globales.
 * Chaque chasse terminee rapporte des points de passe et, en option, un butin.
 */
public final class HuntsFile {
    public static final List<String> SOLO = List.of("facile", "normal", "moyen", "difficile");
    public static final List<String> ALL = List.of("facile", "normal", "moyen", "difficile", "globale");

    public boolean actif = true;
    public Map<String, Solo> solo = new LinkedHashMap<>();
    public Global globale = new Global();
    /** Lance automatiquement une nouvelle chasse solo des que la recharge est terminee. */
    public boolean acceptationAuto = false;
    /** Le Pokemon remis est retire au joueur. */
    public boolean prendrePokemon = true;
    /** Remise automatique des la capture d'un Pokemon correspondant. */
    public boolean remiseAutoCapture = false;
    /** Seuls les Pokemon captures apres le debut de la chasse sont acceptes. */
    public boolean captureApresDebut = true;
    /** true : une chasse globale remise par un joueur est terminee pour tout le monde. */
    public boolean globalesVerrouilleesPourTous = false;
    /** "poids" : un seul lot tire selon les poids ; "pourcentage" : chaque lot a sa chance (0-1). */
    public String modeButin = "poids";
    /** IV minimum exige sur les statistiques tirees au sort. */
    public int ivMinimum = 20;
    /** Pokemon possibles par difficulte (facile, normal, moyen, difficile, globale). */
    public Map<String, List<HuntEntry>> pokemon = new LinkedHashMap<>();
    /** Butin par difficulte (en plus des points de passe). */
    public Map<String, List<Reward>> butin = new LinkedHashMap<>();

    public static final class Solo {
        public boolean actif = true;
        /** Duree pour capturer et remettre le Pokemon (secondes). */
        public int duree = 1800;
        /** Attente avant la chasse suivante (secondes). */
        public int recharge = 600;
        /** Points de passe gagnes. */
        public int points = 100;
        /** Permission LuckPerms facultative (vide = tout le monde). */
        public String permission = "";

        public Solo() {}

        public Solo(int duree, int recharge, int points) {
            this.duree = duree;
            this.recharge = recharge;
            this.points = points;
        }
    }

    public static final class Global {
        public boolean actif = true;
        /** Nombre de chasses globales simultanees. */
        public int simultanees = 3;
        public int duree = 3600;
        public int recharge = 900;
        public int points = 300;
        public String permission = "";
    }

    public Solo solo(String diff) {
        return solo.computeIfAbsent(diff, k -> new Solo());
    }

    public List<HuntEntry> pool(String diff) {
        return pokemon.computeIfAbsent(diff, k -> new ArrayList<>());
    }

    public List<Reward> loot(String diff) {
        return butin.computeIfAbsent(diff, k -> new ArrayList<>());
    }

    public void normalize() {
        if (solo == null) solo = new LinkedHashMap<>();
        if (globale == null) globale = new Global();
        if (pokemon == null) pokemon = new LinkedHashMap<>();
        if (butin == null) butin = new LinkedHashMap<>();
        if (modeButin == null) modeButin = "poids";
        ivMinimum = Math.max(0, Math.min(31, ivMinimum));
        for (String d : SOLO) {
            Solo s = solo(d);
            s.duree = Math.max(30, s.duree);
            s.recharge = Math.max(0, s.recharge);
            s.points = Math.max(0, s.points);
            if (s.permission == null) s.permission = "";
        }
        globale.simultanees = Math.max(1, Math.min(12, globale.simultanees));
        globale.duree = Math.max(30, globale.duree);
        globale.recharge = Math.max(0, globale.recharge);
        globale.points = Math.max(0, globale.points);
        if (globale.permission == null) globale.permission = "";
        for (String d : ALL) {
            List<HuntEntry> p = pool(d);
            p.removeIf(e -> e == null);
            p.forEach(HuntEntry::normalize);
            p.removeIf(e -> e.espece.isEmpty());
            List<Reward> l = loot(d);
            l.removeIf(r -> r == null);
            l.forEach(Reward::normalize);
        }
    }
}
