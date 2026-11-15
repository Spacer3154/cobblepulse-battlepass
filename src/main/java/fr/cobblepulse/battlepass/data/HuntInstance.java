package fr.cobblepulse.battlepass.data;

import java.util.ArrayList;
import java.util.List;

/** Chasse tiree au sort (en cours, en apercu ou globale). */
public final class HuntInstance {
    public HuntEntry entry = new HuntEntry();
    /** Genre exige ("" = indifferent) : male / female. */
    public String genre = "";
    /** Nature exigee ("" = indifferente). */
    public String nature = "";
    /** Statistiques dont l'IV doit atteindre le minimum (hp, atk, def, spa, spd, spe). */
    public List<String> ivs = new ArrayList<>();
    public long debut;
    public long fin;

    public void normalize() {
        if (entry == null) entry = new HuntEntry();
        entry.normalize();
        if (genre == null) genre = "";
        if (nature == null) nature = "";
        if (ivs == null) ivs = new ArrayList<>();
    }
}
