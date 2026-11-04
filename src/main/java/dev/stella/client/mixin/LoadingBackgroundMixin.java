package dev.stella.client.mixin;

import dev.stella.client.gui.menu.LoadingDesign;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Connecting / saving / progress screens keep their own text and buttons (so Cancel still works);
 * only their background is replaced by the Stella cosmic background with the animated star.
 */
@Mixin(Screen.class)
public abstract class LoadingBackgroundMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void stella$loadingBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ConnectScreen || self instanceof MessageScreen || self instanceof ProgressScreen)) return;
        if (!LoadingDesign.enabled()) return;
        LoadingDesign.background(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), 1.0);
        ci.cancel();
    }
}
