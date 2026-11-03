package dev.stella.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.11 Camera keeps its position in a field without a mapped getter. */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("pos")
    Vec3d stella$getPos();

    static Vec3d pos(Camera camera) { return ((CameraAccessor) camera).stella$getPos(); }
}
