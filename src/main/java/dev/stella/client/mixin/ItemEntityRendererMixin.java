package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.ItemPhysics;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void stella$physics(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (!StellaClient.isReady()) return;
        ItemPhysics ip = ModuleManager.get(ItemPhysics.class);
        if (!ip.isEnabled() || state.itemRenderState.isEmpty()) return;
        ci.cancel();
        boolean depth = state.itemRenderState.hasDepth();
        float angle = ip.randomRotation.isOn() ? (float) Math.toDegrees(state.uniqueOffset) : 0f;
        int count = Math.max(1, state.renderedAmount);
        matrices.push();
        matrices.translate(0f, depth ? 0f : 0.02f, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        if (!depth) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
        for (int i = 0; i < count; i++) {
            matrices.push();
            if (i > 0) {
                if (depth) matrices.translate((i % 2) * 0.06f - 0.03f, i * 0.05f, ((i / 2) % 2) * 0.06f - 0.03f);
                else matrices.translate(0f, 0f, -i * 0.035f);
            }
            state.itemRenderState.render(matrices, vcp, light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
        matrices.pop();
    }
}
