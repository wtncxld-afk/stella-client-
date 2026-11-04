package dev.stella.client.mixin;

import dev.stella.client.gui.menu.LoadingDesign;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stella world-loading screen ("Loading world" with real progress). */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
    @Shadow private float loadProgress;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void stella$loading(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LoadingDesign.enabled()) return;
        ctx.createNewRootLayer();
        int pct = Math.round(Math.max(0, Math.min(1, loadProgress)) * 100);
        LoadingDesign.full(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), 1.0, loadProgress,
                "LOADING WORLD", "Preparing terrain  " + pct + "%", true);
    }
}
