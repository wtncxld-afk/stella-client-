package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.GlowOutline;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGlowColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void stella$glowColor(CallbackInfoReturnable<Integer> cir) {
        if (!StellaClient.isReady() || !((Entity) (Object) this).getEntityWorld().isClient()) return;
        int c = ModuleManager.get(GlowOutline.class).color((Entity) (Object) this);
        if (c != -1) cir.setReturnValue(c);
    }
}
