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
    @org.spongepowered.asm.mixin.Unique private boolean stella$pushed;

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN"))
    private void stella$popScale(ItemEntityRenderState state, MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue queue, net.minecraft.client.render.state.CameraRenderState camera, CallbackInfo ci) {
        if (stella$pushed) {
            stella$pushed = false;
            matrices.pop();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void stella$physics(ItemEntityRenderState state, MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue queue, net.minecraft.client.render.state.CameraRenderState camera, CallbackInfo ci) {
        if (!StellaClient.isReady()) return;
        ItemPhysics ip = ModuleManager.get(ItemPhysics.class);
        float dropScale = ModuleManager.get(dev.stella.client.modules.visuals.ItemScale.class).droppedScale();
        if (!ip.isEnabled() || state.itemRenderState.isEmpty()) {
            if (dropScale != 1f) {
                matrices.push();
                matrices.scale(dropScale, dropScale, dropScale);
                stella$pushed = true;
            }
            return;
        }
        ci.cancel();
        var bounds = state.itemRenderState.getModelBoundingBox();
        boolean depth = bounds != null && bounds.getLengthZ() > 0.1;
        float angle = ip.randomRotation.isOn() ? (float) Math.toDegrees(state.uniqueOffset) : 0f;
        int count = Math.max(1, state.renderedAmount);
        matrices.push();
        matrices.translate(0f, depth ? 0f : 0.02f, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        float sc = ip.scale.getFloat() * dropScale;
        matrices.scale(sc, sc, sc);
        float spread = ip.stackSpread.getFloat();
        if (!depth) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
        for (int i = 0; i < count; i++) {
            matrices.push();
            if (i > 0) {
                if (depth) matrices.translate(((i % 2) * 0.06f - 0.03f) * spread, i * 0.05f, (((i / 2) % 2) * 0.06f - 0.03f) * spread);
                else matrices.translate(0.02f * spread * (i % 2 == 0 ? 1 : -1), 0.02f * spread * ((i / 2) % 2 == 0 ? 1 : -1), -i * 0.035f);
            }
            state.itemRenderState.render(matrices, queue, state.light, OverlayTexture.DEFAULT_UV, state.outlineColor);
            matrices.pop();
        }
        matrices.pop();
    }
}
