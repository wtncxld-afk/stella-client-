package dev.stella.client.modules.anticheatoff;

import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/** Faster swimming (singleplayer / authorized test servers only). */
public class WaterSpeed extends GatedModule {
    private final NumberSetting speed = add(new NumberSetting("Swim Speed", "Horizontal speed in water (blocks per tick)", 0.35, 0.05, 2.0, 0.01));
    private final NumberSetting vSpeed = add(new NumberSetting("Vertical Speed", "Up / down speed in water", 0.2, 0.0, 1.5, 0.01));
    private final BoolSetting lava = add(new BoolSetting("Lava Too", "Also apply in lava", false));

    public WaterSpeed() { super("WaterSpeed", "Adjustable swimming speed"); }

    @Override
    protected void tick() {
        var p = mc.player;
        if (!(p.isTouchingWater() || (lava.isOn() && p.isInLava()))) return;
        double[] h = horizontal(speed.get());
        Vec3d v = p.getVelocity();
        double y = v.y;
        if (mc.options.jumpKey.isPressed()) y = vSpeed.get();
        else if (mc.options.sneakKey.isPressed()) y = -vSpeed.get();
        if (h[0] == 0 && h[1] == 0) p.setVelocity(v.x, y, v.z);
        else p.setVelocity(h[0], y, h[1]);
    }
}
