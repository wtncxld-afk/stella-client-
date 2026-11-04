package dev.stella.client.mixin;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Knife Skins draw your arm holding the knife. */
@Mixin(HeldItemRenderer.class)
public interface HeldItemRendererInvoker {
    @Invoker("renderArmHoldingItem")
    void stella$renderArm(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, float equipProgress, float swingProgress, Arm arm);
}
