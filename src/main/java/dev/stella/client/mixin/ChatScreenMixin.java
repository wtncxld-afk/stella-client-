package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.hud.HudManager;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets interactive HUD elements (Block Notifier list) receive clicks while chat is open. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void stella$click(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (StellaClient.isReady() && HudManager.click(mouseX, mouseY, button)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void stella$scroll(double mouseX, double mouseY, double h, double v, CallbackInfoReturnable<Boolean> cir) {
        if (StellaClient.isReady() && HudManager.scroll(mouseX, mouseY, v)) cir.setReturnValue(true);
    }
}
