package dev.stella.client.mixin;

import dev.stella.client.util.track.TpsTracker;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    /** TAIL: only reached on the client thread (HEAD would also run on the netty thread). */
    @Inject(method = "onWorldTimeUpdate", at = @At("TAIL"))
    private void stella$tps(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        TpsTracker.onTimeUpdate(packet.time());
    }
}
