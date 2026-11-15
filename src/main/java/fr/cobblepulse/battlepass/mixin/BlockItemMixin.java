package fr.cobblepulse.battlepass.mixin;

import fr.cobblepulse.battlepass.quest.PlacedBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Memorise les blocs poses par les joueurs (ils ne comptent pas pour les quetes de minage). */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("RETURN"))
    private void cpbp$place(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (ctx.getPlayer() != null && ctx.getLevel() instanceof ServerLevel level && cir.getReturnValue().consumesAction()) {
            PlacedBlocks.mark(level, ctx.getClickedPos());
        }
    }
}
