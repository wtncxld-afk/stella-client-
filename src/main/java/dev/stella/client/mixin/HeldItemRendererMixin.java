package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.ItemScale;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Scale: scales/offsets the held item right before vanilla draws it (inside vanilla's push/pop). */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void stella$scale(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                              ItemStack item, float equipProgress, MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (StellaClient.isReady()) ModuleManager.get(ItemScale.class).applyHeld(matrices, hand);
    }

    @org.spongepowered.asm.mixin.Unique private ItemStack stella$heldStack;

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"))
    private void stella$captureHeld(net.minecraft.entity.LivingEntity entity, ItemStack stack, net.minecraft.item.ItemDisplayContext ctx, MatrixStack matrices,
                                    net.minecraft.client.render.command.OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        stella$heldStack = stack;
    }

    /** Glow Outline "Held Items": give first-person items the same outline colour as dropped ones. */
    @org.spongepowered.asm.mixin.injection.ModifyArg(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V"),
            index = 4, require = 0)
    private int stella$heldOutline(int outline) {
        if (!StellaClient.isReady() || outline != 0) return outline;
        int c = ModuleManager.get(dev.stella.client.modules.visuals.GlowOutline.class).heldOutline(stella$heldStack);
        return c != 0 ? c : outline;
    }
}
