package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.BlockOutline;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block Outline: hide vanilla's black outline while Stella draws its own. */
@Mixin(WorldRenderer.class)
public abstract class BlockOutlineMixin {
    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private void stella$outline(VertexConsumerProvider.Immediate immediate, MatrixStack matrices, boolean translucent, WorldRenderState state, CallbackInfo ci) {
        if (StellaClient.isReady() && ModuleManager.get(BlockOutline.class).replacesVanilla()) ci.cancel();
    }
}
