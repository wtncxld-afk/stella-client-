package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.KeySetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.Animation;
import dev.stella.client.util.OptionOverrides;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class Zoom extends Module {
    private final KeySetting zoomKey = add(new KeySetting("Zoom Key", "Hold to zoom", GLFW.GLFW_KEY_C));
    private final NumberSetting factor = add(new NumberSetting("Zoom Factor", "Magnification", 4, 1.5, 50, 0.5).suffix("x"));
    private final BoolSetting smooth = add(new BoolSetting("Smooth", "Animate zoom in and out", true));
    private final BoolSetting scroll = add(new BoolSetting("Scroll Adjust", "Mouse wheel changes zoom while held", true));
    private final BoolSetting cinematic = add(new BoolSetting("Cinematic Camera", "Smooth camera while zoomed", false));
    private final BoolSetting reduceSensitivity = add(new BoolSetting("Reduce Sensitivity", "Scale mouse sensitivity with zoom", true));

    private final Animation anim = new Animation(1, 14);
    private boolean zooming;
    private double scrollFactor = 1;
    private boolean savedCinematic;

    public Zoom() { super("Zoom", "Hold a key to zoom", Category.VISUALS, true); }

    public boolean isZooming() { return isEnabled() && zooming; }

    @Override
    public void onTick() {
        boolean held = zoomKey.isBound() && mc.currentScreen == null && dev.stella.client.util.Keys.down(zoomKey.key());
        if (held && !zooming) start();
        else if (!held && zooming) end();
    }

    private void start() {
        zooming = true;
        scrollFactor = 1;
        if (cinematic.isOn()) { savedCinematic = mc.options.smoothCameraEnabled; mc.options.smoothCameraEnabled = true; }
        if (reduceSensitivity.isOn()) {
            double base = mc.options.getMouseSensitivity().getValue();
            OptionOverrides.set(mc.options.getMouseSensitivity(), () -> base / Math.max(1, currentFactor()) * 1.2);
        }
    }

    private void end() {
        zooming = false;
        if (cinematic.isOn()) mc.options.smoothCameraEnabled = savedCinematic;
        OptionOverrides.clear(mc.options.getMouseSensitivity());
    }

    @Override protected void onDisable() { if (zooming) end(); anim.snap(1); }

    private double currentFactor() { return Math.max(1, factor.get() * scrollFactor); }

    public float apply(float fov) {
        double target = isZooming() ? currentFactor() : 1;
        double f = smooth.isOn() ? anim.target(target).get() : target;
        if (!smooth.isOn()) anim.snap(target);
        return (float) (fov / f);
    }

    /** @return true if the scroll was consumed. */
    public boolean onScroll(double amount) {
        if (!isZooming() || !scroll.isOn()) return false;
        scrollFactor = Math.max(0.25, Math.min(10, scrollFactor * (amount > 0 ? 1.15 : 1 / 1.15)));
        return true;
    }
}
