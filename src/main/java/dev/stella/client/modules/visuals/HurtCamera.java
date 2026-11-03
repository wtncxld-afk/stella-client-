package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.OptionOverrides;

public class HurtCamera extends Module {
    private final NumberSetting strength = add(new NumberSetting("Strength", "Camera shake when hurt (0 = none, 1 = vanilla)", 0, 0, 1, 0.05));
    private final dev.stella.client.setting.BoolSetting noBob = add(new dev.stella.client.setting.BoolSetting("Disable View Bobbing", "Also stop the walking camera bob while enabled", false));

    public HurtCamera() { super("Hurt Camera", "Reduce or remove the damage camera tilt", Category.VISUALS); }

    @Override
    protected void onEnable() {
        OptionOverrides.set(mc.options.getDamageTiltStrength(), () -> strength.get());
        OptionOverrides.set(mc.options.getBobView(), () -> noBob.isOn() ? Boolean.FALSE : null);
    }

    @Override
    protected void onDisable() {
        OptionOverrides.clear(mc.options.getDamageTiltStrength());
        OptionOverrides.clear(mc.options.getBobView());
    }
}
