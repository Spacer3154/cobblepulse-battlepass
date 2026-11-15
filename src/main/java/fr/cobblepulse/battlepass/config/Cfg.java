package fr.cobblepulse.battlepass.config;

import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.core.Json;
import fr.cobblepulse.battlepass.data.HuntsFile;
import fr.cobblepulse.battlepass.data.QuestsFile;
import fr.cobblepulse.battlepass.data.RewardsFile;
import fr.cobblepulse.battlepass.storage.JsonStorage;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * Fichiers de config/cobblepulse_battlepass/ :
 * storage.toml et messages.toml (texte), saison.json, recompenses.json, quetes.json et chasses.json
 * (modifiables a la main ou dans l'editeur graphique /passe admin).
 */
public final class Cfg {
    public static TomlFile storage;
    public static TomlFile messages;
    public static PassConfig pass = new PassConfig();
    public static RewardsFile rewards = new RewardsFile();
    public static QuestsFile quests = new QuestsFile();
    public static HuntsFile hunts = new HuntsFile();
    /** Incremente a chaque rechargement / modification (invalide les caches de quetes). */
    public static int revision;

    private Cfg() {}

    public static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve("cobblepulse_battlepass");
    }

    public static Path dataDir() {
        return root().resolve("data");
    }

    public static Path cobblePulseRoot() {
        return FabricLoader.getInstance().getConfigDir().resolve("cobblepulse");
    }

    public static void loadAll() {
        Path root = root();
        storage = TomlFile.load(root, "storage.toml");
        messages = TomlFile.load(root, "messages.toml");
        try {
            Files.createDirectories(dataDir().resolve("players"));
        } catch (Exception e) {
            BattlePass.LOG.warn("[BattlePass] Creation des dossiers impossible : {}", e.toString());
        }
        loadJson();
    }

    public static void loadJson() {
        pass = load("saison.json", PassConfig.class, Defaults::pass);
        pass.normalize();
        rewards = load("recompenses.json", RewardsFile.class, Defaults::rewards);
        rewards.normalize();
        quests = load("quetes.json", QuestsFile.class, Defaults::quests);
        quests.normalize();
        hunts = load("chasses.json", HuntsFile.class, Defaults::hunts);
        hunts.normalize();
        revision++;
    }

    private static <T> T load(String file, Class<T> type, Supplier<T> defaults) {
        Path f = root().resolve(file);
        try {
            if (!Files.exists(f)) {
                T def = defaults.get();
                JsonStorage.writeAtomic(f, Json.CFG.toJson(def));
                BattlePass.LOG.info("[BattlePass] {} cree avec les valeurs d'exemple", file);
                return def;
            }
            String txt = Files.readString(f, StandardCharsets.UTF_8);
            T v = Json.CFG.fromJson(txt, type);
            if (v == null) throw new IllegalStateException("fichier vide");
            return v;
        } catch (Exception e) {
            BattlePass.LOG.error("[BattlePass] {} illisible ({}). Copie de sauvegarde {}.bak et valeurs d'exemple utilisees en memoire.",
                    file, e.getMessage(), file);
            try {
                Files.copy(f, f.resolveSibling(file + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
            return defaults.get();
        }
    }

    private static void write(String file, Object value) {
        try {
            JsonStorage.writeAtomic(root().resolve(file), Json.CFG.toJson(value));
        } catch (Exception e) {
            BattlePass.LOG.error("[BattlePass] Ecriture de {} impossible", file, e);
        }
        revision++;
    }

    public static void savePass() {
        pass.normalize();
        write("saison.json", pass);
    }

    public static void saveRewards() {
        rewards.normalize();
        write("recompenses.json", rewards);
    }

    public static void saveQuests() {
        quests.normalize();
        write("quetes.json", quests);
    }

    public static void saveHunts() {
        hunts.normalize();
        write("chasses.json", hunts);
    }
}
