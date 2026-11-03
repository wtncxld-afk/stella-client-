package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.misc.FreeLook;
import dev.stella.client.modules.misc.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse look goes to the free camera instead of the player's head. */
@Mixin(Entity.class)
public abstract class EntityLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void stella$look(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!StellaClient.isReady() || (Object) this != MinecraftClient.getInstance().player) return;
        Freecam fc = ModuleManager.get(Freecam.class);
        if (fc.active()) { fc.look(cursorDeltaX, cursorDeltaY); ci.cancel(); return; }
        FreeLook fl = ModuleManager.get(FreeLook.class);
        if (fl.active()) { fl.look(cursorDeltaX, cursorDeltaY); ci.cancel(); }
    }
}
