package fr.cobblepulse.battlepass.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Ecran du Passe de Combat (d'apres l'ecran de Cobblemon Trainer Pass) : paliers gratuits / premium a reclamer,
 * quetes quotidiennes, hebdomadaires et de saison, chasses solo et globales.
 */
public final class PassScreen extends Screen {
    private record Hot(int x, int y, int w, int h, Runnable action, List<Component> tip) {}

    private static int lastTab;
    private static int lastQuestCat;

    private JsonObject data;
    private JsonArray tiers = new JsonArray();
    private long clockOffset;
    private int tab;
    private int questCat;
    private int firstTier = -1;
    private double questScroll;
    private JsonObject candidates;
    private double candScroll;
    private final List<Hot> hots = new ArrayList<>();
    private final List<Component> tooltip = new ArrayList<>();
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int cy;
    private int ch;
    private Button claimAllBtn;
    private Button premiumBtn;
    private String toast = "";
    private int toastColor = Ui.OK;
    private long toastUntil;
    private int savedGuiScale = -1;

    public PassScreen(JsonObject data) {
        super(Component.literal("Passe de Combat"));
        this.tab = lastTab;
        this.questCat = lastQuestCat;
        update(data, true);
    }

    /** Nouvelles donnees du serveur ({@code full} : avec les paliers). */
    public void update(JsonObject d, boolean full) {
        this.data = d;
        if (full && d.has("tiers")) {
            this.tiers = d.getAsJsonArray("tiers");
            Ui.clearCaches();
        }
        this.clockOffset = Ui.lng(Ui.obj(d, "saison"), "now", System.currentTimeMillis()) - System.currentTimeMillis();
        if (candidates != null && minecraft != null) Ui.send("hunt_candidates", candRequest());
        if (minecraft != null) rebuild();
    }

    public void showCandidates(JsonObject c) {
        this.candidates = c;
        this.candScroll = 0;
    }

    public void toast(boolean ok, String msg) {
        this.toast = msg;
        this.toastColor = ok ? Ui.OK : Ui.ERROR;
        this.toastUntil = System.currentTimeMillis() + 4000;
        if (ok && candidates != null) candidates = null;
    }

    private long now() {
        return System.currentTimeMillis() + clockOffset;
    }

    private JsonObject season() {
        return Ui.obj(data, "saison");
    }

    private JsonObject player() {
        return Ui.obj(data, "joueur");
    }

    // ------------------------------------------------------------------ Construction

    private static final int MIN_W = 440;
    private static final int MIN_H = 260;

    private void fitGuiScale() {
        com.mojang.blaze3d.platform.Window win = minecraft.getWindow();
        int cur = (int) win.getGuiScale();
        int s = cur;
        while (s > 1 && (win.getWidth() / s < MIN_W || win.getHeight() / s < MIN_H)) s--;
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
        // Le serveur n'envoie les mises a jour en direct qu'aux ecrans ouverts
        Ui.send("view", openArg(true));
        px = Math.max(6, (width - Math.min(width - 12, 760)) / 2);
        pw = Math.min(width - 12, 760);
        py = 6;
        ph = height - 12;
        cy = py + 62;
        ch = py + ph - 6 - cy;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        String[] tabs = {"Récompenses", "Quêtes", "Chasses"};
        int bx = px + 8;
        for (int i = 0; i < tabs.length; i++) {
            int idx = i;
            Button b = Button.builder(Component.literal(tabs[i]), btn -> {
                tab = idx;
                lastTab = idx;
                rebuild();
            }).bounds(bx, py + 38, 84, 18).build();
            b.active = tab != i;
            addRenderableWidget(b);
            bx += 88;
        }
        if (Ui.bool(data, "admin", false)) {
            addRenderableWidget(Button.builder(Component.literal("Éditeur"), btn -> Ui.send("admin_open")).bounds(bx, py + 38, 60, 18).build());
        }
        int n = (int) Ui.lng(player(), "aReclamer", 0);
        claimAllBtn = Button.builder(Component.literal(n > 0 ? "Tout réclamer (" + n + ")" : "Rien à réclamer"), btn -> Ui.send("claim_all"))
                .bounds(px + pw - 128, py + 38, 120, 18).build();
        claimAllBtn.active = n > 0;
        addRenderableWidget(claimAllBtn);
        JsonObject s = season();
        boolean canBuy = Ui.bool(s, "premiumActif", false) && !Ui.bool(player(), "premium", false)
                && !"aucun".equals(Ui.str(s, "achatMode", "aucun"));
        if (canBuy) {
            premiumBtn = Button.builder(Component.literal("★ Premium : " + Ui.str(s, "achatPrix", "")), btn -> Ui.send("buy_premium"))
                    .bounds(px + pw - 128 - 154, py + 38, 150, 18).build();
            addRenderableWidget(premiumBtn);
        }
        if (firstTier < 0) {
            int t = (int) Ui.lng(player(), "palier", 0);
            firstTier = Math.max(1, t - 1);
        }
    }

    @Override
    public void removed() {
        super.removed();
        Ui.send("view", openArg(false));
        if (savedGuiScale > 0) {
            minecraft.getWindow().setGuiScale(savedGuiScale);
            savedGuiScale = -1;
        }
    }

    static JsonObject openArg(boolean open) {
        JsonObject o = new JsonObject();
        o.addProperty("open", open);
        return o;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ Rendu

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph, Ui.PANEL, Ui.BORDER);
        g.fill(px + 1, py + 60, px + pw - 1, py + 61, Ui.BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hots.clear();
        tooltip.clear();
        header(g);
        boolean modal = candidates != null;
        int mx = modal ? -1000 : mouseX;
        int my = modal ? -1000 : mouseY;
        String status = Ui.str(season(), "statut", "ouvert");
        if (!"ouvert".equals(status) && !"terminee".equals(status)) {
            Ui.centered(g, "Passe de Combat : " + Ui.str(season(), "statutTexte", status), px + pw / 2, cy + ch / 2 - 10, Ui.ERROR);
            Ui.centered(g, "Votre progression est conservée.", px + pw / 2, cy + ch / 2 + 4, Ui.MUTED);
        } else {
            switch (tab) {
                case 1 -> quests(g, mx, my);
                case 2 -> hunts(g, mx, my);
                default -> rewards(g, mx, my);
            }
        }
        if (modal) candidatesOverlay(g, mouseX, mouseY);
        if (!toast.isEmpty() && System.currentTimeMillis() < toastUntil) {
            int tw = Ui.font().width(toast) + 12;
            Ui.panel(g, px + pw / 2 - tw / 2, py + ph - 22, tw, 16, 0xF0101010, toastColor);
            Ui.centered(g, toast, px + pw / 2, py + ph - 18, toastColor);
        }
        if (!tooltip.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : tooltip) lines.addAll(Ui.font().split(c, 240));
            g.renderTooltip(Ui.font(), lines, mouseX, mouseY);
        }
    }

    private void header(GuiGraphics g) {
        JsonObject s = season();
        JsonObject p = player();
        Ui.text(g, "★ Passe de Combat", px + 8, py + 6, Ui.GOLD);
        Ui.text(g, "— " + Ui.str(s, "nom", ""), px + 12 + Ui.font().width("★ Passe de Combat"), py + 6, Ui.TEXT);
        String status = Ui.str(s, "statut", "ouvert");
        String right = switch (status) {
            case "ouvert" -> "Fin dans " + Ui.duration(Ui.lng(s, "fin", 0) - now());
            case "terminee" -> "Saison terminée";
            default -> Ui.str(s, "statutTexte", status);
        };
        double boost = Ui.dbl(s, "boost", 1.0);
        if (boost > 1.0 && Ui.lng(s, "boostFin", 0) > now()) right = "Bonus x" + fmt(boost) + " (" + Ui.duration(Ui.lng(s, "boostFin", 0) - now()) + ")  " + right;
        Ui.text(g, right, px + pw - 8 - Ui.font().width(right), py + 6, "ouvert".equals(status) ? Ui.MUTED : Ui.ERROR);

        int tier = (int) Ui.lng(p, "palier", 0);
        int max = (int) Ui.lng(s, "paliers", 1);
        long points = Ui.lng(p, "points", 0);
        long base = Ui.lng(p, "base", 0);
        long next = Ui.lng(p, "next", 0);
        String tierTxt = "Palier " + tier + " / " + max;
        Ui.text(g, tierTxt, px + 8, py + 22, Ui.TEXT);
        int bx = px + 14 + Ui.font().width("Palier 000 / 000");
        boolean prem = Ui.bool(p, "premium", false);
        String badge = Ui.bool(s, "premiumActif", true) ? (prem ? "★ PREMIUM" : "☆ GRATUIT") : "";
        int bw = pw - (bx - px) - 16 - Ui.font().width("☆ GRATUIT") - 10;
        double pct = next <= 0 ? 1.0 : (points - base) / (double) next;
        Ui.bar(g, bx, py + 20, bw, 12, pct, prem ? Ui.GOLD : Ui.ACCENT);
        String barTxt = next <= 0 ? Ui.num(points) + " points - palier maximum !" : Ui.num(points - base) + " / " + Ui.num(next) + " points";
        Ui.centered(g, barTxt, bx + bw / 2, py + 22, Ui.TEXT);
        if (!badge.isEmpty()) Ui.text(g, badge, px + pw - 8 - Ui.font().width(badge), py + 22, prem ? Ui.GOLD : Ui.MUTED);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    /** Onglet : l'onglet courant est mis en avant (non cliquable). */
    private void tabHot(GuiGraphics g, int x, int y, int w, int h, String label, boolean selected, int mx, int my, Runnable action) {
        if (selected) {
            Ui.panel(g, x, y, w, h, 0xFF2B4766, Ui.GOLD);
            Ui.centered(g, Ui.cut(label, w - 4), x + w / 2, y + (h - 8) / 2, Ui.GOLD);
        } else {
            hot(g, x, y, w, h, label, Ui.TEXT, true, mx, my, action);
        }
    }

    private void hot(GuiGraphics g, int x, int y, int w, int h, String label, int color, boolean enabled, int mx, int my, Runnable action) {
        boolean hov = enabled && Ui.in(mx, my, x, y, w, h);
        Ui.panel(g, x, y, w, h, enabled ? (hov ? 0xFF2C3E58 : 0xFF223044) : 0xFF1A1F27, enabled ? color : Ui.BORDER);
        Ui.centered(g, Ui.cut(label, w - 4), x + w / 2, y + (h - 8) / 2, enabled ? color : Ui.MUTED);
        if (enabled) hots.add(new Hot(x, y, w, h, action, null));
    }

    private void tipArea(int x, int y, int w, int h, int mx, int my, List<Component> lines) {
        if (Ui.in(mx, my, x, y, w, h)) {
            tooltip.clear();
            tooltip.addAll(lines);
        }
    }

    // ------------------------------------------------------------------ Recompenses

    private void rewards(GuiGraphics g, int mx, int my) {
        int total = tiers.size();
        if (total == 0) {
            Ui.centered(g, "Aucun palier.", px + pw / 2, cy + 20, Ui.MUTED);
            return;
        }
        int arrowW = 14;
        int ax = px + 6;
        int area = pw - 12 - 2 * (arrowW + 4);
        int cardW = 74;
        int n = Math.max(3, area / (cardW + 4));
        cardW = area / n - 4;
        firstTier = Math.max(1, Math.min(firstTier, Math.max(1, total - n + 1)));
        hot(g, ax, cy + 4, arrowW, ch - 18, "<", Ui.TEXT, firstTier > 1, mx, my, () -> firstTier = Math.max(1, firstTier - n));
        hot(g, px + pw - 6 - arrowW, cy + 4, arrowW, ch - 18, ">", Ui.TEXT, firstTier + n <= total, mx, my, () -> firstTier += n);
        int cx = ax + arrowW + 4;
        int tierNow = (int) Ui.lng(player(), "palier", 0);
        boolean prem = Ui.bool(player(), "premium", false);
        boolean premActive = Ui.bool(season(), "premiumActif", true);
        java.util.Set<String> claimed = new java.util.HashSet<>();
        for (JsonElement e : Ui.arr(player(), "reclames")) claimed.add(e.getAsString());
        boolean canClaim = !"maintenance".equals(Ui.str(season(), "statut", "ouvert"));
        for (int i = 0; i < n && firstTier + i <= total; i++) {
            JsonObject t = tiers.get(firstTier + i - 1).getAsJsonObject();
            int tn = (int) Ui.lng(t, "t", firstTier + i);
            int x = cx + i * (cardW + 4);
            boolean unlocked = tn <= tierNow;
            boolean current = tn == tierNow + 1;
            Ui.panel(g, x, cy + 4, cardW, ch - 18, unlocked ? Ui.CARD_ON : Ui.CARD, current ? Ui.ACCENT : unlocked ? 0xFF3E6A96 : Ui.BORDER);
            g.fill(x + 1, cy + 5, x + cardW - 1, cy + 18, unlocked ? 0xFF2B4766 : 0xFF202733);
            Ui.centered(g, "Palier " + tn, x + cardW / 2, cy + 8, unlocked ? Ui.TEXT : Ui.MUTED);
            int half = (ch - 18 - 16) / 2;
            int fy = cy + 20;
            track(g, x, fy, cardW, half - 2, Ui.arr(t, "g"), false, tn, unlocked, claimed.contains("g:" + tn), true, canClaim, mx, my);
            if (premActive) {
                track(g, x, fy + half, cardW, half - 4, Ui.arr(t, "p"), true, tn, unlocked, claimed.contains("p:" + tn), prem, canClaim, mx, my);
            }
        }
        String hint = "Molette : faire défiler  •  Cliquez sur une récompense débloquée pour la réclamer";
        Ui.centered(g, Ui.cut(hint, pw - 20), px + pw / 2, cy + ch - 10, Ui.MUTED);
    }

    private void track(GuiGraphics g, int x, int y, int w, int h, JsonArray rewards, boolean premium, int tier, boolean unlocked,
                       boolean claimed, boolean hasAccess, boolean canClaim, int mx, int my) {
        int border = premium ? Ui.GOLD_DARK : 0xFF2E4A66;
        boolean claimable = unlocked && !claimed && hasAccess && canClaim && rewards.size() > 0;
        if (claimable) {
            long t = System.currentTimeMillis() / 400 % 2;
            border = premium ? (t == 0 ? Ui.GOLD : 0xFFFFE9A0) : (t == 0 ? Ui.ACCENT : 0xFFA8E4FF);
        }
        Ui.panel(g, x + 3, y, w - 6, h, premium ? 0xFF241E10 : 0xFF141C26, border);
        Ui.text(g, premium ? "★ Premium" : "Gratuit", x + 6, y + 3, premium ? Ui.GOLD : Ui.ACCENT);
        int n = rewards.size();
        if (n == 0) {
            Ui.centered(g, "-", x + w / 2, y + h / 2 - 4, Ui.MUTED);
        } else {
            int per = Math.max(1, Math.min(n, (w - 10) / 18));
            int rows = (n + per - 1) / per;
            float scale = rows > 1 ? 1.0f : n == 1 ? Math.min(2.0f, Math.max(1f, (w - 16) / 28f)) : n == 2 ? 1.5f : 1.0f;
            if (h < 50) scale = Math.min(scale, 1.25f);
            int size = (int) (16 * scale);
            int gy = y + 14 + Math.max(0, (h - 26 - rows * (size + 2)) / 2);
            for (int i = 0; i < n; i++) {
                JsonObject r = rewards.get(i).getAsJsonObject();
                int row = i / per;
                int col = i % per;
                int inRow = Math.min(per, n - row * per);
                int rx = x + w / 2 - (inRow * (size + 2)) / 2 + col * (size + 2);
                int ry = gy + row * (size + 2);
                ItemStack s = Ui.item(Ui.str(r, "icon", ""));
                Ui.item(g, s, rx, ry, scale, true);
                if (Ui.in(mx, my, rx, ry, size, size)) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(Ui.comp(Ui.str(r, "name", "")));
                    for (JsonElement l : Ui.arr(r, "lore")) tip.add(Ui.comp(l.getAsString()));
                    tooltip.clear();
                    tooltip.addAll(tip);
                }
            }
        }
        String state = claimed ? "✔ Réclamé" : !unlocked ? "Verrouillé" : !hasAccess ? "★ Requis" : n == 0 ? "" : "Réclamer";
        int sc = claimed ? Ui.OK : claimable ? (premium ? Ui.GOLD : Ui.ACCENT) : Ui.MUTED;
        Ui.centered(g, Ui.cut(state, w - 8), x + w / 2, y + h - 11, sc);
        if (claimed || !unlocked || !hasAccess) g.fill(x + 4, y + 13, x + w - 4, y + h - 13, 0x60000000);
        if (claimable) {
            boolean pr = premium;
            hots.add(new Hot(x + 3, y, w - 6, h, () -> {
                JsonObject a = new JsonObject();
                a.addProperty("premium", pr);
                a.addProperty("tier", tier);
                Ui.send("claim", a);
            }, null));
        }
    }

    // ------------------------------------------------------------------ Quetes

    private JsonArray categories() {
        return Ui.arr(data, "quetes");
    }

    private void quests(GuiGraphics g, int mx, int my) {
        JsonArray cats = categories();
        int bx = px + 8;
        for (int i = 0; i < cats.size(); i++) {
            JsonObject c = cats.get(i).getAsJsonObject();
            String label = Ui.str(c, "label", "?");
            int count = 0;
            int done = 0;
            for (JsonElement e : Ui.arr(c, "list")) {
                count++;
                if (Ui.bool(e.getAsJsonObject(), "done", false)) done++;
            }
            String txt = label + " " + done + "/" + count;
            int idx = i;
            tabHot(g, bx, cy + 4, 118, 16, txt, questCat == i, mx, my, () -> {
                questCat = idx;
                lastQuestCat = idx;
                questScroll = 0;
            });
            bx += 122;
        }
        JsonObject login = Ui.obj(data, "connexion");
        if (Ui.bool(login, "actif", false)) {
            boolean today = Ui.bool(login, "aujourdhui", false);
            String l = (today ? "✔ " : "") + "Connexion du jour : +" + Ui.lng(login, "points", 0) + "  •  Série : " + Ui.lng(player(), "serie", 0) + " j";
            Ui.text(g, Ui.cut(l, px + pw - bx - 12), px + pw - 8 - Math.min(Ui.font().width(l), px + pw - bx - 12), cy + 8, today ? Ui.OK : Ui.MUTED);
        }
        if (cats.isEmpty()) return;
        questCat = Math.max(0, Math.min(questCat, cats.size() - 1));
        JsonObject cat = cats.get(questCat).getAsJsonObject();
        String reset = "saison".equals(Ui.str(cat, "cat", "")) ? "Fin de saison dans " + Ui.duration(Ui.lng(cat, "reset", 0) - now())
                : "Nouvelles quêtes dans " + Ui.duration(Ui.lng(cat, "reset", 0) - now());
        Ui.text(g, reset, px + 10, cy + 24, Ui.MUTED);
        JsonArray list = Ui.arr(cat, "list");
        int top = cy + 36;
        int bottom = cy + ch - 4;
        int rowH = 28;
        int maxScroll = Math.max(0, list.size() * rowH - (bottom - top));
        questScroll = Math.max(0, Math.min(maxScroll, questScroll));
        if (list.isEmpty()) Ui.centered(g, "Aucune quête dans cette catégorie.", px + pw / 2, top + 20, Ui.MUTED);
        g.enableScissor(px + 4, top, px + pw - 4, bottom);
        for (int i = 0; i < list.size(); i++) {
            int y = top + i * rowH - (int) questScroll;
            if (y + rowH < top || y > bottom) continue;
            quest(g, list.get(i).getAsJsonObject(), px + 8, y, pw - 16, rowH - 3, mx, my, top, bottom);
        }
        g.disableScissor();
    }

    private void quest(GuiGraphics g, JsonObject q, int x, int y, int w, int h, int mx, int my, int top, int bottom) {
        boolean done = Ui.bool(q, "done", false);
        boolean locked = Ui.bool(q, "locked", false);
        boolean premium = Ui.bool(q, "premium", false);
        Ui.panel(g, x, y, w, h, done ? 0xFF16261A : locked ? 0xFF1C1A14 : Ui.CARD, done ? 0xFF2E6B3A : premium ? Ui.GOLD_DARK : Ui.BORDER);
        Ui.item(g, Ui.item(Ui.str(q, "icon", "")), x + 5, y + (h - 16) / 2, 1f, false);
        long progress = Ui.lng(q, "progress", 0);
        long target = Math.max(1, Ui.lng(q, "target", 1));
        String name = (premium ? "★ " : "") + Ui.str(q, "name", "");
        int barW = Math.min(150, w / 4);
        int right = x + w - 6;
        String pts = "+" + Ui.num(Ui.lng(q, "points", 0));
        int ptsW = Ui.font().width(pts);
        boolean deliver = Ui.bool(q, "deliver", false) && !done && !locked;
        int btnW = deliver ? 46 : 0;
        int barX = right - ptsW - 8 - barW - (deliver ? btnW + 6 : 0);
        Ui.text(g, Ui.cut(name, barX - x - 32), x + 26, y + 4, done ? Ui.OK : locked ? Ui.MUTED : premium ? Ui.GOLD : Ui.TEXT);
        String sub = done ? "Terminée" : locked ? "Réservée au passe premium" : Ui.str(q, "typeLabel", "");
        JsonArray conds = Ui.arr(q, "conds");
        if (!done && !locked && conds.size() > 0) sub = sub + " • " + conds.get(0).getAsString();
        Ui.text(g, Ui.cut(sub, barX - x - 32), x + 26, y + 14, Ui.MUTED);
        Ui.bar(g, barX, y + (h - 10) / 2, barW, 10, progress / (double) target, done ? Ui.OK : premium ? Ui.GOLD : Ui.ACCENT);
        Ui.centered(g, Ui.num(progress) + "/" + Ui.num(target), barX + barW / 2, y + (h - 10) / 2 + 1, Ui.TEXT);
        Ui.text(g, pts, right - ptsW, y + (h - 8) / 2, done ? Ui.OK : Ui.GOLD);
        if (deliver && y >= top && y + h <= bottom) {
            String key = Ui.str(q, "key", "");
            hot(g, barX + barW + 6, y + (h - 14) / 2, btnW, 14, "Livrer", Ui.ACCENT, true, mx, my, () -> {
                JsonObject a = new JsonObject();
                a.addProperty("key", key);
                Ui.send("deliver", a);
            });
        }
        if (Ui.in(mx, my, x, y, barX - x, h) && my >= top && my <= bottom) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(Ui.str(q, "name", "")));
            tip.add(Component.literal(Ui.str(q, "typeLabel", "")).withStyle(s -> s.withColor(0x8E9AAB)));
            for (JsonElement c : conds) tip.add(Component.literal("• " + c.getAsString()).withStyle(s -> s.withColor(0xC8D0DC)));
            tip.add(Component.literal("Récompense : " + Ui.num(Ui.lng(q, "points", 0)) + " points de passe").withStyle(s -> s.withColor(0xFFD24A)));
            tooltip.clear();
            tooltip.addAll(tip);
        }
    }

    // ------------------------------------------------------------------ Chasses

    private void hunts(GuiGraphics g, int mx, int my) {
        JsonObject hd = Ui.obj(data, "chasses");
        if (!Ui.bool(hd, "actif", false)) {
            Ui.centered(g, "Les chasses sont désactivées.", px + pw / 2, cy + 30, Ui.MUTED);
            return;
        }
        JsonArray solo = Ui.arr(hd, "solo");
        int n = Math.max(1, solo.size());
        int gap = 6;
        int cw = (pw - 16 - gap * (n - 1)) / n;
        int soloH = Math.min(150, (ch - 8) * 3 / 5);
        for (int i = 0; i < solo.size(); i++) {
            soloCard(g, solo.get(i).getAsJsonObject(), px + 8 + i * (cw + gap), cy + 4, cw, soloH, mx, my, hd);
        }
        JsonObject gl = Ui.obj(hd, "globale");
        int gy = cy + soloH + 10;
        int gh = cy + ch - gy - 4;
        if (!Ui.bool(gl, "actif", false)) return;
        boolean active = "ACTIVE".equals(Ui.str(gl, "state", ""));
        String title = active ? "Chasses globales — fin dans " + Ui.duration(Ui.lng(gl, "fin", 0) - now()) + "  •  +" + Ui.lng(gl, "points", 0) + " points"
                : "Chasses globales — prochaine vague dans " + Ui.duration(Ui.lng(gl, "rechargeFin", 0) - now());
        Ui.text(g, title, px + 10, gy, Ui.PURPLE);
        JsonArray list = Ui.arr(gl, "list");
        if (list.isEmpty()) return;
        int m = list.size();
        int gcw = Math.min(170, (pw - 16 - gap * (m - 1)) / m);
        for (int i = 0; i < m; i++) {
            JsonObject h = list.get(i).getAsJsonObject();
            int x = px + 8 + i * (gcw + gap);
            int y = gy + 12;
            int hh = gh - 12;
            boolean done = Ui.bool(h, "done", false);
            Ui.panel(g, x, y, gcw, hh, done ? 0xFF16261A : 0xFF1E1828, done ? 0xFF2E6B3A : 0xFF5B3F7A);
            Ui.item(g, Ui.item(Ui.str(h, "icon", "")), x + 4, y + 4, 1.5f, false);
            Ui.text(g, Ui.cut(Ui.comp(Ui.str(h, "species", "")).getString(), gcw - 34), x + 30, y + 6, Ui.TEXT);
            Ui.text(g, Ui.cut(requirements(h, hd), gcw - 34), x + 30, y + 16, Ui.MUTED);
            int index = (int) Ui.lng(h, "index", i);
            if (done) Ui.centered(g, "✔ Terminée", x + gcw / 2, y + hh - 14, Ui.OK);
            else if (hh >= 40) hot(g, x + 4, y + hh - 18, gcw - 8, 14, "Remettre", Ui.PURPLE, true, mx, my, () -> askCandidates("globale", index));
            lootTip(h, x, y, gcw, 26, mx, my);
        }
    }

    private String requirements(JsonObject h, JsonObject hd) {
        List<String> r = new ArrayList<>();
        if (!Ui.str(h, "genre", "").isEmpty()) r.add(Ui.str(h, "genre", ""));
        if (!Ui.str(h, "nature", "").isEmpty()) r.add(Ui.str(h, "nature", ""));
        JsonArray ivs = Ui.arr(h, "ivs");
        if (ivs.size() > 0) {
            List<String> s = new ArrayList<>();
            for (JsonElement e : ivs) s.add(e.getAsString());
            r.add("IV " + String.join("/", s) + " ≥ " + Ui.lng(hd, "ivMin", 20));
        }
        JsonArray asp = Ui.arr(h, "aspects");
        for (JsonElement e : asp) r.add(e.getAsString());
        return r.isEmpty() ? "Aucune exigence" : String.join(", ", r);
    }

    private void lootTip(JsonObject h, int x, int y, int w, int hh, int mx, int my) {
        if (!Ui.in(mx, my, x, y, w, hh)) return;
        List<Component> tip = new ArrayList<>();
        tip.add(Ui.comp(Ui.str(h, "species", "")));
        JsonArray loot = Ui.arr(h, "loot");
        if (loot.size() > 0) {
            tip.add(Component.literal("Butin possible :").withStyle(s -> s.withColor(0xFFD24A)));
            for (JsonElement e : loot) tip.add(Component.literal("• ").append(Ui.comp(Ui.str(e.getAsJsonObject(), "name", ""))));
        }
        tooltip.clear();
        tooltip.addAll(tip);
    }

    private void soloCard(GuiGraphics g, JsonObject c, int x, int y, int w, int h, int mx, int my, JsonObject hd) {
        String state = Ui.str(c, "state", "DISABLED");
        String diff = Ui.str(c, "diff", "");
        boolean active = "ACTIVE".equals(state);
        Ui.panel(g, x, y, w, h, active ? 0xFF1A2A20 : Ui.CARD, active ? 0xFF3C8A52 : Ui.BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 14, 0xFF202836);
        Ui.text(g, Ui.str(c, "label", diff), x + 5, y + 4, Ui.TEXT);
        String pts = "+" + Ui.lng(c, "points", 0) + " pts";
        Ui.text(g, pts, x + w - 5 - Ui.font().width(pts), y + 4, Ui.GOLD);
        JsonObject hunt = c.has("hunt") && c.get("hunt").isJsonObject() ? c.getAsJsonObject("hunt") : null;
        int by = y + h - 20;
        switch (state) {
            case "DISABLED" -> Ui.centered(g, "Désactivée", x + w / 2, y + h / 2, Ui.MUTED);
            case "LOCKED" -> Ui.centered(g, "Verrouillée", x + w / 2, y + h / 2, Ui.MUTED);
            case "COOLDOWN" -> {
                Ui.centered(g, "Recharge", x + w / 2, y + h / 2 - 10, Ui.MUTED);
                Ui.centered(g, Ui.duration(Ui.lng(c, "rechargeFin", 0) - now()), x + w / 2, y + h / 2 + 2, Ui.TEXT);
            }
            default -> {
                if (hunt != null) {
                    float scale = Math.min(2.5f, Math.max(1.5f, (huntIconSize(w)) / 16f));
                    int size = (int) (16 * scale);
                    Ui.item(g, Ui.item(Ui.str(hunt, "icon", "")), x + w / 2 - size / 2, y + 18, scale, false);
                    int ty = y + 20 + size;
                    Ui.centered(g, Ui.cut(Ui.comp(Ui.str(hunt, "species", "")).getString(), w - 6), x + w / 2, ty, Ui.TEXT);
                    String req = requirements(hunt, hd);
                    List<FormattedCharSequence> lines = Ui.font().split(Component.literal(req), w - 8);
                    for (int i = 0; i < Math.min(2, lines.size()); i++) {
                        g.drawString(Ui.font(), lines.get(i), x + w / 2 - Ui.font().width(lines.get(i)) / 2, ty + 11 + i * 9, Ui.MUTED, false);
                    }
                    if (active) Ui.centered(g, "Temps : " + Ui.duration(Ui.lng(hunt, "fin", 0) - now()), x + w / 2, by - 11, Ui.OK);
                    else Ui.centered(g, "Durée : " + Ui.duration(Ui.lng(c, "duree", 0) * 1000L), x + w / 2, by - 11, Ui.MUTED);
                    lootTip(hunt, x, y + 16, w, size + 14, mx, my);
                } else {
                    Ui.centered(g, "Aucun Pokémon", x + w / 2, y + h / 2, Ui.MUTED);
                }
                if (active) {
                    int bw = (w - 12) / 2;
                    hot(g, x + 4, by, bw + 8, 16, "Remettre", Ui.OK, true, mx, my, () -> askCandidates(diff, 0));
                    hot(g, x + 16 + bw, by, w - 20 - bw, 16, "Abandon", Ui.ERROR, true, mx, my, () -> {
                        JsonObject a = new JsonObject();
                        a.addProperty("diff", diff);
                        Ui.send("hunt_abandon", a);
                    });
                } else if ("READY".equals(state) && hunt != null) {
                    hot(g, x + 4, by, w - 8, 16, "Lancer la chasse", Ui.ACCENT, true, mx, my, () -> {
                        JsonObject a = new JsonObject();
                        a.addProperty("diff", diff);
                        Ui.send("hunt_start", a);
                    });
                }
            }
        }
    }

    private static int huntIconSize(int w) {
        return Math.min(40, w / 3);
    }

    private String candDiff = "";
    private int candIndex;

    private JsonObject candRequest() {
        JsonObject a = new JsonObject();
        a.addProperty("diff", candDiff);
        a.addProperty("index", candIndex);
        return a;
    }

    private void askCandidates(String diff, int index) {
        candDiff = diff;
        candIndex = index;
        candidates = new JsonObject();
        candidates.add("list", new JsonArray());
        candidates.addProperty("loading", true);
        Ui.send("hunt_candidates", candRequest());
    }

    private void candidatesOverlay(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(px, py, px + pw, py + ph, 0xA0000000);
        int w = Math.min(pw - 40, 420);
        int h = Math.min(ph - 40, 260);
        int x = px + (pw - w) / 2;
        int y = py + (ph - h) / 2;
        Ui.panel(g, x, y, w, h, Ui.PANEL2, Ui.ACCENT);
        Ui.text(g, "Choisissez le Pokémon à remettre", x + 8, y + 6, Ui.GOLD);
        List<Hot> local = new ArrayList<>();
        int closeX = x + w - 58;
        boolean hovClose = Ui.in(mx, my, closeX, y + 4, 52, 14);
        Ui.panel(g, closeX, y + 4, 52, 14, hovClose ? 0xFF3A2020 : 0xFF2A1A1A, Ui.ERROR);
        Ui.centered(g, "Fermer", closeX + 26, y + 7, Ui.ERROR);
        local.add(new Hot(closeX, y + 4, 52, 14, () -> candidates = null, null));
        JsonArray list = Ui.arr(candidates, "list");
        int top = y + 24;
        int bottom = y + h - 6;
        if (Ui.bool(candidates, "loading", false) && list.isEmpty()) Ui.centered(g, "Chargement…", x + w / 2, top + 20, Ui.MUTED);
        else if (list.isEmpty()) {
            Ui.centered(g, "Aucun Pokémon de cette espèce dans votre équipe ou votre PC.", x + w / 2, top + 20, Ui.MUTED);
            Ui.centered(g, "Capturez-le puis revenez ici.", x + w / 2, top + 32, Ui.MUTED);
        }
        int rowH = 30;
        candScroll = Math.max(0, Math.min(Math.max(0, list.size() * rowH - (bottom - top)), candScroll));
        g.enableScissor(x + 2, top, x + w - 2, bottom);
        List<Component> tip = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            JsonObject c = list.get(i).getAsJsonObject();
            int ry = top + i * rowH - (int) candScroll;
            if (ry + rowH < top || ry > bottom) continue;
            boolean ok = Ui.bool(c, "ok", false);
            Ui.panel(g, x + 6, ry, w - 12, rowH - 3, ok ? 0xFF16261A : 0xFF261616, ok ? 0xFF2E6B3A : 0xFF6B2E2E);
            Ui.item(g, Ui.item(Ui.str(c, "icon", "")), x + 10, ry + 5, 1f, false);
            String name = Ui.comp(Ui.str(c, "name", "")).getString() + " Nv. " + Ui.lng(c, "level", 0) + (Ui.bool(c, "shiny", false) ? " ★" : "");
            Ui.text(g, Ui.cut(name, w - 140), x + 30, ry + 4, Ui.TEXT);
            StringBuilder iv = new StringBuilder();
            for (JsonElement e : Ui.arr(c, "ivs")) iv.append(iv.length() == 0 ? "" : "/").append(e.getAsInt());
            String info = (Ui.bool(c, "party", false) ? "Équipe" : "PC") + " • " + Ui.str(c, "genre", "") + " • " + Ui.str(c, "nature", "") + " • IV " + iv;
            Ui.text(g, Ui.cut(info, w - 140), x + 30, ry + 15, Ui.MUTED);
            if (ok) {
                boolean hov = Ui.in(mx, my, x + w - 92, ry + 6, 80, 14) && my >= top && my <= bottom;
                Ui.panel(g, x + w - 92, ry + 6, 80, 14, hov ? 0xFF2C5838 : 0xFF223A28, Ui.OK);
                Ui.centered(g, "Remettre", x + w - 52, ry + 9, Ui.OK);
                String uuid = Ui.str(c, "uuid", "");
                local.add(new Hot(x + w - 92, ry + 6, 80, 14, () -> {
                    JsonObject a = candRequest();
                    a.addProperty("uuid", uuid);
                    Ui.send("hunt_turnin", a);
                }, null));
            } else {
                Ui.text(g, "Ne convient pas", x + w - 92, ry + 9, Ui.ERROR);
                if (Ui.in(mx, my, x + 6, ry, w - 12, rowH - 3) && my >= top && my <= bottom) {
                    for (JsonElement e : Ui.arr(c, "problems")) tip.add(Component.literal("• " + e.getAsString()));
                }
            }
        }
        g.disableScissor();
        g.pose().popPose();
        hots.clear();
        hots.addAll(local);
        if (!tip.isEmpty()) {
            tooltip.clear();
            tooltip.addAll(tip);
        }
    }

    // ------------------------------------------------------------------ Entrees

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Hot h : new ArrayList<>(hots)) {
                if (Ui.in(mx, my, h.x, h.y, h.w, h.h)) {
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    h.action.run();
                    return true;
                }
            }
            if (candidates != null) return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (candidates != null) {
            candScroll -= sy * 20;
            return true;
        }
        switch (tab) {
            case 0 -> firstTier = Math.max(1, firstTier - (int) Math.signum(sy));
            case 1 -> questScroll -= sy * 20;
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (candidates != null && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            candidates = null;
            return true;
        }
        if (tab == 0 && key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            firstTier = Math.max(1, firstTier - 1);
            return true;
        }
        if (tab == 0 && key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            firstTier++;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
