package dev.stella.client.modules.anticheatoff;

import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/** Boat movement controls (singleplayer / authorized test servers only). */
public class BoatFly extends GatedModule {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Horizontal boat speed (blocks per tick)", 1.2, 0.1, 5.0, 0.05));
    private final NumberSetting vSpeed = add(new NumberSetting("Vertical Speed", "Up (jump) / down (sprint key) speed", 0.5, 0.05, 3.0, 0.05));
    private final NumberSetting fall = add(new NumberSetting("Glide Fall", "Downward drift when not pressing up/down", 0.0, 0.0, 0.5, 0.01));
    private final BoolSetting yawLock = add(new BoolSetting("Face Look Direction", "Turn the boat to where you look", true));

    public BoatFly() { super("BoatFly", "Fly a boat with adjustable speed"); }

    @Override
    protected void tick() {
        var v = mc.player.getVehicle();
        if (!(v instanceof net.minecraft.entity.vehicle.AbstractBoatEntity boat)) return;
        if (yawLock.isOn()) boat.setYaw(mc.player.getYaw());
        double[] h = horizontal(speed.get());
        double y = -fall.get();
        if (mc.options.jumpKey.isPressed()) y = vSpeed.get();
        if (mc.options.sprintKey.isPressed()) y = -vSpeed.get();
        boat.setVelocity(h[0], y, h[1]);
    }
}
