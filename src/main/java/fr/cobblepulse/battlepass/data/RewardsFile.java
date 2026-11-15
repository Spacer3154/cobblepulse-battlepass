package fr.cobblepulse.battlepass.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Recompenses de chaque palier (recompenses.json) : piste gratuite et piste premium. */
public final class RewardsFile {
    private static final Comparator<String> NUMERIC = (a, b) -> {
        try {
            return Integer.compare(Integer.parseInt(a.trim()), Integer.parseInt(b.trim()));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    };

    public Map<String, Tier> paliers = new TreeMap<>(NUMERIC);

    public static final class Tier {
        public List<Reward> gratuit = new ArrayList<>();
        public List<Reward> premium = new ArrayList<>();

        public void normalize() {
            if (gratuit == null) gratuit = new ArrayList<>();
            if (premium == null) premium = new ArrayList<>();
            gratuit.removeIf(r -> r == null);
            premium.removeIf(r -> r == null);
            gratuit.forEach(Reward::normalize);
            premium.forEach(Reward::normalize);
        }

        public boolean isEmpty() {
            return gratuit.isEmpty() && premium.isEmpty();
        }
    }

    public Tier tier(int t) {
        return paliers.get(String.valueOf(t));
    }

    public Tier tierOrCreate(int t) {
        return paliers.computeIfAbsent(String.valueOf(t), k -> new Tier());
    }

    public void normalize() {
        if (paliers == null) paliers = new TreeMap<>(NUMERIC);
        Map<String, Tier> copy = new TreeMap<>(NUMERIC);
        for (Map.Entry<String, Tier> e : paliers.entrySet()) {
            if (e.getValue() == null) continue;
            e.getValue().normalize();
            copy.put(e.getKey().trim(), e.getValue());
        }
        paliers = copy;
    }
}
