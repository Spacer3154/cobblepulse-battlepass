package fr.cobblepulse.battlepass.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reserves de quetes (quetes.json) : quotidiennes, hebdomadaires (tirees au sort) et de saison (toutes actives). */
public final class QuestsFile {
    public List<QuestDef> quotidiennes = new ArrayList<>();
    public List<QuestDef> hebdomadaires = new ArrayList<>();
    public List<QuestDef> saison = new ArrayList<>();

    public List<QuestDef> pool(String category) {
        return switch (category) {
            case "quotidiennes" -> quotidiennes;
            case "hebdomadaires" -> hebdomadaires;
            default -> saison;
        };
    }

    public void normalize() {
        if (quotidiennes == null) quotidiennes = new ArrayList<>();
        if (hebdomadaires == null) hebdomadaires = new ArrayList<>();
        if (saison == null) saison = new ArrayList<>();
        for (List<QuestDef> l : List.of(quotidiennes, hebdomadaires, saison)) {
            l.removeIf(q -> q == null);
            Set<String> ids = new HashSet<>();
            int n = 1;
            for (QuestDef q : l) {
                q.normalize();
                if (q.id.isEmpty()) q.id = q.type + "_" + n;
                while (!ids.add(q.id)) q.id = q.id + "_" + (++n);
                n++;
            }
        }
    }
}
