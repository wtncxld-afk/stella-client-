package dev.stella.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stella.client.modules.cosmetics.KnifeSkins;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Knife Skins: draws the knife model instead of the sword in first person (visual only). */
@Mixin(HeldItemRenderer.class)
public abstract class KnifeRenderMixin {
    @WrapOperation(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void stella$knife(HeldItemRenderer self, LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, MatrixStack matrices,
                              OrderedRenderCommandQueue queue, int light, Operation<Void> original) {
        KnifeSkins k = ctx.isFirstPerson() ? KnifeSkins.active(stack) : null;
        if (k == null) { original.call(self, entity, stack, ctx, matrices, queue, light); return; }
        try {
            k.renderFirstPerson(matrices, queue, ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND, light, stack);
        } catch (Throwable t) {
            dev.stella.client.StellaClient.reportError("Knife Skins", t);
            original.call(self, entity, stack, ctx, matrices, queue, light);
        }
    }
}
