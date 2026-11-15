package fr.cobblepulse.battlepass.integration;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.core.TextUtil;
import fr.cobblepulse.battlepass.core.TimeFmt;
import fr.cobblepulse.battlepass.data.PlayerData;
import fr.cobblepulse.battlepass.season.Season;
import fr.cobblepulse.battlepass.storage.PlayerStore;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Function;

/** Placeholders %cobblepulse_battlepass:...% (Text Placeholder API). Ne jamais referencer si le mod est absent. */
final class PlaceholderHook {
    private PlaceholderHook() {}

    private static void reg(String id, Function<ServerPlayer, Component> fn) {
        Placeholders.register(ResourceLocation.fromNamespaceAndPath(BattlePass.MOD_ID, id), (ctx, arg) -> {
            ServerPlayer p = ctx.player();
            if (p == null) return PlaceholderResult.invalid("No player!");
            return PlaceholderResult.value(fn.apply(p));
        });
    }

    private static void global(String id, java.util.function.Supplier<Component> fn) {
        Placeholders.register(ResourceLocation.fromNamespaceAndPath(BattlePass.MOD_ID, id), (ctx, arg) -> PlaceholderResult.value(fn.get()));
    }

    /** Etoile du chat : ★ premium, ☆ passe gratuit (pendant une saison en cours), rien sinon. */
    static Component star(ServerPlayer p) {
        String st = Season.status();
        if (Cfg.pass.debut <= 0 || "terminee".equals(st)) return Component.empty();
        PlayerData d = PlayerStore.get(p);
        String fmt = Season.isPremium(d, p) ? Cfg.pass.etoiles.premium : Cfg.pass.etoiles.gratuit;
        return fmt == null || fmt.isEmpty() ? Component.empty() : TextUtil.legacy(fmt);
    }

    static void register() {
        reg("etoile", PlaceholderHook::star);
        reg("palier", p -> Component.literal(String.valueOf(Season.tier(PlayerStore.get(p)))));
        reg("points", p -> Component.literal(String.valueOf(PlayerStore.get(p).points)));
        reg("premium", p -> Component.literal(Season.isPremium(PlayerStore.get(p), p) ? "oui" : "non"));
        reg("serie", p -> Component.literal(String.valueOf(PlayerStore.get(p).serie)));
        reg("a_reclamer", p -> Component.literal(String.valueOf(Season.claimable(PlayerStore.get(p), p))));
        reg("points_prochain_palier", p -> {
            PlayerData d = PlayerStore.get(p);
            int t = Season.tier(d);
            if (t >= Cfg.pass.paliers) return Component.literal("0");
            return Component.literal(String.valueOf(Cfg.pass.totalFor(t + 1) - d.points));
        });
        global("saison", () -> Component.literal(Cfg.pass.nom));
        global("paliers", () -> Component.literal(String.valueOf(Cfg.pass.paliers)));
        global("temps_restant", () -> Component.literal(TimeFmt.duration(Math.max(0, Season.end() - TimeFmt.now()))));
        global("statut", () -> Component.literal(fr.cobblepulse.battlepass.core.Msg.raw("status." + Season.status())));
    }
}
