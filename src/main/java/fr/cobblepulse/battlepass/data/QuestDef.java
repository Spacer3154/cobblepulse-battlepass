package fr.cobblepulse.battlepass.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Definition d'une quete (fichier quetes.json, editeur graphique). */
public final class QuestDef {
    /** Identifiant unique dans sa categorie (lettres, chiffres, _ et -). */
    public String id = "";
    /** Nom affiche (vide = description generee automatiquement). */
    public String nom = "";
    /** Type (voir QuestType : capture, minage, recolte, tour_etage, chasse...). */
    public String type = "capture";
    public int objectif = 1;
    public int points = 100;
    /** Quete reservee aux joueurs premium. */
    public boolean premium;
    /** Icone (identifiant d'objet, facultatif). */
    public String icone = "";
    public Filters filtres = new Filters();

    /** Conditions facultatives : une liste vide ou une valeur vide = pas de condition. */
    public static final class Filters {
        /** Especes (pikachu, cobblemon:eevee, monpack:volt_mon...). */
        public List<String> especes = new ArrayList<>();
        /** Types (fire / feu, water / eau...). Au moins un des types du Pokemon. */
        public List<String> types = new ArrayList<>();
        /** Etiquettes Cobblemon (legendary/legendaire, mythical/fabuleux, ultra_beast, paradox, baby...). */
        public List<String> etiquettes = new ArrayList<>();
        /** Aspects / formes (alolan, galarian, hisuian...). */
        public List<String> formes = new ArrayList<>();
        public List<String> natures = new ArrayList<>();
        /** Poke Balls (ultra_ball, cobblemon:dusk_ball...). */
        public List<String> balls = new ArrayList<>();
        /** Biomes ou tags de biome (minecraft:plains, #minecraft:is_forest). */
        public List<String> biomes = new ArrayList<>();
        public List<String> dimensions = new ArrayList<>();
        /** Blocs ou tags (minecraft:diamond_ore, #minecraft:logs, #c:ores). */
        public List<String> blocs = new ArrayList<>();
        /** Objets ou tags (minecraft:bread, #minecraft:fishes, cobblemon:oran_berry). */
        public List<String> objets = new ArrayList<>();
        /** Entites ou tags (minecraft:zombie, #minecraft:skeletons). */
        public List<String> entites = new ArrayList<>();
        /** "", "oui" ou "non". */
        public String chromatique = "";
        /** "", "jour" ou "nuit". */
        public String moment = "";
        /** "", "sauvage", "dresseur" ou "joueur". */
        public String combat = "";
        /** "", "male", "femelle" ou "asexue". */
        public String genre = "";
        /** Chasses : "", "facile", "normal", "moyen", "difficile" ou "globale". */
        public String difficulte = "";
        /** Statistique Minecraft (type stat) : "minecraft:custom/minecraft:jump", "minecraft:mined/minecraft:stone"... */
        public String stat = "";
        /** Niveau minimum du Pokemon (0 = aucun) ; etage minimum pour tour_etage. */
        public int niveauMin;
        /** Niveau maximum du Pokemon (0 = aucun). */
        public int niveauMax;

        public void normalize() {
            especes = clean(especes);
            types = clean(types);
            etiquettes = clean(etiquettes);
            formes = clean(formes);
            natures = clean(natures);
            balls = clean(balls);
            biomes = clean(biomes);
            dimensions = clean(dimensions);
            blocs = clean(blocs);
            objets = clean(objets);
            entites = clean(entites);
            chromatique = low(chromatique);
            moment = low(moment);
            combat = low(combat);
            genre = low(genre);
            difficulte = low(difficulte);
            stat = low(stat);
            niveauMin = Math.max(0, niveauMin);
            niveauMax = Math.max(0, niveauMax);
        }

        private static List<String> clean(List<String> in) {
            List<String> out = new ArrayList<>();
            if (in == null) return out;
            for (String s : in) {
                if (s == null) continue;
                String v = s.trim().toLowerCase(Locale.ROOT);
                if (!v.isEmpty() && !out.contains(v)) out.add(v);
            }
            return out;
        }

        private static String low(String s) {
            return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        }
    }

    public void normalize() {
        if (id == null) id = "";
        id = id.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (nom == null) nom = "";
        if (type == null || type.isBlank()) type = "capture";
        type = type.trim().toLowerCase(Locale.ROOT);
        objectif = Math.max(1, objectif);
        points = Math.max(0, points);
        if (icone == null) icone = "";
        if (filtres == null) filtres = new Filters();
        filtres.normalize();
    }
}
