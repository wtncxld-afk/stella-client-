package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;

/** Client-side weather visuals; the server's real weather is unchanged. */
public class WeatherCustomizer extends Module {
    private final ModeSetting mode = add(new ModeSetting("Weather", "Weather to display", "Clear", "Clear", "Rain", "Thunder"));
    private final NumberSetting intensity = add(new NumberSetting("Intensity", "Rain strength", 1, 0.1, 1, 0.05)).visibleWhen(() -> !mode.is("Clear"));

    public WeatherCustomizer() { super("Weather Customizer", "Force clear skies, rain or thunder visually", Category.VISUALS); }

    public float rain(float original) { return mode.is("Clear") ? 0f : intensity.getFloat(); }
    public float thunder(float original) { return mode.is("Thunder") ? intensity.getFloat() : 0f; }
}
