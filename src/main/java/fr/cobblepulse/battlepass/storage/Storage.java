package fr.cobblepulse.battlepass.storage;

import fr.cobblepulse.battlepass.data.PlayerData;

import java.util.List;

/**
 * Couche de persistance. Trois implementations : JSON local (atomique), MySQL (HikariCP) et SQLite.
 * Les methodes sont bloquantes et ne sont appelees que depuis le thread d'E/S ({@link DataService}).
 */
public interface Storage {
    String name();

    void init() throws Exception;

    void close();

    List<PlayerData> loadAllPlayers() throws Exception;

    void savePlayer(PlayerData data) throws Exception;

    /** Etat annexe (chasses globales, bonus de points...). */
    String loadKv(String key) throws Exception;

    void saveKv(String key, String json) throws Exception;
}
