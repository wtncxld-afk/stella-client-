package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.Zoom;
import dev.stella.client.util.track.CpsTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void stella$cps(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && MinecraftClient.getInstance().currentScreen == null) CpsTracker.click(button);
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void stella$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!StellaClient.isReady() || MinecraftClient.getInstance().currentScreen != null) return;
        if (ModuleManager.get(Zoom.class).onScroll(vertical)) ci.cancel();
    }
}
