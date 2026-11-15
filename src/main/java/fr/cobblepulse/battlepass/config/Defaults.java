package fr.cobblepulse.battlepass.config;

import com.google.gson.JsonObject;
import fr.cobblepulse.battlepass.data.HuntEntry;
import fr.cobblepulse.battlepass.data.HuntsFile;
import fr.cobblepulse.battlepass.data.MonSpec;
import fr.cobblepulse.battlepass.data.QuestDef;
import fr.cobblepulse.battlepass.data.QuestsFile;
import fr.cobblepulse.battlepass.data.Reward;
import fr.cobblepulse.battlepass.data.RewardsFile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Contenu d'exemple cree au premier demarrage (a adapter dans l'editeur). */
public final class Defaults {
    private Defaults() {}

    public static PassConfig pass() {
        PassConfig c = new PassConfig();
        c.normalize();
        return c;
    }

    // ------------------------------------------------------------------ Recompenses

    public static Reward item(String id, int count) {
        Reward r = Reward.of("objet");
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("count", count);
        r.objet = o;
        return r;
    }

    public static Reward mon(String species, int level, boolean shiny, Consumer<MonSpec> tweak) {
        Reward r = Reward.of("pokemon");
        MonSpec m = new MonSpec();
        m.species = species;
        m.level = level;
        m.shiny = shiny;
        if (tweak != null) tweak.accept(m);
        m.normalize();
        r.pokemon = m;
        return r;
    }

    public static Reward cmd(String name, String icon, String... commands) {
        Reward r = Reward.of("commande");
        r.nom = name;
        r.icone = icon;
        r.commandes = new ArrayList<>(List.of(commands));
        return r;
    }

    public static Reward amount(String type, double amount) {
        Reward r = Reward.of(type);
        r.montant = amount;
        return r;
    }

    public static RewardsFile rewards() {
        RewardsFile f = new RewardsFile();
        for (int t = 1; t <= 50; t++) {
            RewardsFile.Tier tier = f.tierOrCreate(t);
            // Piste gratuite
            if (t == 10) {
                tier.gratuit.add(mon("eevee", 10, false, m -> {
                    m.ivs.put("hp", 31);
                    m.ivs.put("spe", 31);
                    m.ball = "cobblemon:premier_ball";
                }));
            } else if (t == 25) {
                tier.gratuit.add(mon("riolu", 15, false, m -> m.ivs.put("atk", 31)));
            } else if (t == 50) {
                tier.gratuit.add(mon("dratini", 20, false, m -> {
                    m.ivs.put("hp", 31);
                    m.ivs.put("atk", 31);
                    m.ivs.put("spe", 31);
                }));
                tier.gratuit.add(item("cobblemon:master_ball", 1));
            } else if (t % 5 == 0) {
                tier.gratuit.add(item("cobblemon:rare_candy", 2 + t / 10));
            } else if (t % 3 == 0) {
                tier.gratuit.add(item("cobblemon:great_ball", 8));
            } else if (t % 2 == 0) {
                tier.gratuit.add(item("cobblemon:exp_candy_m", 3));
            } else {
                tier.gratuit.add(item("cobblemon:poke_ball", 16));
            }
            // Piste premium
            switch (t) {
                case 1 -> {
                    tier.premium.add(item("cobblemon:ultra_ball", 8));
                    tier.premium.add(amount("bp_battletower", 100));
                }
                case 10 -> tier.premium.add(mon("eevee", 20, true, m -> {
                    m.ivs.put("hp", 31);
                    m.ivs.put("atk", 31);
                    m.ivs.put("spe", 31);
                    m.nature = "jolly";
                    m.ball = "cobblemon:premier_ball";
                }));
                case 20 -> {
                    tier.premium.add(amount("bp_battletower", 500));
                    tier.premium.add(item("cobblemon:ability_capsule", 1));
                }
                case 25 -> tier.premium.add(mon("larvitar", 25, true, m -> {
                    m.nature = "adamant";
                    m.heldItem = "cobblemon:leftovers";
                    for (String s : List.of("hp", "atk", "def")) m.ivs.put(s, 31);
                    m.evs.put("atk", 252);
                    m.evs.put("hp", 252);
                }));
                case 30 -> tier.premium.add(amount("coins_jobs", 500));
                case 40 -> tier.premium.add(cmd("&d1000 points d'expérience", "minecraft:experience_bottle", "xp add %player% 1000 points"));
                case 50 -> {
                    tier.premium.add(mon("dratini", 30, true, m -> {
                        m.nickname = "Saison 1";
                        m.nature = "adamant";
                        for (String s : MonSpec.STATS) m.ivs.put(s, 31);
                        m.evs.put("atk", 252);
                        m.evs.put("spe", 252);
                        m.moves = new ArrayList<>(List.of("dragondance", "extremespeed", "aquatail", "outrage"));
                        m.heldItem = "cobblemon:life_orb";
                        m.ball = "cobblemon:cherish_ball";
                    }));
                    tier.premium.add(item("cobblemon:master_ball", 2));
                }
                default -> {
                    if (t % 5 == 0) tier.premium.add(item("cobblemon:exp_candy_l", 3));
                    else if (t % 2 == 0) tier.premium.add(item("cobblemon:ultra_ball", 6));
                    else tier.premium.add(item("cobblemon:exp_candy_m", 5));
                }
            }
        }
        f.normalize();
        return f;
    }

    // ------------------------------------------------------------------ Quetes

    private static QuestDef q(String id, String nom, String type, int objectif, int points, Consumer<QuestDef.Filters> f) {
        QuestDef d = new QuestDef();
        d.id = id;
        d.nom = nom;
        d.type = type;
        d.objectif = objectif;
        d.points = points;
        if (f != null) f.accept(d.filtres);
        return d;
    }

    private static QuestDef premium(QuestDef d) {
        d.premium = true;
        return d;
    }

    public static QuestsFile quests() {
        QuestsFile f = new QuestsFile();
        f.quotidiennes.add(q("capture_10", "Capturer 10 Pokémon", "capture", 10, 150, null));
        f.quotidiennes.add(q("capture_eau", "Capturer 5 Pokémon de type Eau", "capture", 5, 200, x -> x.types.add("eau")));
        f.quotidiennes.add(q("vaincre_15", "Vaincre 15 Pokémon sauvages", "vaincre_sauvage", 15, 150, null));
        f.quotidiennes.add(q("dresseurs_3", "Gagner 3 combats contre des dresseurs", "victoire_combat", 3, 250, x -> x.combat = "dresseur"));
        f.quotidiennes.add(q("pierre_128", "Miner 128 blocs de pierre", "minage", 128, 150,
                x -> x.blocs.addAll(List.of("minecraft:stone", "minecraft:deepslate", "minecraft:cobblestone"))));
        f.quotidiennes.add(q("minerais_16", "Miner 16 minerais", "minage", 16, 200, x -> x.blocs.add("#c:ores")));
        f.quotidiennes.add(q("recolte_32", "Récolter 32 cultures mûres", "recolte", 32, 150, null));
        f.quotidiennes.add(q("baies_10", "Récolter 10 baies", "baies", 10, 150, null));
        f.quotidiennes.add(q("peche_5", "Pêcher 5 Pokémon", "peche_pokemon", 5, 200, null));
        f.quotidiennes.add(q("niveaux_10", "Faire gagner 10 niveaux à vos Pokémon", "niveau", 10, 150, null));
        f.quotidiennes.add(q("monstres_20", "Éliminer 20 monstres", "tuer", 20, 150,
                x -> x.entites.addAll(List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider",
                        "minecraft:husk", "minecraft:drowned", "minecraft:stray"))));
        f.quotidiennes.add(q("tour_3", "Gagner 3 étages de la Tour de Combat", "tour_etage", 3, 250, null));
        f.quotidiennes.add(q("chasse_1", "Terminer une chasse", "chasse", 1, 200, null));
        f.quotidiennes.add(premium(q("nuit_3", "Capturer 3 Pokémon de nuit", "capture", 3, 300, x -> x.moment = "nuit")));
        f.quotidiennes.add(premium(q("pain_32", "Livrer 32 pains", "livraison", 32, 300, x -> x.objets.add("minecraft:bread"))));

        f.hebdomadaires.add(q("capture_75", "Capturer 75 Pokémon", "capture", 75, 1000, null));
        f.hebdomadaires.add(q("evolution_10", "Faire évoluer 10 Pokémon", "evolution", 10, 800, null));
        f.hebdomadaires.add(q("eclosion_5", "Faire éclore 5 œufs", "eclosion", 5, 800, null));
        f.hebdomadaires.add(q("victoires_25", "Gagner 25 combats", "victoire_combat", 25, 800, null));
        f.hebdomadaires.add(q("diamant_8", "Miner 8 minerais de diamant", "minage", 8, 1000,
                x -> x.blocs.addAll(List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"))));
        f.hebdomadaires.add(q("recolte_256", "Récolter 256 cultures mûres", "recolte", 256, 800, null));
        f.hebdomadaires.add(q("pokedex_10", "Enregistrer 10 nouvelles espèces au Pokédex", "pokedex", 10, 1000, null));
        f.hebdomadaires.add(q("chasses_difficiles", "Terminer 2 chasses difficiles", "chasse", 2, 1200, x -> x.difficulte = "difficile"));
        f.hebdomadaires.add(q("boss_du_jour", "Vaincre le Boss du Jour de la Tour", "tour_boss", 1, 1000, null));
        f.hebdomadaires.add(q("distance_5000", "Parcourir 5 000 blocs", "distance", 5000, 600, null));
        f.hebdomadaires.add(q("echanges_3", "Échanger 3 Pokémon", "echange", 3, 600, null));
        f.hebdomadaires.add(premium(q("ultra_10", "Capturer 10 Pokémon dans une Hyper Ball", "capture", 10, 1200,
                x -> x.balls.add("cobblemon:ultra_ball"))));
        f.hebdomadaires.add(premium(q("fossiles_2", "Ressusciter 2 fossiles", "fossile", 2, 1000, null)));

        f.saison.add(q("capture_500", "Capturer 500 Pokémon", "capture", 500, 5000, null));
        f.saison.add(q("legendaire_1", "Capturer un Pokémon légendaire", "capture", 1, 5000, x -> x.etiquettes.add("legendaire")));
        f.saison.add(q("chromatique_1", "Capturer un Pokémon chromatique", "capture", 1, 4000, x -> x.chromatique = "oui"));
        f.saison.add(q("tour_complete", "Terminer la Tour de Combat", "tour_complete", 1, 5000, null));
        f.saison.add(q("connexion_20", "Se connecter 20 jours", "connexion", 20, 3000, null));
        f.saison.add(q("chasses_25", "Terminer 25 chasses", "chasse", 25, 4000, null));
        f.saison.add(q("pokedex_100", "Enregistrer 100 espèces au Pokédex", "pokedex", 100, 5000, null));
        f.saison.add(premium(q("evolution_100", "Faire évoluer 100 Pokémon", "evolution", 100, 5000, null)));
        f.saison.add(premium(q("pvp_10", "Gagner 10 combats contre des joueurs", "victoire_combat", 10, 3000, x -> x.combat = "joueur")));
        f.normalize();
        return f;
    }

    // ------------------------------------------------------------------ Chasses (d'apres les reserves par defaut de CobbleHunts)

    private static List<HuntEntry> entries(String genre, String[] species, String[] natures, int ivs) {
        List<HuntEntry> out = new ArrayList<>();
        for (int i = 0; i < species.length; i++) {
            String g = genre == null ? "" : (genre.equals("alt") ? (i % 2 == 0 ? "male" : "femelle") : genre);
            out.add(new HuntEntry(species[i], g, natures == null ? "" : natures[i % natures.length], ivs));
        }
        return out;
    }

    public static HuntsFile hunts() {
        HuntsFile h = new HuntsFile();
        h.solo.put("facile", new HuntsFile.Solo(1800, 600, 100));
        h.solo.put("normal", new HuntsFile.Solo(1800, 900, 200));
        h.solo.put("moyen", new HuntsFile.Solo(2700, 1200, 350));
        h.solo.put("difficile", new HuntsFile.Solo(3600, 1800, 600));
        String[] easy = {"pidgey", "rattata", "caterpie", "weedle", "zigzagoon", "bidoof", "patrat", "lillipup", "pidove", "sentret"};
        h.pokemon.put("facile", entries(null, easy, null, 0));
        h.pokemon.put("normal", entries("alt", new String[]{"eevee", "pikachu", "buneary", "shinx", "staravia", "bibarel", "watchog",
                "herdier", "tranquill", "furret"}, null, 0));
        h.pokemon.put("moyen", entries("alt", new String[]{"growlithe", "machop", "vulpix", "sandshrew", "geodude", "ponyta", "doduo",
                "seel", "grimer", "shellder"}, new String[]{"adamant", "brave", "timid", "impish", "relaxed", "jolly", "adamant", "calm",
                "bold", "naughty"}, 0));
        h.pokemon.put("difficile", entries("alt", new String[]{"dragonite", "snorlax", "lapras", "aerodactyl", "garchomp", "milotic",
                "tyranitar", "salamence"}, new String[]{"adamant", "relaxed", "modest", "jolly", "jolly", "bold", "adamant", "naive"}, 2));
        h.pokemon.put("globale", entries(null, new String[]{"eevee", "pikachu", "growlithe", "ponyta", "machop", "abra", "gastly",
                "magikarp", "dratini", "larvitar"}, null, 0));
        h.butin.put("facile", new ArrayList<>(List.of(item("cobblemon:poke_ball", 10), item("cobblemon:oran_berry", 5))));
        h.butin.put("normal", new ArrayList<>(List.of(item("cobblemon:great_ball", 8), item("cobblemon:exp_candy_s", 4))));
        h.butin.put("moyen", new ArrayList<>(List.of(item("cobblemon:ultra_ball", 6), item("cobblemon:exp_candy_m", 4))));
        h.butin.put("difficile", new ArrayList<>(List.of(item("cobblemon:ultra_ball", 12), item("cobblemon:rare_candy", 3))));
        h.butin.put("globale", new ArrayList<>(List.of(item("cobblemon:great_ball", 10), amount("bp_battletower", 50))));
        h.normalize();
        return h;
    }
}
