package fr.cobblepulse.battlepass.integration;

import fr.cobblepulse.battlepass.BattlePass;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Battle Points de CobblePulse Battletower, par reflexion (aucune dependance dure) : fonctionne aussi pour les joueurs
 * hors ligne et cree leur dossier Battletower si besoin.
 */
public final class BattletowerBridge {
    private static Boolean present;
    private static Method getOrCreate;
    private static Method give;
    private static Method take;

    private BattletowerBridge() {}

    public static boolean present() {
        if (present == null) present = FabricLoader.getInstance().isModLoaded("cobblepulse_battletower");
        return present;
    }

    private static Object data(UUID uuid, String name) throws Exception {
        if (getOrCreate == null) {
            Class<?> store = Class.forName("fr.cobblepulse.battletower.storage.PlayerStore");
            getOrCreate = store.getMethod("getOrCreate", UUID.class, String.class);
            Class<?> data = Class.forName("fr.cobblepulse.battletower.data.PlayerData");
            Class<?> bp = Class.forName("fr.cobblepulse.battletower.economy.BattlePoints");
            give = bp.getMethod("give", data, long.class);
            take = bp.getMethod("take", data, long.class);
        }
        return getOrCreate.invoke(null, uuid, name);
    }

    public static boolean give(UUID uuid, String name, long amount) {
        if (!present() || amount <= 0) return false;
        try {
            give.invoke(null, data(uuid, name), amount);
            return true;
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass] Don de BP Battletower impossible : {}", t.toString());
            return false;
        }
    }

    public static long balance(UUID uuid, String name) {
        if (!present()) return 0;
        try {
            Object d = data(uuid, name);
            return d.getClass().getField("bp").getLong(d);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean take(UUID uuid, String name, long amount) {
        if (!present()) return false;
        try {
            Object d = data(uuid, name);
            return (Boolean) take.invoke(null, d, amount);
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass] Retrait de BP Battletower impossible : {}", t.toString());
            return false;
        }
    }
}
