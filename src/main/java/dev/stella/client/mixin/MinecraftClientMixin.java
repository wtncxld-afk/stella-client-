package dev.stella.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Smoke test only: lets the scripted demo hold "attack" in CI, where there is no captured mouse. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @ModifyVariable(method = "handleBlockBreaking", at = @At("HEAD"), argsOnly = true)
    private boolean stella$smokeAttack(boolean breaking) {
        return breaking || dev.stella.client.StellaSmokeTest.forceAttack;
    }
}
