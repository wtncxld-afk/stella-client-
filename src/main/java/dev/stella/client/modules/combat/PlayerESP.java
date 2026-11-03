package dev.stella.client.modules.combat;

import dev.stella.client.data.StaffDatabase;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Projection;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.Render3D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Hitbox outlines / fills, 2D tracers from a configurable screen point, and
 * name / distance / health labels. Health is whatever the server sends; many
 * servers hide or spoof other players' health.
 */
public class PlayerESP extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance", 128, 8, 512, 8));
    private final BoolSetting ignoreInvisible = add(new BoolSetting("Ignore Invisible", "Skip players with invisibility", false));
    private final BoolSetting staffColor = add(new BoolSetting("Highlight Staff", "Use the staff colour for verified staff (staff.json)", true));
    private final ColorSetting staffCol = add(new ColorSetting("Staff Color", "", 0xFFF2555A)).visibleWhen(staffColor::isOn);

    // hitbox
    private final BoolSetting box = add(new BoolSetting("Hitbox Outline", "Outline each player's hitbox", true));
    private final ColorSetting boxColor = add(new ColorSetting("Outline Color", "", 0xFFA855F7)).visibleWhen(box::isOn);
    private final NumberSetting lineWidth = add(new NumberSetting("Outline Width", "", 2, 1, 5, 0.5)).visibleWhen(box::isOn);
    private final BoolSetting fill = add(new BoolSetting("Hitbox Fill", "Transparent hitbox fill", false));
    private final ColorSetting fillColor = add(new ColorSetting("Fill Color", "", 0x30A855F7)).visibleWhen(fill::isOn);

    // tracers
    private final BoolSetting tracers = add(new BoolSetting("Tracers", "Lines from the screen to each player", false));
    private final ModeSetting tracerOrigin = add(new ModeSetting("Tracer Origin", "Screen position tracers start from",
            "Crosshair", dev.stella.client.render.Tracers.ORIGINS)).visibleWhen(tracers::isOn);
    private final NumberSetting originX = add(new NumberSetting("Origin X %", "", 50, 0, 100, 1)).visibleWhen(() -> tracers.isOn() && tracerOrigin.is("Custom"));
    private final NumberSetting originY = add(new NumberSetting("Origin Y %", "", 50, 0, 100, 1)).visibleWhen(() -> tracers.isOn() && tracerOrigin.is("Custom"));
    private final ModeSetting tracerTarget = add(new ModeSetting("Tracer Target", "Body part tracers point to", "Body", "Feet", "Body", "Head")).visibleWhen(tracers::isOn);
    private final ColorSetting tracerColor = add(new ColorSetting("Tracer Color", "", 0xC0D8B4FE)).visibleWhen(tracers::isOn);
    private final NumberSetting tracerWidth = add(new NumberSetting("Tracer Width", "Line thickness in screen pixels", 1.5, 0.5, 5, 0.5)).visibleWhen(tracers::isOn);
    private final dev.stella.client.render.TracerStyle tracerStyle = new dev.stella.client.render.TracerStyle(st -> add(st), tracers::isOn);
    private final BoolSetting distanceFade = add(new BoolSetting("Distance Fade", "Fade tracers for far players", true)).visibleWhen(tracers::isOn);

    // labels
    private final BoolSetting names = add(new BoolSetting("Names", "Show player names", true));
    private final ColorSetting nameColor = add(new ColorSetting("Name Color", "", 0xFFF3F0F7)).visibleWhen(names::isOn);
    private final BoolSetting distance = add(new BoolSetting("Distance", "Show distance", true));
    private final BoolSetting health = add(new BoolSetting("Health", "Show server-reported health", false));
    private final NumberSetting labelScale = add(new NumberSetting("Label Scale", "", 0.85, 0.4, 2, 0.05));

    public PlayerESP() { super("Player ESP", "Hitboxes, tracers, names, distance and health for players", Category.DONUTSMP); }

    private boolean valid(PlayerEntity p) {
        if (p == mc.player || p.isRemoved()) return false;
        if (ignoreInvisible.isOn() && p.isInvisible()) return false;
        return mc.player.squaredDistanceTo(p) <= range.get() * range.get();
    }

    private boolean staff(PlayerEntity p) { return staffColor.isOn() && StaffDatabase.isStaff(p.getGameProfile().name()); }

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame()) return;
        r.setLineWidth(lineWidth.getFloat());
        float td = r.tickDelta();
        Vec3d origin = dev.stella.client.render.Tracers.start(tracerOrigin.get(), originX.get(), originY.get());
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!valid(p)) continue;
            if (tracers.isOn()) {
                double ty = switch (tracerTarget.get()) {
                    case "Feet" -> 0;
                    case "Head" -> p.getHeight() * 0.9;
                    default -> p.getHeight() / 2;
                };
                double dist = Math.sqrt(mc.player.squaredDistanceTo(p));
                int c = staff(p) ? staffCol.argb() : tracerColor.argb();
                if (distanceFade.isOn()) c = dev.stella.client.util.ColorUtil.mulAlpha(c, 1 - 0.7 * Math.min(1, dist / range.get()));
                dev.stella.client.render.Tracers.draw(r, origin, p.getLerpedPos(td).add(0, ty, 0), tracerWidth.getFloat(), c, tracerStyle);
            }
            if (!box.isOn() && !fill.isOn()) continue;
            Vec3d lerp = p.getLerpedPos(td);
            Box b = p.getBoundingBox().offset(lerp.subtract(p.getEntityPos()));
            int oc = staff(p) ? staffCol.argb() : boxColor.argb();
            r.box(b, oc, fillColor.argb(), box.isOn(), fill.isOn());
        }
    }

    @Override
    public void onRenderOverlay(DrawContext ctx, float tickDelta) {
        if (!inGame()) return;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!valid(p)) continue;
            Vec3d pos = p.getLerpedPos(tickDelta);
            double dist = Math.sqrt(mc.player.squaredDistanceTo(p));
            if (names.isOn() || distance.isOn() || health.isOn()) {
                Vec3d s = Projection.toScreen(pos.add(0, p.getHeight() + 0.35, 0));
                if (!Projection.onScreen(s)) continue;
                StringBuilder sb = new StringBuilder();
                if (names.isOn()) sb.append(p.getGameProfile().name());
                if (distance.isOn()) sb.append(sb.isEmpty() ? "" : " ").append((int) dist).append("m");
                if (health.isOn()) {
                    float hp = p.getHealth() + p.getAbsorptionAmount();
                    sb.append(sb.isEmpty() ? "" : " ").append(String.format("%.1f", hp)).append("❤");
                }
                int c = staff(p) ? staffCol.argb() : nameColor.argb();
                Render2D.label(ctx, sb.toString(), s.x, s.y, labelScale.getFloat(), c, 0x90000000);
            }
        }
    }
}
