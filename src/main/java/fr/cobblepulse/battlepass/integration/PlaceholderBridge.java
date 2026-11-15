package fr.cobblepulse.battlepass.integration;

import fr.cobblepulse.battlepass.BattlePass;
import net.fabricmc.loader.api.FabricLoader;

/** Enregistre les placeholders %cobblepulse_battlepass:...% si Text Placeholder API est installe. */
public final class PlaceholderBridge {
    private PlaceholderBridge() {}

    public static boolean present() {
        return FabricLoader.getInstance().isModLoaded("placeholder-api");
    }

    public static void init() {
        if (!present()) {
            BattlePass.LOG.info("[BattlePass] Text Placeholder API absent : placeholders non exposes");
            return;
        }
        try {
            PlaceholderHook.register();
            BattlePass.LOG.info("[BattlePass] Placeholders enregistres (%cobblepulse_battlepass:etoile% ...)");
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass] Enregistrement des placeholders impossible : {}", t.toString());
        }
    }
}
