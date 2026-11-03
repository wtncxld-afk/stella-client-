package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.OptionOverrides;

/** Raises the effective gamma without changing your saved brightness option. */
public class Fullbright extends Module {
    private final NumberSetting brightness = add(new NumberSetting("Brightness", "Effective gamma (vanilla max is 1.0)", 15, 1, 15, 0.5));

    public Fullbright() { super("Fullbright", "See in the dark", Category.VISUALS); }

    @Override protected void onEnable() { OptionOverrides.set(mc.options.getGamma(), () -> brightness.get()); }
    @Override protected void onDisable() { OptionOverrides.clear(mc.options.getGamma()); }
}
