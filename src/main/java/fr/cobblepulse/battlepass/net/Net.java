package fr.cobblepulse.battlepass.net;

import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.config.PassConfig;
import fr.cobblepulse.battlepass.core.Json;
import fr.cobblepulse.battlepass.core.Msg;
import fr.cobblepulse.battlepass.core.Perms;
import fr.cobblepulse.battlepass.core.Players;
import fr.cobblepulse.battlepass.core.Scheduler;
import fr.cobblepulse.battlepass.data.HuntEntry;
import fr.cobblepulse.battlepass.data.HuntsFile;
import fr.cobblepulse.battlepass.data.MonSpec;
import fr.cobblepulse.battlepass.data.PlayerData;
import fr.cobblepulse.battlepass.data.QuestDef;
import fr.cobblepulse.battlepass.data.QuestsFile;
import fr.cobblepulse.battlepass.data.Reward;
import fr.cobblepulse.battlepass.data.RewardsFile;
import fr.cobblepulse.battlepass.hunt.Hunts;
import fr.cobblepulse.battlepass.quest.QuestType;
import fr.cobblepulse.battlepass.quest.Quests;
import fr.cobblepulse.battlepass.reward.PokemonFactory;
import fr.cobblepulse.battlepass.reward.RewardService;
import fr.cobblepulse.battlepass.reward.SpeciesLookup;
import fr.cobblepulse.battlepass.season.Season;
import fr.cobblepulse.battlepass.storage.PlayerStore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reseau cote serveur : ouverture des ecrans, actions des joueurs et de l'editeur, mises a jour en direct. */
public final class Net {
    private static final Set<UUID> VIEWERS = new HashSet<>();
    private static final Set<UUID> PENDING = new HashSet<>();
    private static final Map<UUID, Integer> SENT_REV = new HashMap<>();
    /** Auto-test : derniere donnee envoyee par type. */
    public static final Map<String, String> LAST_SENT = new HashMap<>();

    private Net() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Payloads.Action.TYPE, Payloads.Action.CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.Data.TYPE, Payloads.Data.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Action.TYPE, (payload, ctx) -> ctx.server().execute(() -> {
            try {
                handle(ctx.player(), payload.action(), Views.json(payload.json()));
            } catch (Throwable t) {
                BattlePass.LOG.error("[BattlePass] Action {} de {} en echec", payload.action(), ctx.player().getGameProfile().getName(), t);
                toast(ctx.player(), false, "Erreur : " + t.getMessage());
            }
        }));
        Scheduler.repeat(10, 10, Net::flush);
    }

    public static boolean hasClient(ServerPlayer p) {
        return p != null && !Players.isTest(p.getUUID()) && ServerPlayNetworking.canSend(p, Payloads.Data.TYPE);
    }

    public static void send(ServerPlayer p, String kind, JsonElement json) {
        String s = Json.COMPACT.toJson(json);
        LAST_SENT.put(kind, s);
        if (hasClient(p)) ServerPlayNetworking.send(p, new Payloads.Data(kind, s));
    }

    public static void toast(ServerPlayer p, boolean ok, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", ok);
        o.addProperty("message", message);
        send(p, "toast", o);
    }

    /** Ouvre l'ecran du passe ; false si le joueur n'a pas le mod cote client. */
    public static boolean openPass(ServerPlayer p) {
        if (!hasClient(p)) return false;
        VIEWERS.add(p.getUUID());
        SENT_REV.put(p.getUUID(), Cfg.revision);
        send(p, "pass_open", Views.pass(p, true));
        return true;
    }

    public static boolean openAdmin(ServerPlayer p) {
        if (!hasClient(p)) return false;
        send(p, "admin_open", Views.admin(p));
        return true;
    }

    /** Demande une mise a jour de l'ecran du passe (envoyee au plus toutes les 10 ticks, seulement s'il est ouvert). */
    public static void refresh(ServerPlayer p) {
        if (p != null && VIEWERS.contains(p.getUUID())) PENDING.add(p.getUUID());
    }

    private static void flush() {
        if (PENDING.isEmpty()) return;
        List<UUID> ids = new ArrayList<>(PENDING);
        PENDING.clear();
        for (UUID id : ids) {
            ServerPlayer p = Players.get(id);
            if (p == null || !VIEWERS.contains(id)) continue;
            boolean full = !Integer.valueOf(Cfg.revision).equals(SENT_REV.get(id));
            if (full) SENT_REV.put(id, Cfg.revision);
            send(p, full ? "pass_full" : "pass", Views.pass(p, full));
        }
    }

    public static void forget(UUID id) {
        VIEWERS.remove(id);
        PENDING.remove(id);
        SENT_REV.remove(id);
    }

    private static String str(JsonElement j, String key, String def) {
        if (j == null || !j.isJsonObject() || !j.getAsJsonObject().has(key)) return def;
        try {
            return j.getAsJsonObject().get(key).getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    private static int num(JsonElement j, String key, int def) {
        try {
            return j.getAsJsonObject().get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    private static double dbl(JsonElement j, String key, double def) {
        try {
            return j.getAsJsonObject().get(key).getAsDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean bool(JsonElement j, String key, boolean def) {
        try {
            return j.getAsJsonObject().get(key).getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    // ------------------------------------------------------------------ Actions

    static void handle(ServerPlayer p, String action, JsonElement a) {
        if (action.startsWith("admin_")) {
            admin(p, action, a);
            return;
        }
        if (!Perms.player(p, Perms.USE)) {
            Msg.send(p, "general.no_permission");
            return;
        }
        switch (action) {
            case "open" -> openPass(p);
            case "view" -> {
                if (bool(a, "open", false)) {
                    VIEWERS.add(p.getUUID());
                    PENDING.add(p.getUUID());
                } else {
                    VIEWERS.remove(p.getUUID());
                }
            }
            case "claim" -> Season.claim(p, bool(a, "premium", false), num(a, "tier", 0), false);
            case "claim_all" -> Season.claimAll(p);
            case "buy_premium" -> Season.buyPremium(p);
            case "deliver" -> Quests.deliver(p, str(a, "key", ""));
            case "hunt_start" -> Hunts.start(p, str(a, "diff", ""), false);
            case "hunt_abandon" -> Hunts.abandon(p, str(a, "diff", ""));
            case "hunt_candidates" -> send(p, "candidates", Views.candidates(p, str(a, "diff", ""), num(a, "index", -1)));
            case "hunt_turnin" -> {
                try {
                    UUID id = UUID.fromString(str(a, "uuid", ""));
                    if (Hunts.turnIn(p, str(a, "diff", ""), num(a, "index", -1), id)) toast(p, true, "Chasse terminée !");
                } catch (IllegalArgumentException e) {
                    toast(p, false, "Pokémon invalide");
                }
            }
            default -> BattlePass.LOG.debug("[BattlePass] Action inconnue {}", action);
        }
        refresh(p);
    }

    // ------------------------------------------------------------------ Editeur

    private static void admin(ServerPlayer p, String action, JsonElement a) {
        if (!Perms.anyAdmin(p) && !Perms.staff(p, Perms.STAFF_POINTS)) {
            Msg.send(p, "general.no_permission");
            return;
        }
        String by = p.getGameProfile().getName();
        switch (action) {
            case "admin_open" -> openAdmin(p);
            case "admin_save" -> {
                if (!Perms.admin(p, Perms.ADMIN_EDIT)) {
                    toast(p, false, "Permission manquante : " + Perms.ADMIN_EDIT);
                    return;
                }
                List<String> warnings = new ArrayList<>();
                String section = str(a, "section", "");
                JsonElement data = a.getAsJsonObject().get("data");
                boolean ok = save(p, section, data, warnings);
                JsonObject r = new JsonObject();
                r.addProperty("ok", ok);
                r.addProperty("section", section);
                r.addProperty("message", ok ? "Enregistré" + (warnings.isEmpty() ? "." : " avec " + warnings.size() + " avertissement(s).")
                        : "Enregistrement refusé.");
                JsonArray w = new JsonArray();
                for (String s : warnings) w.add(s);
                r.add("warnings", w);
                send(p, "admin_result", r);
                send(p, "admin_data", Views.admin(p));
                if (ok) BattlePass.LOG.info("[BattlePass] {} : section {} modifiee par {}", Cfg.pass.nom, section, by);
            }
            case "admin_check_pokemon" -> {
                List<String> errors = new ArrayList<>();
                MonSpec spec = Json.CFG.fromJson(a.getAsJsonObject().get("spec"), MonSpec.class);
                Pokemon pk = spec == null ? null : PokemonFactory.create(spec, errors);
                JsonObject r = new JsonObject();
                r.addProperty("ok", pk != null && errors.isEmpty());
                JsonArray e = new JsonArray();
                for (String s : errors) e.add(s);
                r.add("errors", e);
                if (pk != null) {
                    r.addProperty("icon", Views.item(PokemonItem.from(pk), p.registryAccess()));
                    r.addProperty("name", Views.comp(pk.getDisplayName(false), p.registryAccess()));
                    r.addProperty("ability", pk.getAbility().getTemplate().getName());
                    r.addProperty("nature", pk.getNature().getName().getPath());
                }
                send(p, "pokemon_check", r);
            }
            case "admin_player" -> {
                PlayerData d = PlayerStore.byName(str(a, "name", ""));
                if (d == null) {
                    toast(p, false, "Joueur inconnu : " + str(a, "name", ""));
                    return;
                }
                send(p, "admin_player", Views.player(d));
            }
            case "admin_points" -> {
                if (!Perms.staff(p, Perms.STAFF_POINTS)) {
                    toast(p, false, "Permission manquante : " + Perms.STAFF_POINTS);
                    return;
                }
                PlayerData d = PlayerStore.byName(str(a, "name", ""));
                if (d == null) {
                    toast(p, false, "Joueur inconnu");
                    return;
                }
                long amount = Math.max(0, (long) dbl(a, "amount", 0));
                switch (str(a, "op", "give")) {
                    case "take" -> Season.takePoints(d, amount);
                    case "set" -> {
                        d.points = amount;
                        PlayerStore.save(d);
                    }
                    default -> Season.addPoints(d, null, amount, "staff", false);
                }
                toast(p, true, d.name + " : " + d.points + " points (palier " + Season.tier(d) + ")");
                send(p, "admin_player", Views.player(d));
            }
            case "admin_premium" -> {
                if (!Perms.admin(p, Perms.ADMIN_PREMIUM)) {
                    toast(p, false, "Permission manquante : " + Perms.ADMIN_PREMIUM);
                    return;
                }
                PlayerData d = PlayerStore.byName(str(a, "name", ""));
                if (d == null) {
                    toast(p, false, "Joueur inconnu");
                    return;
                }
                Season.setPremium(d, bool(a, "on", true), "commande");
                toast(p, true, d.name + " : premium " + (d.premium ? "activé" : "retiré"));
                send(p, "admin_player", Views.player(d));
            }
            case "admin_reset" -> {
                if (!Perms.admin(p, Perms.ADMIN_RESET)) {
                    toast(p, false, "Permission manquante : " + Perms.ADMIN_RESET);
                    return;
                }
                String name = str(a, "name", "");
                boolean keep = bool(a, "keepPremium", true);
                if ("*".equals(name)) {
                    int n = Season.resetAll(keep);
                    toast(p, true, n + " joueur(s) réinitialisé(s).");
                    BattlePass.LOG.info("[BattlePass] Reinitialisation de tous les joueurs par {}", by);
                } else {
                    PlayerData d = PlayerStore.byName(name);
                    if (d == null) {
                        toast(p, false, "Joueur inconnu");
                        return;
                    }
                    Season.reset(d, keep);
                    toast(p, true, d.name + " réinitialisé.");
                    send(p, "admin_player", Views.player(d));
                }
            }
            case "admin_toggle" -> {
                if (!Perms.admin(p, Perms.ADMIN_TOGGLE)) {
                    toast(p, false, "Permission manquante : " + Perms.ADMIN_TOGGLE);
                    return;
                }
                Season.setOpen(bool(a, "open", !Cfg.pass.ouvert));
                send(p, "admin_data", Views.admin(p));
            }
            case "admin_new_season" -> {
                if (!Perms.admin(p, Perms.ADMIN_SEASON)) {
                    toast(p, false, "Permission manquante : " + Perms.ADMIN_SEASON);
                    return;
                }
                Season.newSeason(by);
                toast(p, true, Cfg.pass.nom + " lancée.");
                send(p, "admin_data", Views.admin(p));
            }
            case "admin_boost" -> {
                if (!Perms.admin(p, Perms.ADMIN_BOOST)) {
                    toast(p, false, "Permission manquante : " + Perms.ADMIN_BOOST);
                    return;
                }
                Season.setBoost(dbl(a, "mult", 1.0), num(a, "minutes", 0), by);
                send(p, "admin_data", Views.admin(p));
            }
            case "admin_hunts_restart" -> {
                if (!Perms.admin(p, Perms.ADMIN_EDIT)) return;
                Hunts.resetGlobal();
                toast(p, true, "Nouvelle vague de chasses globales.");
            }
            default -> BattlePass.LOG.debug("[BattlePass] Action admin inconnue {}", action);
        }
    }

    /** Enregistre une section modifiee dans l'editeur (avec verifications). */
    public static boolean save(ServerPlayer p, String section, JsonElement data, List<String> warnings) {
        if (data == null || !data.isJsonObject()) {
            warnings.add("Données vides");
            return false;
        }
        try {
            switch (section) {
                case "saison" -> {
                    PassConfig c = Json.CFG.fromJson(data, PassConfig.class);
                    c.normalize();
                    PassConfig old = Cfg.pass;
                    c.saison = old.saison;
                    c.debut = old.debut;
                    boolean toggled = c.ouvert != old.ouvert;
                    if (toggled && p != null && !Perms.admin(p, Perms.ADMIN_TOGGLE)) {
                        c.ouvert = old.ouvert;
                        warnings.add("Ouverture/fermeture ignorée : permission " + Perms.ADMIN_TOGGLE + " manquante");
                        toggled = false;
                    }
                    boolean daysChanged = c.jours != old.jours;
                    boolean groupsChanged = !c.luckperms.groupeGratuit.equals(old.luckperms.groupeGratuit)
                            || !c.luckperms.groupePremium.equals(old.luckperms.groupePremium) || c.luckperms.actif != old.luckperms.actif;
                    if (c.premium.achatEnJeu != null && !List.of("aucun", "coins_jobs", "bp_battletower", "objet").contains(c.premium.achatEnJeu)) {
                        warnings.add("Achat en jeu inconnu : " + c.premium.achatEnJeu + " (aucun utilisé)");
                        c.premium.achatEnJeu = "aucun";
                    }
                    Cfg.pass = c;
                    Cfg.savePass();
                    if (toggled) Season.setOpen(c.ouvert);
                    if (daysChanged) Season.setDays(c.jours);
                    if (groupsChanged) {
                        Season.ensureGroups();
                        Season.syncAllGroups();
                    }
                }
                case "recompenses" -> {
                    RewardsFile f = Json.CFG.fromJson(data, RewardsFile.class);
                    f.normalize();
                    for (Map.Entry<String, RewardsFile.Tier> e : f.paliers.entrySet()) {
                        checkRewards("Palier " + e.getKey() + " (gratuit)", e.getValue().gratuit, warnings);
                        checkRewards("Palier " + e.getKey() + " (premium)", e.getValue().premium, warnings);
                    }
                    Cfg.rewards = f;
                    Cfg.saveRewards();
                }
                case "quetes" -> {
                    QuestsFile f = Json.CFG.fromJson(data, QuestsFile.class);
                    f.normalize();
                    for (String cat : Quests.CATEGORIES) {
                        for (QuestDef q : f.pool(cat)) {
                            if (QuestType.of(q.type) == null) warnings.add("Quête " + q.id + " : type inconnu " + q.type);
                            for (String s : q.filtres.especes) if (SpeciesLookup.resolve(s) == null) warnings.add("Quête " + q.id + " : espèce inconnue " + s);
                        }
                    }
                    Cfg.quests = f;
                    Cfg.saveQuests();
                    Quests.invalidate();
                }
                case "chasses" -> {
                    HuntsFile f = Json.CFG.fromJson(data, HuntsFile.class);
                    f.normalize();
                    for (String diff : HuntsFile.ALL) {
                        for (HuntEntry e : f.pool(diff)) if (SpeciesLookup.resolve(e.espece) == null) warnings.add("Chasse " + diff + " : espèce inconnue " + e.espece);
                        checkRewards("Butin " + diff, f.loot(diff), warnings);
                    }
                    Cfg.hunts = f;
                    Cfg.saveHunts();
                }
                default -> {
                    warnings.add("Section inconnue " + section);
                    return false;
                }
            }
        } catch (Exception e) {
            warnings.add("Format invalide : " + e.getMessage());
            return false;
        }
        Season.refreshAll();
        return true;
    }

    private static void checkRewards(String where, List<Reward> list, List<String> warnings) {
        for (Reward r : list) {
            switch (r.type()) {
                case "objet" -> {
                    if (RewardService.decodeItem(r.objet).isEmpty()) warnings.add(where + " : objet invalide");
                }
                case "pokemon" -> {
                    List<String> errs = new ArrayList<>();
                    if (PokemonFactory.create(Json.copy(r.pokemon, MonSpec.class), errs) == null || !errs.isEmpty()) {
                        warnings.add(where + " : " + (errs.isEmpty() ? "Pokémon invalide" : String.join(", ", errs)));
                    }
                }
                case "commande" -> {
                    if (r.commandes.isEmpty()) warnings.add(where + " : aucune commande");
                }
                default -> {
                    if (r.montant <= 0 && !"commande".equals(r.type())) warnings.add(where + " : montant nul");
                }
            }
        }
    }
}
