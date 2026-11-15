package fr.cobblepulse.battlepass.reward;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.core.ItemBuilder;
import fr.cobblepulse.battlepass.core.Json;
import fr.cobblepulse.battlepass.core.TextUtil;
import fr.cobblepulse.battlepass.data.MonSpec;
import fr.cobblepulse.battlepass.data.PlayerData;
import fr.cobblepulse.battlepass.data.Reward;
import fr.cobblepulse.battlepass.integration.BattletowerBridge;
import fr.cobblepulse.battlepass.integration.JobsBridge;
import fr.cobblepulse.battlepass.season.Season;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Distribution et affichage des recompenses (objets, Pokemon, commandes, BP Battletower, coins Jobs+, points). */
public final class RewardService {
    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();
    private static int cacheRevision = -1;

    private RewardService() {}

    public static HolderLookup.Provider regs() {
        MinecraftServer s = BattlePass.server();
        return s == null ? null : s.registryAccess();
    }

    // ------------------------------------------------------------------ Objets (JSON de Minecraft)

    /** Objet decrit en JSON ({"id", "count", "components"}) ; la quantite peut depasser 99 (distribuee en plusieurs piles). */
    public static ItemStack decodeItem(JsonElement json) {
        HolderLookup.Provider regs = regs();
        if (json == null || regs == null) return ItemStack.EMPTY;
        try {
            int count = 1;
            JsonElement parse = json;
            if (json.isJsonObject()) {
                JsonObject o = json.getAsJsonObject().deepCopy();
                if (o.has("count")) count = Math.max(1, o.get("count").getAsInt());
                o.addProperty("count", 1);
                parse = o;
            } else if (json.isJsonPrimitive()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", json.getAsString());
                o.addProperty("count", 1);
                parse = o;
            }
            ItemStack s = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, regs), parse).result().orElse(ItemStack.EMPTY);
            if (!s.isEmpty()) s.setCount(count);
            return s;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static JsonElement encodeItem(ItemStack stack) {
        HolderLookup.Provider regs = regs();
        if (stack == null || stack.isEmpty() || regs == null) return null;
        int count = stack.getCount();
        ItemStack one = stack.copyWithCount(1);
        JsonElement e = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, regs), one).result().orElse(null);
        if (e != null && e.isJsonObject()) e.getAsJsonObject().addProperty("count", count);
        return e;
    }

    /** Donne l'objet (piles decoupees) ; ce qui ne rentre pas dans l'inventaire tombe aux pieds du joueur. */
    public static void giveItem(ServerPlayer p, ItemStack stack) {
        int left = stack.getCount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (left > 0) {
            int n = Math.min(max, left);
            ItemStack part = stack.copyWithCount(n);
            if (!p.getInventory().add(part) || !part.isEmpty()) {
                if (!part.isEmpty()) p.drop(part, false);
            }
            left -= n;
        }
        p.containerMenu.broadcastChanges();
    }

    // ------------------------------------------------------------------ Pokemon

    public static void givePokemon(ServerPlayer p, Pokemon pk) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(p);
        if (!party.add(pk)) Cobblemon.INSTANCE.getStorage().getPC(p).add(pk);
    }

    // ------------------------------------------------------------------ Distribution

    /** Recompense prete a etre donnee (verifiee a l'avance pour ne rien donner a moitie). */
    public interface Prepared {
        void give();
    }

    /** Verifie la recompense ; renvoie null (et une erreur) si elle ne peut pas etre donnee maintenant. */
    public static Prepared prepare(ServerPlayer p, PlayerData d, Reward r, List<String> errors) {
        r.normalize();
        String who = p.getGameProfile().getName();
        switch (r.type()) {
            case "objet" -> {
                ItemStack s = decodeItem(r.objet);
                if (s.isEmpty()) {
                    errors.add("objet invalide (" + (r.objet == null ? "vide" : r.objet.toString()) + ")");
                    return null;
                }
                return () -> giveItem(p, s);
            }
            case "pokemon" -> {
                List<String> errs = new ArrayList<>();
                Pokemon pk = PokemonFactory.create(r.pokemon, errs);
                if (pk == null) {
                    errors.addAll(errs);
                    return null;
                }
                if (!errs.isEmpty()) BattlePass.LOG.warn("[BattlePass] Recompense Pokemon {} : {}", r.pokemon.species, String.join(" | ", errs));
                return () -> givePokemon(p, pk);
            }
            case "commande" -> {
                if (r.commandes.isEmpty()) {
                    errors.add("aucune commande");
                    return null;
                }
                List<String> cmds = new ArrayList<>(r.commandes);
                return () -> runCommands(p.getServer(), cmds, who, p.getUUID().toString());
            }
            case "bp_battletower" -> {
                if (!BattletowerBridge.present()) {
                    errors.add("CobblePulse Battletower n'est pas installé");
                    return null;
                }
                long n = Math.round(r.montant);
                return () -> BattletowerBridge.give(p.getUUID(), who, n);
            }
            case "coins_jobs" -> {
                if (!JobsBridge.usable(p)) {
                    errors.add("Jobs+ n'est pas installé");
                    return null;
                }
                double n = r.montant;
                return () -> JobsBridge.add(p, n);
            }
            case "points" -> {
                long n = Math.round(r.montant);
                return () -> Season.addPoints(d, p, n, "recompense", false);
            }
            default -> {
                errors.add("type inconnu " + r.type);
                return null;
            }
        }
    }

    /** Donne une liste de recompenses en tout ou rien. */
    public static boolean giveAll(ServerPlayer p, PlayerData d, List<Reward> rewards, List<String> errors) {
        List<Prepared> ready = new ArrayList<>();
        for (Reward r : rewards) {
            Prepared pr = prepare(p, d, r, errors);
            if (pr == null) return false;
            ready.add(pr);
        }
        for (Prepared pr : ready) {
            try {
                pr.give();
            } catch (Throwable t) {
                BattlePass.LOG.error("[BattlePass] Erreur en donnant une recompense a {}", p.getGameProfile().getName(), t);
            }
        }
        return true;
    }

    public static void runCommands(MinecraftServer server, List<String> commands, String player, String uuid) {
        if (server == null) return;
        for (String c : commands) {
            String cmd = c.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            cmd = cmd.replace("%player%", player).replace("%joueur%", player).replace("%uuid%", uuid);
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
            } catch (Throwable t) {
                BattlePass.LOG.warn("[BattlePass] Commande de recompense en echec '{}' : {}", cmd, t.toString());
            }
        }
    }

    // ------------------------------------------------------------------ Affichage

    private static String defaultIcon(String type) {
        return switch (type) {
            case "pokemon" -> "cobblemon:poke_ball";
            case "bp_battletower" -> "minecraft:emerald";
            case "coins_jobs" -> "minecraft:gold_ingot";
            case "points" -> "minecraft:experience_bottle";
            default -> "minecraft:paper";
        };
    }

    /** Icone (objet affiche) de la recompense : l'objet lui-meme, le modele du Pokemon, ou l'icone choisie. */
    public static ItemStack icon(Reward r) {
        if (cacheRevision != Cfg.revision) {
            ICON_CACHE.clear();
            cacheRevision = Cfg.revision;
        }
        r.normalize();
        switch (r.type()) {
            case "objet" -> {
                ItemStack s = decodeItem(r.objet);
                return s.isEmpty() ? new ItemStack(Items.BARRIER) : s;
            }
            case "pokemon" -> {
                String key = Json.COMPACT.toJson(r.pokemon);
                ItemStack cached = ICON_CACHE.get(key);
                if (cached != null) return cached.copy();
                ItemStack out;
                try {
                    Pokemon pk = PokemonFactory.create(Json.copy(r.pokemon, MonSpec.class), new ArrayList<>());
                    out = pk == null ? new ItemStack(Items.BARRIER) : PokemonItem.from(pk);
                } catch (Throwable t) {
                    out = new ItemStack(Items.BARRIER);
                }
                ICON_CACHE.put(key, out.copy());
                return out;
            }
            default -> {
                String id = r.icone.isBlank() ? defaultIcon(r.type()) : r.icone;
                return new ItemStack(ItemBuilder.item(id.contains(":") ? id : "minecraft:" + id, Items.PAPER));
            }
        }
    }

    /** Nom affiche de la recompense. */
    public static MutableComponent name(Reward r) {
        if (!r.nom.isBlank()) return TextUtil.legacy(r.nom);
        switch (r.type()) {
            case "objet" -> {
                ItemStack s = decodeItem(r.objet);
                if (s.isEmpty()) return Component.literal("Objet invalide").withStyle(ChatFormatting.RED);
                MutableComponent c = Component.empty().append(s.getHoverName());
                return s.getCount() > 1 ? c.append(Component.literal(" x" + s.getCount())) : c;
            }
            case "pokemon" -> {
                MonSpec m = r.pokemon;
                MutableComponent c = Component.empty();
                var sp = SpeciesLookup.resolve(m.species);
                c.append(sp == null ? Component.literal(m.species) : sp.getTranslatedName());
                c.append(Component.literal(" Nv. " + m.level));
                if (m.shiny) c.append(Component.literal(" ★").withStyle(ChatFormatting.GOLD));
                return c;
            }
            case "bp_battletower" -> {
                return Component.literal(fmt(r.montant) + " BP (Tour de Combat)").withStyle(ChatFormatting.GREEN);
            }
            case "coins_jobs" -> {
                return Component.literal(fmt(r.montant) + " coins Jobs+").withStyle(ChatFormatting.GOLD);
            }
            case "points" -> {
                return Component.literal(fmt(r.montant) + " points de passe").withStyle(ChatFormatting.AQUA);
            }
            default -> {
                return Component.literal("Récompense spéciale");
            }
        }
    }

    public static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    /** Details (infobulle) d'une recompense. */
    public static List<MutableComponent> lore(Reward r) {
        List<MutableComponent> out = new ArrayList<>();
        switch (r.type()) {
            case "pokemon" -> {
                MonSpec m = r.pokemon;
                if (m.shiny) out.add(Component.literal("Chromatique").withStyle(ChatFormatting.GOLD));
                if (!m.form.isBlank()) out.add(Component.literal("Forme : " + m.form).withStyle(ChatFormatting.GRAY));
                if (!m.nickname.isBlank()) out.add(Component.literal("Surnom : " + m.nickname).withStyle(ChatFormatting.GRAY));
                if (!m.nature.isBlank()) out.add(Component.literal("Nature : " + Names.nature(m.nature)).withStyle(ChatFormatting.GRAY));
                if (!m.ability.isBlank()) out.add(Component.literal("Talent : " + m.ability).withStyle(ChatFormatting.GRAY));
                String g = PokemonFactory.gender(m.gender);
                if (!g.isEmpty()) out.add(Component.literal("Genre : " + Names.gender(g)).withStyle(ChatFormatting.GRAY));
                StringBuilder iv = new StringBuilder();
                boolean anyIv = false;
                for (String s : MonSpec.STATS) {
                    int v = m.iv(s);
                    if (v >= 0) anyIv = true;
                    iv.append(iv.length() == 0 ? "" : " / ").append(v < 0 ? "?" : String.valueOf(v));
                }
                if (anyIv) out.add(Component.literal("IV : " + iv).withStyle(ChatFormatting.AQUA));
                int evTotal = 0;
                StringBuilder ev = new StringBuilder();
                for (String s : MonSpec.STATS) {
                    evTotal += m.ev(s);
                    ev.append(ev.length() == 0 ? "" : " / ").append(m.ev(s));
                }
                if (evTotal > 0) out.add(Component.literal("EV : " + ev).withStyle(ChatFormatting.LIGHT_PURPLE));
                if (!m.moves.isEmpty()) out.add(Component.literal("Capacités : " + String.join(", ", m.moves)).withStyle(ChatFormatting.GRAY));
                if (!m.heldItem.isBlank()) out.add(Component.literal("Objet tenu : " + m.heldItem).withStyle(ChatFormatting.GRAY));
                if (!m.ball.isBlank()) out.add(Component.literal("Ball : " + m.ball).withStyle(ChatFormatting.GRAY));
                if (!m.teraType.isBlank()) out.add(Component.literal("Type Téra : " + m.teraType).withStyle(ChatFormatting.GRAY));
            }
            case "commande" -> out.add(Component.literal("Récompense spéciale").withStyle(ChatFormatting.GRAY));
            default -> {
            }
        }
        return out;
    }
}
