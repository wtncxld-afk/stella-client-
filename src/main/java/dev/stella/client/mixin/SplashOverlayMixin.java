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

    @Inject(method = "render", at = @At("TAIL"))
    private void stella$splash(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        dev.stella.client.render.StellaFont.clearCache(); // fonts are (re)loading: cached text widths may be stale
        if (!LoadingDesign.enabled()) return;
        // stay fully opaque until Minecraft removes the overlay; fading ourselves let the red Mojang screen show through
        float alpha = 1f;
        ctx.createNewRootLayer();
        // no text here: fonts may still be loading during the first reload
        LoadingDesign.full(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), alpha, progress, null, null, false);
    }
}
