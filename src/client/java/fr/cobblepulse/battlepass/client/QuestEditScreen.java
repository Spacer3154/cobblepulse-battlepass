package fr.cobblepulse.battlepass.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Edition d'une quete : type, objectif, points, premium et conditions adaptees au type. */
public final class QuestEditScreen extends EditScreen {
    private final JsonArray types;

    public QuestEditScreen(Screen parent, JsonObject quest, JsonArray types, Consumer<JsonObject> onSave) {
        super("Quête", parent, quest, onSave);
        this.types = types;
    }

    private JsonObject type(String id) {
        for (JsonElement e : types) if (Ui.str(e.getAsJsonObject(), "id", "").equals(id)) return e.getAsJsonObject();
        return new JsonObject();
    }

    private boolean has(JsonObject t, String group) {
        for (JsonElement e : Ui.arr(t, "filters")) if (e.getAsString().equals(group)) return true;
        return false;
    }

    @Override
    protected int previewWidth() {
        return 110;
    }

    @Override
    protected void fields(Form f) {
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (JsonElement e : types) {
            ids.add(Ui.str(e.getAsJsonObject(), "id", ""));
            names.add(Ui.str(e.getAsJsonObject(), "label", ""));
        }
        String cur = Ui.str(obj, "type", "capture");
        f.onChange(() -> {
            if (!cur.equals(Ui.str(obj, "type", "capture"))) minecraft.execute(this::rebuild);
        });
        f.section("Quête");
        f.text("Identifiant", obj, "id", "Unique dans la catégorie (lettres, chiffres, _). Changer l'identifiant remet la progression à zéro.");
        f.choice("Type", obj, "type", ids, names, "Maj + clic pour reculer");
        f.text("Nom affiché", obj, "nom", "Vide = texte automatique (ex. « Capturer 10 Pokémon »)");
        f.integer("Objectif", obj, "objectif", 1, 100_000_000, "Distance : en blocs. Temps de jeu : en minutes.");
        f.integer("Points de passe", obj, "points", 0, 10_000_000, null);
        f.bool("Réservée au premium", obj, "premium", "Visible par tous mais ne progresse que pour les joueurs premium");
        EditBox icon = f.text("Icône", obj, "icone", "Objet affiché (vide = automatique)");
        f.suggest(icon, ClientData::items);
        JsonObject t = type(cur);
        JsonObject flt = Ui.obj(obj, "filtres");
        if (has(t, "pokemon")) {
            f.section("Conditions sur le Pokémon (vide = tous)");
            EditBox sp = f.list("Espèces", flt, "especes", "Séparées par des virgules (fakemons : monpack:volt_mon)");
            f.suggest(sp, ClientData::species);
            f.list("Types", flt, "types", "feu, eau, plante, electrik, glace, combat, poison, sol, vol, psy, insecte, roche, spectre, dragon, tenebres, acier, fee, normal");
            f.list("Catégories", flt, "etiquettes", "legendaire, fabuleux, ultra_chimere, paradoxe, bebe... (étiquettes Cobblemon)");
            f.list("Formes / aspects", flt, "formes", "alolan, galarian, hisuian...");
            f.list("Natures", flt, "natures", "rigide, timide... (français ou anglais)");
            f.choice("Chromatique", flt, "chromatique", List.of("", "oui", "non"), List.of("Indifférent", "Oui", "Non"), null);
            f.choice("Genre", flt, "genre", List.of("", "male", "femelle", "asexue"), List.of("Indifférent", "Mâle", "Femelle", "Asexué"), null);
            f.integer("Niveau minimum", flt, "niveau_min", 0, 100, "0 = aucun");
            f.integer("Niveau maximum", flt, "niveau_max", 0, 100, "0 = aucun");
        }
        if (has(t, "balls")) {
            EditBox b = f.list("Poké Balls", flt, "balls", "ex. cobblemon:ultra_ball, dusk_ball");
            f.suggest(b, ClientData::items);
        }
        if (has(t, "lieu")) {
            f.section("Lieu");
            f.list("Biomes", flt, "biomes", "ex. minecraft:plains, #minecraft:is_forest");
            f.list("Dimensions", flt, "dimensions", "ex. minecraft:overworld, minecraft:the_nether");
        }
        if (has(t, "moment")) f.choice("Moment", flt, "moment", List.of("", "jour", "nuit"), List.of("Indifférent", "De jour", "De nuit"), null);
        if (has(t, "combat")) {
            f.choice("Adversaire", flt, "combat", List.of("", "sauvage", "dresseur", "joueur"),
                    List.of("Tous", "Pokémon sauvage", "Dresseur (PNJ)", "Joueur"), null);
        }
        if (has(t, "blocs")) {
            EditBox b = f.list("Blocs", flt, "blocs", "ex. minecraft:diamond_ore, #c:ores, #minecraft:logs (vide = tous)");
            f.suggest(b, ClientData::blocks);
        }
        if (has(t, "objets")) {
            EditBox b = f.list("Objets", flt, "objets", "ex. minecraft:bread, #minecraft:fishes, cobblemon:oran_berry (livraison : obligatoire)");
            f.suggest(b, ClientData::items);
        }
        if (has(t, "entites")) {
            EditBox b = f.list("Créatures", flt, "entites", "ex. minecraft:zombie, #minecraft:skeletons (vide = toutes)");
            f.suggest(b, ClientData::entities);
        }
        if (has(t, "difficulte")) {
            f.choice("Difficulté", flt, "difficulte", List.of("", "facile", "normal", "moyen", "difficile", "globale"),
                    List.of("Toutes", "Facile", "Normale", "Moyenne", "Difficile", "Globale"), null);
        }
        if (has(t, "stat")) f.text("Statistique", flt, "stat", "type/valeur, ex. minecraft:custom/minecraft:jump ou minecraft:mined/minecraft:stone");
        if (has(t, "etage")) f.integer("Étage minimum", flt, "niveau_min", 0, 1000, "Seuls les étages à partir de celui-ci comptent (0 = tous)");
    }

    @Override
    protected void extra(GuiGraphics g, int mouseX, int mouseY) {
        JsonObject t = type(Ui.str(obj, "type", "capture"));
        int x = px + pw - 104;
        int y = py + 26;
        Ui.panel(g, x, y, 96, 60, Ui.CARD, Ui.BORDER);
        String iconId = Ui.str(obj, "icone", "");
        if (iconId.isBlank()) iconId = Ui.str(t, "icon", "minecraft:paper");
        Ui.item(g, Ui.itemById(iconId), x + 32, y + 6, 2f, false);
        String title = Ui.str(obj, "nom", "");
        if (title.isBlank()) title = Ui.str(t, "phrase", "").replace("%n", String.valueOf(Ui.lng(obj, "objectif", 1)));
        int yy = y + 64;
        for (var l : Ui.font().split(net.minecraft.network.chat.Component.literal(title), 100)) {
            g.drawString(Ui.font(), l, x - 2, yy, Ui.TEXT, false);
            yy += 10;
        }
        Ui.text(g, "+" + Ui.lng(obj, "points", 0) + " points", x - 2, yy + 4, Ui.GOLD);
    }
}
