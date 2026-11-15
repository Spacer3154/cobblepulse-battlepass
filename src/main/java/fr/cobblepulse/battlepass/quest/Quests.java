package fr.cobblepulse.battlepass.quest;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.core.Fx;
import fr.cobblepulse.battlepass.core.ItemBuilder;
import fr.cobblepulse.battlepass.core.Msg;
import fr.cobblepulse.battlepass.core.Players;
import fr.cobblepulse.battlepass.core.TimeFmt;
import fr.cobblepulse.battlepass.data.PlayerData;
import fr.cobblepulse.battlepass.data.QuestDef;
import fr.cobblepulse.battlepass.net.Net;
import fr.cobblepulse.battlepass.reward.Names;
import fr.cobblepulse.battlepass.reward.PokemonFactory;
import fr.cobblepulse.battlepass.reward.SpeciesLookup;
import fr.cobblepulse.battlepass.season.Season;
import fr.cobblepulse.battlepass.storage.PlayerStore;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Moteur de quetes : tirage des quetes actives (quotidiennes, hebdomadaires, saison), progression, conditions et
 * recompense en points de passe.
 */
public final class Quests {
    public static final List<String> CATEGORIES = List.of("quotidiennes", "hebdomadaires", "saison");

    /** Quete active pour un joueur, avec sa cle de progression. */
    public record Active(String category, String period, QuestDef def, String key) {
        public QuestType type() {
            return QuestType.of(def.type);
        }

        public long target() {
            QuestType t = type();
            return (long) def.objectif * (t == null ? 1 : t.scale);
        }
    }

    private record Cache(int revision, String periods, boolean premium, List<Active> list) {}

    private static final Map<String, Cache> CACHE = new HashMap<>();
    private static Set<String> interesting = new HashSet<>();
    private static int interestingRevision = -1;
    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();

    private Quests() {}

    public static void invalidate() {
        CACHE.clear();
        interestingRevision = -1;
    }

    public static void invalidate(String uuid) {
        if (uuid != null) CACHE.remove(uuid);
    }

    // ------------------------------------------------------------------ Periodes

    public static String period(String category) {
        long now = TimeFmt.now();
        return switch (category) {
            case "quotidiennes" -> Season.dayKey(now);
            case "hebdomadaires" -> Season.weekKey(now);
            default -> "s" + Cfg.pass.saison;
        };
    }

    public static long nextReset(String category) {
        return switch (category) {
            case "quotidiennes" -> Season.nextDayReset();
            case "hebdomadaires" -> Season.nextWeekReset();
            default -> Season.end();
        };
    }

    /** Prefixe des cles de progression de la periode en cours d'une categorie. */
    public static String prefix(String category) {
        return prefix(category, period(category));
    }

    private static String prefix(String category, String period) {
        return category.charAt(0) + "|" + period + "|";
    }

    public static Set<String> activePrefixes() {
        Set<String> out = new LinkedHashSet<>();
        for (String c : CATEGORIES) out.add(prefix(c, period(c)));
        return out;
    }

    // ------------------------------------------------------------------ Tirage

    private static long seed(String s) {
        return UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
    }

    private static List<QuestDef> pick(List<QuestDef> pool, int count, String seed) {
        List<QuestDef> sorted = new ArrayList<>(pool);
        sorted.sort(Comparator.comparing(q -> q.id));
        if (count >= sorted.size()) return sorted;
        Collections.shuffle(sorted, new Random(seed(seed)));
        return new ArrayList<>(sorted.subList(0, Math.max(0, count)));
    }

    /** Quetes actives du joueur (les quetes premium sont visibles par tous mais ne progressent que pour les premium). */
    public static List<Active> active(PlayerData d) {
        StringBuilder periods = new StringBuilder();
        for (String c : CATEGORIES) periods.append(period(c)).append(';');
        boolean prem = d.premium;
        Cache c = CACHE.get(d.uuid);
        if (c != null && c.revision == Cfg.revision && c.periods.equals(periods.toString()) && c.premium == prem) return c.list;
        List<Active> out = new ArrayList<>();
        boolean perPlayer = !"global".equalsIgnoreCase(Cfg.pass.selectionQuetes);
        for (String cat : CATEGORIES) {
            String per = period(cat);
            List<QuestDef> pool = Cfg.quests.pool(cat);
            List<QuestDef> chosen;
            if ("saison".equals(cat)) {
                chosen = pool;
            } else {
                int n = "quotidiennes".equals(cat) ? Cfg.pass.quetesQuotidiennes : Cfg.pass.quetesHebdomadaires;
                int np = "quotidiennes".equals(cat) ? Cfg.pass.quetesQuotidiennesPremium : Cfg.pass.quetesHebdomadairesPremium;
                String base = cat + "|" + per + "|" + Cfg.pass.saison + "|" + (perPlayer ? d.uuid : "*");
                chosen = new ArrayList<>(pick(pool.stream().filter(q -> !q.premium).toList(), n, base));
                chosen.addAll(pick(pool.stream().filter(q -> q.premium).toList(), np, base + "|premium"));
            }
            for (QuestDef q : chosen) out.add(new Active(cat, per, q, prefix(cat, per) + q.id));
        }
        CACHE.put(d.uuid, new Cache(Cfg.revision, periods.toString(), prem, out));
        if (CACHE.size() > 2000) CACHE.clear();
        d.prune(activePrefixes());
        return out;
    }

    public static Active find(PlayerData d, String key) {
        for (Active a : active(d)) if (a.key.equals(key)) return a;
        return null;
    }

    public static long progress(PlayerData d, Active a) {
        if (d.terminees.contains(a.key)) return a.target();
        return Math.min(a.target(), d.progression.getOrDefault(a.key, 0L));
    }

    private static boolean isInteresting(String type) {
        if (interestingRevision != Cfg.revision) {
            Set<String> s = new HashSet<>();
            for (String c : CATEGORIES) for (QuestDef q : Cfg.quests.pool(c)) s.add(q.type);
            interesting = s;
            interestingRevision = Cfg.revision;
        }
        return interesting.contains(type);
    }

    /** Un type de quete est-il utilise (evite le travail inutile pour les evenements frequents). */
    public static boolean used(String type) {
        return isInteresting(type);
    }

    // ------------------------------------------------------------------ Progression

    public static void fire(ServerPlayer p, String type, Ctx ctx, long amount) {
        if (p == null || amount <= 0 || !isInteresting(type) || !Season.running()) return;
        fire(PlayerStore.get(p), p, type, ctx, amount);
    }

    /** Variante par UUID (joueur eventuellement hors ligne, ex. Tour de Combat). */
    public static void fire(UUID uuid, String name, String type, Ctx ctx, long amount) {
        if (uuid == null || amount <= 0 || !isInteresting(type) || !Season.running()) return;
        ServerPlayer p = Players.get(uuid);
        PlayerData d = p != null ? PlayerStore.get(p) : PlayerStore.getOrCreate(uuid, name);
        fire(d, p, type, ctx, amount);
    }

    private static void fire(PlayerData d, ServerPlayer p, String type, Ctx ctx, long amount) {
        boolean prem = Season.isPremium(d, p);
        boolean changed = false;
        for (Active a : active(d)) {
            if (!a.def.type.equals(type)) continue;
            if (a.def.premium && !prem) continue;
            if (d.terminees.contains(a.key)) continue;
            if (!matches(a.def, ctx)) continue;
            add(d, p, a, amount);
            changed = true;
        }
        if (changed) {
            PlayerStore.save(d);
            if (p != null) Net.refresh(p);
        }
    }

    private static void add(PlayerData d, ServerPlayer p, Active a, long amount) {
        long target = a.target();
        long before = d.progression.getOrDefault(a.key, 0L);
        long cur = Math.min(target, before + amount);
        d.progression.put(a.key, cur);
        if (cur >= target) {
            complete(d, p, a);
        } else if (p != null) {
            QuestType t = a.type();
            int scale = t == null ? 1 : t.scale;
            // Pas de message a chaque centimetre parcouru : seulement quand l'unite affichee change
            if (before / scale != cur / scale) {
                Msg.actionBar(p, "quest.progress", "quest", title(a.def), "progress", cur / scale, "target", a.def.objectif);
            }
        }
    }

    private static void complete(PlayerData d, ServerPlayer p, Active a) {
        d.terminees.add(a.key);
        d.progression.put(a.key, a.target());
        d.quetesTerminees++;
        long gained = Season.addPoints(d, p, a.def.points, "quete", true);
        if (p != null) {
            Fx.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.4f);
            Msg.send(p, "quest.completed", "quest", title(a.def), "points", gained, "category", Names.category(a.category).toLowerCase(Locale.ROOT));
        }
    }

    /** Validation d'une quete par le staff (type "manuelle" ou n'importe quelle quete). */
    public static boolean validate(PlayerData d, String key) {
        Active a = find(d, key);
        if (a == null) {
            for (Active x : active(d)) if (x.def.id.equalsIgnoreCase(key)) a = x;
        }
        if (a == null || d.terminees.contains(a.key)) return false;
        complete(d, Players.get(d.uuid), a);
        PlayerStore.save(d);
        ServerPlayer p = Players.get(d.uuid);
        if (p != null) Net.refresh(p);
        return true;
    }

    /** Livraison : retire de l'inventaire les objets demandes (jusqu'a l'objectif). */
    public static int deliver(ServerPlayer p, String key) {
        PlayerData d = PlayerStore.get(p);
        Active a = find(d, key);
        if (a == null || !"livraison".equals(a.def.type) || d.terminees.contains(a.key)) return 0;
        if (!Season.running()) {
            Msg.send(p, "season.not_running", "status", Msg.raw("status." + Season.status()));
            return 0;
        }
        if (a.def.premium && !Season.isPremium(d, p)) {
            Msg.send(p, "claim.need_premium");
            return 0;
        }
        long need = a.target() - d.progression.getOrDefault(a.key, 0L);
        int taken = 0;
        for (int i = 0; i < p.getInventory().getContainerSize() && taken < need; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty() || !itemMatches(a.def.filtres.objets, s)) continue;
            int n = (int) Math.min(need - taken, s.getCount());
            s.shrink(n);
            taken += n;
        }
        if (taken <= 0) {
            Msg.send(p, "quest.deliver_none");
            return 0;
        }
        p.containerMenu.broadcastChanges();
        add(d, p, a, taken);
        Msg.send(p, "quest.delivered", "count", taken);
        PlayerStore.save(d);
        Net.refresh(p);
        return taken;
    }

    /** Temps de jeu (appele chaque minute ; un joueur immobile ne progresse pas). */
    public static void playMinute(ServerPlayer p) {
        double[] last = LAST_POS.get(p.getUUID());
        double[] now = {p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()};
        LAST_POS.put(p.getUUID(), now);
        if (last != null && Math.abs(last[0] - now[0]) < 0.01 && Math.abs(last[2] - now[2]) < 0.01 && Math.abs(last[3] - now[3]) < 0.01
                && Math.abs(last[4] - now[4]) < 0.01) return;
        fire(p, "temps_jeu", new Ctx(), 1);
    }

    public static void forget(UUID uuid) {
        LAST_POS.remove(uuid);
    }

    // ------------------------------------------------------------------ Conditions

    private static String plainId(String s) {
        if (s == null) return "";
        String v = s.trim().toLowerCase(Locale.ROOT);
        return v.contains(":") ? v.substring(v.indexOf(':') + 1) : v;
    }

    private static boolean idMatches(List<String> wanted, ResourceLocation id) {
        if (id == null) return false;
        String full = id.toString();
        String path = id.getPath();
        for (String w : wanted) {
            if (w.startsWith("#")) continue;
            if (w.equals(full) || (!w.contains(":") && w.equals(path))) return true;
        }
        return false;
    }

    public static boolean itemMatches(List<String> wanted, ItemStack s) {
        if (wanted.isEmpty()) return true;
        if (s == null || s.isEmpty()) return false;
        if (idMatches(wanted, BuiltInRegistries.ITEM.getKey(s.getItem()))) return true;
        for (String w : wanted) {
            if (!w.startsWith("#")) continue;
            ResourceLocation rl = ResourceLocation.tryParse(w.substring(1));
            if (rl != null && s.is(TagKey.create(Registries.ITEM, rl))) return true;
        }
        return false;
    }

    private static boolean blockMatches(List<String> wanted, net.minecraft.world.level.block.state.BlockState st) {
        if (wanted.isEmpty()) return true;
        if (st == null) return false;
        if (idMatches(wanted, BuiltInRegistries.BLOCK.getKey(st.getBlock()))) return true;
        for (String w : wanted) {
            if (!w.startsWith("#")) continue;
            ResourceLocation rl = ResourceLocation.tryParse(w.substring(1));
            if (rl != null && st.is(TagKey.create(Registries.BLOCK, rl))) return true;
        }
        return false;
    }

    private static boolean entityMatches(List<String> wanted, net.minecraft.world.entity.EntityType<?> type) {
        if (wanted.isEmpty()) return true;
        if (type == null) return false;
        if (idMatches(wanted, BuiltInRegistries.ENTITY_TYPE.getKey(type))) return true;
        for (String w : wanted) {
            if (!w.startsWith("#")) continue;
            ResourceLocation rl = ResourceLocation.tryParse(w.substring(1));
            if (rl != null && type.is(TagKey.create(Registries.ENTITY_TYPE, rl))) return true;
        }
        return false;
    }

    private static boolean biomeMatches(List<String> wanted, Ctx c) {
        if (wanted.isEmpty()) return true;
        if (c.level == null || c.pos == null) return false;
        Holder<Biome> b = c.level.getBiome(c.pos);
        ResourceLocation id = b.unwrapKey().map(k -> k.location()).orElse(null);
        if (idMatches(wanted, id)) return true;
        for (String w : wanted) {
            if (!w.startsWith("#")) continue;
            ResourceLocation rl = ResourceLocation.tryParse(w.substring(1));
            if (rl != null && b.is(TagKey.create(Registries.BIOME, rl))) return true;
        }
        return false;
    }

    private static boolean pokemonMatches(QuestDef.Filters f, Pokemon pk) {
        boolean needs = !f.especes.isEmpty() || !f.types.isEmpty() || !f.etiquettes.isEmpty() || !f.formes.isEmpty() || !f.natures.isEmpty()
                || !f.chromatique.isEmpty() || !f.genre.isEmpty() || f.niveauMin > 0 || f.niveauMax > 0;
        if (!needs) return true;
        if (pk == null) return false;
        if (!f.especes.isEmpty()) {
            ResourceLocation id = pk.getSpecies().getResourceIdentifier();
            String n1 = SpeciesLookup.norm(id.getPath());
            String n2 = SpeciesLookup.norm(pk.getSpecies().getName());
            boolean ok = false;
            for (String s : f.especes) {
                if (s.equals(id.toString())) ok = true;
                String k = SpeciesLookup.norm(plainId(s));
                if (k.equals(n1) || k.equals(n2)) ok = true;
            }
            if (!ok) return false;
        }
        if (!f.types.isEmpty()) {
            boolean ok = false;
            for (ElementalType t : pk.getTypes()) {
                String tn = t.getName().toLowerCase(Locale.ROOT);
                for (String w : f.types) if (Names.typeId(w).equals(tn)) ok = true;
            }
            if (!ok) return false;
        }
        if (!f.etiquettes.isEmpty()) {
            boolean ok = false;
            for (String l : f.etiquettes) if (pk.hasLabels(Names.labelId(l))) ok = true;
            if (!ok) return false;
        }
        if (!f.formes.isEmpty()) {
            boolean ok = false;
            Set<String> aspects = new HashSet<>();
            for (String a : pk.getAspects()) aspects.add(a.toLowerCase(Locale.ROOT));
            String formName = pk.getForm().getName().toLowerCase(Locale.ROOT);
            for (String w : f.formes) if (aspects.contains(w) || formName.equals(w)) ok = true;
            if (!ok) return false;
        }
        if (!f.natures.isEmpty()) {
            String n = pk.getNature().getName().getPath().toLowerCase(Locale.ROOT);
            boolean ok = false;
            for (String w : f.natures) if (Names.natureId(w).equals(n)) ok = true;
            if (!ok) return false;
        }
        if (f.chromatique.equals("oui") && !pk.getShiny()) return false;
        if (f.chromatique.equals("non") && pk.getShiny()) return false;
        if (!f.genre.isEmpty()) {
            String g = PokemonFactory.gender(f.genre);
            if (!g.isEmpty() && !pk.getGender().name().equalsIgnoreCase(g)) return false;
        }
        if (f.niveauMin > 0 && pk.getLevel() < f.niveauMin) return false;
        if (f.niveauMax > 0 && pk.getLevel() > f.niveauMax) return false;
        return true;
    }

    public static boolean matches(QuestDef q, Ctx c) {
        QuestDef.Filters f = q.filtres;
        QuestType t = QuestType.of(q.type);
        List<String> groups = t == null ? List.of() : t.filters;
        if (groups.contains("pokemon") && !pokemonMatches(f, c.pokemon)) return false;
        if (groups.contains("balls") && !f.balls.isEmpty()) {
            if (c.ball == null) return false;
            String b = c.ball.toLowerCase(Locale.ROOT);
            boolean ok = false;
            for (String w : f.balls) if (w.equals(b) || plainId(w).equals(plainId(b))) ok = true;
            if (!ok) return false;
        }
        if (groups.contains("lieu")) {
            if (!biomeMatches(f.biomes, c)) return false;
            if (!f.dimensions.isEmpty()) {
                if (c.level == null) return false;
                if (!idMatches(f.dimensions, c.level.dimension().location())) return false;
            }
        }
        if (groups.contains("moment") && !f.moment.isEmpty()) {
            if (c.level == null) return false;
            long tod = c.level.getDayTime() % 24000L;
            boolean night = tod >= 13000 && tod < 23000;
            if (f.moment.equals("nuit") != night) return false;
        }
        if (groups.contains("combat") && !f.combat.isEmpty() && !f.combat.equals(c.combat)) return false;
        if (groups.contains("blocs") && !blockMatches(f.blocs, c.block)) return false;
        if (groups.contains("objets") && !f.objets.isEmpty() && !"livraison".equals(q.type) && !itemMatches(f.objets, c.item)) return false;
        if (groups.contains("entites") && !entityMatches(f.entites, c.entity)) return false;
        if (groups.contains("difficulte") && !f.difficulte.isEmpty() && !f.difficulte.equals(c.difficulte)) return false;
        if (groups.contains("stat")) {
            if (f.stat.isEmpty() || c.stat == null || !f.stat.equals(c.stat)) return false;
        }
        if (groups.contains("etage") && f.niveauMin > 0 && c.floor < f.niveauMin) return false;
        return true;
    }

    // ------------------------------------------------------------------ Affichage

    public static String title(QuestDef q) {
        if (!q.nom.isBlank()) return q.nom;
        QuestType t = QuestType.of(q.type);
        return t == null ? q.type : t.phrase.replace("%n", String.valueOf(q.objectif));
    }

    /** Conditions lisibles (une par ligne). */
    public static List<String> conditions(QuestDef q) {
        List<String> out = new ArrayList<>();
        QuestDef.Filters f = q.filtres;
        QuestType t = QuestType.of(q.type);
        List<String> groups = t == null ? List.of() : t.filters;
        if (groups.contains("pokemon")) {
            if (!f.especes.isEmpty()) out.add("Espèces : " + speciesNames(f.especes));
            if (!f.types.isEmpty()) out.add("Types : " + String.join(", ", f.types.stream().map(Names::typeName).toList()));
            if (!f.etiquettes.isEmpty()) out.add("Catégories : " + String.join(", ", f.etiquettes));
            if (!f.formes.isEmpty()) out.add("Formes : " + String.join(", ", f.formes));
            if (!f.natures.isEmpty()) out.add("Natures : " + String.join(", ", f.natures.stream().map(Names::nature).toList()));
            if (f.chromatique.equals("oui")) out.add("Chromatique uniquement");
            if (f.chromatique.equals("non")) out.add("Non chromatique");
            if (!f.genre.isEmpty()) out.add("Genre : " + Names.gender(f.genre));
            if (f.niveauMin > 0) out.add("Niveau minimum : " + f.niveauMin);
            if (f.niveauMax > 0) out.add("Niveau maximum : " + f.niveauMax);
        }
        if (groups.contains("balls") && !f.balls.isEmpty()) out.add("Balls : " + itemNames(f.balls, "cobblemon"));
        if (groups.contains("lieu")) {
            if (!f.biomes.isEmpty()) out.add("Biomes : " + String.join(", ", f.biomes));
            if (!f.dimensions.isEmpty()) out.add("Dimensions : " + String.join(", ", f.dimensions));
        }
        if (groups.contains("moment") && !f.moment.isEmpty()) out.add(f.moment.equals("nuit") ? "De nuit" : "De jour");
        if (groups.contains("combat") && !f.combat.isEmpty()) out.add("Adversaire : " + switch (f.combat) {
            case "sauvage" -> "Pokémon sauvage";
            case "dresseur" -> "dresseur (PNJ)";
            case "joueur" -> "joueur";
            default -> f.combat;
        });
        if (groups.contains("blocs") && !f.blocs.isEmpty()) out.add("Blocs : " + String.join(", ", f.blocs));
        if (groups.contains("objets") && !f.objets.isEmpty()) out.add("Objets : " + itemNames(f.objets, "minecraft"));
        if (groups.contains("entites") && !f.entites.isEmpty()) out.add("Créatures : " + String.join(", ", f.entites));
        if (groups.contains("difficulte") && !f.difficulte.isEmpty()) out.add("Difficulté : " + Names.difficulty(f.difficulte));
        if (groups.contains("stat") && !f.stat.isEmpty()) out.add("Statistique : " + f.stat);
        if (groups.contains("etage") && f.niveauMin > 0) out.add("À partir de l'étage " + f.niveauMin);
        if (q.premium) out.add("Réservée au passe premium");
        return out;
    }

    private static String speciesNames(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String s : ids) {
            var sp = SpeciesLookup.resolve(s);
            out.add(sp == null ? s : sp.getTranslatedName().getString());
        }
        return String.join(", ", out);
    }

    private static String itemNames(List<String> ids, String ns) {
        List<String> out = new ArrayList<>();
        for (String s : ids) {
            if (s.startsWith("#")) {
                out.add(s);
                continue;
            }
            Item it = ItemBuilder.item(s.contains(":") ? s : ns + ":" + s, Items.AIR);
            out.add(it == Items.AIR ? s : new ItemStack(it).getHoverName().getString());
        }
        return String.join(", ", out);
    }

    public static ItemStack icon(QuestDef q) {
        String id = q.icone;
        if (id == null || id.isBlank()) {
            if (!q.filtres.objets.isEmpty() && !q.filtres.objets.get(0).startsWith("#")) id = q.filtres.objets.get(0);
            else if (!q.filtres.blocs.isEmpty() && !q.filtres.blocs.get(0).startsWith("#")) id = q.filtres.blocs.get(0);
            else {
                QuestType t = QuestType.of(q.type);
                id = t == null ? "minecraft:paper" : t.icon;
            }
        }
        Item it = ItemBuilder.item(id.contains(":") ? id : "minecraft:" + id, Items.AIR);
        if (it == Items.AIR) it = ItemBuilder.item("cobblemon:" + plainId(id), Items.PAPER);
        return new ItemStack(it);
    }
}
