package dev.stella.client.mixin;

import dev.stella.client.modules.cosmetics.CustomGlint;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom Glint: serves Stella's glint texture in place of the vanilla one while enabled (vanilla's is left untouched). */
@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {
    @Inject(method = "getTexture(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/texture/AbstractTexture;", at = @At("HEAD"), cancellable = true)
    private void stella$glint(Identifier id, CallbackInfoReturnable<AbstractTexture> cir) {
        AbstractTexture t = CustomGlint.override(id);
        if (t != null) cir.setReturnValue(t);
    }
}
