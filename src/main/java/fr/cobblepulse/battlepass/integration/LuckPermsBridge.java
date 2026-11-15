package fr.cobblepulse.battlepass.integration;

import fr.cobblepulse.battlepass.BattlePass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Facade sans dependance dure vers LuckPerms (les classes LP ne sont touchees que si le mod est present). */
public final class LuckPermsBridge {
    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("luckperms");
    private static boolean warned;

    private LuckPermsBridge() {}

    public static boolean present() {
        return PRESENT;
    }

    private static void warn(Throwable t) {
        if (!warned) {
            warned = true;
            BattlePass.LOG.warn("[BattlePass] API LuckPerms indisponible : {}", t.toString());
        }
    }

    /** @return true/false si le noeud est defini (y compris via joker), null sinon ou si LuckPerms est absent. */
    public static Boolean check(ServerPlayer p, String node) {
        if (!PRESENT || p == null) return null;
        try {
            return LuckPermsHook.check(p, node);
        } catch (Throwable t) {
            warn(t);
            return null;
        }
    }

    /**
     * Noeud accorde explicitement (directement ou par un groupe), sans tenir compte des jokers (* ne donne pas le premium).
     * @return null si LuckPerms est absent ou le joueur non charge.
     */
    public static Boolean exact(ServerPlayer p, String node) {
        if (!PRESENT || p == null) return null;
        try {
            return LuckPermsHook.exact(p, node);
        } catch (Throwable t) {
            warn(t);
            return null;
        }
    }

    /** Cree (si besoin) les groupes du passe : poids, nom affiche, permission premium. */
    public static void ensureGroups(String free, String premium, int weight) {
        if (!PRESENT) return;
        try {
            LuckPermsHook.ensureGroup(free, "Passe gratuit", weight, null);
            LuckPermsHook.ensureGroup(premium, "Passe premium", weight + 1, null);
        } catch (Throwable t) {
            warn(t);
        }
    }

    /** Remplace le grade du passe du joueur par {@code group}, valable jusqu'a {@code expiryMs} (null = retire les deux). */
    public static void setPassGroup(UUID uuid, String group, long expiryMs, String free, String premium) {
        if (!PRESENT || uuid == null) return;
        try {
            LuckPermsHook.setPassGroup(uuid, group, expiryMs, free, premium);
        } catch (Throwable t) {
            warn(t);
        }
    }

    /** Groupes herites (auto-test). */
    public static String describe(UUID uuid) {
        if (!PRESENT) return "luckperms absent";
        try {
            return LuckPermsHook.describe(uuid);
        } catch (Throwable t) {
            return "erreur : " + t;
        }
    }
}
