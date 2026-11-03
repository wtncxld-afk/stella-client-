package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.OptionOverrides;

/** Glint visibility, speed and strength (colour is fixed by the vanilla glint texture). */
public class EnchantmentGlint extends Module {
    public final BoolSetting hide = add(new BoolSetting("Hide Glint", "Remove the enchantment shimmer entirely", false));
    private final NumberSetting speed = add(new NumberSetting("Speed", "Glint animation speed", 1, 0, 3, 0.05)).visibleWhen(() -> !hide.isOn());
    private final NumberSetting strength = add(new NumberSetting("Strength", "Glint brightness", 0.75, 0, 1, 0.05)).visibleWhen(() -> !hide.isOn());

    public EnchantmentGlint() { super("Enchantment Glint", "Customise the enchantment shimmer", Category.VISUALS); }

    public boolean shouldHide() { return isEnabled() && hide.isOn(); }

    @Override
    protected void onEnable() {
        OptionOverrides.set(mc.options.getGlintSpeed(), () -> speed.get());
        OptionOverrides.set(mc.options.getGlintStrength(), () -> strength.get());
    }

    @Override
    protected void onDisable() {
        OptionOverrides.clear(mc.options.getGlintSpeed());
        OptionOverrides.clear(mc.options.getGlintStrength());
    }
}
