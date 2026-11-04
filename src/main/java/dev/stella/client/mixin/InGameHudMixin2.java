package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.SaturationOverlay;
import dev.stella.client.modules.visuals.ScoreboardTweaks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Saturation Overlay on the food bar, and Scoreboard hide / move / scale. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin2 {
    @Inject(method = "renderFood", at = @At("TAIL"), require = 0)
    private void stella$saturation(DrawContext ctx, PlayerEntity player, int top, int right, CallbackInfo ci) {
        if (StellaClient.isReady()) ModuleManager.get(SaturationOverlay.class).draw(ctx, top, right);
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void stella$sidebarStart(DrawContext ctx, ScoreboardObjective objective, CallbackInfo ci) {
        if (!StellaClient.isReady()) return;
        ScoreboardTweaks t = ModuleManager.get(ScoreboardTweaks.class);
        if (!t.isEnabled()) return;
        if (t.hidden()) { ci.cancel(); return; }
        t.push(ctx);
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("RETURN"), require = 0)
    private void stella$sidebarEnd(DrawContext ctx, ScoreboardObjective objective, CallbackInfo ci) {
        if (!StellaClient.isReady()) return;
        ScoreboardTweaks t = ModuleManager.get(ScoreboardTweaks.class);
        if (t.isEnabled() && !t.hidden()) t.pop(ctx);
    }
}
