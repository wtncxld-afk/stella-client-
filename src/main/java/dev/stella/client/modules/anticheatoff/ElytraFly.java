package dev.stella.client.modules.anticheatoff;

import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/** Elytra flight controls (singleplayer / authorized test servers only). */
public class ElytraFly extends GatedModule {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Control: fly where you look at a set speed. Boost: add speed to normal gliding",
            "Control", "Control", "Boost"));
    private final NumberSetting speed = add(new NumberSetting("Speed", "Flight speed (blocks per tick)", 1.6, 0.1, 5.0, 0.05));
    private final NumberSetting vSpeed = add(new NumberSetting("Vertical Speed", "Up (jump) / down (sneak) speed in Control mode", 0.8, 0.05, 3.0, 0.05));
    private final NumberSetting boost = add(new NumberSetting("Boost Amount", "Acceleration per tick in Boost mode", 0.05, 0.01, 0.3, 0.01)).visibleWhen(() -> mode.is("Boost"));
    private final BoolSetting hover = add(new BoolSetting("Hover When Idle", "Stop in the air when no keys are held (Control)", true));

    public ElytraFly() { super("ElytraFly", "Configurable Elytra flight"); }

    @Override
    protected void tick() {
        var p = mc.player;
        if (!p.isGliding()) return;
        if (mode.is("Boost")) {
            if (input().y > 0) {
                Vec3d look = p.getRotationVector();
                Vec3d v = p.getVelocity().add(look.multiply(boost.get()));
                double max = speed.get();
                if (v.length() > max) v = v.normalize().multiply(max);
                p.setVelocity(v);
            }
            return;
        }
        Vec3d v;
        if (input().y > 0) v = p.getRotationVector().multiply(speed.get());
        else {
            double[] h = horizontal(speed.get());
            v = new Vec3d(h[0], 0, h[1]);
            if (h[0] == 0 && h[1] == 0 && !hover.isOn()) v = p.getVelocity();
        }
        double y = v.y;
        if (mc.options.jumpKey.isPressed()) y = vSpeed.get();
        else if (mc.options.sneakKey.isPressed()) y = -vSpeed.get();
        else if (input().y <= 0 && hover.isOn()) y = 0;
        p.setVelocity(v.x, y, v.z);
    }
}
