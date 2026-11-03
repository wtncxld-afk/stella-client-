package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.CustomCrosshair;
import dev.stella.client.modules.visuals.MotionBlur;
import dev.stella.client.modules.visuals.NoPumpkinOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void stella$motionBlur(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!StellaClient.isReady()) return;
        MotionBlur mb = ModuleManager.get(MotionBlur.class);
        if (mb.isEnabled()) {
            try { mb.apply(context); } catch (Throwable t) { StellaClient.reportError("Motion Blur", t); }
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void stella$crosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!StellaClient.isReady()) return;
        CustomCrosshair cc = ModuleManager.get(CustomCrosshair.class);
        if (cc.shouldReplace()) {
            ci.cancel();
            cc.render(context, context.getScaledWindowWidth() / 2f, context.getScaledWindowHeight() / 2f);
        }
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void stella$vignette(CallbackInfo ci) {
        if (StellaClient.isReady()) { var n = ModuleManager.get(NoPumpkinOverlay.class); if (n.hide(n.vignette)) ci.cancel(); }
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void stella$spyglass(CallbackInfo ci) {
        if (StellaClient.isReady()) { var n = ModuleManager.get(NoPumpkinOverlay.class); if (n.hide(n.spyglass)) ci.cancel(); }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void stella$portal(CallbackInfo ci) {
        if (StellaClient.isReady()) { var n = ModuleManager.get(NoPumpkinOverlay.class); if (n.hide(n.portal)) ci.cancel(); }
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void stella$nausea(CallbackInfo ci) {
        if (StellaClient.isReady()) { var n = ModuleManager.get(NoPumpkinOverlay.class); if (n.hide(n.nausea)) ci.cancel(); }
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void stella$overlay(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        if (StellaClient.isReady() && ModuleManager.get(NoPumpkinOverlay.class).shouldHide(texture)) ci.cancel();
    }
}
