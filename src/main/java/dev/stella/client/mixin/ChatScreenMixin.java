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
    private void stella$click(net.minecraft.client.gui.Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!StellaClient.isReady()) return;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        double mx = mc.mouse.getScaledX(mc.getWindow()), my = mc.mouse.getScaledY(mc.getWindow());
        if (HudManager.click(mx, my, click.button())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void stella$scroll(double mouseX, double mouseY, double h, double v, CallbackInfoReturnable<Boolean> cir) {
        if (StellaClient.isReady() && HudManager.scroll(mouseX, mouseY, v)) cir.setReturnValue(true);
    }
}
