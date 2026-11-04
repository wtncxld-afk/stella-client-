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

import java.util.ArrayDeque;

/** A glowing ribbon trail behind you while sprinting or flying (client-side). */
public class Trails extends Module {
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme accent, rainbow or a custom colour", "Rainbow", "Theme", "Rainbow", "Custom"));
    private final ColorSetting custom = add(new ColorSetting("Custom Color", "", 0xFF4FD8FF)).visibleWhen(() -> colorMode.is("Custom"));
    private final NumberSetting length = add(new NumberSetting("Length", "Trail length (points)", 40, 10, 120, 5));
    private final NumberSetting width = add(new NumberSetting("Width", "Ribbon height", 0.35, 0.1, 1.0, 0.05));
    private final BoolSetting always = add(new BoolSetting("Always", "Also while walking (not only sprinting / flying)", false));

    private final ArrayDeque<Vec3d> points = new ArrayDeque<>();

    public Trails() { super("Trails", "Glowing ribbon behind you while sprinting or flying", Category.COSMETICS, false); }

    @Override protected void onEnable() { points.clear(); }

    /** Adds a point (also used by the preview). */
    public void push(Vec3d p) { points.addFirst(p); while (points.size() > length.getInt()) points.removeLast(); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        boolean moving = mc.player.isSprinting() || mc.player.isGliding() || mc.player.getAbilities().flying || always.isOn();
        Vec3d p = mc.player.getEntityPos().add(0, mc.player.isGliding() ? 0.3 : 0.9, 0);
        if (moving && (points.isEmpty() || points.peekFirst().squaredDistanceTo(p) > 0.01)) push(p);
        else if (!points.isEmpty()) points.removeLast();
    }

    @Override
    public void onRender3D(Render3D r) {
        if (points.size() < 2) return;
        double hw = width.get() / 2;
        int n = points.size(), i = 0;
        Vec3d prev = null;
        for (Vec3d p : points) {
            if (prev != null) {
                double a0 = 1 - (i - 1) / (double) n, a1 = 1 - i / (double) n;
                int c0 = ColorUtil.withAlpha(Cosmetics.color(colorMode.get(), custom.get(), (i - 1) / (double) n), (int) (0xC0 * a0));
                int c1 = ColorUtil.withAlpha(Cosmetics.color(colorMode.get(), custom.get(), i / (double) n), (int) (0xC0 * a1));
                Vec3d up0 = new Vec3d(0, hw * a0, 0), up1 = new Vec3d(0, hw * a1, 0);
                r.quadWorld(prev.add(up0), p.add(up1), p.subtract(up1), prev.subtract(up0), c0, c1, c1, c0, true);
                r.quadWorld(prev.add(up0.multiply(0.3)), p.add(up1.multiply(0.3)), p.subtract(up1.multiply(0.3)), prev.subtract(up0.multiply(0.3)),
                        ColorUtil.withAlpha(0xFFFFFFFF, (int) (0x70 * a0)), ColorUtil.withAlpha(0xFFFFFFFF, (int) (0x70 * a1)),
                        ColorUtil.withAlpha(0xFFFFFFFF, (int) (0x70 * a1)), ColorUtil.withAlpha(0xFFFFFFFF, (int) (0x70 * a0)), true);
            }
            prev = p; i++;
        }
    }
}
