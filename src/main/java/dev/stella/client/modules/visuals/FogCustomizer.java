package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.block.enums.CameraSubmersionType;

public class FogCustomizer extends Module {
    private final BoolSetting disable = add(new BoolSetting("Disable Fog", "Remove distance fog", false));
    private final NumberSetting start = add(new NumberSetting("Start", "Fog start as % of vanilla", 100, 0, 300, 5).suffix("%")).visibleWhen(() -> !disable.isOn());
    private final NumberSetting end = add(new NumberSetting("End", "Fog end as % of vanilla", 100, 10, 300, 5).suffix("%")).visibleWhen(() -> !disable.isOn());
    private final BoolSetting customColor = add(new BoolSetting("Custom Color", "Override the fog colour", false));
    private final ColorSetting color = add(new ColorSetting("Color", "Fog colour", 0xFF2A1840)).visibleWhen(customColor::isOn);
    private final BoolSetting liquids = add(new BoolSetting("Affect Liquids", "Also change water / lava / powder snow fog", false));

    public FogCustomizer() { super("Fog Customizer", "Change fog distance and colour", Category.VISUALS); }

    public Fog apply(Fog fog, Camera camera) {
        if (!liquids.isOn() && camera.getSubmersionType() != CameraSubmersionType.NONE) return fog;
        float s = fog.start(), e = fog.end();
        if (disable.isOn()) { s = 1e6f; e = 1e6f + 1; }
        else { s *= start.getFloat() / 100f; e *= end.getFloat() / 100f; if (e <= s) e = s + 1; }
        float r = fog.red(), g = fog.green(), b = fog.blue();
        if (customColor.isOn()) { r = color.rf(); g = color.gf(); b = color.bf(); }
        return new Fog(s, e, fog.shape(), r, g, b, fog.alpha());
    }

    public boolean customColor() { return customColor.isOn(); }
    public float[] color() { return new float[]{color.rf(), color.gf(), color.bf()}; }
}
