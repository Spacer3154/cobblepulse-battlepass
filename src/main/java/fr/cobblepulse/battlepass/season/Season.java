package fr.cobblepulse.battlepass.season;

import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.config.PassConfig;
import fr.cobblepulse.battlepass.core.Fx;
import fr.cobblepulse.battlepass.core.ItemBuilder;
import fr.cobblepulse.battlepass.core.Json;
import fr.cobblepulse.battlepass.core.Msg;
import fr.cobblepulse.battlepass.core.Perms;
import fr.cobblepulse.battlepass.core.Players;
import fr.cobblepulse.battlepass.core.Scheduler;
import fr.cobblepulse.battlepass.core.TimeFmt;
import fr.cobblepulse.battlepass.data.PlayerData;
import fr.cobblepulse.battlepass.data.Reward;
import fr.cobblepulse.battlepass.data.RewardsFile;
import fr.cobblepulse.battlepass.hunt.Hunts;
import fr.cobblepulse.battlepass.integration.BattletowerBridge;
import fr.cobblepulse.battlepass.integration.JobsBridge;
import fr.cobblepulse.battlepass.integration.LuckPermsBridge;
import fr.cobblepulse.battlepass.net.Net;
import fr.cobblepulse.battlepass.quest.Ctx;
import fr.cobblepulse.battlepass.quest.Quests;
import fr.cobblepulse.battlepass.reward.RewardService;
import fr.cobblepulse.battlepass.storage.DataService;
import fr.cobblepulse.battlepass.storage.PlayerStore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Saison du passe : calendrier, points, paliers, reclamations, premium, connexion quotidienne et grades LuckPerms. */
public final class Season {
    /** Etat persistant (cle "etat") : bonus de points temporaire, derniere saison dont la fin a ete traitee. */
    public static final class State {
        public double boostMult = 1.0;
        public long boostFin;
        public int saisonTerminee;
        public String boostPar = "";
    }

    private static State state = new State();
    private static final Deque<String> GROUP_QUEUE = new ArrayDeque<>();

    private Season() {}

    public static State state() {
        return state;
    }

    public static void load() {
        String json = DataService.blocking(s -> s.loadKv("etat"));
        try {
            State st = json == null ? null : Json.GSON.fromJson(json, State.class);
            state = st == null ? new State() : st;
        } catch (Exception e) {
            state = new State();
        }
        Scheduler.repeat(20, 20, Season::tick);
        Scheduler.repeat(40, 20 * 60, Season::minute);
        Scheduler.repeat(5, 5, Season::drainGroups);
    }

    public static void saveState() {
        DataService.markKv("etat", () -> state);
    }

    // ------------------------------------------------------------------ Calendrier

    public static PassConfig cfg() {
        return Cfg.pass;
    }

    public static long end() {
        return Cfg.pass.debut <= 0 ? 0 : Cfg.pass.debut + Cfg.pass.jours * TimeFmt.DAY;
    }

    /** ouvert, maintenance, attente (pas encore commencee) ou terminee. */
    public static String status() {
        if (!Cfg.pass.ouvert) return "maintenance";
        if (Cfg.pass.debut <= 0 || Cfg.pass.debut > TimeFmt.now()) return "attente";
        if (TimeFmt.now() >= end()) return "terminee";
        return "ouvert";
    }

    /** La progression (points, quetes, chasses) est possible. */
    public static boolean running() {
        return "ouvert".equals(status());
    }

    /** Les reclamations restent possibles apres la fin de la saison (pas en maintenance). */
    public static boolean canClaim() {
        return Cfg.pass.ouvert && Cfg.pass.debut > 0;
    }

    public static ZoneId zone() {
        try {
            return ZoneId.of(Cfg.pass.fuseau);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    private static LocalDate localDay(long ms) {
        return Instant.ofEpochMilli(ms).atZone(zone()).minusHours(Cfg.pass.heureReset).toLocalDate();
    }

    /** Jour "de jeu" (decale de l'heure de remise a zero), ex. 2026-11-15. */
    public static String dayKey(long ms) {
        return localDay(ms).toString();
    }

    public static DayOfWeek weekStart() {
        try {
            return DayOfWeek.valueOf(Cfg.pass.jourResetHebdo.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return DayOfWeek.MONDAY;
        }
    }

    public static String weekKey(long ms) {
        return "w" + localDay(ms).with(TemporalAdjusters.previousOrSame(weekStart()));
    }

    private static long atReset(LocalDate d) {
        return ZonedDateTime.of(d.atStartOfDay(), zone()).plusHours(Cfg.pass.heureReset).toInstant().toEpochMilli();
    }

    public static long nextDayReset() {
        return atReset(localDay(TimeFmt.now()).plusDays(1));
    }

    public static long nextWeekReset() {
        return atReset(localDay(TimeFmt.now()).with(TemporalAdjusters.previousOrSame(weekStart())).plusDays(7));
    }

    // ------------------------------------------------------------------ Boucles

    private static void tick() {
        PassConfig c = Cfg.pass;
        if (c.debut <= 0 && c.ouvert) {
            c.debut = TimeFmt.now();
            Cfg.savePass();
            BattlePass.LOG.info("[BattlePass] {} commencee ({} jours)", c.nom, c.jours);
            syncAllGroups();
        }
        if ("terminee".equals(status()) && state.saisonTerminee != c.saison) {
            state.saisonTerminee = c.saison;
            saveState();
            Msg.broadcast("season.ended", "season", c.nom);
            BattlePass.LOG.info("[BattlePass] {} terminee", c.nom);
            if ("nouvelle_saison".equalsIgnoreCase(c.finDeSaison)) newSeason("fin automatique");
        }
    }

    /** Chaque minute : connexion quotidienne (joueurs restes connectes apres minuit), temps de jeu, controle du premium. */
    private static void minute() {
        MinecraftServer s = BattlePass.server();
        if (s == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            try {
                dailyLogin(p);
                Quests.playMinute(p);
                PlayerData d = PlayerStore.get(p);
                boolean before = d.premium;
                if (isPremium(d, p) != before) Net.refresh(p);
            } catch (Throwable t) {
                BattlePass.LOG.warn("[BattlePass] Tache minute ({}) : {}", p.getGameProfile().getName(), t.toString());
            }
        }
    }

    // ------------------------------------------------------------------ Actions admin

    public static void newSeason(String by) {
        PassConfig c = Cfg.pass;
        c.saison++;
        if (c.nom == null || c.nom.isBlank() || c.nom.matches("(?i)saison\\s*\\d+")) c.nom = "Saison " + c.saison;
        c.debut = TimeFmt.now();
        c.ouvert = true;
        Cfg.savePass();
        state.saisonTerminee = 0;
        saveState();
        Hunts.resetGlobal();
        Quests.invalidate();
        MinecraftServer s = BattlePass.server();
        if (s != null) {
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                PlayerStore.get(p);
                syncGroup(PlayerStore.get(p));
                dailyLogin(p);
                Net.refresh(p);
            }
        }
        syncAllGroups();
        Msg.broadcast("season.new", "season", c.nom, "days", c.jours);
        BattlePass.LOG.info("[BattlePass] Nouvelle saison {} lancee par {}", c.saison, by);
    }

    public static void setOpen(boolean open) {
        Cfg.pass.ouvert = open;
        Cfg.savePass();
        Msg.broadcast(open ? "season.opened" : "season.closed", "season", Cfg.pass.nom);
        refreshAll();
    }

    public static void setDays(int days) {
        Cfg.pass.jours = Math.max(1, days);
        Cfg.savePass();
        if (state.saisonTerminee == Cfg.pass.saison && !"terminee".equals(status())) {
            state.saisonTerminee = 0;
            saveState();
        }
        syncAllGroups();
        refreshAll();
    }

    public static void refreshAll() {
        Quests.invalidate();
        MinecraftServer s = BattlePass.server();
        if (s != null) for (ServerPlayer p : s.getPlayerList().getPlayers()) Net.refresh(p);
    }

    // ------------------------------------------------------------------ Bonus de points

    public static double boost() {
        return TimeFmt.now() < state.boostFin ? Math.max(1.0, state.boostMult) : 1.0;
    }

    public static void setBoost(double mult, long minutes, String by) {
        state.boostMult = Math.max(1.0, mult);
        state.boostFin = minutes <= 0 ? 0 : TimeFmt.now() + minutes * TimeFmt.MINUTE;
        state.boostPar = by == null ? "" : by;
        saveState();
        if (minutes > 0) Msg.broadcast("season.boost", "mult", RewardService.fmt(state.boostMult), "duration", TimeFmt.duration(minutes * TimeFmt.MINUTE));
        else Msg.broadcast("season.boost_end");
        refreshAll();
    }

    // ------------------------------------------------------------------ Points et paliers

    public static int tier(PlayerData d) {
        return Cfg.pass.tierFor(d.points);
    }

    /**
     * Ajoute des points (bonus temporaire et bonus premium inclus si {@code bonus}). Annonce les paliers atteints.
     * @return points reellement ajoutes
     */
    public static long addPoints(PlayerData d, ServerPlayer p, long base, String source, boolean bonus) {
        if (base <= 0 || d == null) return 0;
        if (p == null) p = Players.get(d.uuid);
        long gained = base;
        if (bonus) {
            gained = Math.round(base * boost());
            if (Cfg.pass.bonusPremiumPourcent > 0 && isPremium(d, p)) gained += Math.round(base * Cfg.pass.bonusPremiumPourcent / 100.0);
        }
        int before = tier(d);
        d.points += gained;
        switch (source) {
            case "quete" -> d.pointsQuetes += gained;
            case "connexion" -> d.pointsConnexion += gained;
            case "chasse" -> d.pointsChasses += gained;
            case "staff" -> d.pointsStaff += gained;
            default -> {
            }
        }
        PlayerStore.save(d);
        int after = tier(d);
        if (p != null) {
            Msg.actionBar(p, "points.gained", "points", gained, "total", d.points, "source", Msg.raw("source." + source));
            if (after > before) tierUp(p, d, before, after);
            Net.refresh(p);
        }
        return gained;
    }

    /** Retire des points (staff). */
    public static void takePoints(PlayerData d, long amount) {
        d.points = Math.max(0, d.points - Math.max(0, amount));
        PlayerStore.save(d);
        ServerPlayer p = Players.get(d.uuid);
        if (p != null) Net.refresh(p);
    }

    private static void tierUp(ServerPlayer p, PlayerData d, int before, int after) {
        Fx.title(p, Msg.format("tier.title", "tier", after), Msg.format("tier.subtitle", "tier", after));
        Fx.sound(p, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
        Msg.send(p, "tier.reached", "tier", after, "max", Cfg.pass.paliers);
        for (int t = before + 1; t <= after; t++) {
            if (t > d.dernierPalierAnnonce && Cfg.pass.annoncePaliers.contains(t)) {
                Msg.broadcast("tier.broadcast", "player", p.getGameProfile().getName(), "tier", t, "season", Cfg.pass.nom);
            }
        }
        d.dernierPalierAnnonce = Math.max(d.dernierPalierAnnonce, after);
    }

    // ------------------------------------------------------------------ Premium

    /**
     * Premium : achete/donne pour la saison, ou permission {@code cobblepulse.battlepass.premium} explicitement accordee
     * (ex. par une boutique en ligne). Le grade passe_premium du mod ne donne pas cette permission (pas de boucle).
     */
    public static boolean isPremium(PlayerData d, ServerPlayer p) {
        if (!Cfg.pass.premium.actif || d == null) return false;
        if (d.premium) return true;
        if (p != null && Boolean.TRUE.equals(LuckPermsBridge.exact(p, Perms.PREMIUM))) {
            setPremium(d, true, "luckperms");
            Msg.send(p, "premium.unlocked", "season", Cfg.pass.nom);
            return true;
        }
        return false;
    }

    public static void setPremium(PlayerData d, boolean on, String source) {
        d.premium = on;
        d.premiumSource = on ? source : "";
        d.premiumDepuis = on ? TimeFmt.now() : 0;
        PlayerStore.save(d);
        syncGroup(d);
        Quests.invalidate(d.uuid);
        ServerPlayer p = Players.get(d.uuid);
        if (p != null) Net.refresh(p);
    }

    /** Achat en jeu du premium (si configure). */
    public static boolean buyPremium(ServerPlayer p) {
        PlayerData d = PlayerStore.get(p);
        PassConfig.Premium pc = Cfg.pass.premium;
        if (!pc.actif) {
            Msg.send(p, "premium.disabled");
            return false;
        }
        if (isPremium(d, p)) {
            Msg.send(p, "premium.already");
            return false;
        }
        if (!running()) {
            Msg.send(p, "season.not_running", "status", Msg.raw("status." + status()));
            return false;
        }
        String mode = pc.achatEnJeu.toLowerCase(Locale.ROOT);
        double price = Math.max(0, pc.prix);
        boolean paid;
        switch (mode) {
            case "coins_jobs" -> paid = JobsBridge.usable(p) && JobsBridge.take(p, price);
            case "bp_battletower" -> paid = BattletowerBridge.present() && BattletowerBridge.take(p.getUUID(), p.getGameProfile().getName(), Math.round(price));
            case "objet" -> paid = takeItems(p, pc.objet, (int) Math.round(price));
            default -> {
                Msg.send(p, "premium.store_only");
                return false;
            }
        }
        if (!paid) {
            Msg.send(p, "premium.not_enough", "price", priceLabel());
            return false;
        }
        setPremium(d, true, "achat");
        Fx.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        Msg.send(p, "premium.unlocked", "season", Cfg.pass.nom);
        return true;
    }

    public static String priceLabel() {
        PassConfig.Premium pc = Cfg.pass.premium;
        return switch (pc.achatEnJeu.toLowerCase(Locale.ROOT)) {
            case "coins_jobs" -> RewardService.fmt(pc.prix) + " coins Jobs+";
            case "bp_battletower" -> RewardService.fmt(pc.prix) + " BP";
            case "objet" -> RewardService.fmt(pc.prix) + " x " + pc.objet;
            default -> "";
        };
    }

    private static boolean matchesItem(ItemStack s, String spec) {
        if (s.isEmpty() || spec == null || spec.isBlank()) return false;
        String v = spec.trim();
        if (v.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(v.substring(1));
            return rl != null && s.is(TagKey.create(Registries.ITEM, rl));
        }
        Item item = ItemBuilder.item(v, Items.AIR);
        return item != Items.AIR && s.is(item);
    }

    private static boolean takeItems(ServerPlayer p, String spec, int amount) {
        if (amount <= 0) return true;
        int have = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (matchesItem(s, spec)) have += s.getCount();
        }
        if (have < amount) return false;
        int left = amount;
        for (int i = 0; i < p.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!matchesItem(s, spec)) continue;
            int n = Math.min(left, s.getCount());
            s.shrink(n);
            left -= n;
        }
        p.containerMenu.broadcastChanges();
        return true;
    }

    // ------------------------------------------------------------------ Reclamations

    public static String claimKey(boolean premium, int tier) {
        return (premium ? "p:" : "g:") + tier;
    }

    /** Reclame les recompenses d'un palier sur une piste. */
    public static boolean claim(ServerPlayer p, boolean premiumTrack, int tier, boolean quiet) {
        PlayerData d = PlayerStore.get(p);
        if (!canClaim()) {
            if (!quiet) Msg.send(p, "season.not_running", "status", Msg.raw("status." + status()));
            return false;
        }
        if (tier < 1 || tier > Cfg.pass.paliers || tier > tier(d)) {
            if (!quiet) Msg.send(p, "claim.locked", "tier", tier);
            return false;
        }
        if (premiumTrack && !isPremium(d, p)) {
            if (!quiet) Msg.send(p, "claim.need_premium");
            return false;
        }
        String key = claimKey(premiumTrack, tier);
        if (d.reclames.contains(key)) {
            if (!quiet) Msg.send(p, "claim.already", "tier", tier);
            return false;
        }
        RewardsFile.Tier t = Cfg.rewards.tier(tier);
        List<Reward> list = t == null ? List.of() : (premiumTrack ? t.premium : t.gratuit);
        List<String> errors = new ArrayList<>();
        if (!RewardService.giveAll(p, d, list, errors)) {
            Msg.send(p, "claim.error", "tier", tier);
            BattlePass.LOG.warn("[BattlePass] Palier {} ({}) impossible a donner a {} : {}", tier, premiumTrack ? "premium" : "gratuit",
                    p.getGameProfile().getName(), String.join(" | ", errors));
            return false;
        }
        d.reclames.add(key);
        PlayerStore.save(d);
        if (!quiet) {
            Fx.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
            Msg.send(p, premiumTrack ? "claim.done_premium" : "claim.done", "tier", tier);
        }
        Net.refresh(p);
        return true;
    }

    /** Reclame tout ce qui est disponible. */
    public static int claimAll(ServerPlayer p) {
        PlayerData d = PlayerStore.get(p);
        if (!canClaim()) {
            Msg.send(p, "season.not_running", "status", Msg.raw("status." + status()));
            return 0;
        }
        int n = 0;
        boolean prem = isPremium(d, p);
        for (int t = 1; t <= tier(d); t++) {
            if (!d.reclames.contains(claimKey(false, t)) && claim(p, false, t, true)) n++;
            if (prem && !d.reclames.contains(claimKey(true, t)) && claim(p, true, t, true)) n++;
        }
        if (n > 0) {
            Fx.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
            Msg.send(p, "claim.all", "count", n);
        } else {
            Msg.send(p, "claim.nothing");
        }
        return n;
    }

    public static int claimable(PlayerData d, ServerPlayer p) {
        int n = 0;
        boolean prem = isPremium(d, p);
        for (int t = 1; t <= tier(d); t++) {
            RewardsFile.Tier tr = Cfg.rewards.tier(t);
            if (tr == null) continue;
            if (!tr.gratuit.isEmpty() && !d.reclames.contains(claimKey(false, t))) n++;
            if (prem && !tr.premium.isEmpty() && !d.reclames.contains(claimKey(true, t))) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ Connexion

    public static void onJoin(ServerPlayer p) {
        PlayerData d = PlayerStore.get(p);
        isPremium(d, p);
        syncGroup(d);
        dailyLogin(p);
        int n = claimable(d, p);
        if (n > 0) Msg.send(p, "join.claimable", "count", n);
    }

    public static void dailyLogin(ServerPlayer p) {
        if (!running() || !Cfg.pass.connexion.actif) return;
        PlayerData d = PlayerStore.get(p);
        long now = TimeFmt.now();
        String today = dayKey(now);
        if (today.equals(d.dernierJour)) return;
        String yesterday = localDay(now).minusDays(1).toString();
        if (d.dernierJour.equals(yesterday) || !Cfg.pass.connexion.serieCasseeSiJourManque) d.serie++;
        else d.serie = 1;
        d.dernierJour = today;
        d.joursConnexion++;
        d.meilleureSerie = Math.max(d.meilleureSerie, d.serie);
        long pts = Cfg.pass.connexion.points;
        Integer bonus = Cfg.pass.connexion.bonusSerie.get(String.valueOf(d.serie));
        long gained = addPoints(d, p, pts + (bonus == null ? 0 : bonus), "connexion", true);
        if (bonus != null && bonus > 0) Msg.send(p, "login.streak_bonus", "points", gained, "streak", d.serie);
        else Msg.send(p, "login.daily", "points", gained, "streak", d.serie);
        Quests.fire(p, "connexion", new Ctx(), 1);
    }

    // ------------------------------------------------------------------ Grades LuckPerms

    public static void ensureGroups() {
        PassConfig.LuckPerms lp = Cfg.pass.luckperms;
        if (lp.actif) LuckPermsBridge.ensureGroups(lp.groupeGratuit, lp.groupePremium, lp.poids);
    }

    /** Grade du passe du joueur : gratuit ou premium jusqu'a la fin de la saison ; retire hors saison. */
    public static void syncGroup(PlayerData d) {
        PassConfig.LuckPerms lp = Cfg.pass.luckperms;
        if (!lp.actif || !LuckPermsBridge.present() || d == null || d.uuid == null) return;
        UUID uuid;
        try {
            uuid = UUID.fromString(d.uuid);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (Players.isTest(uuid)) return;
        String st = status();
        long end = end();
        String group = null;
        if (Cfg.pass.debut > 0 && !"terminee".equals(st) && end > TimeFmt.now()) {
            group = d.premium && Cfg.pass.premium.actif ? lp.groupePremium : lp.groupeGratuit;
        }
        LuckPermsBridge.setPassGroup(uuid, group, end, lp.groupeGratuit, lp.groupePremium);
    }

    /** Met a jour (par lots) le grade de tous les joueurs connus, ex. apres un changement de duree. */
    public static void syncAllGroups() {
        if (!Cfg.pass.luckperms.actif || !LuckPermsBridge.present()) return;
        GROUP_QUEUE.clear();
        for (PlayerData d : PlayerStore.all()) GROUP_QUEUE.add(d.uuid);
    }

    private static void drainGroups() {
        for (int i = 0; i < 10 && !GROUP_QUEUE.isEmpty(); i++) {
            String id = GROUP_QUEUE.poll();
            try {
                PlayerData d = PlayerStore.get(UUID.fromString(id));
                if (d != null) syncGroup(d);
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ Remises a zero

    public static void reset(PlayerData d, boolean keepPremium) {
        d.resetProgress(keepPremium);
        d.saison = Cfg.pass.saison;
        PlayerStore.save(d);
        syncGroup(d);
        Quests.invalidate(d.uuid);
        ServerPlayer p = Players.get(d.uuid);
        if (p != null) {
            Msg.send(p, "reset.notified");
            dailyLogin(p);
            Net.refresh(p);
        }
    }

    public static int resetAll(boolean keepPremium) {
        int n = 0;
        for (PlayerData d : PlayerStore.all()) {
            reset(d, keepPremium);
            n++;
        }
        Hunts.resetGlobal();
        return n;
    }
}
