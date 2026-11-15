package fr.cobblepulse.battlepass.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Edition d'une recompense : objet (y compris l'objet tenu en main), Pokemon personnalise, commande, BP, coins Jobs+, points. */
public final class RewardEditScreen extends EditScreen {
    private final boolean loot;
    private List<String> checkErrors = new ArrayList<>();
    private String checkInfo = "";
    private ItemStack checkIcon = ItemStack.EMPTY;

    public RewardEditScreen(Screen parent, JsonObject reward, boolean loot, Consumer<JsonObject> onSave) {
        super(loot ? "Lot de chasse" : "Récompense", parent, reward, onSave);
        this.loot = loot;
    }

    @Override
    protected int previewWidth() {
        return 110;
    }

    @Override
    protected void fields(Form f) {
        f.onChange(() -> {});
        f.choice("Type", obj, "type", ClientData.REWARD_TYPES, ClientData.REWARD_TYPE_NAMES, "Maj + clic pour revenir en arrière");
        // Changement de type : on reconstruit le formulaire avec les bons champs
        String type = Ui.str(obj, "type", "objet");
        f.onChange(() -> {
            if (!type.equals(Ui.str(obj, "type", "objet"))) {
                String t = Ui.str(obj, "type", "objet");
                if ("objet".equals(t) && !obj.has("objet")) obj.add("objet", ClientData.newReward("objet").get("objet"));
                if ("pokemon".equals(t) && !obj.has("pokemon")) obj.add("pokemon", ClientData.newMon());
                minecraft.execute(this::rebuild);
            }
        });
        switch (type) {
            case "objet" -> item(f);
            case "pokemon" -> pokemon(f);
            case "commande" -> {
                f.section("Commandes console");
                EditBox cmds = f.free("Commandes", joined(), "Séparez les commandes par ; — %player% = pseudo, %uuid% = UUID. Sans le /.");
                cmds.setResponder(v -> {
                    JsonArray a = new JsonArray();
                    for (String c : v.split(";")) if (!c.isBlank()) a.add(c.trim());
                    obj.add("commandes", a);
                });
                EditBox icon = f.text("Icône", obj, "icone", "Objet affiché dans le passe (ex. minecraft:paper)");
                f.suggest(icon, ClientData::items);
                f.text("Nom affiché", obj, "nom", "Codes couleur & acceptés");
            }
            default -> {
                f.section(switch (type) {
                    case "bp_battletower" -> "Battle Points de la Tour de Combat";
                    case "coins_jobs" -> "Coins Jobs+";
                    default -> "Points de passe";
                });
                f.decimal("Montant", obj, "montant", null);
                f.text("Nom affiché", obj, "nom", "Vide = texte automatique");
                EditBox icon = f.text("Icône", obj, "icone", "Vide = icône par défaut");
                f.suggest(icon, ClientData::items);
            }
        }
        if (loot) {
            f.section("Butin");
            f.decimal("Chance / poids", obj, "chance", "Mode « poids » : poids relatif du lot. Mode « pourcentage » : probabilité de 0 à 1.");
        }
    }

    private String joined() {
        List<String> out = new ArrayList<>();
        if (obj.has("commandes") && obj.get("commandes").isJsonArray()) for (JsonElement e : obj.getAsJsonArray("commandes")) out.add(e.getAsString());
        return String.join("; ", out);
    }

    private void item(Form f) {
        f.section("Objet");
        f.button("Objet en main", "Prendre l'objet tenu", () -> {
            if (minecraft.player == null) return;
            ItemStack hand = minecraft.player.getMainHandItem();
            if (hand.isEmpty()) {
                message = "Tenez l'objet (avec ses enchantements, son nom...) dans la main principale.";
                messageColor = Ui.ERROR;
                return;
            }
            JsonElement e = Ui.encodeItem(hand);
            if (e != null) {
                obj.add("objet", e);
                message = "Objet copié : " + hand.getHoverName().getString() + " x" + hand.getCount();
                messageColor = Ui.OK;
                minecraft.execute(this::rebuild);
            }
        }, "Copie l'objet exact (composants, enchantements, nom personnalisé) tenu dans la main principale.");
        JsonObject o = obj.has("objet") && obj.get("objet").isJsonObject() ? obj.getAsJsonObject("objet") : new JsonObject();
        if (!obj.has("objet") || !obj.get("objet").isJsonObject()) obj.add("objet", o);
        boolean custom = o.has("components") && o.getAsJsonObject("components").size() > 0;
        EditBox id = f.text("Identifiant", o, "id", custom ? "Objet personnalisé (composants conservés tant que l'identifiant ne change pas)" : "ex. cobblemon:rare_candy");
        f.suggest(id, ClientData::items);
        f.integer("Quantité", o, "count", 1, 6400, "Peut dépasser 64 : l'objet est donné en plusieurs piles.");
        f.text("Nom affiché", obj, "nom", "Facultatif ; codes couleur & acceptés");
    }

    private void pokemon(Form f) {
        JsonObject m = Ui.obj(obj, "pokemon");
        f.section("Pokémon");
        EditBox sp = f.text("Espèce", m, "species", "Nom ou identifiant complet (fakemons : monpack:volt_mon)");
        f.suggest(sp, ClientData::species);
        f.text("Forme / aspects", m, "form", "Séparés par des virgules : alolan, galarian, hisuian, wash, rapid-strike...");
        f.integer("Niveau", m, "level", 1, 100, null);
        f.bool("Chromatique", m, "shiny", null);
        List<String> nat = new ArrayList<>(List.of(""));
        nat.addAll(ClientData.NATURES);
        List<String> natFr = new ArrayList<>(List.of("Aléatoire"));
        natFr.addAll(ClientData.NATURES_FR);
        f.choice("Nature", m, "nature", nat, natFr, "Maj + clic pour reculer");
        f.text("Talent", m, "ability", "Vide = talent normal aléatoire (ex. intimidate)");
        f.choice("Genre", m, "gender", List.of("", "male", "female", "genderless"), List.of("Aléatoire", "Mâle", "Femelle", "Asexué"), null);
        EditBox held = f.text("Objet tenu", m, "held_item", "ex. cobblemon:leftovers");
        f.suggest(held, ClientData::items);
        EditBox ball = f.text("Poké Ball", m, "ball", "ex. cobblemon:ultra_ball (vide = Poké Ball)");
        f.suggest(ball, ClientData::items);
        f.text("Surnom", m, "nickname", null);
        f.text("Type Téra", m, "tera_type", "Vide = type normal");
        f.section("Statistiques (PV / Atq / Déf / Atq.Spé / Déf.Spé / Vit)");
        f.intRow("IV (-1 = aléatoire)", Ui.obj(m, "ivs"), List.of("hp", "atk", "def", "spa", "spd", "spe"), -1, 31, "De 0 à 31, ou -1 pour un tirage aléatoire");
        f.intRow("EV", Ui.obj(m, "evs"), List.of("hp", "atk", "def", "spa", "spd", "spe"), 0, 252, "De 0 à 252 (total conseillé ≤ 510)");
        f.section("Capacités");
        List<EditBox> moves = f.textRow("Capacités", m, "moves", 4, "Vide = capacités normales du niveau");
        for (EditBox b : moves) f.suggest(b, ClientData::moves);
        f.button("Vérification", "Vérifier sur le serveur", () -> {
            JsonObject a = new JsonObject();
            a.add("spec", m.deepCopy());
            Ui.send("admin_check_pokemon", a);
            checkInfo = "Vérification…";
            checkErrors = new ArrayList<>();
        }, "Crée le Pokémon côté serveur pour valider l'espèce, la forme, les capacités, le talent et les objets.");
        f.text("Nom affiché", obj, "nom", "Facultatif");
    }

    /** Reponse du serveur a "Verifier". */
    public void onCheck(JsonObject r) {
        checkErrors = new ArrayList<>();
        for (JsonElement e : Ui.arr(r, "errors")) checkErrors.add(e.getAsString());
        checkIcon = Ui.item(Ui.str(r, "icon", ""));
        checkInfo = Ui.bool(r, "ok", false) ? "Valide ✔ " + Ui.comp(Ui.str(r, "name", "")).getString()
                + (r.has("ability") ? " — talent : " + Ui.str(r, "ability", "") : "") : "Erreurs :";
    }

    @Override
    protected void extra(GuiGraphics g, int mouseX, int mouseY) {
        int x = px + pw - 104;
        int y = py + 26;
        Ui.panel(g, x, y, 96, 96, Ui.CARD, Ui.BORDER);
        ItemStack icon = ClientData.rewardIcon(obj);
        Ui.item(g, icon, x + 16, y + 10, 4f, false);
        String label = ClientData.rewardLabel(obj);
        List<net.minecraft.util.FormattedCharSequence> lines = Ui.font().split(Component.literal(label), 96);
        for (int i = 0; i < Math.min(3, lines.size()); i++) g.drawString(Ui.font(), lines.get(i), x, y + 100 + i * 10, Ui.TEXT, false);
        if (!checkInfo.isEmpty()) {
            int cy = y + 136;
            List<net.minecraft.util.FormattedCharSequence> info = Ui.font().split(Component.literal(checkInfo), 100);
            for (net.minecraft.util.FormattedCharSequence l : info) {
                g.drawString(Ui.font(), l, x - 2, cy, checkErrors.isEmpty() ? Ui.OK : Ui.ERROR, false);
                cy += 10;
            }
            for (String e : checkErrors) {
                for (net.minecraft.util.FormattedCharSequence l : Ui.font().split(Component.literal("• " + e), 100)) {
                    if (cy > py + ph - 30) break;
                    g.drawString(Ui.font(), l, x - 2, cy, Ui.ERROR, false);
                    cy += 10;
                }
            }
        }
    }
}
