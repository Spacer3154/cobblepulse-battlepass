package fr.cobblepulse.battlepass.data;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Une recompense du passe (ou du butin d'une chasse).
 * <ul>
 *   <li>{@code objet} : objet complet (composants, enchantements, nom...) dans {@link #objet} ;</li>
 *   <li>{@code pokemon} : Pokemon ou fakemon personnalise ({@link #pokemon}) ;</li>
 *   <li>{@code commande} : commandes console ({@code %player%}, {@code %uuid%}), icone et nom affiches ;</li>
 *   <li>{@code bp_battletower} : Battle Points de CobblePulse Battletower ;</li>
 *   <li>{@code coins_jobs} : coins Jobs+ ;</li>
 *   <li>{@code points} : points de passe (utile dans le butin des chasses).</li>
 * </ul>
 */
public final class Reward {
    public static final List<String> TYPES = List.of("objet", "pokemon", "commande", "bp_battletower", "coins_jobs", "points");

    public String type = "objet";
    /** Nom affiche (facultatif ; sinon genere : nom de l'objet, espece...). Codes couleur acceptes. */
    public String nom = "";
    /** Objet au format JSON de Minecraft ({"id": "minecraft:diamond", "count": 3, "components": {...}}). */
    public JsonElement objet;
    /** Identifiant d'objet servant d'icone (commandes, BP, coins...). */
    public String icone = "";
    public MonSpec pokemon;
    public List<String> commandes = new ArrayList<>();
    public double montant;
    /** Butin des chasses : poids (mode "poids") ou probabilite 0-1 (mode "pourcentage"). Ignore dans le passe. */
    public double chance = 1.0;

    public Reward() {}

    public static Reward of(String type) {
        Reward r = new Reward();
        r.type = type;
        return r;
    }

    public String type() {
        return type == null ? "objet" : type.toLowerCase(Locale.ROOT);
    }

    public void normalize() {
        if (type == null || !TYPES.contains(type.toLowerCase(Locale.ROOT))) type = "objet";
        type = type.toLowerCase(Locale.ROOT);
        if (nom == null) nom = "";
        if (icone == null) icone = "";
        if (commandes == null) commandes = new ArrayList<>();
        commandes.removeIf(c -> c == null || c.isBlank());
        if (pokemon != null) pokemon.normalize();
        if ("pokemon".equals(type) && pokemon == null) pokemon = new MonSpec();
        if (montant < 0) montant = 0;
        if (chance < 0) chance = 0;
    }
}
