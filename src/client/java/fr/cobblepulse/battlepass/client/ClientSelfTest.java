package fr.cobblepulse.battlepass.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.config.Defaults;
import fr.cobblepulse.battlepass.core.ItemSerial;
import fr.cobblepulse.battlepass.core.Json;
import fr.cobblepulse.battlepass.data.QuestDef;
import fr.cobblepulse.battlepass.data.QuestsFile;
import fr.cobblepulse.battlepass.data.Reward;
import fr.cobblepulse.battlepass.data.RewardsFile;
import fr.cobblepulse.battlepass.quest.QuestType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Auto-test visuel (CI) : ouvre les ecrans avec des donnees de demonstration sur l'ecran titre et les capture. */
final class ClientSelfTest {
    private static int ticks;
    private static int start = -1;
    private static PassScreen pass;
    private static AdminScreen admin;

    private ClientSelfTest() {}

    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::tick);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> BattlePass.LOG.info("[BattlePass][clienttest] {}", c.getString()));
    }

    private static String comp(String s) {
        return Component.Serializer.toJson(Component.literal(s), Ui.regs());
    }

    private static JsonObject rewardView(Reward r) {
        JsonObject cfg = Json.CFG.toJsonTree(r).getAsJsonObject();
        JsonObject o = new JsonObject();
        o.addProperty("type", r.type());
        o.addProperty("name", comp(ClientData.rewardLabel(cfg)));
        o.addProperty("icon", ItemSerial.encode(ClientData.rewardIcon(cfg), Ui.regs()));
        o.add("lore", new JsonArray());
        return o;
    }

    static JsonObject demoPass() {
        long now = System.currentTimeMillis();
        JsonObject root = new JsonObject();
        root.addProperty("rev", 1);
        root.addProperty("admin", true);
        JsonObject s = new JsonObject();
        s.addProperty("nom", "Saison 1");
        s.addProperty("numero", 1);
        s.addProperty("statut", "ouvert");
        s.addProperty("statutTexte", "Ouverte");
        s.addProperty("debut", now - 18L * 86_400_000L);
        s.addProperty("fin", now + 12L * 86_400_000L + 3_600_000L);
        s.addProperty("now", now);
        s.addProperty("paliers", 50);
        s.addProperty("premiumActif", true);
        s.addProperty("achatMode", "coins_jobs");
        s.addProperty("achatPrix", "5000 coins Jobs+");
        s.addProperty("boost", 2.0);
        s.addProperty("boostFin", now + 45 * 60_000L);
        root.add("saison", s);
        JsonObject j = new JsonObject();
        j.addProperty("nom", "Testeur");
        j.addProperty("points", 12_450);
        j.addProperty("palier", 12);
        j.addProperty("premium", false);
        j.addProperty("base", 12_000);
        j.addProperty("next", 1000);
        j.addProperty("serie", 4);
        j.addProperty("jours", 15);
        j.addProperty("aReclamer", 3);
        JsonArray rec = new JsonArray();
        for (int t = 1; t <= 9; t++) rec.add("g:" + t);
        j.add("reclames", rec);
        root.add("joueur", j);
        JsonObject login = new JsonObject();
        login.addProperty("actif", true);
        login.addProperty("points", 100);
        login.addProperty("aujourdhui", true);
        root.add("connexion", login);
        RewardsFile rf = Defaults.rewards();
        JsonArray tiers = new JsonArray();
        for (int t = 1; t <= 50; t++) {
            JsonObject to = new JsonObject();
            to.addProperty("t", t);
            to.addProperty("cost", 1000);
            JsonArray g = new JsonArray();
            JsonArray p = new JsonArray();
            RewardsFile.Tier tr = rf.tier(t);
            if (tr != null) {
                for (Reward r : tr.gratuit) g.add(rewardView(r));
                for (Reward r : tr.premium) p.add(rewardView(r));
            }
            to.add("g", g);
            to.add("p", p);
            tiers.add(to);
        }
        root.add("tiers", tiers);
        QuestsFile qf = Defaults.quests();
        JsonArray cats = new JsonArray();
        String[] cn = {"quotidiennes", "hebdomadaires", "saison"};
        String[] cl = {"Quotidiennes", "Hebdomadaires", "Saison"};
        long[] reset = {now + 5 * 3_600_000L, now + 3 * 86_400_000L, now + 12 * 86_400_000L};
        for (int c = 0; c < 3; c++) {
            JsonObject co = new JsonObject();
            co.addProperty("cat", cn[c]);
            co.addProperty("label", cl[c]);
            co.addProperty("reset", reset[c]);
            JsonArray list = new JsonArray();
            List<QuestDef> pool = qf.pool(cn[c]);
            for (int i = 0; i < Math.min(pool.size(), c == 2 ? 9 : 5); i++) {
                QuestDef q = pool.get(i);
                QuestType t = QuestType.of(q.type);
                JsonObject o = new JsonObject();
                o.addProperty("key", cn[c] + i);
                o.addProperty("name", q.nom);
                JsonArray conds = new JsonArray();
                if (!q.filtres.types.isEmpty()) conds.add("Types : " + String.join(", ", q.filtres.types));
                o.add("conds", conds);
                o.addProperty("typeLabel", t == null ? q.type : t.label);
                o.addProperty("progress", i == 0 ? q.objectif : Math.min(q.objectif, i * q.objectif / 4));
                o.addProperty("target", q.objectif);
                o.addProperty("points", q.points);
                o.addProperty("done", i == 0);
                o.addProperty("premium", q.premium);
                o.addProperty("locked", q.premium);
                o.addProperty("deliver", "livraison".equals(q.type));
                o.addProperty("icon", ItemSerial.encode(Ui.itemById(t == null ? "minecraft:paper" : t.icon), Ui.regs()));
                list.add(o);
            }
            co.add("list", list);
            cats.add(co);
        }
        root.add("quetes", cats);
        JsonObject h = new JsonObject();
        h.addProperty("actif", true);
        h.addProperty("ivMin", 20);
        JsonArray solo = new JsonArray();
        String[][] demo = {{"facile", "Facile", "READY", "pidgey", "", ""}, {"normal", "Normale", "ACTIVE", "eevee", "Mâle", ""},
                {"moyen", "Moyenne", "COOLDOWN", "", "", ""}, {"difficile", "Difficile", "READY", "dragonite", "Femelle", "Rigide"}};
        int[] pts = {100, 200, 350, 600};
        for (int i = 0; i < 4; i++) {
            JsonObject o = new JsonObject();
            o.addProperty("diff", demo[i][0]);
            o.addProperty("label", demo[i][1]);
            o.addProperty("state", demo[i][2]);
            o.addProperty("points", pts[i]);
            o.addProperty("duree", 1800);
            o.addProperty("rechargeFin", now + 420_000L);
            if (!demo[i][3].isEmpty()) o.add("hunt", huntView(demo[i][3], demo[i][4], demo[i][5], i == 3, now));
            solo.add(o);
        }
        h.add("solo", solo);
        JsonObject gl = new JsonObject();
        gl.addProperty("actif", true);
        gl.addProperty("points", 300);
        gl.addProperty("state", "ACTIVE");
        gl.addProperty("fin", now + 2_400_000L);
        JsonArray gll = new JsonArray();
        String[] gs = {"growlithe", "magikarp", "abra"};
        for (int i = 0; i < 3; i++) {
            JsonObject o = huntView(gs[i], "", "", false, now);
            o.addProperty("index", i);
            o.addProperty("done", i == 1);
            gll.add(o);
        }
        gl.add("list", gll);
        h.add("globale", gl);
        root.add("chasses", h);
        return root;
    }

    private static JsonObject huntView(String species, String gender, String nature, boolean ivs, long now) {
        JsonObject o = new JsonObject();
        o.addProperty("species", comp(ClientData.speciesName(species)));
        o.addProperty("icon", ItemSerial.encode(ClientData.pokemonIcon(species, "", false), Ui.regs()));
        o.addProperty("genre", gender);
        o.addProperty("nature", nature);
        JsonArray iv = new JsonArray();
        if (ivs) {
            iv.add("Attaque");
            iv.add("Vitesse");
        }
        o.add("ivs", iv);
        o.add("aspects", new JsonArray());
        o.addProperty("debut", now - 300_000L);
        o.addProperty("fin", now + 1_500_000L);
        JsonArray loot = new JsonArray();
        loot.add(rewardView(Defaults.item("cobblemon:ultra_ball", 6)));
        o.add("loot", loot);
        return o;
    }

    static JsonObject demoAdmin() {
        JsonObject o = new JsonObject();
        o.addProperty("rev", 1);
        o.add("saison", Json.CFG.toJsonTree(Defaults.pass()));
        o.add("recompenses", Json.CFG.toJsonTree(Defaults.rewards()));
        o.add("quetes", Json.CFG.toJsonTree(Defaults.quests()));
        o.add("chasses", Json.CFG.toJsonTree(Defaults.hunts()));
        JsonArray types = new JsonArray();
        for (QuestType t : QuestType.values()) {
            JsonObject x = new JsonObject();
            x.addProperty("id", t.id);
            x.addProperty("label", t.label);
            x.addProperty("phrase", t.phrase);
            x.addProperty("icon", t.icon);
            JsonArray f = new JsonArray();
            for (String s : t.filters) f.add(s);
            x.add("filters", f);
            types.add(x);
        }
        o.add("types", types);
        o.addProperty("statut", "ouvert");
        o.addProperty("statutTexte", "Ouverte");
        o.addProperty("fin", System.currentTimeMillis() + 12L * 86_400_000L);
        o.addProperty("boost", 1.0);
        JsonArray names = new JsonArray();
        names.add("Testeur");
        o.add("joueurs", names);
        JsonObject perms = new JsonObject();
        for (String k : List.of("edit", "toggle", "saison", "reset", "premium", "boost", "points")) perms.addProperty(k, true);
        o.add("perms", perms);
        return o;
    }

    private static void tick(Minecraft mc) {
        ticks++;
        if (start < 0) {
            if (mc.getOverlay() == null && ((mc.screen instanceof TitleScreen && ticks > 60) || (mc.screen != null && ticks > 400))) {
                start = ticks;
                BattlePass.LOG.info("[BattlePass][clienttest] debut sur {}", mc.screen.getClass().getSimpleName());
            } else if (ticks > 6000) {
                BattlePass.LOG.error("[BattlePass][clienttest] ecran titre jamais atteint");
                mc.stop();
            }
            return;
        }
        int t = ticks - start;
        try {
            switch (t) {
                case 20 -> {
                    pass = new PassScreen(demoPass());
                    mc.setScreen(pass);
                }
                case 50 -> shot(mc, "bp_pass_recompenses");
                case 52 -> {
                    pass = new PassScreen(demoPass());
                    setTab(pass, 1);
                    mc.setScreen(pass);
                }
                case 80 -> shot(mc, "bp_pass_quetes");
                case 82 -> {
                    pass = new PassScreen(demoPass());
                    setTab(pass, 2);
                    mc.setScreen(pass);
                }
                case 110 -> shot(mc, "bp_pass_chasses");
                case 112 -> {
                    JsonObject c = new JsonObject();
                    JsonArray list = new JsonArray();
                    for (int i = 0; i < 3; i++) {
                        JsonObject x = new JsonObject();
                        x.addProperty("uuid", java.util.UUID.randomUUID().toString());
                        x.addProperty("name", comp("Évoli"));
                        x.addProperty("icon", ItemSerial.encode(ClientData.pokemonIcon("eevee", "", i == 2), Ui.regs()));
                        x.addProperty("level", 12 + i);
                        x.addProperty("party", i < 2);
                        x.addProperty("ok", i == 0);
                        x.addProperty("shiny", i == 2);
                        x.addProperty("nature", "Rigide");
                        x.addProperty("genre", i == 1 ? "Femelle" : "Mâle");
                        JsonArray ivs = new JsonArray();
                        for (int k = 0; k < 6; k++) ivs.add(10 + k * 3);
                        x.add("ivs", ivs);
                        JsonArray probs = new JsonArray();
                        if (i == 1) probs.add("Genre attendu : Mâle");
                        if (i == 2) probs.add("Doit être capturé après le début de la chasse");
                        x.add("problems", probs);
                        list.add(x);
                    }
                    c.add("list", list);
                    pass.showCandidates(c);
                }
                case 135 -> shot(mc, "bp_pass_remise");
                case 137 -> {
                    admin = new AdminScreen(demoAdmin());
                    mc.setScreen(admin);
                }
                case 165 -> shot(mc, "bp_admin_saison");
                case 167 -> openAdminTab(mc, 1);
                case 195 -> shot(mc, "bp_admin_recompenses");
                case 197 -> openAdminTab(mc, 2);
                case 225 -> shot(mc, "bp_admin_quetes");
                case 227 -> openAdminTab(mc, 3);
                case 255 -> shot(mc, "bp_admin_chasses");
                case 257 -> openAdminTab(mc, 4);
                case 285 -> shot(mc, "bp_admin_joueurs");
                case 287 -> {
                    JsonObject r = Json.CFG.toJsonTree(Defaults.rewards().tier(50).premium.get(0)).getAsJsonObject();
                    mc.setScreen(new RewardEditScreen(admin, r, false, x -> {}));
                }
                case 315 -> shot(mc, "bp_admin_recompense_pokemon");
                case 317 -> {
                    JsonObject q = Json.CFG.toJsonTree(Defaults.quests().quotidiennes.get(1)).getAsJsonObject();
                    JsonArray types = demoAdmin().getAsJsonArray("types");
                    mc.setScreen(new QuestEditScreen(admin, q, types, x -> {}));
                }
                case 345 -> shot(mc, "bp_admin_quete");
                case 370 -> {
                    BattlePass.LOG.info("[BattlePass][clienttest] CLIENT TEST DONE");
                    mc.stop();
                }
                default -> {
                }
            }
        } catch (Throwable e) {
            BattlePass.LOG.error("[BattlePass][clienttest] erreur", e);
            mc.stop();
        }
    }

    private static void setTab(PassScreen s, int tab) {
        try {
            var f = PassScreen.class.getDeclaredField("tab");
            f.setAccessible(true);
            f.setInt(s, tab);
        } catch (Exception ignored) {
        }
    }

    private static void openAdminTab(Minecraft mc, int tab) {
        try {
            var f = AdminScreen.class.getDeclaredField("lastTab");
            f.setAccessible(true);
            f.setInt(null, tab);
        } catch (Exception ignored) {
        }
        admin = new AdminScreen(demoAdmin());
        mc.setScreen(admin);
    }
}
