package dev.stella.client.mixin;

import dev.stella.client.modules.visuals.DamageTint;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin {
    @Shadow @Final private NativeImageBackedTexture texture;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void stella$capture(CallbackInfo ci) {
        DamageTint.setOverlay(this.texture);
    }
}
