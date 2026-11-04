package dev.stella.client.modules.cosmetics;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Render3D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.util.math.Vec3d;

/** Translucent conical hat that follows your head (client-side; visible in third person). */
public class ChineseHat extends Module {
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme accent, rainbow or a custom colour", "Theme", "Theme", "Rainbow", "Custom"));
    private final ColorSetting custom = add(new ColorSetting("Custom Color", "", 0xFFB46CFF)).visibleWhen(() -> colorMode.is("Custom"));
    private final NumberSetting size = add(new NumberSetting("Size", "Brim radius", 1.0, 0.6, 1.6, 0.05).suffix("x"));
    private final NumberSetting height = add(new NumberSetting("Height", "How pointy the hat is", 0.3, 0.1, 0.7, 0.02));
    private final NumberSetting opacity = add(new NumberSetting("Opacity", "", 0.55, 0.15, 1.0, 0.05));
    private final BoolSetting spin = add(new BoolSetting("Spin Colors", "Colours rotate around the brim", true));
    private final BoolSetting rim = add(new BoolSetting("Glowing Rim", "Bright glowing edge on the brim", true));

    public ChineseHat() { super("Chinese Hat", "Conical hat on your head (only you see it, third person)", Category.COSMETICS, false); }

    @Override
    public void onRender3D(Render3D r) {
        if (!Cosmetics.selfVisible()) return;
        Cosmetics.Head h = Cosmetics.Head.of(mc.player, r.tickDelta());
        int seg = 48;
        double rad = 0.62 * size.get(), top = 0.5 + 0.06, apexY = top + height.get();
        double rot = spin.isOn() ? (Cosmetics.now() * 0.25) % 1.0 : 0;
        Vec3d apex = h.at(0, apexY, 0);
        int apexCol = ColorUtil.withAlpha(ColorUtil.lerp(Cosmetics.color(colorMode.get(), custom.get(), rot), 0xFFFFFFFF, 0.35), (int) (255 * opacity.get()));
        Vec3d prev = null;
        int prevCol = 0;
        for (int i = 0; i <= seg; i++) {
            double a = i * Math.PI * 2 / seg;
            // brim droops slightly at the edge
            Vec3d p = h.at(Math.cos(a) * rad, top - 0.03, Math.sin(a) * rad);
            double hue = Math.abs(((i / (double) seg) + rot) % 1.0 - 0.5) * 2; // seamless 0..1..0 around the brim
            int col = ColorUtil.withAlpha(Cosmetics.color(colorMode.get(), custom.get(), hue), (int) (255 * opacity.get() * 0.8));
            if (prev != null) {
                r.triWorld(apex, prev, p, apexCol, prevCol, col, false);
                if (rim.isOn()) {
                    Vec3d pi = h.at(Math.cos(a) * rad * 0.93, top - 0.03 + height.get() * 0.07, Math.sin(a) * rad * 0.93);
                    double pa = (i - 1) * Math.PI * 2 / seg;
                    Vec3d ppi = h.at(Math.cos(pa) * rad * 0.93, top - 0.03 + height.get() * 0.07, Math.sin(pa) * rad * 0.93);
                    int g = ColorUtil.withAlpha(col, 0xB0);
                    r.quadWorld(ppi, pi, p, prev, ColorUtil.withAlpha(g, 0x10), ColorUtil.withAlpha(g, 0x10), g, ColorUtil.withAlpha(prevCol, 0xB0), true);
                }
            }
            prev = p;
            prevCol = col;
        }
    }
}
