package fr.cobblepulse.battlepass.core;

import fr.cobblepulse.battlepass.integration.LuckPermsBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * Permissions LuckPerms. Sans LuckPerms (ou noeud non defini) : noeuds joueurs autorises,
 * staff = operateurs niveau 2, admin = operateurs niveau 3.
 */
public final class Perms {
    public static final String USE = "cobblepulse.battlepass.use";
    public static final String HUNTS = "cobblepulse.battlepass.chasses";
    /** Accorde le premium de la saison (ex. via une boutique en ligne : lp user X permission settemp ... 30d). */
    public static final String PREMIUM = "cobblepulse.battlepass.premium";
    public static final String STAFF = "cobblepulse.battlepass.staff";
    public static final String STAFF_POINTS = "cobblepulse.battlepass.staff.points";
    public static final String STAFF_INSPECT = "cobblepulse.battlepass.staff.inspect";
    public static final String STAFF_QUESTS = "cobblepulse.battlepass.staff.quetes";
    public static final String STAFF_HUNTS = "cobblepulse.battlepass.staff.chasses";
    public static final String ADMIN = "cobblepulse.battlepass.admin";
    public static final String ADMIN_EDIT = "cobblepulse.battlepass.admin.edit";
    public static final String ADMIN_TOGGLE = "cobblepulse.battlepass.admin.toggle";
    public static final String ADMIN_SEASON = "cobblepulse.battlepass.admin.saison";
    public static final String ADMIN_RESET = "cobblepulse.battlepass.admin.reset";
    public static final String ADMIN_PREMIUM = "cobblepulse.battlepass.admin.premium";
    public static final String ADMIN_BOOST = "cobblepulse.battlepass.admin.boost";
    public static final String ADMIN_RELOAD = "cobblepulse.battlepass.admin.reload";

    private Perms() {}

    public static boolean player(ServerPlayer p, String node) {
        Boolean v = LuckPermsBridge.check(p, node);
        return v == null || v;
    }

    public static boolean staff(ServerPlayer p, String node) {
        Boolean v = LuckPermsBridge.check(p, node);
        if (v != null) return v;
        Boolean all = LuckPermsBridge.check(p, STAFF + ".*");
        if (all != null) return all;
        Boolean admin = LuckPermsBridge.check(p, ADMIN + ".*");
        return admin != null ? admin : p.hasPermissions(2);
    }

    /** Noeud admin : le noeud precis, sinon le joker admin.*, sinon operateur niveau 3. */
    public static boolean admin(ServerPlayer p, String node) {
        Boolean v = LuckPermsBridge.check(p, node);
        if (v != null) return v;
        Boolean all = LuckPermsBridge.check(p, ADMIN + ".*");
        return all != null ? all : p.hasPermissions(3);
    }

    public static Predicate<CommandSourceStack> playerReq(String node) {
        return src -> !src.isPlayer() || player(src.getPlayer(), node);
    }

    public static Predicate<CommandSourceStack> staffReq(String node) {
        return src -> !src.isPlayer() || staff(src.getPlayer(), node);
    }

    public static Predicate<CommandSourceStack> adminReq(String node) {
        return src -> !src.isPlayer() || admin(src.getPlayer(), node);
    }

    /** Peut ouvrir l'editeur ou au moins une action admin. */
    public static boolean anyAdmin(ServerPlayer p) {
        return admin(p, ADMIN_EDIT) || admin(p, ADMIN_TOGGLE) || admin(p, ADMIN_SEASON) || admin(p, ADMIN_RESET);
    }
}
