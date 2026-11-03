package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.misc.Freecam;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While Freecam is on, movement keys fly the camera and the player stands still. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void stella$freeze(CallbackInfo ci) {
        if (StellaClient.isReady() && ModuleManager.get(Freecam.class).active()) {
            this.movementForward = 0f;
            this.movementSideways = 0f;
            this.playerInput = PlayerInput.DEFAULT;
        }
    }
}
