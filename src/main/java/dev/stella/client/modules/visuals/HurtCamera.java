package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.OptionOverrides;

public class HurtCamera extends Module {
    private final NumberSetting strength = add(new NumberSetting("Strength", "Camera shake when hurt (0 = none, 1 = vanilla)", 0, 0, 1, 0.05));

    public HurtCamera() { super("Hurt Camera", "Reduce or remove the damage camera tilt", Category.VISUALS); }

    @Override protected void onEnable() { OptionOverrides.set(mc.options.getDamageTiltStrength(), () -> strength.get()); }
    @Override protected void onDisable() { OptionOverrides.clear(mc.options.getDamageTiltStrength()); }
}
