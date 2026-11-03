package dev.stella.client.util;

import dev.stella.client.mixin.CameraAccessor;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** Camera position (1.21.11 has no mapped getter). */
public final class CameraPos {
    private CameraPos() {}

    public static Vec3d of(Camera camera) { return ((CameraAccessor) camera).stella$getPos(); }
}
