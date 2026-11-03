package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.Ambience;
import dev.stella.client.modules.visuals.CustomSky;
import dev.stella.client.modules.visuals.FogCustomizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** 1.21.11 fog: FogRenderer writes start/end distances into a uniform buffer. */
@Mixin(FogRenderer.class)
public abstract class BackgroundRendererMixin {
    @ModifyArgs(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void stella$fog(Args args) {
        if (!StellaClient.isReady()) return;
        FogCustomizer fc = ModuleManager.get(FogCustomizer.class);
        if (!fc.isEnabled()) return;
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        // 3 env start, 4 env end, 5 render-distance start, 6 render-distance end
        args.set(3, fc.scale(args.<Float>get(3), false, camera));
        args.set(4, fc.scale(args.<Float>get(4), true, camera));
        args.set(5, fc.scale(args.<Float>get(5), false, camera));
        args.set(6, fc.scale(args.<Float>get(6), true, camera));
    }

    @Inject(method = "getFogColor", at = @At("RETURN"))
    private void stella$fogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness,
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
