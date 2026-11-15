package fr.cobblepulse.battlepass.net;

import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.core.ItemSerial;
import fr.cobblepulse.battlepass.core.Json;
import fr.cobblepulse.battlepass.core.Msg;
import fr.cobblepulse.battlepass.core.Perms;
import fr.cobblepulse.battlepass.core.TimeFmt;
import fr.cobblepulse.battlepass.data.HuntInstance;
import fr.cobblepulse.battlepass.data.HuntsFile;
import fr.cobblepulse.battlepass.data.PlayerData;
import fr.cobblepulse.battlepass.data.Reward;
import fr.cobblepulse.battlepass.data.RewardsFile;
import fr.cobblepulse.battlepass.hunt.Hunts;
import fr.cobblepulse.battlepass.quest.QuestType;
import fr.cobblepulse.battlepass.quest.Quests;
import fr.cobblepulse.battlepass.reward.Names;
import fr.cobblepulse.battlepass.reward.RewardService;
import fr.cobblepulse.battlepass.reward.SpeciesLookup;
import fr.cobblepulse.battlepass.season.Season;
import fr.cobblepulse.battlepass.storage.PlayerStore;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Construction des donnees JSON envoyees a l'ecran du passe et a l'editeur. */
public final class Views {
    private Views() {}

    static String comp(Component c, HolderLookup.Provider regs) {
        return Component.Serializer.toJson(c, regs);
    }

    static String item(ItemStack s, HolderLookup.Provider regs) {
        return ItemSerial.encode(s, regs);
    }

    private static JsonObject reward(Reward r, HolderLookup.Provider regs) {
        JsonObject o = new JsonObject();
        o.addProperty("type", r.type());
        o.addProperty("name", comp(RewardService.name(r), regs));
        o.addProperty("icon", item(RewardService.icon(r), regs));
        JsonArray lore = new JsonArray();
        for (Component c : RewardService.lore(r)) lore.add(comp(c, regs));
        o.add("lore", lore);
        return o;
    }

    private static JsonArray rewards(List<Reward> list, HolderLookup.Provider regs) {
        JsonArray a = new JsonArray();
        for (Reward r : list) a.add(reward(r, regs));
        return a;
    }

    /** Ecran du passe. {@code full} : inclut les paliers et leurs recompenses (envoyes a l'ouverture et apres modification). */
    public static JsonObject pass(ServerPlayer p, boolean full) {
        HolderLookup.Provider regs = p.registryAccess();
        PlayerData d = PlayerStore.get(p);
        boolean premium = Season.isPremium(d, p);
        long now = TimeFmt.now();
        JsonObject root = new JsonObject();
        root.addProperty("rev", Cfg.revision);
        root.addProperty("admin", Perms.anyAdmin(p));

        JsonObject s = new JsonObject();
        s.addProperty("nom", Cfg.pass.nom);
        s.addProperty("numero", Cfg.pass.saison);
        String status = Season.status();
        s.addProperty("statut", status);
        s.addProperty("statutTexte", Msg.raw("status." + status));
        s.addProperty("debut", Cfg.pass.debut);
        s.addProperty("fin", Season.end());
        s.addProperty("now", now);
        s.addProperty("paliers", Cfg.pass.paliers);
        s.addProperty("premiumActif", Cfg.pass.premium.actif);
        s.addProperty("achatMode", Cfg.pass.premium.achatEnJeu);
        s.addProperty("achatPrix", Season.priceLabel());
        s.addProperty("boost", Season.boost());
        s.addProperty("boostFin", Season.state().boostFin);
        root.add("saison", s);

        JsonObject j = new JsonObject();
        int tier = Season.tier(d);
        j.addProperty("nom", p.getGameProfile().getName());
        j.addProperty("points", d.points);
        j.addProperty("palier", tier);
        j.addProperty("premium", premium);
        j.addProperty("base", Cfg.pass.totalFor(tier));
        j.addProperty("next", tier >= Cfg.pass.paliers ? 0 : Cfg.pass.costOf(tier + 1));
        j.addProperty("serie", d.serie);
        j.addProperty("jours", d.joursConnexion);
        j.addProperty("aReclamer", Season.claimable(d, p));
        j.addProperty("chassesTerminees", d.chassesTerminees);
        j.addProperty("quetesTerminees", d.quetesTerminees);
        JsonArray claimed = new JsonArray();
        for (String k : d.reclames) claimed.add(k);
        j.add("reclames", claimed);
        root.add("joueur", j);

        JsonObject login = new JsonObject();
        login.addProperty("actif", Cfg.pass.connexion.actif);
        login.addProperty("points", Cfg.pass.connexion.points);
        login.addProperty("aujourdhui", Season.dayKey(now).equals(d.dernierJour));
        JsonObject bonus = new JsonObject();
        for (Map.Entry<String, Integer> e : Cfg.pass.connexion.bonusSerie.entrySet()) bonus.addProperty(e.getKey(), e.getValue());
        login.add("bonus", bonus);
        root.add("connexion", login);

        if (full) {
            JsonArray tiers = new JsonArray();
            for (int t = 1; t <= Cfg.pass.paliers; t++) {
                JsonObject to = new JsonObject();
                to.addProperty("t", t);
                to.addProperty("cost", Cfg.pass.costOf(t));
                RewardsFile.Tier tr = Cfg.rewards.tier(t);
                to.add("g", rewards(tr == null ? List.of() : tr.gratuit, regs));
                to.add("p", rewards(tr == null ? List.of() : tr.premium, regs));
                tiers.add(to);
            }
            root.add("tiers", tiers);
        }

        JsonArray cats = new JsonArray();
        List<Quests.Active> active = Quests.active(d);
        for (String cat : Quests.CATEGORIES) {
            JsonObject co = new JsonObject();
            co.addProperty("cat", cat);
            co.addProperty("label", Names.category(cat));
            co.addProperty("reset", Quests.nextReset(cat));
            JsonArray list = new JsonArray();
            for (Quests.Active a : active) {
                if (!a.category().equals(cat)) continue;
                QuestType qt = a.type();
                JsonObject q = new JsonObject();
                q.addProperty("key", a.key());
                q.addProperty("id", a.def().id);
                q.addProperty("name", Quests.title(a.def()));
                JsonArray conds = new JsonArray();
                for (String c : Quests.conditions(a.def())) conds.add(c);
                q.add("conds", conds);
                q.addProperty("type", a.def().type);
                q.addProperty("typeLabel", qt == null ? a.def().type : qt.label);
                int scale = qt == null ? 1 : qt.scale;
                q.addProperty("progress", Quests.progress(d, a) / scale);
                q.addProperty("target", a.def().objectif);
                q.addProperty("points", a.def().points);
                q.addProperty("done", d.terminees.contains(a.key()));
                q.addProperty("premium", a.def().premium);
                q.addProperty("locked", a.def().premium && !premium);
                q.addProperty("deliver", "livraison".equals(a.def().type));
                q.addProperty("icon", item(Quests.icon(a.def()), regs));
                list.add(q);
            }
            co.add("list", list);
            cats.add(co);
        }
        root.add("quetes", cats);
        root.add("chasses", hunts(p, d, regs, now));
        return root;
    }

    private static JsonObject hunt(HuntInstance h, HolderLookup.Provider regs, String diff) {
        JsonObject o = new JsonObject();
        Species sp = SpeciesLookup.resolve(h.entry.espece);
        o.addProperty("species", comp(sp == null ? Component.literal(h.entry.espece) : sp.getTranslatedName(), regs));
        ItemStack icon;
        try {
            Set<String> aspects = new HashSet<>(h.entry.aspects);
            icon = sp == null ? new ItemStack(Items.BARRIER) : PokemonItem.from(sp, aspects, 1, new org.joml.Vector4f(1f, 1f, 1f, 1f));
        } catch (Throwable t) {
            icon = new ItemStack(Items.BARRIER);
        }
        o.addProperty("icon", item(icon, regs));
        o.addProperty("genre", h.genre.isEmpty() ? "" : Names.gender(h.genre));
        o.addProperty("nature", h.nature.isEmpty() ? "" : Names.nature(h.nature));
        JsonArray ivs = new JsonArray();
        for (String st : h.ivs) ivs.add(Names.stat(st));
        o.add("ivs", ivs);
        o.addProperty("forme", h.entry.forme);
        JsonArray asp = new JsonArray();
        for (String a : h.entry.aspects) asp.add(a);
        o.add("aspects", asp);
        o.addProperty("debut", h.debut);
        o.addProperty("fin", h.fin);
        o.add("loot", rewards(Cfg.hunts.loot(diff), regs));
        return o;
    }

    private static JsonObject hunts(ServerPlayer p, PlayerData d, HolderLookup.Provider regs, long now) {
        HuntsFile hf = Cfg.hunts;
        JsonObject root = new JsonObject();
        root.addProperty("actif", hf.actif);
        root.addProperty("ivMin", hf.ivMinimum);
        root.addProperty("prendre", hf.prendrePokemon);
        JsonArray solo = new JsonArray();
        for (String diff : HuntsFile.SOLO) {
            HuntsFile.Solo cfg = hf.solo(diff);
            JsonObject o = new JsonObject();
            o.addProperty("diff", diff);
            o.addProperty("label", Names.difficulty(diff));
            Hunts.State st = Hunts.state(p, d, diff);
            o.addProperty("state", st.name());
            o.addProperty("points", cfg.points);
            o.addProperty("duree", cfg.duree);
            o.addProperty("recharge", cfg.recharge);
            o.addProperty("rechargeFin", d.recharges.getOrDefault(diff, 0L));
            HuntInstance h = st == Hunts.State.ACTIVE ? d.chasses.get(diff) : st == Hunts.State.READY ? Hunts.preview(d, diff) : null;
            if (h != null) o.add("hunt", hunt(h, regs, diff));
            solo.add(o);
        }
        root.add("solo", solo);
        JsonObject g = new JsonObject();
        Hunts.Global gs = Hunts.global();
        g.addProperty("actif", hf.globale.actif && Hunts.allowed(p, "globale"));
        g.addProperty("points", hf.globale.points);
        g.addProperty("fin", gs.fin);
        g.addProperty("rechargeFin", gs.rechargeFin);
        boolean active = !gs.chasses.isEmpty() && now < gs.fin;
        g.addProperty("state", active ? "ACTIVE" : "COOLDOWN");
        JsonArray list = new JsonArray();
        if (active) {
            for (int i = 0; i < gs.chasses.size(); i++) {
                JsonObject h = hunt(gs.chasses.get(i), regs, "globale");
                h.addProperty("index", i);
                h.addProperty("done", Hunts.globalDone(d, i));
                list.add(h);
            }
        }
        g.add("list", list);
        root.add("globale", g);
        return root;
    }

    /** Pokemon proposes pour une remise de chasse. */
    public static JsonObject candidates(ServerPlayer p, String diff, int index) {
        HolderLookup.Provider regs = p.registryAccess();
        PlayerData d = PlayerStore.get(p);
        JsonObject root = new JsonObject();
        root.addProperty("diff", diff);
        root.addProperty("index", index);
        JsonArray list = new JsonArray();
        HuntInstance h = Hunts.hunt(d, diff, index);
        if (h != null) {
            for (Hunts.Candidate c : Hunts.candidates(p, h)) {
                Pokemon pk = c.pokemon();
                JsonObject o = new JsonObject();
                o.addProperty("uuid", pk.getUuid().toString());
                o.addProperty("name", comp(pk.getDisplayName(false), regs));
                o.addProperty("icon", item(PokemonItem.from(pk), regs));
                o.addProperty("level", pk.getLevel());
                o.addProperty("party", c.party());
                o.addProperty("ok", c.problems().isEmpty());
                o.addProperty("shiny", pk.getShiny());
                o.addProperty("nature", Names.nature(pk.getNature().getName().getPath()));
                o.addProperty("genre", Names.gender(pk.getGender().name()));
                JsonArray ivs = new JsonArray();
                for (String st : Hunts.IV_STATS) ivs.add(Hunts.statIv(pk, st));
                o.add("ivs", ivs);
                JsonArray probs = new JsonArray();
                for (String s : c.problems()) probs.add(s);
                o.add("problems", probs);
                list.add(o);
            }
        }
        root.add("list", list);
        return root;
    }

    // ------------------------------------------------------------------ Editeur

    public static JsonObject admin(ServerPlayer p) {
        JsonObject root = new JsonObject();
        root.addProperty("rev", Cfg.revision);
        root.add("saison", JsonParser.parseString(Json.CFG_COMPACT.toJson(Cfg.pass)));
        root.add("recompenses", JsonParser.parseString(Json.CFG_COMPACT.toJson(Cfg.rewards)));
        root.add("quetes", JsonParser.parseString(Json.CFG_COMPACT.toJson(Cfg.quests)));
        root.add("chasses", JsonParser.parseString(Json.CFG_COMPACT.toJson(Cfg.hunts)));
        JsonArray types = new JsonArray();
        for (QuestType t : QuestType.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", t.id);
            o.addProperty("label", t.label);
            o.addProperty("phrase", t.phrase);
            o.addProperty("icon", t.icon);
            JsonArray f = new JsonArray();
            for (String x : t.filters) f.add(x);
            o.add("filters", f);
            types.add(o);
        }
        root.add("types", types);
        root.addProperty("statut", Season.status());
        root.addProperty("statutTexte", Msg.raw("status." + Season.status()));
        root.addProperty("fin", Season.end());
        root.addProperty("now", TimeFmt.now());
        root.addProperty("boost", Season.boost());
        root.addProperty("boostFin", Season.state().boostFin);
        JsonArray names = new JsonArray();
        for (String n : PlayerStore.names()) names.add(n);
        root.add("joueurs", names);
        JsonObject perms = new JsonObject();
        perms.addProperty("edit", Perms.admin(p, Perms.ADMIN_EDIT));
        perms.addProperty("toggle", Perms.admin(p, Perms.ADMIN_TOGGLE));
        perms.addProperty("saison", Perms.admin(p, Perms.ADMIN_SEASON));
        perms.addProperty("reset", Perms.admin(p, Perms.ADMIN_RESET));
        perms.addProperty("premium", Perms.admin(p, Perms.ADMIN_PREMIUM));
        perms.addProperty("boost", Perms.admin(p, Perms.ADMIN_BOOST));
        perms.addProperty("points", Perms.staff(p, Perms.STAFF_POINTS));
        root.add("perms", perms);
        return root;
    }

    /** Fiche d'un joueur (onglet Joueurs de l'editeur). */
    public static JsonObject player(PlayerData d) {
        JsonObject o = new JsonObject();
        o.addProperty("nom", d.name);
        o.addProperty("uuid", d.uuid);
        o.addProperty("points", d.points);
        o.addProperty("palier", Season.tier(d));
        o.addProperty("premium", d.premium);
        o.addProperty("premiumSource", d.premiumSource);
        o.addProperty("reclames", d.reclames.size());
        o.addProperty("quetes", d.quetesTerminees);
        o.addProperty("chasses", d.chassesTerminees);
        o.addProperty("serie", d.serie);
        o.addProperty("jours", d.joursConnexion);
        o.addProperty("pointsQuetes", d.pointsQuetes);
        o.addProperty("pointsConnexion", d.pointsConnexion);
        o.addProperty("pointsChasses", d.pointsChasses);
        o.addProperty("pointsStaff", d.pointsStaff);
        return o;
    }

    public static JsonElement json(String s) {
        try {
            return JsonParser.parseString(s == null || s.isBlank() ? "{}" : s);
        } catch (Exception e) {
            return new JsonObject();
        }
    }
}
