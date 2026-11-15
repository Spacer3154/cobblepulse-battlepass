package fr.cobblepulse.battlepass.client;

import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.cobblepulse.battlepass.reward.SpeciesLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Suggestions et apercus a partir des registres synchronises sur le client (especes, fakemons, capacites, objets). */
public final class ClientData {
    public static final List<String> NATURES = List.of("hardy", "lonely", "brave", "adamant", "naughty", "bold", "docile", "relaxed",
            "impish", "lax", "timid", "hasty", "serious", "jolly", "naive", "modest", "mild", "quiet", "bashful", "rash", "calm", "gentle",
            "sassy", "careful", "quirky");
    public static final List<String> NATURES_FR = List.of("Hardi", "Solo", "Brave", "Rigide", "Mauvais", "Assuré", "Docile", "Relax",
            "Malin", "Lâche", "Timide", "Pressé", "Sérieux", "Jovial", "Naïf", "Modeste", "Doux", "Discret", "Pudique", "Foufou", "Calme",
            "Gentil", "Malpoli", "Prudent", "Bizarre");

    private ClientData() {}

    private static String id(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static List<String> species(String prefix) {
        String p = id(prefix);
        List<String> out = new ArrayList<>();
        if (p.isEmpty()) return out;
        try {
            for (Species s : PokemonSpecies.getSpecies()) {
                ResourceLocation rid = s.getResourceIdentifier();
                boolean cobblemon = "cobblemon".equals(rid.getNamespace());
                if (id(s.getName()).startsWith(p) || id(rid.getPath()).startsWith(p) || id(rid.toString()).startsWith(p)) {
                    out.add(cobblemon ? rid.getPath() : rid.toString());
                }
                if (out.size() >= 8) break;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static List<String> moves(String prefix) {
        String p = id(prefix);
        List<String> out = new ArrayList<>();
        if (p.isEmpty()) return out;
        try {
            for (String n : Moves.names()) {
                if (id(n).startsWith(p)) out.add(id(n));
                if (out.size() >= 8) break;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static List<String> items(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (p.length() < 2) return out;
        boolean tag = p.startsWith("#");
        if (tag) return out;
        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            String s = rl.toString();
            if (s.startsWith(p) || rl.getPath().startsWith(p)) out.add(s);
            if (out.size() >= 8) break;
        }
        return out;
    }

    public static List<String> blocks(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (p.length() < 2 || p.startsWith("#")) return out;
        for (ResourceLocation rl : BuiltInRegistries.BLOCK.keySet()) {
            if (rl.toString().startsWith(p) || rl.getPath().startsWith(p)) out.add(rl.toString());
            if (out.size() >= 8) break;
        }
        return out;
    }

    public static List<String> entities(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (p.length() < 2 || p.startsWith("#")) return out;
        for (ResourceLocation rl : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (rl.toString().startsWith(p) || rl.getPath().startsWith(p)) out.add(rl.toString());
            if (out.size() >= 8) break;
        }
        return out;
    }

    /** Icone d'un Pokemon (espece, forme/aspects, chromatique) a partir d'une fiche JSON. */
    public static ItemStack pokemonIcon(String species, String form, boolean shiny) {
        try {
            Species s = SpeciesLookup.resolve(species);
            if (s == null) return new ItemStack(Items.BARRIER);
            Set<String> aspects = new HashSet<>();
            if (shiny) aspects.add("shiny");
            if (form != null) {
                for (String a : form.split(",")) {
                    String v = a.trim().toLowerCase(Locale.ROOT);
                    if (v.isEmpty()) continue;
                    aspects.add(switch (v) {
                        case "alola", "alolan" -> "alolan";
                        case "galar", "galarian" -> "galarian";
                        case "hisui", "hisuian" -> "hisuian";
                        case "paldea", "paldean" -> "paldean";
                        default -> v;
                    });
                }
            }
            return PokemonItem.from(s, aspects, 1, new Vector4f(1f, 1f, 1f, 1f));
        } catch (Throwable t) {
            return new ItemStack(Items.BARRIER);
        }
    }

    public static String speciesName(String species) {
        try {
            Species s = SpeciesLookup.resolve(species);
            return s == null ? species : s.getTranslatedName().getString();
        } catch (Throwable t) {
            return species;
        }
    }

    // ------------------------------------------------------------------ Recompenses (affichage dans l'editeur)

    public static ItemStack rewardIcon(JsonObject r) {
        String type = Ui.str(r, "type", "objet");
        switch (type) {
            case "objet" -> {
                ItemStack s = Ui.itemJson(r.get("objet"));
                return s.isEmpty() ? new ItemStack(Items.BARRIER) : s;
            }
            case "pokemon" -> {
                JsonObject m = Ui.obj(r, "pokemon");
                return pokemonIcon(Ui.str(m, "species", ""), Ui.str(m, "form", ""), Ui.bool(m, "shiny", false));
            }
            default -> {
                String icon = Ui.str(r, "icone", "");
                if (icon.isBlank()) icon = switch (type) {
                    case "bp_battletower" -> "minecraft:emerald";
                    case "coins_jobs" -> "minecraft:gold_ingot";
                    case "points" -> "minecraft:experience_bottle";
                    default -> "minecraft:paper";
                };
                return Ui.itemById(icon);
            }
        }
    }

    public static String rewardLabel(JsonObject r) {
        String nom = Ui.str(r, "nom", "");
        if (!nom.isBlank()) return fr.cobblepulse.battlepass.core.TextUtil.strip(nom);
        String type = Ui.str(r, "type", "objet");
        double amount = Ui.dbl(r, "montant", 0);
        String a = amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
        return switch (type) {
            case "objet" -> {
                ItemStack s = Ui.itemJson(r.get("objet"));
                yield s.isEmpty() ? "Objet invalide" : s.getHoverName().getString() + (s.getCount() > 1 ? " x" + s.getCount() : "");
            }
            case "pokemon" -> {
                JsonObject m = Ui.obj(r, "pokemon");
                yield speciesName(Ui.str(m, "species", "?")) + " Nv. " + Ui.lng(m, "level", 1) + (Ui.bool(m, "shiny", false) ? " ★" : "");
            }
            case "commande" -> {
                JsonElement c = r.get("commandes");
                yield "Commande" + (c != null && c.isJsonArray() && c.getAsJsonArray().size() > 0 ? " : /" + c.getAsJsonArray().get(0).getAsString() : "");
            }
            case "bp_battletower" -> a + " BP (Tour)";
            case "coins_jobs" -> a + " coins Jobs+";
            case "points" -> a + " points de passe";
            default -> type;
        };
    }

    public static final List<String> REWARD_TYPES = List.of("objet", "pokemon", "commande", "bp_battletower", "coins_jobs", "points");
    public static final List<String> REWARD_TYPE_NAMES = List.of("Objet", "Pokémon", "Commande console", "BP Tour de Combat", "Coins Jobs+",
            "Points de passe");

    /** Nouvelle recompense d'un type, avec des valeurs par defaut raisonnables. */
    public static JsonObject newReward(String type) {
        JsonObject r = new JsonObject();
        r.addProperty("type", type);
        r.addProperty("nom", "");
        r.addProperty("icone", "");
        r.addProperty("montant", 100);
        r.addProperty("chance", 1.0);
        r.add("commandes", new com.google.gson.JsonArray());
        if ("objet".equals(type)) {
            JsonObject o = new JsonObject();
            o.addProperty("id", "cobblemon:poke_ball");
            o.addProperty("count", 5);
            r.add("objet", o);
        }
        if ("pokemon".equals(type)) r.add("pokemon", newMon());
        return r;
    }

    public static JsonObject newMon() {
        JsonObject m = new JsonObject();
        m.addProperty("species", "pikachu");
        m.addProperty("form", "");
        m.addProperty("nickname", "");
        m.addProperty("level", 10);
        m.addProperty("nature", "");
        m.addProperty("ability", "");
        m.addProperty("gender", "");
        m.addProperty("shiny", false);
        m.addProperty("held_item", "");
        m.addProperty("ball", "");
        m.addProperty("tera_type", "");
        JsonObject ivs = new JsonObject();
        JsonObject evs = new JsonObject();
        for (String s : List.of("hp", "atk", "def", "spa", "spd", "spe")) {
            ivs.addProperty(s, -1);
            evs.addProperty(s, 0);
        }
        m.add("ivs", ivs);
        m.add("evs", evs);
        m.add("moves", new com.google.gson.JsonArray());
        return m;
    }
}
