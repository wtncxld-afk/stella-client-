package dev.stella.client.modules.anticheatoff;

import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/** Configurable flight (singleplayer / authorized test servers only). */
public class Flight extends GatedModule {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Abilities: creative-style flying. Velocity: direct speed control", "Velocity", "Velocity", "Abilities"));
    private final NumberSetting hSpeed = add(new NumberSetting("Horizontal Speed", "Blocks per tick sideways", 1.0, 0.1, 5.0, 0.05));
    private final NumberSetting vSpeed = add(new NumberSetting("Vertical Speed", "Blocks per tick up/down (jump / sneak)", 0.6, 0.05, 3.0, 0.05));
    private final BoolSetting antiKick = add(new BoolSetting("Slow Fall When Idle", "Drift down very slowly when no keys are held", false));

    public Flight() { super("Flight", "Fly with adjustable horizontal and vertical speed"); }

    @Override
    protected void tick() {
        var p = mc.player;
        if (mode.is("Abilities")) {
            p.getAbilities().allowFlying = true;
            p.getAbilities().flying = true;
            p.getAbilities().setFlySpeed((float) (hSpeed.get() * 0.05));
            return;
        }
        double[] h = horizontal(hSpeed.get());
        double y = 0;
        if (mc.options.jumpKey.isPressed()) y += vSpeed.get();
        if (mc.options.sneakKey.isPressed()) y -= vSpeed.get();
        if (y == 0 && antiKick.isOn()) y = -0.04;
        p.setVelocity(h[0], y, h[1]);
        p.fallDistance = 0;
    }

    @Override
    protected void stop() {
        var a = mc.player.getAbilities();
        if (!mc.player.isCreative() && !mc.player.isSpectator()) { a.allowFlying = false; a.flying = false; }
        a.setFlySpeed(0.05f);
    }
}
