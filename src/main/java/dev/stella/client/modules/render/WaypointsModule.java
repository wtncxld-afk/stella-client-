package dev.stella.client.modules.render;

import dev.stella.client.data.Waypoints;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Projection;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.Render3D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.Dimensions;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Renders waypoints created by Block Notifier (stored in waypoints.json). */
public class WaypointsModule extends Module {
    private final NumberSetting maxDistance = add(new NumberSetting("Max Distance", "Hide waypoints further than this", 10000, 50, 100000, 50));
    private final BoolSetting beam = add(new BoolSetting("Beam", "Tall translucent beam", true));
    private final BoolSetting clear = add(new BoolSetting("Clear All", "Toggle to delete every waypoint", false)).onChange(this::clearNow);

    public WaypointsModule() { super("Waypoints", "Shows saved waypoints in the world", Category.RENDER, true); }

    private void clearNow() {
        if (clear.isOn()) { Waypoints.clear(); clear.set(false); }
    }

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame()) return;
        String dim = Dimensions.current();
        for (Waypoints.Waypoint w : Waypoints.all()) {
            if (!w.dimension().equals(dim)) continue;
            if (mc.player.squaredDistanceTo(w.x(), w.y(), w.z()) > maxDistance.get() * maxDistance.get()) continue;
            Box b = new Box(w.x(), w.y(), w.z(), w.x() + 1, w.y() + 1, w.z() + 1);
            r.box(b, w.color(), (w.color() & 0xFFFFFF) | 0x30000000, true, true);
            if (beam.isOn()) r.box(new Box(w.x() + 0.4, w.y() + 1, w.z() + 0.4, w.x() + 0.6, w.y() + 64, w.z() + 0.6), 0, (w.color() & 0xFFFFFF) | 0x28000000, false, true);
        }
    }

    @Override
    public void onRenderOverlay(DrawContext ctx, float tickDelta) {
        if (!inGame()) return;
        String dim = Dimensions.current();
        for (Waypoints.Waypoint w : Waypoints.all()) {
            if (!w.dimension().equals(dim)) continue;
            double d = Math.sqrt(mc.player.squaredDistanceTo(w.x() + 0.5, w.y() + 0.5, w.z() + 0.5));
            if (d > maxDistance.get()) continue;
            Vec3d s = Projection.toScreen(new Vec3d(w.x() + 0.5, w.y() + 1.5, w.z() + 0.5));
            if (!Projection.onScreen(s)) continue;
            Render2D.label(ctx, w.name() + " " + (int) d + "m", s.x, s.y, 0.85f, w.color(), 0xA0000000);
        }
    }
}
