package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.CustomSky;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    /** Custom Sky replaces the vanilla sky pass; the dome is drawn after opaque terrain. */
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void stella$sky(CallbackInfo ci) {
        if (StellaClient.isReady() && ModuleManager.get(CustomSky.class).active()) ci.cancel();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void stella$clouds(CallbackInfo ci) {
        if (StellaClient.isReady() && ModuleManager.get(CustomSky.class).hideClouds()) ci.cancel();
    }
}
