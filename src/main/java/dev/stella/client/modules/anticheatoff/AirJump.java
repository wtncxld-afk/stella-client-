package dev.stella.client.modules.anticheatoff;

import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/** Jump again while in the air (singleplayer / authorized test servers only). */
public class AirJump extends GatedModule {
    private final NumberSetting height = add(new NumberSetting("Jump Height", "Upward speed of each air jump (0.42 = normal jump)", 0.42, 0.1, 1.5, 0.01));
    private final NumberSetting maxJumps = add(new NumberSetting("Max Air Jumps", "Air jumps before you must land (0 = unlimited)", 0, 0, 10, 1));
    private final BoolSetting hold = add(new BoolSetting("Hold To Repeat", "Keep jumping while the key is held", false));

    private boolean wasDown;
    private int used, cooldown;

    public AirJump() { super("AirJump", "Jump repeatedly while airborne"); }

    @Override
    protected void tick() {
        var p = mc.player;
        boolean down = mc.options.jumpKey.isPressed();
        if (p.isOnGround()) used = 0;
        if (cooldown > 0) cooldown--;
        boolean trigger = hold.isOn() ? down && cooldown == 0 : down && !wasDown;
        if (trigger && !p.isOnGround() && !p.isTouchingWater() && (maxJumps.getInt() == 0 || used < maxJumps.getInt())) {
            Vec3d vel = p.getVelocity();
            p.setVelocity(vel.x, height.get(), vel.z);
            p.fallDistance = 0;
            used++;
            cooldown = 5;
        }
        wasDown = down;
    }
}
