package dev.stella.client.modules.misc;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Detaches the camera from your player. While active your player stands still
 * (movement input is blocked) and WASD / Space / Shift fly the camera; the mouse
 * turns the camera, not your player. Purely client-side: the server only ever
 * sees your player standing where you left it.
 */
public class Freecam extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Blocks per tick", 1.0, 0.1, 5, 0.1));
    private final NumberSetting sprintMultiplier = add(new NumberSetting("Sprint Multiplier", "Speed while holding sprint", 2.5, 1, 6, 0.5));
    private final BoolSetting showPlayer = add(new BoolSetting("Show Player", "Render your own player while flying", true));

    private Vec3d pos = Vec3d.ZERO, prevPos = Vec3d.ZERO;
    private float yaw, pitch;

    public Freecam() {
        super("Freecam", "Press the keybind to fly the camera away from your player", Category.MISC);
        keybind.set(GLFW.GLFW_KEY_V);
    }

    @Override
    public void resetSettings() {
        super.resetSettings();
        keybind.set(GLFW.GLFW_KEY_V);
    }

    public boolean active() { return isEnabled() && inGame(); }
    /** Only draw your own player once the camera has left your head (otherwise it fills the screen). */
    public boolean showPlayer() {
        return showPlayer.isOn() && mc.player != null && pos.squaredDistanceTo(mc.player.getEyePos()) > 2.25;
    }

    /** Moves the camera directly (used by the CI smoke test). */
    public void nudge(double dx, double dy, double dz) {
        prevPos = pos = pos.add(dx, dy, dz);
    }

    public void setLook(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }

    @Override
    protected void onEnable() {
        if (!inGame()) { setEnabled(false); return; }
        pos = mc.gameRenderer.getCamera().getPos();
        prevPos = pos;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
    }

    @Override
    public void onTick() {
        if (!inGame()) { setEnabled(false); return; }
        prevPos = pos;
        double f = 0, s = 0, v = 0;
        if (mc.options.forwardKey.isPressed()) f++;
        if (mc.options.backKey.isPressed()) f--;
        if (mc.options.leftKey.isPressed()) s++;
        if (mc.options.rightKey.isPressed()) s--;
        if (mc.options.jumpKey.isPressed()) v++;
        if (mc.options.sneakKey.isPressed()) v--;
        if (mc.currentScreen != null) { f = 0; s = 0; v = 0; }
        double sp = speed.get() * (mc.options.sprintKey.isPressed() ? sprintMultiplier.get() : 1);
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        double dx = (-sin * f + cos * s), dz = (cos * f + sin * s);
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 1e-4) { dx /= len; dz /= len; }
        pos = pos.add(dx * sp, v * sp, dz * sp);
    }

    public Vec3d lerpedPos(float tickDelta) { return prevPos.lerp(pos, tickDelta); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    /** Mouse movement turns the camera instead of the player. */
    public void look(double cursorDeltaX, double cursorDeltaY) {
        yaw += (float) cursorDeltaX * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) cursorDeltaY * 0.15f, -90f, 90f);
    }
}
