package fr.cobblepulse.battlepass;

import fr.cobblepulse.battlepass.command.Commands;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.core.Scheduler;
import fr.cobblepulse.battlepass.hunt.Hunts;
import fr.cobblepulse.battlepass.integration.BattletowerBridge;
import fr.cobblepulse.battlepass.integration.JobsBridge;
import fr.cobblepulse.battlepass.integration.LuckPermsBridge;
import fr.cobblepulse.battlepass.integration.PlaceholderBridge;
import fr.cobblepulse.battlepass.net.Net;
import fr.cobblepulse.battlepass.quest.PlacedBlocks;
import fr.cobblepulse.battlepass.quest.QuestHooks;
import fr.cobblepulse.battlepass.quest.Quests;
import fr.cobblepulse.battlepass.season.Season;
import fr.cobblepulse.battlepass.storage.DataService;
import fr.cobblepulse.battlepass.storage.PlayerStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CobblePulse - Passe de Combat. Base : Cobblemon Trainer Pass (Fredrick Von Burger, MIT) entierement reecrit en francais,
 * chasses reprises de CobbleHunts (Hysocs, LGPL-3.0).
 */
public final class BattlePass implements ModInitializer {
    public static final String MOD_ID = "cobblepulse_battlepass";
    public static final Logger LOG = LoggerFactory.getLogger("CobblePulse-BattlePass");
    private static MinecraftServer server;

    public static MinecraftServer server() {
        return server;
    }

    @Override
    public void onInitialize() {
        Cfg.loadAll();
        Net.register();
        QuestHooks.register();
        PlaceholderBridge.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> Commands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            server = s;
            Cfg.loadAll();
            DataService.start();
            PlayerStore.load();
            Season.load();
            Hunts.load();
            Season.ensureGroups();
            LOG.info("[BattlePass] {} ({}) - {} paliers, {} jours. LuckPerms : {}, Battletower : {}, Jobs+ : {}",
                    Cfg.pass.nom, Season.status(), Cfg.pass.paliers, Cfg.pass.jours, LuckPermsBridge.present() ? "oui" : "non",
                    BattletowerBridge.present() ? "oui" : "non", JobsBridge.present() ? "oui" : "non");
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            DataService.shutdown();
            PlayerStore.clear();
            Scheduler.clear();
            PlacedBlocks.clear();
            Quests.invalidate();
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(Scheduler::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            ServerPlayer p = handler.player;
            // Laisse a LuckPerms le temps de charger le joueur
            Scheduler.later(40, () -> {
                if (!p.hasDisconnected()) Season.onJoin(p);
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            Net.forget(handler.player.getUUID());
            Quests.forget(handler.player.getUUID());
        });
        LOG.info("[BattlePass] Mod initialise");
    }
}
