package dev.stella.client.modules.cosmetics;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Shared helpers for client-side cosmetics. Cosmetics are drawn only on your own player and only
 * on your screen; nothing is sent to the server.
 */
public final class Cosmetics {
    private Cosmetics() {}

    /** Colour for a cosmetic: "Theme", "Rainbow" or "Custom", sampled at {@code t} (0..1 along the shape). */
    public static int color(String mode, int custom, double t) {
        return switch (mode) {
            case "Rainbow" -> ColorUtil.fromHsb((float) ((System.currentTimeMillis() % 4000L) / 4000.0 + t * 0.6) % 1f, 0.75f, 1f, 255);
            case "Custom" -> custom | 0xFF000000;
            default -> ThemeManager.accentAt(t * 0.6);
        };
    }

    /** True when your own model is visible (third person). */
    public static boolean selfVisible() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && !mc.options.getPerspective().isFirstPerson() && !mc.player.isInvisible();
    }

    /**
     * The player's body frame at tickDelta: local x = right, y = up (from the feet), z = forward.
     * Crouching lowers and tilts the torso like the vanilla model.
     */
    public record Frame(Vec3d origin, double yaw, boolean sneaking) {
        public static Frame body(PlayerEntity p, float td) {
            double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(td, p.lastBodyYaw, p.bodyYaw));
            return new Frame(p.getLerpedPos(td), yaw, p.isInSneakingPose());
        }

        /** Body-local point to world. */
        public Vec3d at(double x, double y, double z) {
            if (sneaking) {
                // torso pitches forward 0.5 rad around the neck and the whole model drops a little
                double ny = 1.5, py = y - ny, a = 0.5;
                double y2 = py * Math.cos(a) - z * Math.sin(a), z2 = -py * Math.sin(a) + z * Math.cos(a);
                y = ny + y2 - 0.25; z = z2;
            }
            double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            return origin.add(rx * x + fx * z, y, rz * x + fz * z);
        }
    }

    /** Head frame: local origin at the neck, x right, y up, z forward, following head yaw and pitch. */
    public record Head(Vec3d neck, double yaw, double pitch) {
        public static Head of(PlayerEntity p, float td) {
            Vec3d pos = p.getLerpedPos(td);
            double neckY = p.isInSneakingPose() ? 1.5 - 0.27 : 1.5;
            if (p.isSwimming() || p.isGliding()) neckY = 0.4;
            return new Head(pos.add(0, neckY, 0), Math.toRadians(p.getYaw(td)), Math.toRadians(p.getPitch(td)));
        }

        public Vec3d at(double x, double y, double z) {
            // look up/down: rotate around the local x axis (positive pitch = looking down)
            double y2 = y * Math.cos(pitch) - z * Math.sin(pitch), z2 = y * Math.sin(pitch) + z * Math.cos(pitch);
            double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            return neck.add(rx * x + fx * z2, y2, rz * x + fz * z2);
        }
    }

    public static double now() { return System.currentTimeMillis() / 1000.0; }
}
