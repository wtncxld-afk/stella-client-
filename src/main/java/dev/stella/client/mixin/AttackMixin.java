package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.util.CombatEvents;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells Target HUD, Hit Effects and Combat Timer when you hit something. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class AttackMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void stella$attack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (StellaClient.isReady()) CombatEvents.fireAttack(target);
    }
}
