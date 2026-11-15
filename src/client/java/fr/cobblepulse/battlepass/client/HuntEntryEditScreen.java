package fr.cobblepulse.battlepass.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Pokemon pouvant etre demande par une chasse (espece, forme, genre, nature, IV parfaits, poids du tirage). */
public final class HuntEntryEditScreen extends EditScreen {
    public HuntEntryEditScreen(Screen parent, JsonObject entry, Consumer<JsonObject> onSave) {
        super("Pokémon de chasse", parent, entry, onSave);
    }

    @Override
    protected int previewWidth() {
        return 110;
    }

    @Override
    protected void fields(Form f) {
        f.section("Pokémon demandé");
        EditBox sp = f.text("Espèce", obj, "espece", "Nom ou identifiant (fakemons acceptés)");
        f.suggest(sp, ClientData::species);
        f.text("Forme", obj, "forme", "Nom de forme Cobblemon (vide = normale, * = n'importe laquelle)");
        f.list("Aspects exigés", obj, "aspects", "ex. alolan, galarian (séparés par des virgules)");
        f.section("Exigences");
        f.choice("Genre", obj, "genre", List.of("", "male", "femelle", "aleatoire"), List.of("Indifférent", "Mâle", "Femelle", "Tiré au sort"), null);
        List<String> nat = new ArrayList<>(List.of("", "aleatoire"));
        nat.addAll(ClientData.NATURES);
        List<String> natFr = new ArrayList<>(List.of("Indifférente", "Tirée au sort"));
        natFr.addAll(ClientData.NATURES_FR);
        f.choice("Nature", obj, "nature", nat, natFr, "Maj + clic pour reculer");
        f.integer("IV parfaits", obj, "iv_parfaits", 0, 6, "Nombre de statistiques tirées au sort dont l'IV doit atteindre le minimum (réglages)");
        f.decimal("Poids du tirage", obj, "poids", "Plus le poids est élevé, plus ce Pokémon sort souvent");
    }

    @Override
    protected void extra(GuiGraphics g, int mouseX, int mouseY) {
        int x = px + pw - 104;
        int y = py + 26;
        Ui.panel(g, x, y, 96, 96, Ui.CARD, Ui.BORDER);
        String forms = String.join(",", Ui.str(obj, "forme", ""));
        Ui.item(g, ClientData.pokemonIcon(Ui.str(obj, "espece", ""), forms, false), x + 16, y + 10, 4f, false);
        Ui.centered(g, Ui.cut(ClientData.speciesName(Ui.str(obj, "espece", "")), 96), x + 48, y + 100, Ui.TEXT);
    }
}
