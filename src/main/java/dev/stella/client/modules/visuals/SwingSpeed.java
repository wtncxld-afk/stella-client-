package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;

/**
 * Changes how long the arm-swing animation takes. Purely visual: attack speed
 * and cooldown are decided by the server and are not changed.
 */
public class SwingSpeed extends Module {
    private final NumberSetting scale = add(new NumberSetting("Swing Duration", "1.0 = vanilla, higher = slower, lower = faster", 1.5, 0.25, 4, 0.05).suffix("x"));
    private final BoolSetting others = add(new BoolSetting("Other Players", "Also change other players' swing animation", false));

    public SwingSpeed() { super("Swing Speed", "Make your arm swing slower or faster (visual only)", Category.VISUALS); }

    public int apply(LivingEntity entity, int vanilla) {
        if (!isEnabled()) return vanilla;
        if (entity != mc.player && !others.isOn()) return vanilla;
        return Math.max(1, Math.round(vanilla * scale.getFloat()));
    }
}
