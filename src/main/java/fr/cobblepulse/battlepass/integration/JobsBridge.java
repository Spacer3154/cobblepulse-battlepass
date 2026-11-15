package fr.cobblepulse.battlepass.integration;

import fr.cobblepulse.battlepass.BattlePass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Coins de Jobs+ (DAQEM), utilises par cobblepulse-jobs : appels par reflexion aux methodes que Jobs+ ajoute au joueur
 * ({@code jobsplus$getCoins}, {@code jobsplus$addCoins}, {@code jobsplus$setCoins}). Le joueur doit etre connecte.
 */
public final class JobsBridge {
    private JobsBridge() {}

    public static boolean present() {
        FabricLoader fl = FabricLoader.getInstance();
        return fl.isModLoaded("jobsplus") || fl.isModLoaded("jobs_plus");
    }

    private static Method method(ServerPlayer p, String name, Class<?>... args) throws NoSuchMethodException {
        return p.getClass().getMethod(name, args);
    }

    public static boolean usable(ServerPlayer p) {
        if (p == null) return false;
        try {
            method(p, "jobsplus$getCoins");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static double coins(ServerPlayer p) {
        try {
            return ((Number) method(p, "jobsplus$getCoins").invoke(p)).doubleValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean add(ServerPlayer p, double amount) {
        if (p == null || amount <= 0) return false;
        try {
            method(p, "jobsplus$addCoins", double.class).invoke(p, amount);
            return true;
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass] Don de coins Jobs+ impossible ({}) : {}", p.getGameProfile().getName(), t.toString());
            return false;
        }
    }

    public static boolean take(ServerPlayer p, double amount) {
        if (p == null || amount < 0) return false;
        try {
            double c = coins(p);
            if (c < amount) return false;
            method(p, "jobsplus$setCoins", double.class).invoke(p, c - amount);
            return true;
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass] Retrait de coins Jobs+ impossible : {}", t.toString());
            return false;
        }
    }
}
