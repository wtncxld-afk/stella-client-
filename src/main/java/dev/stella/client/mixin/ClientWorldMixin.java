package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.Ambience;
import dev.stella.client.scan.BlockScanner;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void stella$blockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        if (StellaClient.isReady()) BlockScanner.onBlockUpdate(pos.toImmutable(), state);
    }

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void stella$skyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        if (!StellaClient.isReady()) return;
        Ambience a = ModuleManager.get(Ambience.class);
        if (a.isEnabled()) cir.setReturnValue(a.skyColor(cir.getReturnValueI()));
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void stella$cloudColor(float tickDelta, CallbackInfoReturnable<Integer> cir) {
        if (!StellaClient.isReady()) return;
        Ambience a = ModuleManager.get(Ambience.class);
        if (a.isEnabled()) cir.setReturnValue(a.cloudColor(cir.getReturnValueI()));
    }
}
