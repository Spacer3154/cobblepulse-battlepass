package fr.cobblepulse.battlepass.mixin;

import fr.cobblepulse.battlepass.integration.BattletowerHooks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Quetes de la Tour de Combat : etage gagne et tour terminee (ignore si CobblePulse Battletower est absent). */
@Pseudo
@Mixin(targets = "fr.cobblepulse.battletower.tower.TowerManager", remap = false)
public abstract class TowerManagerMixin {
    @Inject(method = "onFloorWon", at = @At("HEAD"), remap = false, require = 0)
    private static void cpbp$floorWon(@Coerce Object outcome, @Coerce Object run, ServerPlayer player, CallbackInfo ci) {
        BattletowerHooks.floorWon(outcome, run);
    }

    @Inject(method = "complete", at = @At("HEAD"), remap = false, require = 0)
    private static void cpbp$complete(@Coerce Object run, ServerPlayer player, @Coerce Object data, CallbackInfo ci) {
        BattletowerHooks.completed(run);
    }
}
