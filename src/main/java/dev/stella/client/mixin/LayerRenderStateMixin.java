package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.EnchantmentGlint;
import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemRenderState.LayerRenderState.class)
public abstract class LayerRenderStateMixin {
    @ModifyVariable(method = "setGlint", at = @At("HEAD"), argsOnly = true)
    private ItemRenderState.Glint stella$glint(ItemRenderState.Glint glint) {
        if (StellaClient.isReady() && ModuleManager.get(EnchantmentGlint.class).shouldHide()) return ItemRenderState.Glint.NONE;
        return glint;
    }
}
