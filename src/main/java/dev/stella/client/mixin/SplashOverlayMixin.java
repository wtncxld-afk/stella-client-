package dev.stella.client.mixin;

import dev.stella.client.gui.menu.LoadingDesign;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the Stella design over the Mojang startup / resource-reload screen. Vanilla still decides when it closes. */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;

    @Inject(method = "render", at = @At("TAIL"))
    private void stella$splash(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LoadingDesign.enabled()) return;
        float alpha = 1f;
        if (reloadCompleteTime > -1L) alpha = 1f - Math.min(1f, (System.currentTimeMillis() - reloadCompleteTime) / 1000f);
        if (alpha <= 0.01f) return;
        ctx.createNewRootLayer();
        // no text here: fonts may still be loading during the first reload
        LoadingDesign.full(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), alpha, progress, null, null, false);
    }
}
