package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;

/** Tints the vanilla sky, fog and clouds toward a chosen colour. */
public class Ambience extends Module {
    private final ColorSetting tint = add(new ColorSetting("Tint", "Ambient colour", 0xFF9D6BFF));
    private final NumberSetting strength = add(new NumberSetting("Strength", "How strongly to tint", 0.45, 0, 1, 0.05));
    private final BoolSetting sky = add(new BoolSetting("Sky", "Tint the sky", true));
    private final BoolSetting fog = add(new BoolSetting("Fog", "Tint the fog / horizon", true));
    private final BoolSetting clouds = add(new BoolSetting("Clouds", "Tint the clouds", true));

    public Ambience() { super("Ambience", "Recolour the sky, fog and clouds", Category.VISUALS); }

    public int skyColor(int rgb) { return sky.isOn() ? mix(rgb) : rgb; }
    public int cloudColor(int rgb) { return clouds.isOn() ? mix(rgb) : rgb; }
    public boolean tintFog() { return fog.isOn(); }

    public float[] fogColor(float r, float g, float b) {
        double k = strength.get();
        return new float[]{(float) (r + (tint.rf() - r) * k), (float) (g + (tint.gf() - g) * k), (float) (b + (tint.bf() - b) * k)};
    }

    private int mix(int rgb) {
        int a = rgb & 0xFF000000;
        return a | (ColorUtil.lerp(rgb | 0xFF000000, tint.argb() | 0xFF000000, strength.get()) & 0xFFFFFF);
    }
}
