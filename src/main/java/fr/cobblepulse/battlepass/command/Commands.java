package fr.cobblepulse.battlepass.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.core.Msg;
import fr.cobblepulse.battlepass.core.Perms;
import fr.cobblepulse.battlepass.core.TextUtil;
import fr.cobblepulse.battlepass.core.TimeFmt;
import fr.cobblepulse.battlepass.data.HuntInstance;
import fr.cobblepulse.battlepass.data.HuntsFile;
import fr.cobblepulse.battlepass.data.PlayerData;
import fr.cobblepulse.battlepass.hunt.Hunts;
import fr.cobblepulse.battlepass.net.Net;
import fr.cobblepulse.battlepass.quest.Quests;
import fr.cobblepulse.battlepass.reward.Names;
import fr.cobblepulse.battlepass.season.Season;
import fr.cobblepulse.battlepass.storage.PlayerStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /passe (alias /battlepass) : ecran du passe, reclamations, premium, staff et administration.
 * /chasses (alias /hunts) : chasses.
 */
public final class Commands {
    private Commands() {}

    private static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (ctx, b) -> {
        MinecraftServer s = ctx.getSource().getServer();
        List<String> names = new java.util.ArrayList<>(PlayerStore.names());
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (!names.contains(p.getGameProfile().getName())) names.add(p.getGameProfile().getName());
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static final SuggestionProvider<CommandSourceStack> DIFFS = (ctx, b) -> SharedSuggestionProvider.suggest(HuntsFile.SOLO, b);
    private static final SuggestionProvider<CommandSourceStack> ALL_DIFFS = (ctx, b) -> SharedSuggestionProvider.suggest(HuntsFile.ALL, b);

    private static int player(CommandContext<CommandSourceStack> ctx, Consumer<ServerPlayer> action) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            Msg.send(ctx.getSource(), "general.players_only");
            return 0;
        }
        action.accept(p);
        return 1;
    }

    /** Dossier d'un joueur par son nom (connu, connecte, ou du cache de profils du serveur pour une boutique en ligne). */
    static PlayerData target(CommandSourceStack src, String name) {
        PlayerData d = PlayerStore.byName(name);
        if (d != null) return d;
        MinecraftServer s = src.getServer();
        ServerPlayer online = s.getPlayerList().getPlayerByName(name);
        if (online != null) return PlayerStore.get(online);
        try {
            if (s.getProfileCache() != null) {
                Optional<GameProfile> gp = s.getProfileCache().get(name);
                if (gp.isPresent()) return PlayerStore.getOrCreate(gp.get().getId(), gp.get().getName());
            }
        } catch (Throwable ignored) {
        }
        Msg.send(src, "general.player_not_found", "player", name);
        return null;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("passe").requires(Perms.playerReq(Perms.USE))
                .executes(ctx -> player(ctx, Commands::open))
                .then(literal("info").executes(ctx -> player(ctx, Commands::info)))
                .then(literal("reclamer").executes(ctx -> player(ctx, Season::claimAll))
                        .then(argument("palier", IntegerArgumentType.integer(1)).executes(ctx -> player(ctx, p -> {
                            int t = IntegerArgumentType.getInteger(ctx, "palier");
                            boolean any = Season.claim(p, false, t, false);
                            PlayerData pd = PlayerStore.get(p);
                            if (Season.isPremium(pd, p)) any |= Season.claim(p, true, t, false);
                        }))))
                .then(literal("quetes").executes(ctx -> player(ctx, Commands::quests)))
                .then(literal("premium").then(literal("acheter").executes(ctx -> player(ctx, Season::buyPremium))))
                .then(literal("top").executes(ctx -> top(ctx.getSource())))
                .then(staff())
                .then(admin())
                .then(literal("selftest").requires(src -> "true".equals(System.getProperty("cobblepulse_battlepass.selftest")))
                        .executes(ctx -> SelfTest.run(ctx.getSource()))
                        .then(literal("persisted").executes(ctx -> SelfTest.persisted(ctx.getSource())))
                        .then(literal("report").executes(ctx -> SelfTest.report(ctx.getSource()))));
        var node = d.register(root);
        d.register(literal("battlepass").requires(Perms.playerReq(Perms.USE)).executes(ctx -> player(ctx, Commands::open)).redirect(node));

        LiteralArgumentBuilder<CommandSourceStack> hunts = literal("chasses").requires(Perms.playerReq(Perms.HUNTS))
                .executes(ctx -> player(ctx, Commands::huntsInfo))
                .then(literal("lancer").then(argument("difficulte", StringArgumentType.word()).suggests(DIFFS)
                        .executes(ctx -> player(ctx, p -> Hunts.start(p, StringArgumentType.getString(ctx, "difficulte"), false)))))
                .then(literal("abandonner").then(argument("difficulte", StringArgumentType.word()).suggests(DIFFS)
                        .executes(ctx -> player(ctx, p -> Hunts.abandon(p, StringArgumentType.getString(ctx, "difficulte"))))))
                .then(literal("remettre").then(argument("difficulte", StringArgumentType.word()).suggests(ALL_DIFFS)
                        .executes(ctx -> player(ctx, p -> turnIn(p, StringArgumentType.getString(ctx, "difficulte"), 0)))
                        .then(argument("numero", IntegerArgumentType.integer(1))
                                .executes(ctx -> player(ctx, p -> turnIn(p, StringArgumentType.getString(ctx, "difficulte"),
                                        IntegerArgumentType.getInteger(ctx, "numero") - 1))))));
        var hnode = d.register(hunts);
        d.register(literal("hunts").requires(Perms.playerReq(Perms.HUNTS)).executes(ctx -> player(ctx, Commands::huntsInfo)).redirect(hnode));
    }

    // ------------------------------------------------------------------ Joueur

    private static void open(ServerPlayer p) {
        if (!Net.openPass(p)) info(p);
    }

    private static void info(ServerPlayer p) {
        PlayerData d = PlayerStore.get(p);
        int tier = Season.tier(d);
        long base = Cfg.pass.totalFor(tier);
        long next = tier >= Cfg.pass.paliers ? 0 : Cfg.pass.costOf(tier + 1);
        Msg.send(p, "info.header", "season", Cfg.pass.nom, "status", Msg.raw("status." + Season.status()),
                "left", TimeFmt.duration(Math.max(0, Season.end() - TimeFmt.now())));
        Msg.send(p, "info.tier", "tier", tier, "max", Cfg.pass.paliers, "points", d.points, "progress", d.points - base, "next", next,
                "premium", Season.isPremium(d, p) ? Msg.raw("info.premium_yes") : Msg.raw("info.premium_no"));
        int n = Season.claimable(d, p);
        if (n > 0) p.sendSystemMessage(TextUtil.button(Msg.format("info.claim_button", "count", n), "/passe reclamer", Msg.raw("info.claim_hover")));
        Msg.send(p, "info.streak", "streak", d.serie, "days", d.joursConnexion);
    }

    private static void quests(ServerPlayer p) {
        PlayerData d = PlayerStore.get(p);
        for (String cat : Quests.CATEGORIES) {
            Msg.send(p, "info.quests_header", "category", Names.category(cat), "reset", TimeFmt.duration(Math.max(0, Quests.nextReset(cat) - TimeFmt.now())));
            for (Quests.Active a : Quests.active(d)) {
                if (!a.category().equals(cat)) continue;
                int scale = a.type() == null ? 1 : a.type().scale;
                boolean done = d.terminees.contains(a.key());
                Msg.send(p, done ? "info.quest_done" : "info.quest", "quest", Quests.title(a.def()), "progress", Quests.progress(d, a) / scale,
                        "target", a.def().objectif, "points", a.def().points, "premium", a.def().premium ? Msg.raw("info.quest_premium") : "");
            }
        }
    }

    private static int top(CommandSourceStack src) {
        Msg.send(src, "info.top_header", "season", Cfg.pass.nom);
        int i = 1;
        for (PlayerData d : PlayerStore.top(10)) {
            Msg.send(src, "info.top_line", "rank", i++, "player", d.name, "tier", Season.tier(d), "points", d.points);
        }
        return 1;
    }

    private static void huntsInfo(ServerPlayer p) {
        if (Net.openPass(p)) return;
        PlayerData d = PlayerStore.get(p);
        Msg.send(p, "info.hunts_header");
        for (String diff : HuntsFile.SOLO) {
            Hunts.State st = Hunts.state(p, d, diff);
            HuntInstance h = st == Hunts.State.ACTIVE ? d.chasses.get(diff) : st == Hunts.State.READY ? Hunts.preview(d, diff) : null;
            String what = h == null ? "-" : Hunts.speciesName(h) + describe(h);
            String extra = switch (st) {
                case ACTIVE -> TimeFmt.duration(h.fin - TimeFmt.now());
                case COOLDOWN -> TimeFmt.duration(d.recharges.getOrDefault(diff, 0L) - TimeFmt.now());
                default -> "";
            };
            Msg.send(p, "info.hunt_line", "difficulty", Names.difficulty(diff), "state", Msg.raw("hunt.state." + st.name().toLowerCase()),
                    "pokemon", what, "extra", extra);
        }
        Hunts.Global g = Hunts.global();
        for (int i = 0; i < g.chasses.size(); i++) {
            HuntInstance h = g.chasses.get(i);
            Msg.send(p, "info.hunt_global_line", "number", i + 1, "pokemon", Hunts.speciesName(h) + describe(h),
                    "state", Hunts.globalDone(d, i) ? Msg.raw("info.hunt_done") : TimeFmt.duration(h.fin - TimeFmt.now()));
        }
    }

    private static String describe(HuntInstance h) {
        StringBuilder b = new StringBuilder();
        if (!h.genre.isEmpty()) b.append(", ").append(Names.gender(h.genre));
        if (!h.nature.isEmpty()) b.append(", ").append(Names.nature(h.nature));
        if (!h.ivs.isEmpty()) b.append(", IV ").append(String.join("/", h.ivs.stream().map(Names::stat).toList())).append(" ≥ ").append(Cfg.hunts.ivMinimum);
        return b.toString();
    }

    /** Remise en chat (sans le mod client) : premier Pokemon qui convient, equipe puis PC. */
    private static void turnIn(ServerPlayer p, String diff, int index) {
        PlayerData d = PlayerStore.get(p);
        HuntInstance h = Hunts.hunt(d, diff, "globale".equals(diff) ? index : 0);
        if (h == null) {
            Msg.send(p, "hunt.not_active");
            return;
        }
        for (Hunts.Candidate c : Hunts.candidates(p, h)) {
            if (c.problems().isEmpty()) {
                Hunts.turnIn(p, diff, index, c.pokemon().getUuid());
                return;
            }
        }
        Msg.send(p, "hunt.no_candidate");
    }

    // ------------------------------------------------------------------ Staff

    private static LiteralArgumentBuilder<CommandSourceStack> staff() {
        return literal("staff")
                .then(literal("points").requires(Perms.staffReq(Perms.STAFF_POINTS))
                        .then(pointsOp("donner")).then(pointsOp("retirer")).then(pointsOp("definir")))
                .then(literal("voir").requires(Perms.staffReq(Perms.STAFF_INSPECT))
                        .then(argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS).executes(ctx -> {
                            PlayerData pd = target(ctx.getSource(), StringArgumentType.getString(ctx, "joueur"));
                            if (pd == null) return 0;
                            Msg.send(ctx.getSource(), "staff.inspect", "player", pd.name, "points", pd.points, "tier", Season.tier(pd),
                                    "premium", pd.premium ? "oui (" + pd.premiumSource + ")" : "non", "claimed", pd.reclames.size(),
                                    "quests", pd.quetesTerminees, "hunts", pd.chassesTerminees, "streak", pd.serie, "days", pd.joursConnexion);
                            return 1;
                        })))
                .then(literal("quete").requires(Perms.staffReq(Perms.STAFF_QUESTS))
                        .then(literal("valider").then(argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(argument("quete", StringArgumentType.word()).executes(ctx -> {
                                    PlayerData pd = target(ctx.getSource(), StringArgumentType.getString(ctx, "joueur"));
                                    if (pd == null) return 0;
                                    boolean ok = Quests.validate(pd, StringArgumentType.getString(ctx, "quete"));
                                    Msg.send(ctx.getSource(), ok ? "staff.quest_validated" : "staff.quest_unknown", "player", pd.name,
                                            "quest", StringArgumentType.getString(ctx, "quete"));
                                    return ok ? 1 : 0;
                                })))))
                .then(literal("chasses").requires(Perms.staffReq(Perms.STAFF_HUNTS))
                        .then(literal("recharge").then(argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS).executes(ctx -> {
                            PlayerData pd = target(ctx.getSource(), StringArgumentType.getString(ctx, "joueur"));
                            if (pd == null) return 0;
                            Hunts.resetCooldowns(pd);
                            Msg.send(ctx.getSource(), "staff.hunts_cooldown", "player", pd.name);
                            return 1;
                        }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pointsOp(String op) {
        return literal(op).then(argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                .then(argument("montant", LongArgumentType.longArg(0)).executes(ctx -> {
                    PlayerData pd = target(ctx.getSource(), StringArgumentType.getString(ctx, "joueur"));
                    if (pd == null) return 0;
                    long amount = LongArgumentType.getLong(ctx, "montant");
                    switch (op) {
                        case "donner" -> Season.addPoints(pd, null, amount, "staff", false);
                        case "retirer" -> Season.takePoints(pd, amount);
                        default -> {
                            pd.points = amount;
                            PlayerStore.save(pd);
                        }
                    }
                    Msg.send(ctx.getSource(), "staff.points_done", "player", pd.name, "points", pd.points, "tier", Season.tier(pd));
                    if ("donner".equals(op)) Msg.send(UUID.fromString(pd.uuid), "staff.points_received", "points", amount);
                    BattlePass.LOG.info("[BattlePass] Points {} {} {} par {}", op, pd.name, amount, ctx.getSource().getTextName());
                    return 1;
                })));
    }

    // ------------------------------------------------------------------ Admin

    private static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return literal("admin")
                .requires(src -> !src.isPlayer() || Perms.anyAdmin(src.getPlayer()))
                .executes(ctx -> player(ctx, p -> {
                    if (!Net.openAdmin(p)) Msg.send(p, "general.no_client");
                }))
                .then(literal("ouvrir").requires(Perms.adminReq(Perms.ADMIN_TOGGLE)).executes(ctx -> {
                    Season.setOpen(true);
                    return 1;
                }))
                .then(literal("fermer").requires(Perms.adminReq(Perms.ADMIN_TOGGLE)).executes(ctx -> {
                    Season.setOpen(false);
                    return 1;
                }))
                .then(literal("jours").requires(Perms.adminReq(Perms.ADMIN_SEASON))
                        .then(argument("jours", IntegerArgumentType.integer(1, 3650)).executes(ctx -> {
                            Season.setDays(IntegerArgumentType.getInteger(ctx, "jours"));
                            Msg.send(ctx.getSource(), "admin.days_set", "days", Cfg.pass.jours, "end", TimeFmt.date(Season.end()));
                            return 1;
                        })))
                .then(literal("saison").requires(Perms.adminReq(Perms.ADMIN_SEASON))
                        .then(literal("nouvelle").then(literal("confirmer").executes(ctx -> {
                            Season.newSeason(ctx.getSource().getTextName());
                            return 1;
                        }))))
                .then(literal("reset").requires(Perms.adminReq(Perms.ADMIN_RESET))
                        .then(literal("tous").then(literal("confirmer").executes(ctx -> resetAll(ctx.getSource(), true))
                                .then(literal("complet").executes(ctx -> resetAll(ctx.getSource(), false)))))
                        .then(argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> reset(ctx.getSource(), StringArgumentType.getString(ctx, "joueur"), true))
                                .then(literal("complet").executes(ctx -> reset(ctx.getSource(), StringArgumentType.getString(ctx, "joueur"), false)))))
                .then(literal("premium").requires(Perms.adminReq(Perms.ADMIN_PREMIUM))
                        .then(premiumOp("donner", true)).then(premiumOp("retirer", false)))
                .then(literal("boost").requires(Perms.adminReq(Perms.ADMIN_BOOST))
                        .then(literal("stop").executes(ctx -> {
                            Season.setBoost(1.0, 0, ctx.getSource().getTextName());
                            return 1;
                        }))
                        .then(argument("multiplicateur", DoubleArgumentType.doubleArg(1.0, 10.0))
                                .then(argument("minutes", IntegerArgumentType.integer(1, 60 * 24 * 30)).executes(ctx -> {
                                    Season.setBoost(DoubleArgumentType.getDouble(ctx, "multiplicateur"), IntegerArgumentType.getInteger(ctx, "minutes"),
                                            ctx.getSource().getTextName());
                                    return 1;
                                }))))
                .then(literal("reload").requires(Perms.adminReq(Perms.ADMIN_RELOAD)).executes(ctx -> {
                    Cfg.loadAll();
                    Season.ensureGroups();
                    Season.refreshAll();
                    Msg.send(ctx.getSource(), "admin.reloaded");
                    return 1;
                }))
                .then(literal("chasses").requires(Perms.adminReq(Perms.ADMIN_EDIT))
                        .then(literal("relancer").executes(ctx -> {
                            Hunts.resetGlobal();
                            Msg.send(ctx.getSource(), "admin.hunts_restarted");
                            return 1;
                        })));
    }

    private static int reset(CommandSourceStack src, String name, boolean keepPremium) {
        PlayerData pd = target(src, name);
        if (pd == null) return 0;
        Season.reset(pd, keepPremium);
        Msg.send(src, "admin.reset_done", "player", pd.name);
        BattlePass.LOG.info("[BattlePass] Reinitialisation de {} par {}", pd.name, src.getTextName());
        return 1;
    }

    private static int resetAll(CommandSourceStack src, boolean keepPremium) {
        int n = Season.resetAll(keepPremium);
        Msg.send(src, "admin.reset_all_done", "count", n);
        BattlePass.LOG.info("[BattlePass] Reinitialisation de tous les joueurs ({}) par {}", n, src.getTextName());
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> premiumOp(String op, boolean on) {
        return literal(op).then(argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS).executes(ctx -> {
            PlayerData pd = target(ctx.getSource(), StringArgumentType.getString(ctx, "joueur"));
            if (pd == null) return 0;
            Season.setPremium(pd, on, "commande");
            Msg.send(ctx.getSource(), on ? "admin.premium_given" : "admin.premium_removed", "player", pd.name, "season", Cfg.pass.nom);
            if (on) Msg.send(UUID.fromString(pd.uuid), "premium.unlocked", "season", Cfg.pass.nom);
            BattlePass.LOG.info("[BattlePass] Premium {} pour {} par {}", on ? "donne" : "retire", pd.name, ctx.getSource().getTextName());
            return 1;
        }));
    }
}
