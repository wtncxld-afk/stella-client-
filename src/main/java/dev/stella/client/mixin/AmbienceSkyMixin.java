package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.Ambience;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.11: the sky colour is extracted into SkyRenderState each frame. */
@Mixin(SkyRendering.class)
public abstract class AmbienceSkyMixin {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void stella$skyColor(ClientWorld world, float tickDelta, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (!StellaClient.isReady()) return;
        Ambience a = ModuleManager.get(Ambience.class);
        if (a.isEnabled()) state.skyColor = a.skyColor(state.skyColor);
    }
}
