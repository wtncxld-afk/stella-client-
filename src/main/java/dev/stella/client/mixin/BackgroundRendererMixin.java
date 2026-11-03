package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.Ambience;
import dev.stella.client.modules.visuals.CustomSky;
import dev.stella.client.modules.visuals.FogCustomizer;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true)
    private static void stella$fog(Camera camera, BackgroundRenderer.FogType fogType, Vector4f color, float viewDistance, boolean thickenFog, float tickDelta,
                                   CallbackInfoReturnable<Fog> cir) {
        if (!StellaClient.isReady()) return;
        FogCustomizer fc = ModuleManager.get(FogCustomizer.class);
        if (fc.isEnabled() && cir.getReturnValue() != null) cir.setReturnValue(fc.apply(cir.getReturnValue(), camera));
    }

    @Inject(method = "getFogColor", at = @At("RETURN"))
    private static void stella$fogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness,
                                        CallbackInfoReturnable<Vector4f> cir) {
        if (!StellaClient.isReady()) return;
        Vector4f v = cir.getReturnValue();
        if (v == null) return;
        CustomSky sky = ModuleManager.get(CustomSky.class);
        float[] sc = sky.fogColor();
        if (sc != null) v.set(sc[0], sc[1], sc[2], v.w);
        Ambience amb = ModuleManager.get(Ambience.class);
        if (amb.isEnabled() && amb.tintFog()) {
            float[] t = amb.fogColor(v.x, v.y, v.z);
            v.set(t[0], t[1], t[2], v.w);
        }
        FogCustomizer fc = ModuleManager.get(FogCustomizer.class);
        if (fc.isEnabled() && fc.customColor()) {
            float[] c = fc.color();
            v.set(c[0], c[1], c[2], v.w);
        }
    }
}
