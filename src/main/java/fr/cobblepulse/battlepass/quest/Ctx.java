package fr.cobblepulse.battlepass.quest;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Contexte d'un evenement de quete (seuls les champs utiles sont renseignes). */
public final class Ctx {
    public Pokemon pokemon;
    /** Identifiant de la Poke Ball (capture). */
    public String ball;
    public ServerLevel level;
    public BlockPos pos;
    /** sauvage, dresseur ou joueur. */
    public String combat;
    public BlockState block;
    public ItemStack item;
    public EntityType<?> entity;
    /** Statistique "type/valeur" (ex. minecraft:custom/minecraft:jump). */
    public String stat;
    /** Chasse : facile, normal, moyen, difficile ou globale. */
    public String difficulte;
    /** Etage de la Tour de Combat. */
    public int floor;

    public Ctx pokemon(Pokemon p) {
        this.pokemon = p;
        return this;
    }

    public Ctx ball(String b) {
        this.ball = b;
        return this;
    }

    public Ctx at(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.pos = pos;
        return this;
    }

    public Ctx combat(String c) {
        this.combat = c;
        return this;
    }

    public Ctx block(BlockState s) {
        this.block = s;
        return this;
    }

    public Ctx item(ItemStack s) {
        this.item = s;
        return this;
    }

    public Ctx entity(EntityType<?> t) {
        this.entity = t;
        return this;
    }

    public Ctx stat(String s) {
        this.stat = s;
        return this;
    }

    public Ctx difficulte(String d) {
        this.difficulte = d;
        return this;
    }

    public Ctx floor(int f) {
        this.floor = f;
        return this;
    }
}
