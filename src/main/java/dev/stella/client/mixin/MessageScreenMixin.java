package dev.stella.client.mixin;

import dev.stella.client.gui.menu.LoadingDesign;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Saving world" and other message screens draw their own background; give them the Stella one too. */
@Mixin(MessageScreen.class)
public abstract class MessageScreenMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void stella$background(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LoadingDesign.enabled()) return;
        LoadingDesign.background(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), 1.0);
        ci.cancel();
    }
}
