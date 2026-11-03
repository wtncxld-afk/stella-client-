package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.misc.FreeLook;
import dev.stella.client.modules.misc.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freecam and Free Look override the camera after vanilla positions it. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void moveBy(float f, float g, float h);
    @Shadow protected abstract float clipToSpace(float desiredCameraDistance);

    @Inject(method = "update", at = @At("RETURN"))
    private void stella$override(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!StellaClient.isReady()) return;
        Freecam fc = ModuleManager.get(Freecam.class);
        if (fc.active()) {
            setPos(fc.lerpedPos(tickDelta));
            setRotation(fc.yaw(), fc.pitch());
            return;
        }
        FreeLook fl = ModuleManager.get(FreeLook.class);
        if (fl.active() && focusedEntity != null) {
            setRotation(fl.yaw(), fl.pitch());
            if (fl.thirdPerson()) {
                setPos(focusedEntity.getCameraPosVec(tickDelta));
                moveBy(-clipToSpace(fl.distance()), 0f, 0f);
            }
        }
    }

    /** Render your own player while the camera is detached. */
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void stella$thirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (StellaClient.isReady() && ModuleManager.get(Freecam.class).active() && ModuleManager.get(Freecam.class).showPlayer()) {
            cir.setReturnValue(true);
        }
    }
}
