package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.pvp.TotemPopCounter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem pops arrive as entity status 35, deaths as status 3. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class EntityStatusMixin {
    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void stella$status(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (!StellaClient.isReady() || (packet.getStatus() != 35 && packet.getStatus() != 3)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Entity e = packet.getEntity(mc.world);
        if (e == null) return;
        TotemPopCounter tpc = ModuleManager.get(TotemPopCounter.class);
        if (packet.getStatus() == 35) tpc.onPop(e); else tpc.onDeath(e);
    }
}
