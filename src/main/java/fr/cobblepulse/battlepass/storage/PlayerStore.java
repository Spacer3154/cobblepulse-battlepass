package fr.cobblepulse.battlepass.storage;

import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.data.PlayerData;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tous les dossiers joueurs sont gardes en memoire (quelques Ko chacun) : actions du staff sur des joueurs hors ligne,
 * classement et placeholders instantanes. Les ecritures restent asynchrones ({@link DataService}).
 * Un dossier d'une saison precedente est remis a zero automatiquement a sa premiere lecture.
 */
public final class PlayerStore {
    private static final Map<String, PlayerData> ALL = new HashMap<>();
    private static final Map<String, String> BY_NAME = new HashMap<>();

    private PlayerStore() {}

    public static void load() {
        ALL.clear();
        BY_NAME.clear();
        List<PlayerData> list = DataService.blocking(Storage::loadAllPlayers);
        if (list != null) {
            for (PlayerData d : list) {
                ALL.put(d.uuid, d);
                if (d.name != null && !d.name.isEmpty()) BY_NAME.put(d.name.toLowerCase(Locale.ROOT), d.uuid);
            }
        }
        BattlePass.LOG.info("[BattlePass] {} dossiers joueurs charges", ALL.size());
    }

    /** Remet a zero un dossier d'une saison precedente (le premium etait lie a la saison). */
    private static PlayerData season(PlayerData d) {
        if (d.saison != Cfg.pass.saison) {
            d.resetProgress(false);
            d.saison = Cfg.pass.saison;
            DataService.markPlayer(d);
        }
        return d;
    }

    public static PlayerData get(UUID uuid) {
        if (uuid == null) return null;
        PlayerData d = ALL.get(uuid.toString());
        return d == null ? null : season(d);
    }

    public static PlayerData get(ServerPlayer p) {
        String id = p.getUUID().toString();
        PlayerData d = ALL.get(id);
        String name = p.getGameProfile().getName();
        if (d == null) {
            d = new PlayerData(id, name);
            d.saison = Cfg.pass.saison;
            ALL.put(id, d);
            DataService.markPlayer(d);
        } else if (!name.equals(d.name)) {
            d.name = name;
            DataService.markPlayer(d);
        }
        BY_NAME.put(name.toLowerCase(Locale.ROOT), id);
        return season(d);
    }

    public static PlayerData getOrCreate(UUID uuid, String name) {
        PlayerData d = ALL.get(uuid.toString());
        if (d == null) {
            d = new PlayerData(uuid.toString(), name);
            d.saison = Cfg.pass.saison;
            ALL.put(d.uuid, d);
            if (name != null && !name.isEmpty()) BY_NAME.put(name.toLowerCase(Locale.ROOT), d.uuid);
            DataService.markPlayer(d);
        }
        return season(d);
    }

    public static PlayerData byName(String name) {
        if (name == null) return null;
        String id = BY_NAME.get(name.toLowerCase(Locale.ROOT));
        if (id == null) {
            try {
                id = UUID.fromString(name).toString();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        PlayerData d = ALL.get(id);
        return d == null ? null : season(d);
    }

    public static void save(PlayerData d) {
        DataService.markPlayer(d);
    }

    public static Collection<PlayerData> all() {
        List<PlayerData> out = new ArrayList<>();
        for (PlayerData d : ALL.values()) out.add(season(d));
        return out;
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (PlayerData d : ALL.values()) if (d.name != null && !d.name.isEmpty()) out.add(d.name);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Classement de la saison (points decroissants). */
    public static List<PlayerData> top(int n) {
        List<PlayerData> list = new ArrayList<>();
        for (PlayerData d : all()) if (d.points > 0) list.add(d);
        list.sort(Comparator.comparingLong((PlayerData d) -> -d.points).thenComparing(d -> d.name == null ? "" : d.name.toLowerCase(Locale.ROOT)));
        return list.subList(0, Math.min(n, list.size()));
    }

    public static void clear() {
        ALL.clear();
        BY_NAME.clear();
    }
}
