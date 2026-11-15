package fr.cobblepulse.battlepass.integration;

import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.quest.QuestHooks;

import java.util.UUID;

/** Lecture (par reflexion) des objets de Battletower recus par les mixins TowerManager / DailyBoss. */
public final class BattletowerHooks {
    /** Compteurs de l'auto-test. */
    public static int floors;
    public static int completions;
    public static int bosses;

    private BattletowerHooks() {}

    private static Object field(Object o, String name) throws Exception {
        return o.getClass().getField(name).get(o);
    }

    private static Object call(Object o, String name) throws Exception {
        return o.getClass().getMethod(name).invoke(o);
    }

    private static UUID uuid(Object run) throws Exception {
        Object u = field(run, "uuid");
        return u == null ? null : UUID.fromString(u.toString());
    }

    private static String name(Object run) {
        try {
            Object n = field(run, "name");
            return n == null ? "" : n.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static void floorWon(Object outcome, Object run) {
        try {
            UUID id = (UUID) call(outcome, "player");
            int floor = ((Number) field(run, "floor")).intValue();
            floors++;
            QuestHooks.towerFloor(id, name(run), floor);
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass] Tour de Combat (etage) : {}", t.toString());
        }
    }

    public static void completed(Object run) {
        try {
            completions++;
            QuestHooks.towerComplete(uuid(run), name(run));
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass] Tour de Combat (fin) : {}", t.toString());
        }
    }

    public static void bossResult(Object outcome, Object run) {
        try {
            if (!Boolean.TRUE.equals(call(outcome, "won"))) return;
            bosses++;
            QuestHooks.towerBoss((UUID) call(outcome, "player"), name(run));
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass] Tour de Combat (boss) : {}", t.toString());
        }
    }
}
