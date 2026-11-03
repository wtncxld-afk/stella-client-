package dev.stella.client.modules.settings;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.AnimationSettings;

public class AnimationsModule extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Animation speed multiplier", 1, 0.25, 3, 0.05)).onChange(this::sync);

    public AnimationsModule() { super("Animations", "Smooth GUI and HUD animations", Category.SETTINGS, true); }

    @Override protected void onEnable() { sync(); }
    @Override protected void onDisable() { AnimationSettings.set(false, 1); }
    private void sync() { AnimationSettings.set(isEnabled(), speed.get()); }
}
