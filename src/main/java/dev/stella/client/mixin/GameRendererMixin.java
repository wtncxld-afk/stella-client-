package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.CustomFov;
import dev.stella.client.modules.visuals.Zoom;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** No first-person hand / held item while the camera is detached by Freecam. */
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void stella$noHandInFreecam(CallbackInfo ci) {
        if (StellaClient.isReady() && ModuleManager.get(dev.stella.client.modules.misc.Freecam.class).active()) ci.cancel();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void stella$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (!StellaClient.isReady() || !changingFov) return;
        float fov = cir.getReturnValueF();
        CustomFov custom = ModuleManager.get(CustomFov.class);
        if (custom.isEnabled()) fov = custom.apply(fov);
        Zoom zoom = ModuleManager.get(Zoom.class);
        if (zoom.isEnabled()) fov = zoom.apply(fov);
        cir.setReturnValue(fov);
    }
}
