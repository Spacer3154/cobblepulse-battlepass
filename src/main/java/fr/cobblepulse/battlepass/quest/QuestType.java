package fr.cobblepulse.battlepass.quest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Types de quetes : libelle, unite, icone par defaut et conditions pertinentes (pour l'editeur et les descriptions). */
public enum QuestType {
    CAPTURE("capture", "Capturer des Pokémon", "Capturer %n Pokémon", "cobblemon:poke_ball", 1, "pokemon", "balls", "lieu", "moment"),
    VAINCRE_SAUVAGE("vaincre_sauvage", "Vaincre des Pokémon sauvages", "Vaincre %n Pokémon sauvages", "minecraft:iron_sword", 1, "pokemon", "lieu", "moment"),
    VICTOIRE_COMBAT("victoire_combat", "Gagner des combats", "Gagner %n combats", "minecraft:diamond_sword", 1, "combat"),
    EVOLUTION("evolution", "Faire évoluer des Pokémon", "Faire évoluer %n Pokémon", "cobblemon:fire_stone", 1, "pokemon"),
    ECLOSION("eclosion", "Faire éclore des œufs", "Faire éclore %n œufs", "minecraft:egg", 1, "pokemon"),
    ECHANGE("echange", "Échanger des Pokémon", "Échanger %n Pokémon", "cobblemon:link_cable", 1, "pokemon"),
    NIVEAU("niveau", "Faire monter de niveau", "Faire gagner %n niveaux à vos Pokémon", "cobblemon:rare_candy", 1, "pokemon"),
    PECHE_POKEMON("peche_pokemon", "Pêcher des Pokémon", "Pêcher %n Pokémon", "cobblemon:poke_rod", 1, "pokemon", "lieu"),
    FOSSILE("fossile", "Ressusciter des fossiles", "Ressusciter %n fossiles", "cobblemon:helix_fossil", 1, "pokemon"),
    BAIES("baies", "Récolter des baies", "Récolter %n fois des baies", "cobblemon:oran_berry", 1, "objets"),
    NOIGRUMES("noigrumes", "Récolter des noigrumes", "Récolter %n noigrumes", "cobblemon:red_apricorn", 1, "objets"),
    POKEDEX("pokedex", "Compléter le Pokédex", "Enregistrer %n nouvelles espèces au Pokédex", "cobblemon:pokedex_red", 1, "pokemon"),
    MINAGE("minage", "Miner des blocs", "Miner %n blocs", "minecraft:iron_pickaxe", 1, "blocs", "lieu"),
    RECOLTE("recolte", "Récolter des cultures", "Récolter %n cultures mûres", "minecraft:wheat", 1, "blocs", "lieu"),
    TUER("tuer", "Éliminer des créatures", "Éliminer %n créatures", "minecraft:bone", 1, "entites", "lieu"),
    PECHE("peche", "Pêcher (canne à pêche)", "Pêcher %n prises", "minecraft:fishing_rod", 1, "objets"),
    FABRICATION("fabrication", "Fabriquer des objets", "Fabriquer %n objets", "minecraft:crafting_table", 1, "objets"),
    ELEVAGE("elevage", "Faire se reproduire des animaux", "Faire naître %n animaux", "minecraft:wheat", 1),
    MANGER("manger", "Consommer des aliments", "Consommer %n aliments", "minecraft:bread", 1, "objets"),
    DISTANCE("distance", "Parcourir une distance", "Parcourir %n blocs", "minecraft:leather_boots", 100),
    TEMPS_JEU("temps_jeu", "Temps de jeu", "Jouer %n minutes", "minecraft:clock", 1),
    CONNEXION("connexion", "Jours de connexion", "Se connecter %n jours", "minecraft:sunflower", 1),
    LIVRAISON("livraison", "Livrer des objets", "Livrer %n objets", "minecraft:chest", 1, "objets"),
    TOUR_ETAGE("tour_etage", "Tour de Combat : étages", "Gagner %n étages de la Tour de Combat", "minecraft:iron_bars", 1, "etage"),
    TOUR_COMPLETE("tour_complete", "Tour de Combat : terminer", "Terminer %n fois la Tour de Combat", "minecraft:nether_star", 1),
    TOUR_BOSS("tour_boss", "Tour de Combat : Boss du Jour", "Vaincre %n fois le Boss du Jour", "minecraft:wither_skeleton_skull", 1),
    CHASSE("chasse", "Terminer des chasses", "Terminer %n chasses", "minecraft:spyglass", 1, "difficulte", "pokemon"),
    STAT("stat", "Statistique Minecraft", "Augmenter une statistique de %n", "minecraft:writable_book", 1, "stat"),
    MANUELLE("manuelle", "Validée par le staff", "Objectif validé par le staff", "minecraft:name_tag", 1);

    public final String id;
    public final String label;
    public final String phrase;
    public final String icon;
    /** Facteur entre la progression stockee et l'objectif (distance : centimetres -> blocs). */
    public final int scale;
    /** Groupes de conditions : pokemon, balls, lieu, moment, combat, blocs, objets, entites, difficulte, stat, etage. */
    public final List<String> filters;

    private static final Map<String, QuestType> BY_ID = new LinkedHashMap<>();

    static {
        for (QuestType t : values()) BY_ID.put(t.id, t);
    }

    QuestType(String id, String label, String phrase, String icon, int scale, String... filters) {
        this.id = id;
        this.label = label;
        this.phrase = phrase;
        this.icon = icon;
        this.scale = scale;
        this.filters = List.of(filters);
    }

    public static QuestType of(String id) {
        return id == null ? null : BY_ID.get(id);
    }
}
