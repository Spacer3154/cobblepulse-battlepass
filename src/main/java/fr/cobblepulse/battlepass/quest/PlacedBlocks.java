package fr.cobblepulse.battlepass.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Memorise les blocs poses par les joueurs (anti-triche des quetes de minage : poser puis casser ne compte pas).
 * En memoire seulement, limite aux 200 000 derniers blocs par dimension.
 */
public final class PlacedBlocks {
    private static final int MAX = 200_000;
    private static final Map<String, LinkedHashMap<Long, Boolean>> BY_LEVEL = new HashMap<>();

    private PlacedBlocks() {}

    private static LinkedHashMap<Long, Boolean> map(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level.dimension().location().toString(), k -> new LinkedHashMap<Long, Boolean>(1024, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                return size() > MAX;
            }
        });
    }

    public static void mark(ServerLevel level, BlockPos pos) {
        map(level).put(pos.asLong(), Boolean.TRUE);
    }

    /** @return true si le bloc avait ete pose par un joueur (et l'oublie). */
    public static boolean consume(ServerLevel level, BlockPos pos) {
        return map(level).remove(pos.asLong()) != null;
    }

    public static void clear() {
        BY_LEVEL.clear();
    }
}
