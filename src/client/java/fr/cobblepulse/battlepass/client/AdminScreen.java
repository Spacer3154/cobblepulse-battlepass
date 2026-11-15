package fr.cobblepulse.battlepass.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Editeur graphique du Passe de Combat (/passe admin) : saison (jours, paliers, ouverture, premium, connexion, grades
 * LuckPerms, etoiles), recompenses par palier, quetes, chasses et actions sur les joueurs.
 */
public final class AdminScreen extends Screen {
    private record Hot(int x, int y, int w, int h, Runnable action) {}

    private static final String[] TABS = {"Saison", "Récompenses", "Quêtes", "Chasses", "Joueurs"};
    private static final String[] SECTIONS = {"saison", "recompenses", "quetes", "chasses", ""};
    private static final List<String> CATS = List.of("quotidiennes", "hebdomadaires", "saison");
    private static final List<String> CAT_NAMES = List.of("Quotidiennes", "Hebdomadaires", "Saison");
    private static final List<String> DIFFS = List.of("facile", "normal", "moyen", "difficile", "globale");
    private static final List<String> DIFF_NAMES = List.of("Facile", "Normale", "Moyenne", "Difficile", "Globale");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static int lastTab;

    private JsonObject data;
    private JsonObject saison;
    private JsonObject recompenses;
    private JsonObject quetes;
    private JsonObject chasses;
    private final Set<String> dirty = new HashSet<>();
    private int tab;
    private Form form;
    private final List<Hot> hots = new ArrayList<>();
    private final List<Component> tooltip = new ArrayList<>();
    private String status = "";
    private int statusColor = Ui.MUTED;
    private final List<String> warnings = new ArrayList<>();
    private String confirm = "";
    private long confirmUntil;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int cy;
    private int ch;
    private int savedGuiScale = -1;
    private Button saveBtn;
    private Button undoBtn;
    private final double[] scrolls = new double[8];

    // Etat des onglets
    private int selTier = 1;
    private int questCat;
    private int huntSub;
    private int huntDiff;
    private JsonObject playerInfo;
    private String playerName = "";
    private String amount = "100";
    private String boostMult = "2";
    private String boostMin = "60";

    public AdminScreen(JsonObject data) {
        super(Component.literal("Éditeur du Passe de Combat"));
        this.tab = lastTab;
        load(data, null);
    }

    private void load(JsonObject d, String saved) {
        this.data = d;
        if (saison == null || saved != null && saved.equals("saison") || !dirty.contains("saison")) saison = Ui.obj(d, "saison").deepCopy();
        if (recompenses == null || "recompenses".equals(saved) || !dirty.contains("recompenses")) recompenses = Ui.obj(d, "recompenses").deepCopy();
        if (quetes == null || "quetes".equals(saved) || !dirty.contains("quetes")) quetes = Ui.obj(d, "quetes").deepCopy();
        if (chasses == null || "chasses".equals(saved) || !dirty.contains("chasses")) chasses = Ui.obj(d, "chasses").deepCopy();
        if (saved != null) dirty.remove(saved);
    }

    /** Donnees fraiches du serveur (apres une action ou un enregistrement). */
    public void refreshData(JsonObject d) {
        load(d, null);
        if (minecraft != null) minecraft.execute(this::rebuild);
    }

    public void onResult(JsonObject r) {
        boolean ok = Ui.bool(r, "ok", false);
        String section = Ui.str(r, "section", "");
        status = Ui.str(r, "message", ok ? "Enregistré." : "Erreur.");
        statusColor = ok ? Ui.OK : Ui.ERROR;
        warnings.clear();
        for (JsonElement e : Ui.arr(r, "warnings")) warnings.add(e.getAsString());
        if (ok && !warnings.isEmpty()) statusColor = Ui.GOLD;
        if (ok) {
            dirty.remove(section);
            switch (section) {
                case "saison" -> saison = null;
                case "recompenses" -> recompenses = null;
                case "quetes" -> quetes = null;
                case "chasses" -> chasses = null;
                default -> {
                }
            }
        }
    }

    public void onPlayer(JsonObject p) {
        playerInfo = p;
        playerName = Ui.str(p, "nom", playerName);
    }

    public void toast(boolean ok, String msg) {
        status = msg;
        statusColor = ok ? Ui.OK : Ui.ERROR;
        warnings.clear();
    }

    private void markDirty(String section) {
        dirty.add(section);
    }

    // ------------------------------------------------------------------ Construction

    private void fitGuiScale() {
        com.mojang.blaze3d.platform.Window win = minecraft.getWindow();
        int cur = (int) win.getGuiScale();
        int s = cur;
        while (s > 1 && (win.getWidth() / s < 480 || win.getHeight() / s < 280)) s--;
        if (s != cur) {
            if (savedGuiScale < 0) savedGuiScale = cur;
            win.setGuiScale(s);
            this.width = win.getGuiScaledWidth();
            this.height = win.getGuiScaledHeight();
        }
    }

    @Override
    protected void init() {
        fitGuiScale();
        pw = Math.min(width - 12, 820);
        px = (width - pw) / 2;
        py = 6;
        ph = height - 12;
        cy = py + 44;
        ch = py + ph - 30 - cy;
        rebuild();
    }

    @Override
    public void removed() {
        super.removed();
        if (savedGuiScale > 0) {
            minecraft.getWindow().setGuiScale(savedGuiScale);
            savedGuiScale = -1;
        }
    }

    private boolean perm(String key) {
        return Ui.bool(Ui.obj(data, "perms"), key, false);
    }

    private void rebuild() {
        if (saison == null || recompenses == null || quetes == null || chasses == null) load(data, null);
        if (form != null) scrolls[tab] = form.scroll();
        clearWidgets();
        form = null;
        int bx = px + 8;
        for (int i = 0; i < TABS.length; i++) {
            int idx = i;
            String label = TABS[i] + (!SECTIONS[i].isEmpty() && dirty.contains(SECTIONS[i]) ? " *" : "");
            Button b = Button.builder(Component.literal(label), btn -> {
                if (form != null) scrolls[tab] = form.scroll();
                tab = idx;
                lastTab = idx;
                rebuild();
            }).bounds(bx, py + 20, 92, 18).build();
            b.active = tab != i;
            addRenderableWidget(b);
            bx += 96;
        }
        String section = SECTIONS[tab];
        if (!section.isEmpty()) {
            Button save = Button.builder(Component.literal("Enregistrer"), b -> save(section)).bounds(px + pw - 300, py + ph - 24, 96, 18).build();
            save.active = dirty.contains(section) && perm("edit");
            saveBtn = save;
            addRenderableWidget(save);
            Button undo = Button.builder(Component.literal("Annuler"), b -> {
                dirty.remove(section);
                switch (section) {
                    case "saison" -> saison = null;
                    case "recompenses" -> recompenses = null;
                    case "quetes" -> quetes = null;
                    default -> chasses = null;
                }
                load(data, null);
                rebuild();
            }).bounds(px + pw - 200, py + ph - 24, 96, 18).build();
            undo.active = dirty.contains(section);
            undoBtn = undo;
            addRenderableWidget(undo);
        } else {
            saveBtn = null;
            undoBtn = null;
        }
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(px + pw - 100, py + ph - 24, 92, 18).build());
        switch (tab) {
            case 0 -> buildSeason();
            case 3 -> {
                if (huntSub == 0) buildHuntSettings();
            }
            case 4 -> buildPlayers();
            default -> {
            }
        }
    }

    private void save(String section) {
        JsonObject a = new JsonObject();
        a.addProperty("section", section);
        a.add("data", switch (section) {
            case "saison" -> saison;
            case "recompenses" -> recompenses;
            case "quetes" -> quetes;
            default -> chasses;
        });
        Ui.send("admin_save", a);
        status = "Enregistrement…";
        statusColor = Ui.MUTED;
    }

    private boolean confirmed(String action) {
        if (action.equals(confirm) && System.currentTimeMillis() < confirmUntil) {
            confirm = "";
            return true;
        }
        confirm = action;
        confirmUntil = System.currentTimeMillis() + 5000;
        status = "Cliquez à nouveau pour confirmer.";
        statusColor = Ui.GOLD;
        return false;
    }

    // ------------------------------------------------------------------ Saison

    private void buildSeason() {
        int y = cy + 2;
        boolean open = Ui.bool(saison, "ouvert", true) && !"maintenance".equals(Ui.str(data, "statut", ""));
        Button toggle = Button.builder(Component.literal(open ? "Fermer (maintenance)" : "Ouvrir le passe"), b -> {
            JsonObject a = new JsonObject();
            a.addProperty("open", !open);
            Ui.send("admin_toggle", a);
        }).bounds(px + 8, y, 130, 18).build();
        toggle.active = perm("toggle");
        addRenderableWidget(toggle);
        Button ns = Button.builder(Component.literal("Nouvelle saison…"), b -> {
            if (confirmed("new_season")) Ui.send("admin_new_season");
        }).bounds(px + 142, y, 110, 18).build();
        ns.active = perm("saison");
        addRenderableWidget(ns);
        EditBox mult = new EditBox(Ui.font(), px + 300, y, 30, 18, Component.literal("x"));
        mult.setValue(boostMult);
        mult.setResponder(v -> boostMult = v);
        EditBox min = new EditBox(Ui.font(), px + 352, y, 40, 18, Component.literal("min"));
        min.setValue(boostMin);
        min.setResponder(v -> boostMin = v);
        addRenderableWidget(mult);
        addRenderableWidget(min);
        Button boost = Button.builder(Component.literal("Bonus"), b -> {
            JsonObject a = new JsonObject();
            try {
                a.addProperty("mult", Double.parseDouble(boostMult.replace(',', '.')));
                a.addProperty("minutes", Integer.parseInt(boostMin.trim()));
                Ui.send("admin_boost", a);
            } catch (NumberFormatException e) {
                toast(false, "Bonus : multiplicateur et minutes invalides");
            }
        }).bounds(px + 420, y, 50, 18).build();
        Button stop = Button.builder(Component.literal("Stop"), b -> {
            JsonObject a = new JsonObject();
            a.addProperty("mult", 1.0);
            a.addProperty("minutes", 0);
            Ui.send("admin_boost", a);
        }).bounds(px + 474, y, 40, 18).build();
        boost.active = perm("boost");
        stop.active = perm("boost");
        addRenderableWidget(boost);
        addRenderableWidget(stop);

        form = new Form(this::addRenderableWidget, px + 6, cy + 36, pw - 12, ch - 38).onChange(() -> markDirty("saison"));
        Form f = form;
        f.section("Saison");
        f.text("Nom", saison, "nom", null);
        f.integer("Durée (jours)", saison, "jours", 1, 3650, "Les grades LuckPerms gratuit/premium expirent à la fin de la saison.");
        f.choice("À la fin de la saison", saison, "fin_de_saison", List.of("fermer", "nouvelle_saison"),
                List.of("Fermer le passe", "Nouvelle saison automatique"), null);
        f.integer("Nombre de paliers", saison, "paliers", 1, 1000, null);
        f.integer("Points par palier", saison, "points_par_palier", 1, 100_000_000, null);
        f.intList("Coûts particuliers", saison, "points_par_palier_liste", "Facultatif : coût de chaque palier dans l'ordre (1000, 1000, 1500...)");
        f.intList("Annoncer les paliers", saison, "annonce_paliers", "Annonce dans le chat quand un joueur atteint ces paliers");
        f.section("Quêtes");
        f.integer("Quotidiennes", saison, "quetes_quotidiennes", 0, 50, "Nombre de quêtes tirées chaque jour");
        f.integer("Quotidiennes premium", saison, "quetes_quotidiennes_premium", 0, 50, null);
        f.integer("Hebdomadaires", saison, "quetes_hebdomadaires", 0, 50, null);
        f.integer("Hebdomadaires premium", saison, "quetes_hebdomadaires_premium", 0, 50, null);
        f.choice("Tirage", saison, "selection_quetes", List.of("joueur", "global"),
                List.of("Différent pour chaque joueur", "Le même pour tous"), null);
        f.integer("Heure de remise à zéro", saison, "heure_reset", 0, 23, null);
        f.choice("Jour de remise hebdo", saison, "jour_reset_hebdo",
                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
                List.of("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"), null);
        f.text("Fuseau horaire", saison, "fuseau", "ex. Europe/Paris");
        JsonObject prem = Ui.obj(saison, "premium");
        f.section("Passe premium");
        f.bool("Piste premium", prem, "actif", null);
        f.choice("Achat en jeu", prem, "achat_en_jeu", List.of("aucun", "coins_jobs", "bp_battletower", "objet"),
                List.of("Non (boutique / commande)", "Coins Jobs+", "BP Tour de Combat", "Objet"), "Boutique en ligne : passe admin premium donner %player%");
        f.decimal("Prix", prem, "prix", null);
        EditBox item = f.text("Objet demandé", prem, "objet", "Si achat par objet (ou #tag)");
        f.suggest(item, ClientData::items);
        f.integer("Bonus de points premium (%)", saison, "bonus_premium_pourcent", 0, 1000, null);
        JsonObject login = Ui.obj(saison, "connexion");
        f.section("Connexion quotidienne");
        f.bool("Activée", login, "actif", null);
        f.integer("Points par jour", login, "points", 0, 1_000_000, null);
        f.intMap("Bonus de série", login, "bonus_serie", "jour:points, ex. 7:500, 14:1000");
        f.bool("Jour manqué = série perdue", login, "serie_cassee_si_jour_manque", null);
        JsonObject lp = Ui.obj(saison, "luckperms");
        f.section("Grades LuckPerms (durée = fin de saison)");
        f.bool("Attribuer les grades", lp, "actif", null);
        f.text("Groupe passe gratuit", lp, "groupe_gratuit", null);
        f.text("Groupe passe premium", lp, "groupe_premium", null);
        f.integer("Poids des groupes", lp, "poids", 0, 1000, null);
        JsonObject st = Ui.obj(saison, "etoiles");
        f.section("Étoiles du chat (%cobblepulse_battlepass:etoile%)");
        f.text("Passe gratuit", st, "gratuit", "ex. <#C8C8C8>☆</#> ");
        f.text("Passe premium", st, "premium", "ex. <#FFD24A>★</#> ");
        f.setScroll(scrolls[0]);
        f.layout();
    }

    // ------------------------------------------------------------------ Chasses (reglages)

    private void buildHuntSettings() {
        form = new Form(this::addRenderableWidget, px + 6, cy + 22, pw - 12, ch - 24).onChange(() -> markDirty("chasses"));
        Form f = form;
        f.section("Général");
        f.bool("Chasses activées", chasses, "actif", null);
        f.bool("Lancement automatique", chasses, "acceptation_auto", "Une nouvelle chasse solo démarre dès la fin de la recharge");
        f.bool("Le Pokémon est repris", chasses, "prendre_pokemon", "Le Pokémon remis est retiré au joueur");
        f.bool("Remise auto à la capture", chasses, "remise_auto_capture", null);
        f.bool("Capturé après le début", chasses, "capture_apres_debut", "Seuls les Pokémon capturés après le lancement comptent");
        f.bool("Globales finies pour tous", chasses, "globales_verrouillees_pour_tous", "Une chasse globale remise par un joueur se termine pour tout le monde");
        f.choice("Butin", chasses, "mode_butin", List.of("poids", "pourcentage"), List.of("Un lot tiré selon les poids", "Chaque lot a sa chance"), null);
        f.integer("IV minimum exigé", chasses, "iv_minimum", 0, 31, null);
        JsonObject solo = Ui.obj(chasses, "solo");
        for (int i = 0; i < 4; i++) {
            JsonObject s = Ui.obj(solo, DIFFS.get(i));
            f.section("Chasse " + DIFF_NAMES.get(i).toLowerCase());
            f.bool("Activée", s, "actif", null);
            f.integer("Durée (s)", s, "duree", 30, 10_000_000, null);
            f.integer("Recharge (s)", s, "recharge", 0, 10_000_000, null);
            f.integer("Points de passe", s, "points", 0, 10_000_000, null);
            f.text("Permission", s, "permission", "Facultatif (vide = tout le monde)");
        }
        JsonObject g = Ui.obj(chasses, "globale");
        f.section("Chasses globales");
        f.bool("Activées", g, "actif", null);
        f.integer("Simultanées", g, "simultanees", 1, 12, null);
        f.integer("Durée (s)", g, "duree", 30, 10_000_000, null);
        f.integer("Recharge (s)", g, "recharge", 0, 10_000_000, null);
        f.integer("Points de passe", g, "points", 0, 10_000_000, null);
        f.text("Permission", g, "permission", null);
        f.button("Vague en cours", "Relancer les chasses globales", () -> Ui.send("admin_hunts_restart"), null);
        f.setScroll(scrolls[3]);
        f.layout();
    }

    // ------------------------------------------------------------------ Joueurs

    private void buildPlayers() {
        form = new Form(this::addRenderableWidget, px + 6, cy + 2, Math.min(pw - 12, 430), ch - 4);
        Form f = form;
        f.section("Joueur");
        EditBox name = f.free("Pseudo", playerName, "Joueur connu du passe (Tab pour les suggestions)");
        name.setResponder(v -> playerName = v.trim());
        f.suggest(name, s -> {
            List<String> out = new ArrayList<>();
            for (JsonElement e : Ui.arr(data, "joueurs")) {
                if (e.getAsString().toLowerCase().startsWith(s)) out.add(e.getAsString());
                if (out.size() >= 8) break;
            }
            return out;
        });
        f.button("", "Voir la fiche", () -> playerAction("admin_player", null), null);
        f.section("Points de passe");
        EditBox amt = f.free("Montant", amount, null);
        amt.setFilter(s -> s.matches("\\d{0,10}"));
        amt.setResponder(v -> amount = v);
        List<Button> pts = f.buttons("", List.of("Donner", "Retirer", "Définir"), List.of(
                () -> points("give"), () -> points("take"), () -> points("set")));
        for (Button b : pts) b.active = perm("points");
        f.section("Premium");
        List<Button> pr = f.buttons("", List.of("Donner le premium", "Retirer le premium"), List.of(
                () -> playerAction("admin_premium", o -> o.addProperty("on", true)),
                () -> playerAction("admin_premium", o -> o.addProperty("on", false))));
        for (Button b : pr) b.active = perm("premium");
        f.section("Réinitialisation");
        List<Button> rs = f.buttons("", List.of("Réinitialiser (garde le premium)", "Tout réinitialiser"), List.of(
                () -> playerAction("admin_reset", o -> o.addProperty("keepPremium", true)),
                () -> {
                    if (confirmed("reset_full_" + playerName)) playerAction("admin_reset", o -> o.addProperty("keepPremium", false));
                }));
        for (Button b : rs) b.active = perm("reset");
        Button all = f.button("Tous les joueurs", "Réinitialiser TOUS les joueurs…", () -> {
            if (confirmed("reset_all")) {
                JsonObject a = new JsonObject();
                a.addProperty("name", "*");
                a.addProperty("keepPremium", true);
                Ui.send("admin_reset", a);
            }
        }, "Remet à zéro la progression de la saison de tous les joueurs (le premium est conservé).");
        all.active = perm("reset");
        f.layout();
    }

    private void points(String op) {
        playerAction("admin_points", o -> {
            o.addProperty("op", op);
            try {
                o.addProperty("amount", Long.parseLong(amount.isBlank() ? "0" : amount));
            } catch (NumberFormatException e) {
                o.addProperty("amount", 0);
            }
        });
    }

    private void playerAction(String action, java.util.function.Consumer<JsonObject> extra) {
        if (playerName.isBlank()) {
            toast(false, "Indiquez un pseudo.");
            return;
        }
        JsonObject a = new JsonObject();
        a.addProperty("name", playerName);
        if (extra != null) extra.accept(a);
        Ui.send(action, a);
    }

    // ------------------------------------------------------------------ Rendu

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph, Ui.PANEL, Ui.BORDER);
        g.fill(px + 1, py + 41, px + pw - 1, py + 42, Ui.BORDER);
        g.fill(px + 1, py + ph - 29, px + pw - 1, py + ph - 28, Ui.BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String sec = SECTIONS[tab];
        if (saveBtn != null) saveBtn.active = !sec.isEmpty() && dirty.contains(sec) && perm("edit");
        if (undoBtn != null) undoBtn.active = !sec.isEmpty() && dirty.contains(sec);
        super.render(g, mouseX, mouseY, partialTick);
        hots.clear();
        tooltip.clear();
        Ui.text(g, "★ Éditeur du Passe de Combat", px + 8, py + 6, Ui.GOLD);
        String info = Ui.str(data, "statutTexte", "") + " • fin le " + DATE.format(Instant.ofEpochMilli(Ui.lng(data, "fin", 0)));
        Ui.text(g, info, px + pw - 8 - Ui.font().width(info), py + 6, Ui.MUTED);
        switch (tab) {
            case 0 -> renderSeason(g, mouseX, mouseY);
            case 1 -> renderRewards(g, mouseX, mouseY);
            case 2 -> renderQuests(g, mouseX, mouseY);
            case 3 -> renderHunts(g, mouseX, mouseY);
            default -> renderPlayers(g);
        }
        if (form != null) form.render(g, mouseX, mouseY);
        if (!status.isEmpty()) {
            Ui.text(g, Ui.cut(status, pw - 320), px + 8, py + ph - 24, statusColor);
            if (!warnings.isEmpty()) {
                Ui.text(g, Ui.cut("⚠ " + warnings.get(0) + (warnings.size() > 1 ? " (+" + (warnings.size() - 1) + ")" : ""), pw - 320), px + 8,
                        py + ph - 13, Ui.GOLD);
                if (Ui.in(mouseX, mouseY, px + 8, py + ph - 26, pw - 320, 24)) {
                    tooltip.clear();
                    for (String w : warnings) tooltip.add(Component.literal("• " + w));
                }
            }
        }
        if (!tooltip.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : tooltip) lines.addAll(Ui.font().split(c, 260));
            g.renderTooltip(Ui.font(), lines, mouseX, mouseY);
        }
    }

    private void hot(GuiGraphics g, int x, int y, int w, int h, String label, int color, boolean enabled, int mx, int my, Runnable action) {
        boolean hov = enabled && Ui.in(mx, my, x, y, w, h);
        Ui.panel(g, x, y, w, h, enabled ? (hov ? 0xFF2C3E58 : 0xFF223044) : 0xFF1A1F27, enabled ? color : Ui.BORDER);
        Ui.centered(g, Ui.cut(label, w - 2), x + w / 2, y + (h - 8) / 2, enabled ? color : Ui.MUTED);
        if (enabled) hots.add(new Hot(x, y, w, h, action));
    }

    private void renderSeason(GuiGraphics g, int mx, int my) {
        Ui.text(g, "x", px + 334, cy + 7, Ui.MUTED);
        Ui.text(g, "min", px + 396, cy + 7, Ui.MUTED);
        Ui.text(g, "Bonus :", px + 262, cy + 7, Ui.MUTED);
        double boost = Ui.dbl(data, "boost", 1.0);
        if (boost > 1.0) Ui.text(g, "Bonus x" + boost + " en cours", px + 520, cy + 7, Ui.GOLD);
        Ui.text(g, "Les noms de champs, les messages et les fichiers sont décrits dans le README.", px + 8, cy + 24, Ui.MUTED);
    }

    // ------------------------------------------------------------------ Recompenses

    private JsonObject tier(int t, boolean create) {
        JsonObject paliers = Ui.obj(recompenses, "paliers");
        String k = String.valueOf(t);
        if (!paliers.has(k) || !paliers.get(k).isJsonObject()) {
            if (!create) return null;
            JsonObject o = new JsonObject();
            o.add("gratuit", new JsonArray());
            o.add("premium", new JsonArray());
            paliers.add(k, o);
        }
        return paliers.getAsJsonObject(k);
    }

    private void renderRewards(GuiGraphics g, int mx, int my) {
        int total = (int) Ui.lng(saison, "paliers", 50);
        int listW = 120;
        int lx = px + 8;
        int top = cy + 4;
        int bottom = cy + ch - 4;
        int rowH = 16;
        double max = Math.max(0, total * rowH - (bottom - top));
        scrolls[1] = Math.max(0, Math.min(max, scrolls[1]));
        selTier = Math.max(1, Math.min(total, selTier));
        g.enableScissor(lx, top, lx + listW, bottom);
        for (int t = 1; t <= total; t++) {
            int y = top + (t - 1) * rowH - (int) scrolls[1];
            if (y + rowH < top || y > bottom) continue;
            JsonObject tr = tier(t, false);
            int ng = tr == null ? 0 : Ui.arr(tr, "gratuit").size();
            int np = tr == null ? 0 : Ui.arr(tr, "premium").size();
            boolean sel = t == selTier;
            boolean hov = Ui.in(mx, my, lx, y, listW, rowH - 1) && my >= top && my < bottom;
            g.fill(lx, y, lx + listW, y + rowH - 1, sel ? 0xFF2B4766 : hov ? 0xFF222C3A : 0xFF181E28);
            Ui.text(g, "Palier " + t, lx + 4, y + 4, sel ? Ui.TEXT : Ui.MUTED);
            String c = ng + " / " + np;
            Ui.text(g, c, lx + listW - 4 - Ui.font().width(c), y + 4, ng + np == 0 ? Ui.ERROR : Ui.GOLD);
            int tt = t;
            if (my >= top && my < bottom) hots.add(new Hot(lx, y, listW, rowH - 1, () -> selTier = tt));
        }
        g.disableScissor();
        Ui.text(g, "gratuit / premium", lx, bottom + 2 > py + ph - 30 ? bottom - 10 : bottom + 2, Ui.MUTED);
        int rx = lx + listW + 8;
        int rw = px + pw - 8 - rx;
        int colW = (rw - 8) / 2;
        Ui.text(g, "Palier " + selTier, rx, top, Ui.GOLD);
        hot(g, rx + 80, top - 2, 120, 12, "Copier vers le suivant", Ui.ACCENT, selTier < total, mx, my, () -> {
            JsonObject src = tier(selTier, true);
            Ui.obj(recompenses, "paliers").add(String.valueOf(selTier + 1), src.deepCopy());
            selTier++;
            markDirty("recompenses");
        });
        rewardColumn(g, rx, top + 14, colW, bottom - top - 14, "Gratuit", Ui.arr(tier(selTier, true), "gratuit"), false, mx, my, 2);
        rewardColumn(g, rx + colW + 8, top + 14, colW, bottom - top - 14, "★ Premium", Ui.arr(tier(selTier, true), "premium"), false, mx, my, 3);
    }

    /** Colonne de recompenses (passe ou butin de chasse) : modifier, monter, descendre, supprimer, ajouter. */
    private void rewardColumn(GuiGraphics g, int x, int y, int w, int h, String title, JsonArray list, boolean loot, int mx, int my, int scrollSlot) {
        String section = loot ? "chasses" : "recompenses";
        Ui.panel(g, x, y, w, h, Ui.PANEL2, title.startsWith("★") ? Ui.GOLD_DARK : Ui.BORDER);
        Ui.text(g, title, x + 6, y + 5, title.startsWith("★") ? Ui.GOLD : Ui.ACCENT);
        int ay = y + 2;
        int bw = 54;
        hot(g, x + w - 3 * (bw + 2) - 4, ay, bw, 12, "+ Objet", Ui.OK, perm("edit"), mx, my, () -> addReward(list, "objet", loot));
        hot(g, x + w - 2 * (bw + 2) - 4, ay, bw, 12, "+ Pokémon", Ui.OK, perm("edit"), mx, my, () -> addReward(list, "pokemon", loot));
        hot(g, x + w - (bw + 2) - 4, ay, bw, 12, "+ Autre", Ui.OK, perm("edit"), mx, my, () -> addReward(list, "commande", loot));
        int top = y + 18;
        int bottom = y + h - 4;
        int rowH = 24;
        double max = Math.max(0, list.size() * rowH - (bottom - top));
        scrolls[scrollSlot] = Math.max(0, Math.min(max, scrolls[scrollSlot]));
        if (list.isEmpty()) Ui.centered(g, "Aucune récompense", x + w / 2, top + 10, Ui.MUTED);
        g.enableScissor(x + 1, top, x + w - 1, bottom);
        for (int i = 0; i < list.size(); i++) {
            int ry = top + i * rowH - (int) scrolls[scrollSlot];
            if (ry + rowH < top || ry > bottom) continue;
            JsonObject r = list.get(i).getAsJsonObject();
            Ui.panel(g, x + 4, ry, w - 8, rowH - 2, Ui.CARD, Ui.BORDER);
            Ui.item(g, ClientData.rewardIcon(r), x + 7, ry + 3, 1f, true);
            String label = ClientData.rewardLabel(r) + (loot ? "  (" + Ui.dbl(r, "chance", 1) + ")" : "");
            int btnArea = 4 * 18 + 6;
            Ui.text(g, Ui.cut(label, w - 40 - btnArea), x + 28, ry + 7, Ui.TEXT);
            if (ry >= top && ry + rowH - 2 <= bottom) {
                int idx = i;
                int bx = x + w - 8 - btnArea + 6;
                hot(g, bx, ry + 4, 16, 14, "✎", Ui.ACCENT, perm("edit"), mx, my, () -> editReward(list, idx, loot));
                hot(g, bx + 18, ry + 4, 16, 14, "▲", Ui.TEXT, idx > 0 && perm("edit"), mx, my, () -> {
                    swap(list, idx, idx - 1);
                    markDirty(section);
                });
                hot(g, bx + 36, ry + 4, 16, 14, "▼", Ui.TEXT, idx < list.size() - 1 && perm("edit"), mx, my, () -> {
                    swap(list, idx, idx + 1);
                    markDirty(section);
                });
                hot(g, bx + 54, ry + 4, 16, 14, "X", Ui.ERROR, perm("edit"), mx, my, () -> {
                    list.remove(idx);
                    markDirty(section);
                });
                if (Ui.in(mx, my, x + 4, ry, w - 8 - btnArea, rowH - 2)) {
                    tooltip.clear();
                    tooltip.add(Component.literal(ClientData.rewardLabel(r)));
                    tooltip.add(Component.literal(ClientData.REWARD_TYPE_NAMES.get(Math.max(0, ClientData.REWARD_TYPES.indexOf(Ui.str(r, "type", "objet")))))
                            .withStyle(s -> s.withColor(0x8E9AAB)));
                }
            }
        }
        g.disableScissor();
    }

    private static void swap(JsonArray a, int i, int j) {
        JsonElement x = a.get(i);
        a.set(i, a.get(j));
        a.set(j, x);
    }

    private void addReward(JsonArray list, String type, boolean loot) {
        JsonObject r = ClientData.newReward(type);
        minecraft.setScreen(new RewardEditScreen(this, r, loot, saved -> {
            list.add(saved);
            markDirty(loot ? "chasses" : "recompenses");
        }));
    }

    private void editReward(JsonArray list, int idx, boolean loot) {
        JsonObject copy = list.get(idx).getAsJsonObject().deepCopy();
        minecraft.setScreen(new RewardEditScreen(this, copy, loot, saved -> {
            list.set(idx, saved);
            markDirty(loot ? "chasses" : "recompenses");
        }));
    }

    // ------------------------------------------------------------------ Quetes

    private JsonObject typeInfo(String id) {
        for (JsonElement e : Ui.arr(data, "types")) if (Ui.str(e.getAsJsonObject(), "id", "").equals(id)) return e.getAsJsonObject();
        return new JsonObject();
    }

    private void renderQuests(GuiGraphics g, int mx, int my) {
        int bx = px + 8;
        for (int i = 0; i < CATS.size(); i++) {
            int idx = i;
            int n = Ui.arr(quetes, CATS.get(i)).size();
            hot(g, bx, cy + 2, 130, 16, CAT_NAMES.get(i) + " (" + n + ")", questCat == i ? Ui.GOLD : Ui.TEXT, questCat != i, mx, my, () -> {
                questCat = idx;
                scrolls[4] = 0;
            });
            bx += 134;
        }
        JsonArray list = Ui.arr(quetes, CATS.get(questCat));
        hot(g, px + pw - 140, cy + 2, 132, 16, "+ Nouvelle quête", Ui.OK, perm("edit"), mx, my, () -> {
            JsonObject q = new JsonObject();
            q.addProperty("id", "quete_" + (list.size() + 1));
            q.addProperty("nom", "");
            q.addProperty("type", "capture");
            q.addProperty("objectif", 10);
            q.addProperty("points", 200);
            q.addProperty("premium", false);
            q.addProperty("icone", "");
            q.add("filtres", new JsonObject());
            minecraft.setScreen(new QuestEditScreen(this, q, Ui.arr(data, "types"), saved -> {
                list.add(saved);
                markDirty("quetes");
            }));
        });
        String help = switch (questCat) {
            case 0 -> "Chaque jour, " + Ui.lng(saison, "quetes_quotidiennes", 3) + " quête(s) (+" + Ui.lng(saison, "quetes_quotidiennes_premium", 1)
                    + " premium) sont tirées dans cette réserve.";
            case 1 -> "Chaque semaine, " + Ui.lng(saison, "quetes_hebdomadaires", 5) + " quête(s) (+" + Ui.lng(saison, "quetes_hebdomadaires_premium", 2)
                    + " premium) sont tirées dans cette réserve.";
            default -> "Toutes les quêtes de saison sont actives pendant toute la saison.";
        };
        Ui.text(g, help, px + 8, cy + 22, Ui.MUTED);
        int top = cy + 34;
        int bottom = cy + ch - 2;
        int rowH = 24;
        double max = Math.max(0, list.size() * rowH - (bottom - top));
        scrolls[4] = Math.max(0, Math.min(max, scrolls[4]));
        g.enableScissor(px + 4, top, px + pw - 4, bottom);
        for (int i = 0; i < list.size(); i++) {
            int ry = top + i * rowH - (int) scrolls[4];
            if (ry + rowH < top || ry > bottom) continue;
            JsonObject q = list.get(i).getAsJsonObject();
            JsonObject t = typeInfo(Ui.str(q, "type", ""));
            boolean prem = Ui.bool(q, "premium", false);
            Ui.panel(g, px + 8, ry, pw - 16, rowH - 2, Ui.CARD, prem ? Ui.GOLD_DARK : Ui.BORDER);
            String iconId = Ui.str(q, "icone", "");
            if (iconId.isBlank()) iconId = Ui.str(t, "icon", "minecraft:paper");
            Ui.item(g, Ui.itemById(iconId), px + 12, ry + 3, 1f, false);
            String name = Ui.str(q, "nom", "");
            if (name.isBlank()) name = Ui.str(t, "phrase", Ui.str(q, "type", "")).replace("%n", String.valueOf(Ui.lng(q, "objectif", 1)));
            Ui.text(g, Ui.cut((prem ? "★ " : "") + name, pw / 2 - 40), px + 32, ry + 7, prem ? Ui.GOLD : Ui.TEXT);
            String meta = Ui.str(t, "label", Ui.str(q, "type", "?")) + " • " + Ui.lng(q, "objectif", 1) + " • +" + Ui.lng(q, "points", 0) + " pts • " + Ui.str(q, "id", "");
            Ui.text(g, Ui.cut(meta, pw / 2 - 110), px + pw / 2, ry + 7, Ui.MUTED);
            if (ry >= top && ry + rowH - 2 <= bottom) {
                int idx = i;
                int bx2 = px + pw - 12 - 5 * 18;
                hot(g, bx2, ry + 4, 16, 14, "✎", Ui.ACCENT, perm("edit"), mx, my, () -> {
                    JsonObject copy = list.get(idx).getAsJsonObject().deepCopy();
                    minecraft.setScreen(new QuestEditScreen(this, copy, Ui.arr(data, "types"), saved -> {
                        list.set(idx, saved);
                        markDirty("quetes");
                    }));
                });
                hot(g, bx2 + 18, ry + 4, 16, 14, "+", Ui.OK, perm("edit"), mx, my, () -> {
                    JsonObject copy = list.get(idx).getAsJsonObject().deepCopy();
                    copy.addProperty("id", Ui.str(copy, "id", "quete") + "_copie");
                    list.add(copy);
                    markDirty("quetes");
                });
                hot(g, bx2 + 36, ry + 4, 16, 14, "▲", Ui.TEXT, idx > 0 && perm("edit"), mx, my, () -> {
                    swap(list, idx, idx - 1);
                    markDirty("quetes");
                });
                hot(g, bx2 + 54, ry + 4, 16, 14, "▼", Ui.TEXT, idx < list.size() - 1 && perm("edit"), mx, my, () -> {
                    swap(list, idx, idx + 1);
                    markDirty("quetes");
                });
                hot(g, bx2 + 72, ry + 4, 16, 14, "X", Ui.ERROR, perm("edit"), mx, my, () -> {
                    list.remove(idx);
                    markDirty("quetes");
                });
            }
        }
        g.disableScissor();
        if (list.isEmpty()) Ui.centered(g, "Aucune quête dans cette réserve.", px + pw / 2, top + 20, Ui.MUTED);
    }

    // ------------------------------------------------------------------ Chasses

    private void renderHunts(GuiGraphics g, int mx, int my) {
        String[] subs = {"Réglages", "Pokémon", "Butin"};
        int bx = px + 8;
        for (int i = 0; i < subs.length; i++) {
            int idx = i;
            hot(g, bx, cy + 2, 90, 16, subs[i], huntSub == i ? Ui.GOLD : Ui.TEXT, huntSub != i, mx, my, () -> {
                if (form != null) scrolls[3] = form.scroll();
                huntSub = idx;
                rebuild();
            });
            bx += 94;
        }
        if (huntSub == 0) return;
        bx += 16;
        for (int i = 0; i < DIFFS.size(); i++) {
            int idx = i;
            hot(g, bx, cy + 2, 70, 16, DIFF_NAMES.get(i), huntDiff == i ? Ui.PURPLE : Ui.TEXT, huntDiff != i, mx, my, () -> huntDiff = idx);
            bx += 74;
        }
        String diff = DIFFS.get(huntDiff);
        if (huntSub == 2) {
            JsonArray loot = Ui.arr(Ui.obj(chasses, "butin"), diff);
            rewardColumn(g, px + 8, cy + 24, pw - 16, ch - 26, "Butin " + DIFF_NAMES.get(huntDiff).toLowerCase() + " (en plus des points)", loot, true, mx, my, 5);
            return;
        }
        JsonArray pool = Ui.arr(Ui.obj(chasses, "pokemon"), diff);
        hot(g, px + pw - 140, cy + 22, 132, 14, "+ Ajouter un Pokémon", Ui.OK, perm("edit"), mx, my, () -> {
            JsonObject e = new JsonObject();
            e.addProperty("espece", "pikachu");
            e.addProperty("forme", "");
            e.add("aspects", new JsonArray());
            e.addProperty("genre", "");
            e.addProperty("nature", "");
            e.addProperty("iv_parfaits", 0);
            e.addProperty("poids", 1.0);
            minecraft.setScreen(new HuntEntryEditScreen(this, e, saved -> {
                pool.add(saved);
                markDirty("chasses");
            }));
        });
        int top = cy + 40;
        int bottom = cy + ch - 2;
        int cols = Math.max(1, (pw - 16) / 200);
        int cw = (pw - 16) / cols;
        int rowH = 30;
        int rows = (pool.size() + cols - 1) / cols;
        double max = Math.max(0, rows * rowH - (bottom - top));
        scrolls[6] = Math.max(0, Math.min(max, scrolls[6]));
        if (pool.isEmpty()) Ui.centered(g, "Aucun Pokémon : ajoutez-en pour activer cette chasse.", px + pw / 2, top + 20, Ui.MUTED);
        g.enableScissor(px + 4, top, px + pw - 4, bottom);
        for (int i = 0; i < pool.size(); i++) {
            JsonObject e = pool.get(i).getAsJsonObject();
            int x = px + 8 + (i % cols) * cw;
            int y = top + (i / cols) * rowH - (int) scrolls[6];
            if (y + rowH < top || y > bottom) continue;
            Ui.panel(g, x, y, cw - 4, rowH - 3, Ui.CARD, Ui.BORDER);
            Ui.item(g, ClientData.pokemonIcon(Ui.str(e, "espece", ""), Ui.str(e, "forme", ""), false), x + 4, y + 5, 1f, false);
            Ui.text(g, Ui.cut(ClientData.speciesName(Ui.str(e, "espece", "?")), cw - 70), x + 24, y + 4, Ui.TEXT);
            List<String> req = new ArrayList<>();
            if (!Ui.str(e, "genre", "").isEmpty()) req.add(Ui.str(e, "genre", ""));
            if (!Ui.str(e, "nature", "").isEmpty()) req.add(Ui.str(e, "nature", ""));
            if (Ui.lng(e, "iv_parfaits", 0) > 0) req.add(Ui.lng(e, "iv_parfaits", 0) + " IV");
            req.add("poids " + Ui.dbl(e, "poids", 1));
            Ui.text(g, Ui.cut(String.join(", ", req), cw - 70), x + 24, y + 15, Ui.MUTED);
            if (y >= top && y + rowH - 3 <= bottom) {
                int idx = i;
                hot(g, x + cw - 44, y + 6, 16, 14, "✎", Ui.ACCENT, perm("edit"), mx, my, () -> {
                    JsonObject copy = pool.get(idx).getAsJsonObject().deepCopy();
                    minecraft.setScreen(new HuntEntryEditScreen(this, copy, saved -> {
                        pool.set(idx, saved);
                        markDirty("chasses");
                    }));
                });
                hot(g, x + cw - 24, y + 6, 16, 14, "X", Ui.ERROR, perm("edit"), mx, my, () -> {
                    pool.remove(idx);
                    markDirty("chasses");
                });
            }
        }
        g.disableScissor();
    }

    // ------------------------------------------------------------------ Joueurs

    private void renderPlayers(GuiGraphics g) {
        int x = px + Math.min(pw - 12, 430) + 20;
        int y = cy + 6;
        if (playerInfo == null) {
            Ui.text(g, "Choisissez un joueur puis « Voir la fiche ».", x, y, Ui.MUTED);
            return;
        }
        JsonObject p = playerInfo;
        Ui.text(g, Ui.str(p, "nom", "?"), x, y, Ui.GOLD);
        String[][] rows = {
                {"Points", Ui.num(Ui.lng(p, "points", 0))},
                {"Palier", String.valueOf(Ui.lng(p, "palier", 0))},
                {"Premium", Ui.bool(p, "premium", false) ? "oui (" + Ui.str(p, "premiumSource", "") + ")" : "non"},
                {"Paliers réclamés", String.valueOf(Ui.lng(p, "reclames", 0))},
                {"Quêtes terminées", String.valueOf(Ui.lng(p, "quetes", 0))},
                {"Chasses terminées", String.valueOf(Ui.lng(p, "chasses", 0))},
                {"Série / jours", Ui.lng(p, "serie", 0) + " / " + Ui.lng(p, "jours", 0)},
                {"Points quêtes", Ui.num(Ui.lng(p, "pointsQuetes", 0))},
                {"Points connexion", Ui.num(Ui.lng(p, "pointsConnexion", 0))},
                {"Points chasses", Ui.num(Ui.lng(p, "pointsChasses", 0))},
                {"Points staff", Ui.num(Ui.lng(p, "pointsStaff", 0))}};
        for (int i = 0; i < rows.length; i++) {
            Ui.text(g, rows[i][0], x, y + 16 + i * 12, Ui.MUTED);
            Ui.text(g, rows[i][1], x + 120, y + 16 + i * 12, Ui.TEXT);
        }
    }

    // ------------------------------------------------------------------ Entrees

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (form != null && form.mouseClicked(mx, my)) return true;
        if (button == 0) {
            for (Hot h : new ArrayList<>(hots)) {
                if (Ui.in(mx, my, h.x, h.y, h.w, h.h)) {
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    h.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (form != null && form.mouseScrolled(mx, my, sy)) return true;
        int slot = switch (tab) {
            case 1 -> mx < px + 136 ? 1 : (mx < px + 136 + (pw - 152) / 2 ? 2 : 3);
            case 2 -> 4;
            case 3 -> huntSub == 2 ? 5 : 6;
            default -> 7;
        };
        scrolls[slot] -= sy * 20;
        return true;
    }
}
