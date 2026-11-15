package fr.cobblepulse.battlepass.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.config.Defaults;
import fr.cobblepulse.battlepass.core.Json;
import fr.cobblepulse.battlepass.core.Players;
import fr.cobblepulse.battlepass.core.Scheduler;
import fr.cobblepulse.battlepass.core.TextUtil;
import fr.cobblepulse.battlepass.data.HuntEntry;
import fr.cobblepulse.battlepass.data.HuntInstance;
import fr.cobblepulse.battlepass.data.HuntsFile;
import fr.cobblepulse.battlepass.data.MonSpec;
import fr.cobblepulse.battlepass.data.PlayerData;
import fr.cobblepulse.battlepass.data.QuestDef;
import fr.cobblepulse.battlepass.data.QuestsFile;
import fr.cobblepulse.battlepass.data.Reward;
import fr.cobblepulse.battlepass.data.RewardsFile;
import fr.cobblepulse.battlepass.hunt.Hunts;
import fr.cobblepulse.battlepass.integration.BattletowerBridge;
import fr.cobblepulse.battlepass.integration.BattletowerHooks;
import fr.cobblepulse.battlepass.integration.LuckPermsBridge;
import fr.cobblepulse.battlepass.integration.PlaceholderBridge;
import fr.cobblepulse.battlepass.net.Net;
import fr.cobblepulse.battlepass.net.Views;
import fr.cobblepulse.battlepass.quest.Ctx;
import fr.cobblepulse.battlepass.quest.PlacedBlocks;
import fr.cobblepulse.battlepass.quest.QuestType;
import fr.cobblepulse.battlepass.quest.Quests;
import fr.cobblepulse.battlepass.reward.PokemonFactory;
import fr.cobblepulse.battlepass.reward.RewardService;
import fr.cobblepulse.battlepass.reward.SpeciesLookup;
import fr.cobblepulse.battlepass.season.Season;
import fr.cobblepulse.battlepass.storage.DataService;
import fr.cobblepulse.battlepass.storage.PlayerStore;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Auto-test (CI) : vrais joueurs factices, vraies recompenses, quetes, chasses, LuckPerms, Battletower, persistance. */
public final class SelfTest {
    private static final List<String> REPORT = new ArrayList<>();
    private static int pass;
    private static int fail;

    private SelfTest() {}

    private static void check(CommandSourceStack src, String name, boolean ok, String detail) {
        String line = (ok ? "PASS " : "FAIL ") + name + (detail == null || detail.isEmpty() ? "" : " - " + detail);
        REPORT.add(line);
        if (ok) pass++;
        else fail++;
        BattlePass.LOG.info("[BattlePass][selftest] {}", line);
        if (src != null) src.sendSystemMessage(TextUtil.legacy((ok ? "&a" : "&c") + line));
    }

    private static FakePlayer fake(ServerLevel level, String name) {
        FakePlayer fp = FakePlayer.get(level, new GameProfile(UUID.nameUUIDFromBytes(("bp-" + name).getBytes(StandardCharsets.UTF_8)), name));
        Players.registerTest(fp);
        registerInPlayerList(level.getServer(), fp);
        PlayerStore.get(fp);
        return fp;
    }

    /** Cobblemon resout les joueurs par UUID : le joueur factice est ajoute a l'index par UUID de la liste des joueurs. */
    @SuppressWarnings("unchecked")
    private static void registerInPlayerList(MinecraftServer server, ServerPlayer p) {
        try {
            for (java.lang.reflect.Field f : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                if (!(f.getGenericType() instanceof java.lang.reflect.ParameterizedType pt)) continue;
                java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                if (args.length == 2 && args[0] == UUID.class && args[1] == ServerPlayer.class) {
                    f.setAccessible(true);
                    ((Map<UUID, ServerPlayer>) f.get(server.getPlayerList())).put(p.getUUID(), p);
                    return;
                }
            }
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass][selftest] index des joueurs : {}", t.toString());
        }
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static int partySize(ServerPlayer p) {
        int n = 0;
        for (Pokemon pk : Cobblemon.INSTANCE.getStorage().getParty(p)) if (pk != null) n++;
        return n;
    }

    private static QuestDef quest(String id, String type, int target, int points, java.util.function.Consumer<QuestDef.Filters> f) {
        QuestDef q = new QuestDef();
        q.id = id;
        q.type = type;
        q.objectif = target;
        q.points = points;
        if (f != null) f.accept(q.filtres);
        q.normalize();
        return q;
    }

    private static Pokemon mon(String species, int level) {
        MonSpec m = new MonSpec();
        m.species = species;
        m.level = level;
        return PokemonFactory.create(m, new ArrayList<>());
    }

    // ------------------------------------------------------------------ Execution 1

    public static int run(CommandSourceStack src) {
        REPORT.clear();
        pass = 0;
        fail = 0;
        MinecraftServer server = src.getServer();
        ServerLevel level = server.overworld();
        try {
            defaults(src);
            season(src, level);
            rewards(src, level);
            quests(src, level, server);
            hunts(src, level);
            integrations(src, level);
            views(src, level);
            lifecycle(src, level);
        } catch (Throwable t) {
            BattlePass.LOG.error("[BattlePass][selftest] exception", t);
            check(src, "exception", false, t.toString());
        }
        // LuckPerms est asynchrone : verification apres 3 s
        Scheduler.later(60, () -> luckPermsCheck(src));
        return 1;
    }

    private static void defaults(CommandSourceStack src) {
        List<String> warnings = new ArrayList<>();
        RewardsFile rf = Defaults.rewards();
        int checked = 0;
        for (Map.Entry<String, RewardsFile.Tier> e : rf.paliers.entrySet()) {
            for (Reward r : e.getValue().gratuit) checked += validReward(r, "palier " + e.getKey(), warnings);
            for (Reward r : e.getValue().premium) checked += validReward(r, "palier " + e.getKey() + " premium", warnings);
        }
        HuntsFile hf = Defaults.hunts();
        for (String d : HuntsFile.ALL) {
            for (HuntEntry he : hf.pool(d)) if (SpeciesLookup.resolve(he.espece) == null) warnings.add("chasse " + d + " : " + he.espece);
            for (Reward r : hf.loot(d)) checked += validReward(r, "butin " + d, warnings);
        }
        QuestsFile qf = Defaults.quests();
        for (String c : Quests.CATEGORIES) for (QuestDef q : qf.pool(c)) if (QuestType.of(q.type) == null) warnings.add("quete " + q.id);
        check(src, "defaults.valid", warnings.isEmpty(), checked + " recompenses verifiees " + warnings);
        check(src, "defaults.files", java.nio.file.Files.exists(Cfg.root().resolve("saison.json"))
                && java.nio.file.Files.exists(Cfg.root().resolve("recompenses.json")) && java.nio.file.Files.exists(Cfg.root().resolve("quetes.json"))
                && java.nio.file.Files.exists(Cfg.root().resolve("chasses.json")), Cfg.root().toString());
    }

    private static int validReward(Reward r, String where, List<String> warnings) {
        switch (r.type()) {
            case "objet" -> {
                if (RewardService.decodeItem(r.objet).isEmpty()) warnings.add(where + " objet " + r.objet);
            }
            case "pokemon" -> {
                List<String> errs = new ArrayList<>();
                if (PokemonFactory.create(Json.copy(r.pokemon, MonSpec.class), errs) == null || !errs.isEmpty()) warnings.add(where + " " + errs);
            }
            default -> {
            }
        }
        return 1;
    }

    private static void season(CommandSourceStack src, ServerLevel level) {
        check(src, "season.running", Season.running(), Season.status() + " fin=" + Season.end());
        FakePlayer a = fake(level, "BPLogin");
        PlayerData d = PlayerStore.get(a);
        Season.reset(d, false);
        d.dernierJour = "";
        d.serie = 0;
        long before = d.points;
        Season.dailyLogin(a);
        long after1 = d.points;
        Season.dailyLogin(a);
        check(src, "login.daily_once", after1 - before == Cfg.pass.connexion.points && d.points == after1 && d.serie == 1,
                "gain=" + (after1 - before) + " serie=" + d.serie);
        // tiers : 1000 points par palier par defaut
        d.points = 0;
        Season.addPoints(d, a, 2500, "test", false);
        check(src, "tier.math", Season.tier(d) == 2 && Cfg.pass.tierFor(999) == 0 && Cfg.pass.tierFor(1000) == 1, "palier=" + Season.tier(d));
        long b2 = d.points;
        Season.setBoost(2.0, 5, "selftest");
        Season.addPoints(d, a, 100, "test", true);
        Season.setBoost(1.0, 0, "selftest");
        check(src, "boost.x2", d.points - b2 == 200, "gain=" + (d.points - b2));
    }

    private static void rewards(CommandSourceStack src, ServerLevel level) {
        FakePlayer p = fake(level, "BPRewards");
        PlayerData d = PlayerStore.get(p);
        Season.reset(d, false);
        p.getInventory().clearContent();
        Season.addPoints(d, p, 1000, "test", false);
        int balls = count(p, com.cobblemon.mod.common.CobblemonItems.POKE_BALL);
        boolean ok = Season.claim(p, false, 1, true);
        RewardsFile.Tier t1 = Cfg.rewards.tier(1);
        check(src, "claim.free_tier1", ok && d.reclames.contains("g:1"), "items=" + (count(p, com.cobblemon.mod.common.CobblemonItems.POKE_BALL) - balls)
                + " recompenses=" + (t1 == null ? 0 : t1.gratuit.size()));
        check(src, "claim.twice_refused", !Season.claim(p, false, 1, true), "");
        check(src, "claim.locked_tier", !Season.claim(p, false, 5, true), "palier=" + Season.tier(d));
        check(src, "claim.premium_refused", !Season.claim(p, true, 1, true), "");
        Season.setPremium(d, true, "test");
        long bpBefore = BattletowerBridge.balance(p.getUUID(), p.getGameProfile().getName());
        boolean prem = Season.claim(p, true, 1, true);
        long bpAfter = BattletowerBridge.balance(p.getUUID(), p.getGameProfile().getName());
        check(src, "claim.premium_ok", prem && d.reclames.contains("p:1"), "bp " + bpBefore + " -> " + bpAfter);
        if (BattletowerBridge.present()) check(src, "reward.bp_battletower", bpAfter - bpBefore == 100, "delta=" + (bpAfter - bpBefore));

        // Pokemon personnalise
        Reward r = Defaults.mon("eevee", 20, true, m -> {
            m.nature = "jolly";
            m.ivs.put("spe", 31);
            m.ivs.put("atk", 0);
            m.evs.put("spe", 252);
            m.moves = new ArrayList<>(List.of("quickattack", "bite"));
            m.heldItem = "cobblemon:leftovers";
            m.ball = "cobblemon:ultra_ball";
            m.nickname = "Testeur";
        });
        int party = partySize(p);
        List<String> errs = new ArrayList<>();
        boolean given = RewardService.giveAll(p, d, List.of(r), errs);
        Pokemon last = null;
        for (Pokemon pk : Cobblemon.INSTANCE.getStorage().getParty(p)) if (pk != null) last = pk;
        boolean shape = last != null && last.getShiny() && last.getLevel() == 20 && last.getIvs().getOrDefault(Stats.SPEED) == 31
                && last.getIvs().getOrDefault(Stats.ATTACK) == 0 && last.getEvs().getOrDefault(Stats.SPEED) == 252
                && last.getNature().getName().getPath().equals("jolly") && last.getMoveSet().getMoves().size() == 2
                && last.heldItem().is(com.cobblemon.mod.common.CobblemonItems.LEFTOVERS)
                && last.getCaughtBall().getName().getPath().equals("ultra_ball");
        check(src, "reward.pokemon", given && partySize(p) == party + 1 && shape, errs + " party " + party + "->" + partySize(p)
                + (last == null ? "" : " " + last.getSpecies().getName() + " shiny=" + last.getShiny() + " ball=" + last.getCaughtBall().getName()));

        // Tout ou rien : une recompense invalide bloque tout le palier
        Reward bad = Reward.of("pokemon");
        bad.pokemon = new MonSpec();
        bad.pokemon.species = "pas_un_pokemon";
        int balls2 = count(p, Items.DIAMOND);
        boolean refused = !RewardService.giveAll(p, d, List.of(Defaults.item("minecraft:diamond", 3), bad), new ArrayList<>());
        check(src, "reward.all_or_nothing", refused && count(p, Items.DIAMOND) == balls2, "");
        // Grosse quantite (plusieurs piles)
        RewardService.giveAll(p, d, List.of(Defaults.item("minecraft:cobblestone", 200)), new ArrayList<>());
        check(src, "reward.big_stack", count(p, Items.COBBLESTONE) == 200, "n=" + count(p, Items.COBBLESTONE));
        // Commande console (tableau des scores : fonctionne aussi pour un joueur factice)
        MinecraftServer server = p.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "scoreboard objectives add bp_selftest dummy");
        RewardService.giveAll(p, d, List.of(Defaults.cmd("test", "minecraft:paper", "scoreboard players add %player% bp_selftest 5")), new ArrayList<>());
        int score = 0;
        try {
            var sb = server.getScoreboard();
            var obj = sb.getObjective("bp_selftest");
            var info = obj == null ? null : sb.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(p.getGameProfile().getName()), obj);
            score = info == null ? 0 : info.value();
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass][selftest] score : {}", t.toString());
        }
        check(src, "reward.command", score == 5, "score=" + score);
        // Coins Jobs+ : absent en CI -> refus propre
        List<String> e2 = new ArrayList<>();
        boolean coins = RewardService.giveAll(p, d, List.of(Defaults.amount("coins_jobs", 10)), e2);
        check(src, "reward.coins_without_jobs", coins == fr.cobblepulse.battlepass.integration.JobsBridge.usable(p), e2.toString());
    }

    private static void quests(CommandSourceStack src, ServerLevel level, MinecraftServer server) {
        QuestsFile saved = Cfg.quests;
        QuestsFile test = Json.copyCfg(saved, QuestsFile.class);
        test.saison.add(quest("t_eau", "capture", 2, 300, f -> f.types.add("eau")));
        test.saison.add(quest("t_minage", "minage", 2, 50, f -> f.blocs.add("minecraft:stone")));
        test.saison.add(quest("t_recolte", "recolte", 1, 50, null));
        test.saison.add(quest("t_tour", "tour_etage", 1, 50, null));
        test.saison.add(quest("t_boss", "tour_boss", 1, 50, null));
        test.saison.add(quest("t_saut", "stat", 3, 50, f -> f.stat = "minecraft:custom/minecraft:jump"));
        test.saison.add(quest("t_pain", "livraison", 3, 50, f -> f.objets.add("minecraft:bread")));
        test.saison.add(quest("t_staff", "manuelle", 1, 50, null));
        test.saison.add(quest("t_kill", "tuer", 1, 50, f -> f.entites.add("minecraft:zombie")));
        QuestDef prem = quest("t_premium", "capture", 1, 50, null);
        prem.premium = true;
        test.saison.add(prem);
        test.normalize();
        Cfg.quests = test;
        Cfg.revision++;
        Quests.invalidate();
        try {
            FakePlayer p = fake(level, "BPQuests");
            PlayerData d = PlayerStore.get(p);
            Season.reset(d, false);
            String k = Quests.prefix("saison") + "t_eau";
            long pts = d.points;
            Quests.fire(p, "capture", new Ctx().pokemon(mon("charmander", 5)), 1);
            check(src, "quest.filter_type_refuses", d.progression.getOrDefault(k, 0L) == 0, "");
            Quests.fire(p, "capture", new Ctx().pokemon(mon("squirtle", 5)), 1);
            Quests.fire(p, "capture", new Ctx().pokemon(mon("psyduck", 5)), 1);
            check(src, "quest.capture_type_eau", d.terminees.contains(k) && d.points - pts >= 300, "points+" + (d.points - pts));
            check(src, "quest.premium_locked", !d.terminees.contains(Quests.prefix("saison") + "t_premium"), "");
            // Daily selection : nombre de quetes tirees
            long daily = Quests.active(d).stream().filter(a -> a.category().equals("quotidiennes")).count();
            check(src, "quest.daily_selection", daily == Cfg.pass.quetesQuotidiennes + Cfg.pass.quetesQuotidiennesPremium, "n=" + daily);

            // Minage reel : bloc pose par le monde (compte) puis bloc pose par un joueur (ne compte pas)
            BlockPos pos = new BlockPos(0, level.getMaxBuildHeight() - 5, 0);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            p.gameMode.destroyBlock(pos);
            String km = Quests.prefix("saison") + "t_minage";
            long m1 = d.progression.getOrDefault(km, 0L);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            PlacedBlocks.mark(level, pos);
            p.gameMode.destroyBlock(pos);
            long m2 = d.progression.getOrDefault(km, 0L);
            check(src, "quest.mining_real_break", m1 == 1 && m2 == 1, "apres 1er=" + m1 + " apres pose=" + m2);
            // Recolte : ble mur
            level.setBlockAndUpdate(pos, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
            p.gameMode.destroyBlock(pos);
            check(src, "quest.harvest_mature", d.terminees.contains(Quests.prefix("saison") + "t_recolte"), "");
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

            // Statistique via le vrai ServerPlayer.awardStat (mixin)
            ServerPlayer raw = new ServerPlayer(server, level, new GameProfile(UUID.nameUUIDFromBytes("bp-raw".getBytes(StandardCharsets.UTF_8)),
                    "BPRaw"), ClientInformation.createDefault());
            Players.registerTest(raw);
            PlayerData rd = PlayerStore.get(raw);
            Season.reset(rd, false);
            for (int i = 0; i < 3; i++) raw.awardStat(net.minecraft.stats.Stats.JUMP);
            check(src, "quest.stat_mixin", rd.terminees.contains(Quests.prefix("saison") + "t_saut"),
                    "progression=" + rd.progression.get(Quests.prefix("saison") + "t_saut"));

            // Livraison
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(Items.BREAD, 5));
            int got = Quests.deliver(p, Quests.prefix("saison") + "t_pain");
            check(src, "quest.delivery", got == 3 && count(p, Items.BREAD) == 2 && d.terminees.contains(Quests.prefix("saison") + "t_pain"), "livres=" + got);
            // Validation staff
            check(src, "quest.staff_validate", Quests.validate(d, "t_staff") && d.terminees.contains(Quests.prefix("saison") + "t_staff"), "");
            // Mob tue (evenement Fabric reel)
            net.minecraft.world.entity.monster.Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            if (z != null) {
                z.moveTo(0.5, level.getMaxBuildHeight() - 3, 0.5);
                level.addFreshEntity(z);
                z.hurt(level.damageSources().playerAttack(p), 1000f);
            }
            check(src, "quest.kill_zombie", d.terminees.contains(Quests.prefix("saison") + "t_kill"), "zombie=" + (z != null));

            // Fermeture : plus de progression
            Season.setOpen(false);
            Quests.fire(p, "capture", new Ctx().pokemon(mon("squirtle", 5)), 1);
            boolean frozen = !Season.running() && !Season.claim(p, false, 1, true);
            Season.setOpen(true);
            check(src, "season.closed_blocks", frozen, Season.status());

            // Tour de Combat : appel direct des methodes de Battletower (les mixins doivent intercepter)
            if (BattletowerBridge.present()) {
                tower(src, p, d);
            } else {
                check(src, "tower.absent", true, "Battletower absent");
            }
        } finally {
            Cfg.quests = saved;
            Cfg.revision++;
            Quests.invalidate();
        }
    }

    private static void tower(CommandSourceStack src, ServerPlayer p, PlayerData d) {
        try {
            Class<?> outcome = Class.forName("fr.cobblepulse.battletower.battle.TowerBattle$Outcome");
            Constructor<?> oc = outcome.getDeclaredConstructors()[0];
            oc.setAccessible(true);
            Object won = oc.newInstance(UUID.randomUUID(), p.getUUID(), true, false, 3, false, "");
            Class<?> runState = Class.forName("fr.cobblepulse.battletower.data.RunState");
            Object run = runState.getDeclaredConstructor().newInstance();
            runState.getField("uuid").set(run, p.getUUID().toString());
            runState.getField("name").set(run, p.getGameProfile().getName());
            runState.getField("floor").setInt(run, 1);
            int floorsBefore = BattletowerHooks.floors;
            Class<?> tm = Class.forName("fr.cobblepulse.battletower.tower.TowerManager");
            for (Method m : tm.getDeclaredMethods()) {
                if (!m.getName().equals("onFloorWon")) continue;
                m.setAccessible(true);
                try {
                    m.invoke(null, won, run, p);
                } catch (Throwable t) {
                    BattlePass.LOG.info("[BattlePass][selftest] onFloorWon (corps Battletower) : {}", t.toString());
                }
            }
            check(src, "tower.mixin_floor", BattletowerHooks.floors == floorsBefore + 1
                    && d.terminees.contains(Quests.prefix("saison") + "t_tour"), "hooks=" + BattletowerHooks.floors);
            int bossBefore = BattletowerHooks.bosses;
            Class<?> db = Class.forName("fr.cobblepulse.battletower.tower.DailyBoss");
            for (Method m : db.getDeclaredMethods()) {
                if (!m.getName().equals("onResult")) continue;
                m.setAccessible(true);
                try {
                    m.invoke(null, won, run, p);
                } catch (Throwable t) {
                    BattlePass.LOG.info("[BattlePass][selftest] onResult (corps Battletower) : {}", t.toString());
                }
            }
            check(src, "tower.mixin_boss", BattletowerHooks.bosses == bossBefore + 1
                    && d.terminees.contains(Quests.prefix("saison") + "t_boss"), "hooks=" + BattletowerHooks.bosses);
        } catch (Throwable t) {
            check(src, "tower.reflection", false, t.toString());
        }
    }

    private static void hunts(CommandSourceStack src, ServerLevel level) {
        HuntsFile saved = Cfg.hunts;
        HuntsFile test = Json.copyCfg(saved, HuntsFile.class);
        test.pokemon.put("facile", new ArrayList<>(List.of(new HuntEntry("pikachu", "", "", 0))));
        test.pokemon.put("normal", new ArrayList<>(List.of(new HuntEntry("eevee", "male", "adamant", 1))));
        test.globale.actif = false;
        test.normalize();
        Cfg.hunts = test;
        Cfg.revision++;
        try {
            FakePlayer p = fake(level, "BPHunter");
            PlayerData d = PlayerStore.get(p);
            Season.reset(d, false);
            Cobblemon.INSTANCE.getStorage().getParty(p).add(mon("bulbasaur", 5));
            check(src, "hunt.ready", Hunts.state(p, d, "facile") == Hunts.State.READY, Hunts.state(p, d, "facile").name());
            check(src, "hunt.start", Hunts.start(p, "facile", false) && Hunts.state(p, d, "facile") == Hunts.State.ACTIVE, "");
            HuntInstance h = d.chasses.get("facile");
            // Pokemon capture AVANT le debut : refuse
            Pokemon early = mon("pikachu", 10);
            d.captures.put(early.getUuid().toString(), h.debut - 1000);
            Cobblemon.INSTANCE.getStorage().getParty(p).add(early);
            check(src, "hunt.capture_before_start_refused", !Hunts.turnIn(p, "facile", 0, early.getUuid()), "");
            Pokemon wrong = mon("raichu", 10);
            Cobblemon.INSTANCE.getStorage().getParty(p).add(wrong);
            Hunts.onCapture(p, wrong);
            check(src, "hunt.wrong_species_refused", !Hunts.turnIn(p, "facile", 0, wrong.getUuid()), "");
            Pokemon good = mon("pikachu", 12);
            Cobblemon.INSTANCE.getStorage().getParty(p).add(good);
            Hunts.onCapture(p, good);
            long pts = d.points;
            int party = partySize(p);
            boolean ok = Hunts.turnIn(p, "facile", 0, good.getUuid());
            check(src, "hunt.turn_in", ok && d.points - pts == test.solo("facile").points && partySize(p) == party - 1
                    && Hunts.state(p, d, "facile") == Hunts.State.COOLDOWN, "points+" + (d.points - pts) + " party " + party + "->" + partySize(p));
            check(src, "hunt.reuse_refused", !Hunts.turnIn(p, "facile", 0, good.getUuid()), "");
            // Exigences (genre, nature, IV) : normale = Evoli male rigide avec 1 IV >= minimum
            Hunts.start(p, "normal", false);
            HuntInstance hn = d.chasses.get("normal");
            MonSpec spec = new MonSpec();
            spec.species = "eevee";
            spec.level = 10;
            spec.gender = "female";
            spec.nature = "timid";
            for (String st : MonSpec.STATS) spec.ivs.put(st, 0);
            Pokemon bad = PokemonFactory.create(spec, new ArrayList<>());
            Hunts.onCapture(p, bad);
            List<String> why = Hunts.mismatch(bad, hn, d);
            check(src, "hunt.requirements", why.size() == 3 && hn.genre.equals("male") && hn.nature.equals("adamant") && hn.ivs.size() == 1,
                    why.toString());
            Hunts.resetCooldowns(d);
            check(src, "hunt.staff_cooldown_reset", Hunts.state(p, d, "facile") == Hunts.State.READY, "");
        } finally {
            Cfg.hunts = saved;
            Cfg.revision++;
        }
    }

    private static UUID lpUser;

    private static void integrations(CommandSourceStack src, ServerLevel level) {
        if (LuckPermsBridge.present()) {
            lpUser = UUID.nameUUIDFromBytes("bp-lp-user".getBytes(StandardCharsets.UTF_8));
            PlayerData d = PlayerStore.getOrCreate(lpUser, "BPLuck");
            Season.reset(d, false);
            Season.syncGroup(d);
        }
        if (PlaceholderBridge.present()) {
            FakePlayer p = fake(level, "BPStar");
            PlayerData d = PlayerStore.get(p);
            Season.reset(d, false);
            String free = placeholder(p);
            Season.setPremium(d, true, "test");
            String prem = placeholder(p);
            check(src, "placeholder.star", free.contains("☆") && prem.contains("★"), "gratuit='" + free + "' premium='" + prem + "'");
        }
    }

    private static String placeholder(ServerPlayer p) {
        try {
            Class<?> c = Class.forName("eu.pb4.placeholders.api.Placeholders");
            Class<?> ctx = Class.forName("eu.pb4.placeholders.api.PlaceholderContext");
            Object pc = ctx.getMethod("of", ServerPlayer.class).invoke(null, p);
            Object out = c.getMethod("parseText", Component.class, ctx).invoke(null, Component.literal("%cobblepulse_battlepass:etoile%"), pc);
            return ((Component) out).getString();
        } catch (Throwable t) {
            return "erreur " + t;
        }
    }

    private static void luckPermsCheck(CommandSourceStack src) {
        if (lpUser == null) return;
        String g1 = LuckPermsBridge.describe(lpUser);
        PlayerData d = PlayerStore.get(lpUser);
        Season.setPremium(d, true, "test");
        Scheduler.later(60, () -> {
            String g2 = LuckPermsBridge.describe(lpUser);
            check(src, "luckperms.groups", g1.contains(Cfg.pass.luckperms.groupeGratuit + "@") && g2.contains(Cfg.pass.luckperms.groupePremium + "@")
                    && !g2.contains(Cfg.pass.luckperms.groupeGratuit + "@"), "gratuit: " + g1 + " / premium: " + g2);
            long expected = Season.end() / 1000;
            boolean expiry = false;
            int at = g2.indexOf(Cfg.pass.luckperms.groupePremium + "@");
            if (at >= 0) {
                String v = g2.substring(at + Cfg.pass.luckperms.groupePremium.length() + 1).split("[,\\s]")[0];
                try {
                    expiry = Math.abs(Long.parseLong(v) - expected) < 120;
                } catch (NumberFormatException ignored) {
                }
            }
            check(src, "luckperms.expiry_end_of_season", expiry, "attendu ~" + expected);
            summary(src);
        });
    }

    private static void views(CommandSourceStack src, ServerLevel level) {
        FakePlayer p = fake(level, "BPView");
        JsonObject v = Views.pass(p, true);
        check(src, "view.pass", v.getAsJsonArray("tiers").size() == Cfg.pass.paliers && v.has("quetes") && v.has("chasses"),
                "taille=" + v.toString().length());
        JsonObject a = Views.admin(p);
        List<String> w = new ArrayList<>();
        boolean saved = Net.save(null, "quetes", a.get("quetes"), w) && Net.save(null, "recompenses", a.get("recompenses"), w)
                && Net.save(null, "chasses", a.get("chasses"), w) && Net.save(null, "saison", a.get("saison"), w);
        check(src, "editor.roundtrip", saved && w.isEmpty(), w.toString());
    }

    private static void lifecycle(CommandSourceStack src, ServerLevel level) {
        FakePlayer p = fake(level, "BPPersist");
        PlayerData d = PlayerStore.get(p);
        Season.reset(d, false);
        Season.addPoints(d, p, 4321, "test", false);
        Season.setPremium(d, true, "test");
        d.reclames.add("g:1");
        PlayerStore.save(d);
        // Reinitialisation d'un joueur
        FakePlayer r = fake(level, "BPReset");
        PlayerData rd = PlayerStore.get(r);
        Season.addPoints(rd, r, 3000, "test", false);
        Season.setPremium(rd, true, "test");
        Season.reset(rd, true);
        check(src, "reset.player_keep_premium", rd.points == 0 && rd.premium && rd.reclames.isEmpty(), "points=" + rd.points);
        // Changement de duree
        long end = Season.end();
        Season.setDays(Cfg.pass.jours + 2);
        check(src, "season.days", Season.end() - end == 2 * 86_400_000L, "");
        Season.setDays(Cfg.pass.jours - 2);
        DataService.flushDirty();
    }

    private static void summary(CommandSourceStack src) {
        String s = "SELFTEST SUMMARY pass=" + pass + " fail=" + fail;
        BattlePass.LOG.info("[BattlePass][selftest] {}", s);
        if (src != null) src.sendSystemMessage(TextUtil.legacy((fail == 0 ? "&a" : "&c") + s));
    }

    // ------------------------------------------------------------------ Execution 2 (apres redemarrage)

    public static int persisted(CommandSourceStack src) {
        REPORT.clear();
        pass = 0;
        fail = 0;
        PlayerData d = PlayerStore.byName("BPPersist");
        check(src, "persist.player", d != null && d.points == 4321 && d.premium && d.reclames.contains("g:1"),
                d == null ? "absent" : "points=" + d.points + " premium=" + d.premium + " saison=" + d.saison);
        check(src, "persist.store", DataService.storage() != null, DataService.storage() == null ? "" : DataService.storage().name());
        summary(src);
        return 1;
    }

    public static int report(CommandSourceStack src) {
        for (String l : REPORT) src.sendSystemMessage(TextUtil.legacy((l.startsWith("PASS") ? "&a" : "&c") + l));
        summary(src);
        return 1;
    }
}
