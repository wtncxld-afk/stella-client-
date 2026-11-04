package dev.stella.client.modules.anticheatoff;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;

/** Base for Anti Cheat Off modules: they only run where {@link TestEnvironment#allowed()} says so. */
public abstract class GatedModule extends Module {
    protected GatedModule(String name, String description) { super(name, description, Category.ANTI_CHEAT_OFF, false); }

    private boolean active;

    @Override
    public final void onTick() {
        if (!inGame()) { if (active) { active = false; stop(); } return; }
        if (!TestEnvironment.allowed()) {
            if (active) { active = false; stop(); }
            NotificationManager.post(getName(), "Not authorized on " + TestEnvironment.where() + " - add it in Test Environment (only if you have permission)",
                    Notification.Type.WARNING, 4000);
            setEnabled(false);
            return;
        }
        active = true;
        tick();
    }

    @Override protected final void onDisable() { if (active) { active = false; if (inGame()) stop(); } }

    /** Runs every client tick while enabled in an authorized environment. */
    protected abstract void tick();
    /** Undo any changes (fly state, speeds). */
    protected void stop() {}

    /** Movement input: x = strafe (left +), y = forward (+). */
    protected net.minecraft.util.math.Vec2f input() { return mc.player.input.getMovementInput(); }

    /** Horizontal velocity for the current input and yaw, at {@code speed} blocks/tick. */
    protected double[] horizontal(double speed) {
        var in = input();
        double f = in.y, s = in.x;
        if (f == 0 && s == 0) return new double[]{0, 0};
        double len = Math.sqrt(f * f + s * s);
        f /= len; s /= len;
        double yaw = Math.toRadians(mc.player.getYaw());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        return new double[]{(s * cos - f * sin) * speed, (f * cos + s * sin) * speed};
    }
}
