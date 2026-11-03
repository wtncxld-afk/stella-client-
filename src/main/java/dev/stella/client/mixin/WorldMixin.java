package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.WeatherCustomizer;
import dev.stella.client.modules.visuals.WorldTime;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-world-only visual overrides (the integrated server's world is untouched). */
@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true)
    private void stella$time(CallbackInfoReturnable<Long> cir) {
        if (!StellaClient.isReady() || !((Object) this instanceof ClientWorld)) return;
        WorldTime wt = ModuleManager.get(WorldTime.class);
        if (wt.isEnabled()) cir.setReturnValue(wt.apply(cir.getReturnValueJ()));
    }

    @Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
    private void stella$rain(float delta, CallbackInfoReturnable<Float> cir) {
        if (!StellaClient.isReady() || !((Object) this instanceof ClientWorld)) return;
        WeatherCustomizer w = ModuleManager.get(WeatherCustomizer.class);
        if (w.isEnabled()) cir.setReturnValue(w.rain(cir.getReturnValueF()));
    }

    @Inject(method = "getThunderGradient", at = @At("RETURN"), cancellable = true)
    private void stella$thunder(float delta, CallbackInfoReturnable<Float> cir) {
        if (!StellaClient.isReady() || !((Object) this instanceof ClientWorld)) return;
        WeatherCustomizer w = ModuleManager.get(WeatherCustomizer.class);
        if (w.isEnabled()) cir.setReturnValue(w.thunder(cir.getReturnValueF()));
    }
}
