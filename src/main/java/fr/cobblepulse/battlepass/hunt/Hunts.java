package fr.cobblepulse.battlepass.hunt;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.core.Fx;
import fr.cobblepulse.battlepass.core.Json;
import fr.cobblepulse.battlepass.core.Msg;
import fr.cobblepulse.battlepass.core.Perms;
import fr.cobblepulse.battlepass.core.Scheduler;
import fr.cobblepulse.battlepass.core.TimeFmt;
import fr.cobblepulse.battlepass.data.HuntEntry;
import fr.cobblepulse.battlepass.data.HuntInstance;
import fr.cobblepulse.battlepass.data.HuntsFile;
import fr.cobblepulse.battlepass.data.MonSpec;
import fr.cobblepulse.battlepass.data.PlayerData;
import fr.cobblepulse.battlepass.data.Reward;
import fr.cobblepulse.battlepass.net.Net;
import fr.cobblepulse.battlepass.quest.Ctx;
import fr.cobblepulse.battlepass.quest.Quests;
import fr.cobblepulse.battlepass.reward.Names;
import fr.cobblepulse.battlepass.reward.PokemonFactory;
import fr.cobblepulse.battlepass.reward.RewardService;
import fr.cobblepulse.battlepass.reward.SpeciesLookup;
import fr.cobblepulse.battlepass.season.Season;
import fr.cobblepulse.battlepass.storage.DataService;
import fr.cobblepulse.battlepass.storage.PlayerStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Chasses (portage de CobbleHunts, Hysocs, LGPL-3.0) : chasses solo par difficulte (lancement, duree limitee, recharge)
 * et vagues de chasses globales communes a tous. Le joueur capture le Pokemon demande (genre, nature, IV parfaits
 * selon la difficulte) puis le remet depuis l'ecran du passe pour gagner des points de passe et un butin.
 */
public final class Hunts {
    public static final List<String> IV_STATS = MonSpec.STATS;

    /** Vague de chasses globales (cle "chasses_globales"). */
    public static final class Global {
        public long vague;
        public List<HuntInstance> chasses = new ArrayList<>();
        public long fin;
        public long rechargeFin;
        public Set<Integer> faites = new LinkedHashSet<>();
    }

    public enum State { DISABLED, LOCKED, ACTIVE, COOLDOWN, READY }

    private static Global global = new Global();
    private static final Random RANDOM = new Random();

    private Hunts() {}

    public static Global global() {
        return global;
    }

    public static HuntsFile cfg() {
        return Cfg.hunts;
    }

    public static void load() {
        String json = DataService.blocking(s -> s.loadKv("chasses_globales"));
        try {
            Global g = json == null ? null : Json.GSON.fromJson(json, Global.class);
            global = g == null ? new Global() : g;
            if (global.chasses == null) global.chasses = new ArrayList<>();
            if (global.faites == null) global.faites = new LinkedHashSet<>();
            global.chasses.forEach(HuntInstance::normalize);
        } catch (Exception e) {
            global = new Global();
        }
        Scheduler.repeat(40, 20, Hunts::tick);
    }

    private static void saveGlobal() {
        DataService.markKv("chasses_globales", () -> global);
    }

    // ------------------------------------------------------------------ Boucle

    private static void tick() {
        if (!cfg().actif || !Season.running()) return;
        long now = TimeFmt.now();
        tickGlobal(now);
        MinecraftServer s = BattlePass.server();
        if (s == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            try {
                tickSolo(p, now);
            } catch (Throwable t) {
                BattlePass.LOG.warn("[BattlePass] Chasses ({}) : {}", p.getGameProfile().getName(), t.toString());
            }
        }
    }

    private static void tickGlobal(long now) {
        HuntsFile.Global g = cfg().globale;
        if (!g.actif) return;
        if (!global.chasses.isEmpty()) {
            boolean expired = now >= global.fin;
            boolean allDone = cfg().globalesVerrouilleesPourTous && global.faites.size() >= global.chasses.size();
            if (expired || allDone) {
                global.chasses = new ArrayList<>();
                global.faites = new LinkedHashSet<>();
                global.rechargeFin = now + g.recharge * 1000L;
                saveGlobal();
                refreshViewers();
            }
        } else if (now >= global.rechargeFin) {
            startGlobal(now);
        }
    }

    private static void startGlobal(long now) {
        HuntsFile.Global g = cfg().globale;
        List<HuntEntry> pool = cfg().pool("globale");
        global.chasses = new ArrayList<>();
        global.faites = new LinkedHashSet<>();
        if (pool.isEmpty()) {
            global.rechargeFin = now + Math.max(60, g.recharge) * 1000L;
            saveGlobal();
            return;
        }
        global.vague = now;
        global.fin = now + g.duree * 1000L;
        Set<String> used = new HashSet<>();
        for (int i = 0; i < g.simultanees; i++) {
            List<HuntEntry> avail = pool.stream().filter(e -> !used.contains(e.espece.toLowerCase(Locale.ROOT))).toList();
            HuntEntry e = weighted(avail.isEmpty() ? pool : avail);
            if (e == null) break;
            used.add(e.espece.toLowerCase(Locale.ROOT));
            HuntInstance h = instance(e);
            h.debut = now;
            h.fin = global.fin;
            global.chasses.add(h);
        }
        saveGlobal();
        if (!global.chasses.isEmpty()) Msg.broadcast("hunt.global_started", "count", global.chasses.size(), "duration", TimeFmt.duration(g.duree * 1000L));
        refreshViewers();
    }

    private static void tickSolo(ServerPlayer p, long now) {
        PlayerData d = PlayerStore.get(p);
        boolean changed = false;
        for (String diff : HuntsFile.SOLO) {
            HuntInstance h = d.chasses.get(diff);
            if (h != null && now >= h.fin) {
                d.chasses.remove(diff);
                d.recharges.put(diff, now + cfg().solo(diff).recharge * 1000L);
                Msg.send(p, "hunt.expired", "difficulty", Names.difficulty(diff));
                changed = true;
            } else if (h == null && cfg().acceptationAuto && state(p, d, diff) == State.READY) {
                start(p, diff, true);
                changed = true;
            }
        }
        if (changed) {
            PlayerStore.save(d);
            Net.refresh(p);
        }
    }

    private static void refreshViewers() {
        MinecraftServer s = BattlePass.server();
        if (s != null) for (ServerPlayer p : s.getPlayerList().getPlayers()) Net.refresh(p);
    }

    public static void resetGlobal() {
        global = new Global();
        saveGlobal();
    }

    // ------------------------------------------------------------------ Tirage

    private static HuntEntry weighted(List<HuntEntry> list) {
        if (list.isEmpty()) return null;
        double total = 0;
        for (HuntEntry e : list) total += Math.max(0, e.poids);
        if (total <= 0) return list.get(RANDOM.nextInt(list.size()));
        double r = RANDOM.nextDouble() * total;
        for (HuntEntry e : list) {
            r -= Math.max(0, e.poids);
            if (r <= 0) return e;
        }
        return list.get(list.size() - 1);
    }

    private static final List<String> NATURES = List.of("hardy", "lonely", "brave", "adamant", "naughty", "bold", "docile", "relaxed",
            "impish", "lax", "timid", "hasty", "serious", "jolly", "naive", "modest", "mild", "quiet", "bashful", "rash", "calm",
            "gentle", "sassy", "careful", "quirky");

    public static HuntInstance instance(HuntEntry e) {
        HuntInstance h = new HuntInstance();
        h.entry = Json.copy(e, HuntEntry.class);
        h.entry.normalize();
        String g = e.genre;
        if (g.equals("aleatoire")) g = RANDOM.nextBoolean() ? "male" : "female";
        h.genre = PokemonFactory.gender(g);
        if (h.genre.equals("genderless")) h.genre = "";
        String n = e.nature;
        if (n.equals("aleatoire")) n = NATURES.get(RANDOM.nextInt(NATURES.size()));
        h.nature = n.isEmpty() ? "" : Names.natureId(n);
        if (e.ivParfaits > 0) {
            List<String> stats = new ArrayList<>(IV_STATS);
            java.util.Collections.shuffle(stats, RANDOM);
            h.ivs = new ArrayList<>(stats.subList(0, Math.min(6, e.ivParfaits)));
        }
        // Espece sans genre (Magneti...) : pas d'exigence de genre impossible
        Species sp = SpeciesLookup.resolve(e.espece);
        if (sp != null && !h.genre.isEmpty()) {
            try {
                float ratio = sp.getMaleRatio();
                if (ratio < 0 || (h.genre.equals("male") && ratio <= 0) || (h.genre.equals("female") && ratio >= 1)) h.genre = "";
            } catch (Throwable ignored) {
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ Etat

    public static boolean allowed(ServerPlayer p, String diff) {
        if (!Perms.player(p, Perms.HUNTS)) return false;
        String perm = "globale".equals(diff) ? cfg().globale.permission : cfg().solo(diff).permission;
        return perm == null || perm.isBlank() || Perms.player(p, perm);
    }

    public static State state(ServerPlayer p, PlayerData d, String diff) {
        if (!cfg().actif || !cfg().solo(diff).actif) return State.DISABLED;
        if (p != null && !allowed(p, diff)) return State.LOCKED;
        long now = TimeFmt.now();
        HuntInstance h = d.chasses.get(diff);
        if (h != null && now < h.fin) return State.ACTIVE;
        if (d.recharges.getOrDefault(diff, 0L) > now) return State.COOLDOWN;
        return State.READY;
    }

    /** Apercu (tire une fois puis conserve jusqu'au lancement). */
    public static HuntInstance preview(PlayerData d, String diff) {
        HuntInstance h = d.apercus.get(diff);
        if (h == null) {
            HuntEntry e = weighted(cfg().pool(diff));
            if (e == null) return null;
            h = instance(e);
            d.apercus.put(diff, h);
            PlayerStore.save(d);
        }
        return h;
    }

    public static boolean start(ServerPlayer p, String diff, boolean auto) {
        PlayerData d = PlayerStore.get(p);
        if (!HuntsFile.SOLO.contains(diff)) return false;
        if (!Season.running()) {
            if (!auto) Msg.send(p, "season.not_running", "status", Msg.raw("status." + Season.status()));
            return false;
        }
        State st = state(p, d, diff);
        if (st != State.READY) {
            if (!auto) Msg.send(p, "hunt.cannot_start", "difficulty", Names.difficulty(diff), "state", Msg.raw("hunt.state." + st.name().toLowerCase(Locale.ROOT)));
            return false;
        }
        HuntInstance h = preview(d, diff);
        if (h == null) {
            if (!auto) Msg.send(p, "hunt.empty_pool", "difficulty", Names.difficulty(diff));
            return false;
        }
        long now = TimeFmt.now();
        h.debut = now;
        h.fin = now + cfg().solo(diff).duree * 1000L;
        d.chasses.put(diff, h);
        d.apercus.remove(diff);
        PlayerStore.save(d);
        Msg.send(p, "hunt.started", "difficulty", Names.difficulty(diff), "pokemon", speciesName(h), "duration",
                TimeFmt.duration(cfg().solo(diff).duree * 1000L));
        Net.refresh(p);
        return true;
    }

    public static boolean abandon(ServerPlayer p, String diff) {
        PlayerData d = PlayerStore.get(p);
        if (d.chasses.remove(diff) == null) return false;
        d.recharges.put(diff, TimeFmt.now() + cfg().solo(diff).recharge * 1000L);
        PlayerStore.save(d);
        Msg.send(p, "hunt.abandoned", "difficulty", Names.difficulty(diff));
        Net.refresh(p);
        return true;
    }

    public static String speciesName(HuntInstance h) {
        Species sp = SpeciesLookup.resolve(h.entry.espece);
        return sp == null ? h.entry.espece : sp.getTranslatedName().getString();
    }

    // ------------------------------------------------------------------ Correspondance

    /** Raisons pour lesquelles le Pokemon ne convient pas (vide = il convient). */
    public static List<String> mismatch(Pokemon pk, HuntInstance h, PlayerData d) {
        List<String> out = new ArrayList<>();
        HuntEntry e = h.entry;
        Species sp = SpeciesLookup.resolve(e.espece);
        if (sp == null) {
            if (!SpeciesLookup.norm(pk.getSpecies().getName()).equals(SpeciesLookup.norm(e.espece))) out.add("Mauvaise espèce");
        } else if (pk.getSpecies() != sp && !pk.getSpecies().getResourceIdentifier().equals(sp.getResourceIdentifier())) {
            out.add("Mauvaise espèce");
        }
        if (!e.forme.isBlank() && !e.forme.equals("*") && !pk.getForm().getName().equalsIgnoreCase(e.forme)) {
            out.add("Forme attendue : " + e.forme);
        }
        Set<String> aspects = new HashSet<>();
        for (String a : pk.getAspects()) aspects.add(a.toLowerCase(Locale.ROOT));
        for (String a : e.aspects) if (!aspects.contains(a)) out.add("Aspect manquant : " + a);
        if (!h.genre.isEmpty() && !pk.getGender().name().equalsIgnoreCase(h.genre)) out.add("Genre attendu : " + Names.gender(h.genre));
        if (!h.nature.isEmpty() && !pk.getNature().getName().getPath().equalsIgnoreCase(h.nature)) {
            out.add("Nature attendue : " + Names.nature(h.nature));
        }
        int min = cfg().ivMinimum;
        for (String st : h.ivs) {
            int iv = pk.getIvs().getOrDefault(PokemonFactory.stat(st));
            if (iv < min) out.add("IV " + Names.stat(st) + " : " + iv + " (minimum " + min + ")");
        }
        if (d != null) {
            if (d.pokemonRemis.contains(pk.getUuid().toString())) out.add("Déjà remis pour une chasse");
            if (cfg().captureApresDebut) {
                Long at = d.captures.get(pk.getUuid().toString());
                if (at == null || at < h.debut) out.add("Doit être capturé après le début de la chasse");
            }
        }
        return out;
    }

    /** Pokemon de l'equipe puis du PC (ceux qui conviennent d'abord). */
    public record Candidate(Pokemon pokemon, boolean party, List<String> problems) {}

    public static List<Candidate> candidates(ServerPlayer p, HuntInstance h) {
        PlayerData d = PlayerStore.get(p);
        List<Candidate> ok = new ArrayList<>();
        List<Candidate> near = new ArrayList<>();
        Species sp = SpeciesLookup.resolve(h.entry.espece);
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(p);
        for (Pokemon pk : party) consider(pk, true, h, d, sp, ok, near);
        try {
            PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(p);
            for (Pokemon pk : pc) consider(pk, false, h, d, sp, ok, near);
        } catch (Throwable t) {
            BattlePass.LOG.debug("[BattlePass] PC illisible : {}", t.toString());
        }
        List<Candidate> out = new ArrayList<>(ok);
        out.addAll(near.subList(0, Math.min(near.size(), 6)));
        return out;
    }

    private static void consider(Pokemon pk, boolean party, HuntInstance h, PlayerData d, Species sp, List<Candidate> ok, List<Candidate> near) {
        if (pk == null) return;
        if (sp != null && !pk.getSpecies().getResourceIdentifier().equals(sp.getResourceIdentifier())) return;
        List<String> problems = mismatch(pk, h, d);
        if (problems.isEmpty()) ok.add(new Candidate(pk, party, problems));
        else near.add(new Candidate(pk, party, problems));
    }

    public static HuntInstance hunt(PlayerData d, String diff, int index) {
        if ("globale".equals(diff)) return index >= 0 && index < global.chasses.size() ? global.chasses.get(index) : null;
        return d.chasses.get(diff);
    }

    public static boolean globalDone(PlayerData d, int index) {
        return cfg().globalesVerrouilleesPourTous ? global.faites.contains(index) : d.globalesFaites.contains(global.vague + ":" + index);
    }

    // ------------------------------------------------------------------ Remise

    public static boolean turnIn(ServerPlayer p, String diff, int index, UUID pokemonId) {
        PlayerData d = PlayerStore.get(p);
        if (!Season.running()) {
            Msg.send(p, "season.not_running", "status", Msg.raw("status." + Season.status()));
            return false;
        }
        boolean isGlobal = "globale".equals(diff);
        if (isGlobal ? !cfg().globale.actif || !allowed(p, diff) : state(p, d, diff) != State.ACTIVE) {
            Msg.send(p, "hunt.not_active");
            return false;
        }
        HuntInstance h = hunt(d, diff, index);
        if (h == null || TimeFmt.now() >= h.fin) {
            Msg.send(p, "hunt.not_active");
            return false;
        }
        if (isGlobal && globalDone(d, index)) {
            Msg.send(p, "hunt.already_done");
            return false;
        }
        Pokemon pk = null;
        boolean inParty = false;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(p);
        for (Pokemon x : party) if (x != null && x.getUuid().equals(pokemonId)) {
            pk = x;
            inParty = true;
        }
        PCStore pc = null;
        if (pk == null) {
            pc = Cobblemon.INSTANCE.getStorage().getPC(p);
            for (Pokemon x : pc) if (x != null && x.getUuid().equals(pokemonId)) pk = x;
        }
        if (pk == null) {
            Msg.send(p, "hunt.pokemon_missing");
            return false;
        }
        List<String> problems = mismatch(pk, h, d);
        if (!problems.isEmpty()) {
            Msg.send(p, "hunt.not_matching", "reasons", String.join(", ", problems));
            return false;
        }
        int partyCount = 0;
        for (Pokemon x : party) if (x != null) partyCount++;
        if (inParty && partyCount <= 1 && cfg().prendrePokemon) {
            Msg.send(p, "hunt.last_pokemon");
            return false;
        }
        if (cfg().prendrePokemon) {
            boolean removed = inParty ? party.remove(pk) : pc.remove(pk);
            if (!removed) {
                Msg.send(p, "hunt.pokemon_missing");
                return false;
            }
        }
        d.pokemonRemis.add(pk.getUuid().toString());
        complete(p, d, diff, index, h, pk);
        return true;
    }

    private static void complete(ServerPlayer p, PlayerData d, String diff, int index, HuntInstance h, Pokemon pk) {
        boolean isGlobal = "globale".equals(diff);
        int points = isGlobal ? cfg().globale.points : cfg().solo(diff).points;
        if (isGlobal) {
            if (cfg().globalesVerrouilleesPourTous) {
                global.faites.add(index);
                saveGlobal();
            } else {
                d.globalesFaites.add(global.vague + ":" + index);
            }
        } else {
            d.chasses.remove(diff);
            d.recharges.put(diff, TimeFmt.now() + cfg().solo(diff).recharge * 1000L);
        }
        d.chassesTerminees++;
        d.trim();
        long gained = Season.addPoints(d, p, points, "chasse", true);
        List<Reward> loot = loot(diff);
        List<String> names = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Reward r : loot) {
            RewardService.Prepared pr = RewardService.prepare(p, d, r, errors);
            if (pr != null) {
                pr.give();
                names.add(RewardService.name(r).getString());
            }
        }
        if (!errors.isEmpty()) BattlePass.LOG.warn("[BattlePass] Butin de chasse ({}) : {}", diff, String.join(" | ", errors));
        PlayerStore.save(d);
        Fx.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
        String lootText = names.isEmpty() ? "-" : String.join(", ", names);
        Msg.send(p, "hunt.completed", "difficulty", Names.difficulty(diff), "pokemon", speciesName(h), "points", gained, "loot", lootText);
        if (isGlobal) Msg.broadcast("hunt.global_completed", "player", p.getGameProfile().getName(), "pokemon", speciesName(h));
        Quests.fire(p, "chasse", new Ctx().difficulte(diff).pokemon(pk), 1);
        Net.refresh(p);
        if (isGlobal && cfg().globalesVerrouilleesPourTous) refreshViewers();
    }

    private static List<Reward> loot(String diff) {
        List<Reward> pool = cfg().loot(diff);
        List<Reward> out = new ArrayList<>();
        if (pool.isEmpty()) return out;
        if ("pourcentage".equalsIgnoreCase(cfg().modeButin)) {
            for (Reward r : pool) if (RANDOM.nextDouble() < r.chance) out.add(r);
            return out;
        }
        double total = 0;
        for (Reward r : pool) total += Math.max(0, r.chance);
        if (total <= 0) {
            out.add(pool.get(RANDOM.nextInt(pool.size())));
            return out;
        }
        double x = RANDOM.nextDouble() * total;
        for (Reward r : pool) {
            x -= Math.max(0, r.chance);
            if (x <= 0) {
                out.add(r);
                return out;
            }
        }
        out.add(pool.get(pool.size() - 1));
        return out;
    }

    // ------------------------------------------------------------------ Capture

    /** Note l'heure de capture et, si active, remet automatiquement le Pokemon a une chasse correspondante. */
    public static void onCapture(ServerPlayer p, Pokemon pk) {
        PlayerData d = PlayerStore.get(p);
        d.captures.put(pk.getUuid().toString(), TimeFmt.now());
        d.trim();
        PlayerStore.save(d);
        if (!cfg().actif || !Season.running()) return;
        int matches = 0;
        String firstDiff = null;
        int firstIndex = -1;
        for (String diff : HuntsFile.SOLO) {
            HuntInstance h = d.chasses.get(diff);
            if (h != null && state(p, d, diff) == State.ACTIVE && mismatch(pk, h, d).isEmpty()) {
                matches++;
                if (firstDiff == null) firstDiff = diff;
            }
        }
        if (cfg().globale.actif && allowed(p, "globale")) {
            for (int i = 0; i < global.chasses.size(); i++) {
                HuntInstance h = global.chasses.get(i);
                if (!globalDone(d, i) && TimeFmt.now() < h.fin && mismatch(pk, h, d).isEmpty()) {
                    matches++;
                    if (firstDiff == null) {
                        firstDiff = "globale";
                        firstIndex = i;
                    }
                }
            }
        }
        if (matches == 0) return;
        if (cfg().remiseAutoCapture) {
            String diff = firstDiff;
            int idx = firstIndex;
            UUID id = pk.getUuid();
            // Au tick suivant : le Pokemon vient d'arriver dans l'equipe ou le PC
            Scheduler.later(2, () -> turnIn(p, diff, idx, id));
        } else {
            Msg.send(p, "hunt.capture_matches", "pokemon", pk.getSpecies().getTranslatedName().getString(), "count", matches);
        }
    }

    /** Remet a zero les recharges d'un joueur (staff). */
    public static void resetCooldowns(PlayerData d) {
        d.recharges.clear();
        PlayerStore.save(d);
    }

    public static long statIv(Pokemon pk, String st) {
        return pk.getIvs().getOrDefault(PokemonFactory.stat(st));
    }
}
