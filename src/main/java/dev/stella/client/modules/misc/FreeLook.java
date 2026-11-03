package dev.stella.client.modules.misc;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.KeySetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Hold the look key to turn the camera without turning your player's head or
 * body. Third Person orbits the camera around you; First Person just looks around.
 */
public class FreeLook extends Module {
    private final KeySetting lookKey = add(new KeySetting("Look Key", "Hold to free-look", GLFW.GLFW_KEY_LEFT_ALT));
    private final ModeSetting mode = add(new ModeSetting("Mode", "Orbit in third person or look around in first person", "Third Person", "Third Person", "First Person"));
    private final NumberSetting distance = add(new NumberSetting("Distance", "Third-person camera distance", 4, 1, 12, 0.5)).visibleWhen(() -> mode.is("Third Person"));
    private final dev.stella.client.setting.BoolSetting toggleMode = add(new dev.stella.client.setting.BoolSetting("Toggle Instead Of Hold", "Press once to start, again to stop", false));
    private final NumberSetting sensitivity = add(new NumberSetting("Sensitivity", "Mouse sensitivity multiplier", 1, 0.2, 3, 0.05));
    private final dev.stella.client.setting.BoolSetting invertPitch = add(new dev.stella.client.setting.BoolSetting("Invert Pitch", "Invert up/down while free-looking", false));
    private boolean keyWasDown;

    private boolean looking;
    private float yaw, pitch;
    private Perspective previous;

    public FreeLook() { super("Free Look", "Hold a key to look around without moving your head", Category.MISC, true); }

    public boolean active() { return isEnabled() && looking; }
    public boolean thirdPerson() { return mode.is("Third Person"); }
    public float distance() { return distance.getFloat(); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    @Override
    public void onTick() {
        boolean held = inGame() && mc.currentScreen == null && lookKey.isBound()
                && InputUtil.isKeyPressed(mc.getWindow().getHandle(), lookKey.key());
        if (toggleMode.isOn()) {
            if (held && !keyWasDown) { if (looking) stop(); else start(); }
            if (!inGame() && looking) stop();
        } else {
            if (held && !looking) start();
            else if (!held && looking) stop();
        }
        keyWasDown = held;
    }

    private void start() {
        looking = true;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        if (thirdPerson()) {
            previous = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    private void stop() {
        looking = false;
        if (previous != null) {
            mc.options.setPerspective(previous);
            previous = null;
        }
    }

    @Override protected void onDisable() { if (looking) stop(); }

    public void look(double cursorDeltaX, double cursorDeltaY) {
        float k = 0.15f * sensitivity.getFloat();
        yaw += (float) cursorDeltaX * k;
        pitch = MathHelper.clamp(pitch + (float) cursorDeltaY * k * (invertPitch.isOn() ? -1 : 1), -90f, 90f);
    }
}
