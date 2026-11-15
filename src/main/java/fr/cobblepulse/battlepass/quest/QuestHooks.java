package fr.cobblepulse.battlepass.quest;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.trade.PlayerTradeParticipant;
import com.cobblemon.mod.common.trade.TradeParticipant;
import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.core.Players;
import fr.cobblepulse.battlepass.hunt.Hunts;
import kotlin.Unit;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Branchements des evenements Cobblemon, Fabric et vanilla (via mixins) sur le moteur de quetes. */
public final class QuestHooks {
    private QuestHooks() {}

    private static void safe(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            BattlePass.LOG.warn("[BattlePass] Evenement {} : {}", what, t.toString());
        }
    }

    public static void register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, e -> {
            safe("capture", () -> {
                ServerPlayer p = e.getPlayer();
                Pokemon pk = e.getPokemon();
                String ball = null;
                try {
                    ball = e.getPokeBallEntity().getPokeBall().getName().toString();
                } catch (Throwable ignored) {
                }
                Ctx c = new Ctx().pokemon(pk).ball(ball).at(p.serverLevel(), p.blockPosition());
                Quests.fire(p, "capture", c, 1);
                Hunts.onCapture(p, pk);
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL, e -> {
            safe("ko", () -> {
                BattlePokemon killed = e.getKilled();
                if (killed == null || killed.actor == null || killed.actor.getType() != ActorType.WILD) return;
                PokemonBattle b = e.getBattle();
                Pokemon pk = killed.getOriginalPokemon();
                for (BattleActor a : b.getActors()) {
                    if (!(a instanceof PlayerBattleActor)) continue;
                    for (UUID id : a.getPlayerUUIDs()) {
                        ServerPlayer p = Players.get(id);
                        if (p != null) Quests.fire(p, "vaincre_sauvage", new Ctx().pokemon(pk).at(p.serverLevel(), p.blockPosition()), 1);
                    }
                }
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, e -> {
            safe("victoire", () -> {
                if (e.getWasWildCapture()) return;
                PokemonBattle b = e.getBattle();
                String kind = b.isPvP() ? "joueur" : b.isPvN() ? "dresseur" : b.isPvW() ? "sauvage" : "";
                for (BattleActor a : e.getWinners()) {
                    if (!(a instanceof PlayerBattleActor)) continue;
                    for (UUID id : a.getPlayerUUIDs()) {
                        ServerPlayer p = Players.get(id);
                        if (p != null) Quests.fire(p, "victoire_combat", new Ctx().combat(kind), 1);
                    }
                }
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL, e -> {
            safe("evolution", () -> {
                Pokemon pk = e.getPokemon();
                ServerPlayer p = pk.getOwnerPlayer();
                if (p != null) Quests.fire(p, "evolution", new Ctx().pokemon(pk), 1);
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.HATCH_EGG_POST.subscribe(Priority.NORMAL, e -> {
            safe("eclosion", () -> Quests.fire(e.getPlayer(), "eclosion", new Ctx().pokemon(e.getPokemon()), 1));
            return Unit.INSTANCE;
        });
        CobblemonEvents.TRADE_EVENT_POST.subscribe(Priority.NORMAL, e -> {
            safe("echange", () -> {
                trade(e.getTradeParticipant1(), e.getTradeParticipant2Pokemon());
                trade(e.getTradeParticipant2(), e.getTradeParticipant1Pokemon());
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.NORMAL, e -> {
            safe("niveau", () -> {
                Pokemon pk = e.getPokemon();
                ServerPlayer p = pk.getOwnerPlayer();
                int gained = e.getNewLevel() - e.getOldLevel();
                if (p != null && gained > 0) Quests.fire(p, "niveau", new Ctx().pokemon(pk), gained);
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.BOBBER_SPAWN_POKEMON_POST.subscribe(Priority.NORMAL, e -> {
            safe("peche_pokemon", () -> {
                Player owner = e.getBobber().getPlayerOwner();
                if (owner instanceof ServerPlayer p) {
                    Pokemon pk = e.getPokemon() == null ? null : e.getPokemon().getPokemon();
                    Quests.fire(p, "peche_pokemon", new Ctx().pokemon(pk).at(p.serverLevel(), p.blockPosition()), 1);
                }
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.FOSSIL_REVIVED.subscribe(Priority.NORMAL, e -> {
            safe("fossile", () -> {
                if (e.getPlayer() != null) Quests.fire(e.getPlayer(), "fossile", new Ctx().pokemon(e.getPokemon()), 1);
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.BERRY_HARVEST.subscribe(Priority.NORMAL, e -> {
            safe("baies", () -> {
                ServerPlayer p = e.getPlayer();
                Item berry = e.getBerry().item();
                Quests.fire(p, "baies", new Ctx().item(new ItemStack(berry)), 1);
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.APRICORN_HARVESTED.subscribe(Priority.NORMAL, e -> {
            safe("noigrumes", () -> Quests.fire(e.getPlayer(), "noigrumes", new Ctx().item(new ItemStack(e.getApricorn().item())), 1));
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEDEX_DATA_CHANGED_PRE.subscribe(Priority.LOWEST, e -> {
            safe("pokedex", () -> {
                if (e.isCanceled() || e.getKnowledge() != PokedexEntryProgress.OWNED) return;
                Pokemon pk = (Pokemon) call(e.getDataSource(), "getPokemon");
                ResourceLocation species = pk != null ? pk.getSpecies().getResourceIdentifier()
                        : (ResourceLocation) call(call(e.getRecord(), "getSpeciesDexRecord"), "getId");
                if (species == null) return;
                if (e.getPokedexManager().getKnowledgeForSpecies(species) == PokedexEntryProgress.OWNED) return;
                ServerPlayer p = Players.get(e.getPlayerUUID());
                if (p != null) Quests.fire(p, "pokedex", new Ctx().pokemon(pk), 1);
            });
            return Unit.INSTANCE;
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) return;
            safe("bloc", () -> onBlockBroken(p, sl, pos, state));
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer p && !(entity instanceof Player)) {
                safe("tuer", () -> Quests.fire(p, "tuer", new Ctx().entity(entity.getType()).at(p.serverLevel(), entity.blockPosition()), 1));
            }
        });
    }

    /** Appel sans argument par reflexion (API dont la forme varie selon les versions de Cobblemon). */
    private static Object call(Object o, String method) {
        if (o == null) return null;
        try {
            return o.getClass().getMethod(method).invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void trade(TradeParticipant who, Pokemon received) {
        if (who instanceof PlayerTradeParticipant pp) Quests.fire(pp.getPlayer(), "echange", new Ctx().pokemon(received), 1);
    }

    // ------------------------------------------------------------------ Blocs (minage, recolte)

    private static IntegerProperty age(BlockState st) {
        for (Property<?> prop : st.getProperties()) {
            if (prop instanceof IntegerProperty ip && prop.getName().equals("age")) return ip;
        }
        return null;
    }

    /** Culture mure (propriete "age" au maximum) ou bloc recolte (melon, citrouille, canne a sucre...). */
    static boolean isHarvest(BlockState st) {
        IntegerProperty ip = age(st);
        if (ip != null) {
            int max = 0;
            for (Integer v : ip.getPossibleValues()) max = Math.max(max, v);
            return st.getValue(ip) >= max;
        }
        Block b = st.getBlock();
        return b == Blocks.MELON || b == Blocks.PUMPKIN || b == Blocks.SUGAR_CANE || b == Blocks.CACTUS || b == Blocks.BAMBOO
                || b == Blocks.KELP_PLANT || b == Blocks.BROWN_MUSHROOM || b == Blocks.RED_MUSHROOM;
    }

    private static void onBlockBroken(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        boolean placed = PlacedBlocks.consume(level, pos);
        boolean grows = age(state) != null;
        Ctx c = new Ctx().block(state).at(level, pos);
        if (isHarvest(state) && (grows || !placed)) Quests.fire(p, "recolte", c, 1);
        if (!placed && !grows) Quests.fire(p, "minage", c, 1);
    }

    // ------------------------------------------------------------------ Statistiques (mixin ServerPlayer.awardStat)

    private static final Set<ResourceLocation> DISTANCE = Set.of(Stats.WALK_ONE_CM, Stats.SPRINT_ONE_CM, Stats.SWIM_ONE_CM,
            Stats.WALK_ON_WATER_ONE_CM, Stats.WALK_UNDER_WATER_ONE_CM, Stats.CROUCH_ONE_CM, Stats.HORSE_ONE_CM, Stats.AVIATE_ONE_CM);

    private static <T> String statId(Stat<T> stat) {
        StatType<T> type = stat.getType();
        ResourceLocation t = BuiltInRegistries.STAT_TYPE.getKey(type);
        ResourceLocation v = type.getRegistry().getKey(stat.getValue());
        return t + "/" + v;
    }

    public static void onStat(ServerPlayer p, Stat<?> stat, int amount) {
        if (amount <= 0) return;
        try {
            if (stat.getType() == Stats.CUSTOM && stat.getValue() instanceof ResourceLocation rl) {
                if (DISTANCE.contains(rl)) {
                    if (Quests.used("distance")) Quests.fire(p, "distance", new Ctx(), amount);
                    return;
                }
                if (rl.equals(Stats.ANIMALS_BRED) && Quests.used("elevage")) Quests.fire(p, "elevage", new Ctx(), amount);
                if (rl.equals(Stats.PLAY_TIME) || rl.equals(Stats.TOTAL_WORLD_TIME) || rl.equals(Stats.TIME_SINCE_REST)
                        || rl.equals(Stats.TIME_SINCE_DEATH) || rl.equals(Stats.CROUCH_TIME)) return;
            } else if (stat.getType() == Stats.ITEM_CRAFTED && stat.getValue() instanceof Item item) {
                if (Quests.used("fabrication")) Quests.fire(p, "fabrication", new Ctx().item(new ItemStack(item)), amount);
            }
            if (Quests.used("stat")) Quests.fire(p, "stat", new Ctx().stat(statId(stat)), amount);
        } catch (Throwable t) {
            BattlePass.LOG.debug("[BattlePass] stat : {}", t.toString());
        }
    }

    // ------------------------------------------------------------------ Autres mixins

    public static void onFished(ServerPlayer p, Collection<ItemStack> items) {
        if (!Quests.used("peche")) return;
        for (ItemStack s : items) if (!s.isEmpty()) Quests.fire(p, "peche", new Ctx().item(s.copy()), s.getCount());
    }

    public static void onConsumed(ServerPlayer p, ItemStack s) {
        if (s == null || s.isEmpty() || !Quests.used("manger")) return;
        if (s.get(net.minecraft.core.component.DataComponents.FOOD) == null) return;
        Quests.fire(p, "manger", new Ctx().item(s.copy()), 1);
    }

    // ------------------------------------------------------------------ Tour de Combat (mixins sur Battletower)

    public static void towerFloor(UUID player, String name, int floor) {
        safe("tour_etage", () -> Quests.fire(player, name, "tour_etage", new Ctx().floor(floor), 1));
    }

    public static void towerComplete(UUID player, String name) {
        safe("tour_complete", () -> Quests.fire(player, name, "tour_complete", new Ctx(), 1));
    }

    public static void towerBoss(UUID player, String name) {
        safe("tour_boss", () -> Quests.fire(player, name, "tour_boss", new Ctx(), 1));
    }
}
