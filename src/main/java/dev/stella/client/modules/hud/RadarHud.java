package dev.stella.client.modules.hud;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudElement;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Circular radar: you in the middle, every player the client has loaded drawn as their
 * head at their real position, with rotating N/E/S/W markers and a sweep line.
 * Only uses entities the server already sent to your client (same as F3 / the tab list).
 */
public class RadarHud extends HudElement {
    private final NumberSetting range = add(new NumberSetting("Range", "Blocks from you to the edge of the radar", 64, 16, 256, 8).suffix("m"));
    private final NumberSetting size = add(new NumberSetting("Radius", "Radar size in pixels", 56, 30, 120, 2).suffix("px"));
    private final BoolSetting rotate = add(new BoolSetting("Rotate With View", "Where you look is always up (off: north is up)", true));
    private final BoolSetting names = add(new BoolSetting("Names", "Show names under heads", true));
    private final BoolSetting distance = add(new BoolSetting("Distance", "Show distance under each name", false));
    private final BoolSetting height = add(new BoolSetting("Height Arrows", "Small arrow when a player is well above or below you", true));
    private final BoolSetting edge = add(new BoolSetting("Clamp To Edge", "Players out of range sit on the rim (faded) instead of vanishing", true));
    private final BoolSetting mobs = add(new BoolSetting("Mobs", "Also show mobs as dots (red hostile, green passive)", false));
    private final BoolSetting sweep = add(new BoolSetting("Sweep", "Animated radar sweep", true));
    private final BoolSetting compass = add(new BoolSetting("Compass", "N / E / S / W on the rim", true));

    public RadarHud() { super("Radar HUD", "Circle radar with player heads at their exact positions", 4, 150, false); }

    private record Blip(double sx, double sy, double dist, double dy, boolean out, Entity e) {}

    @Override
    public void render(DrawContext ctx, float td, boolean editor) {
        int r = size.getInt();
        int d = r * 2 + 8;
        setSize(d, d + 4);
        float cx = r + 4, cy = r + 4;
        int accent = ThemeManager.get().accent();

        // --- disc: dark glass with an accent tint, filled with horizontal strips ---
        int bg = col(ColorUtil.withAlpha(ColorUtil.lerp(0xFF0C0814, accent, 0.18), 0xB4));
        for (int y = -r; y <= r; y++) {
            double hw = Math.sqrt(r * r - y * y);
            Render2D.rect(ctx, (float) (cx - hw), cy + y, (float) (hw * 2), 1, bg);
        }
        // inner glow towards the rim
        for (int i = 0; i < 4; i++) ring(ctx, cx, cy, r - i, 1f, col(ColorUtil.withAlpha(accent, 0x30 - i * 0x0A)));
        ring(ctx, cx, cy, r * 0.5, 0.6f, col(0x26FFFFFF));
        ring(ctx, cx, cy, r, 1.4f, col(ColorUtil.withAlpha(accent, 0xE0)));
        Render2D.line(ctx, cx - r + 3, cy, cx + r - 3, cy, 0.6f, col(0x18FFFFFF));
        Render2D.line(ctx, cx, cy - r + 3, cx, cy + r - 3, 0.6f, col(0x18FFFFFF));

        double yaw = mc.player == null ? 0 : Math.toRadians(mc.player.getYaw(td));
        boolean rot = rotate.isOn();

        // --- sweep: bright leading line with a fading trail ---
        if (sweep.isOn()) {
            double a = (System.currentTimeMillis() % 3000L) / 3000.0 * Math.PI * 2;
            for (int i = 0; i < 18; i++) {
                double b = a - i * 0.045;
                int alpha = (int) (0x70 * (1 - i / 18.0));
                Render2D.line(ctx, cx, cy, cx + Math.cos(b) * (r - 1.5), cy + Math.sin(b) * (r - 1.5), i == 0 ? 1.2f : 1.6f,
                        col(ColorUtil.withAlpha(i == 0 ? ColorUtil.lerp(accent, 0xFFFFFFFF, 0.4) : accent, i == 0 ? 0xC0 : alpha / 2)));
            }
        }

        // --- compass letters on the rim ---
        if (compass.isOn()) {
            String[] l = {"N", "E", "S", "W"};
            double[][] dir = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
            for (int i = 0; i < 4; i++) {
                double[] p = project(dir[i][0], dir[i][1], yaw, rot);
                double len = Math.hypot(p[0], p[1]);
                double lx = cx + p[0] / len * (r - 7), ly = cy + p[1] / len * (r - 7);
                int c = i == 0 ? 0xFFFF5A6E : 0xFFE9E4F5;
                Render2D.textScaled(ctx, l[i], (float) (lx - StellaFont.width(l[i]) * 0.35f), (float) (ly - 3), 0.7f, col(c), true);
            }
        }

        // --- blips ---
        List<Blip> players = new ArrayList<>();
        if (mc.player != null && mc.world != null) {
            Vec3d me = mc.player.getLerpedPos(td);
            double scale = r / range.get();
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
                boolean isPlayer = e instanceof PlayerEntity;
                if (!isPlayer && !mobs.isOn()) continue;
                Vec3d p = e.getLerpedPos(td);
                double dx = p.x - me.x, dz = p.z - me.z, dist = Math.hypot(dx, dz);
                double[] s = project(dx, dz, yaw, rot);
                double sx = s[0] * scale, sy = s[1] * scale, len = Math.hypot(sx, sy);
                double lim = r - (isPlayer ? 6 : 2.5);
                boolean out = len > lim;
                if (out) {
                    if (!isPlayer || !edge.isOn()) continue;
                    sx = sx / len * lim; sy = sy / len * lim;
                }
                if (isPlayer) players.add(new Blip(sx, sy, dist, p.y - me.y, out, e));
                else {
                    int c = e instanceof Monster ? 0xFFFF4D5E : 0xFF58E08A;
                    Render2D.roundRect(ctx, (float) (cx + sx - 1.5), (float) (cy + sy - 1.5), 3, 3, 1.5f, col(c));
                }
            }
        } else if (editor) {
            players.add(new Blip(r * 0.35, -r * 0.4, 20, 0, false, null));
            players.add(new Blip(-r * 0.55, r * 0.2, 35, -12, false, null));
        }
        // far players first so near ones sit on top
        players.sort((a, b) -> Double.compare(b.dist, a.dist));
        for (Blip b : players) drawHead(ctx, cx + b.sx, cy + b.sy, b);

        // --- you: arrow in the middle ---
        double myAng = rot ? -Math.PI / 2 : Math.toRadians(mc.player == null ? 180 : mc.player.getYaw(td)) + Math.PI / 2;
        double ax = Math.cos(myAng), ay = Math.sin(myAng), px = -ay, py = ax;
        int me = col(0xFFFFFFFF);
        Render2D.line(ctx, cx + ax * 5, cy + ay * 5, cx - ax * 3 + px * 3.5, cy - ay * 3 + py * 3.5, 1.4f, me);
        Render2D.line(ctx, cx + ax * 5, cy + ay * 5, cx - ax * 3 - px * 3.5, cy - ay * 3 - py * 3.5, 1.4f, me);
        Render2D.line(ctx, cx - ax * 3 + px * 3.5, cy - ay * 3 + py * 3.5, cx - ax * 1, cy - ay * 1, 1.2f, me);
        Render2D.line(ctx, cx - ax * 3 - px * 3.5, cy - ay * 3 - py * 3.5, cx - ax * 1, cy - ay * 1, 1.2f, me);

        // range label under the disc
        String lab = range.getInt() + "m";
        Render2D.textScaled(ctx, lab, cx - StellaFont.width(lab) * 0.3f, cy + r + 1, 0.6f, col(0xB0B9A7D6), true);
    }

    /** World offset (east, south) to radar offset (right, down). */
    private static double[] project(double dx, double dz, double yaw, boolean rotate) {
        if (!rotate) return new double[]{dx, dz};
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // where you look
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // your right hand
        return new double[]{dx * rx + dz * rz, -(dx * fx + dz * fz)};
    }

    private void drawHead(DrawContext ctx, double x, double y, Blip b) {
        int hs = 10;
        float a = b.out ? 0.45f : 1f;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((float) x, (float) y);
        // outline so heads read on any background
        int ring = b.e instanceof PlayerEntity p && dev.stella.client.modules.misc.FriendsModule.is(p) ? dev.stella.client.modules.misc.FriendsModule.get().color.argb()
                : b.e instanceof PlayerEntity p2 && p2.isSneaking() ? 0xFF9AA0B0 : ThemeManager.get().accent();
        Render2D.roundRect(ctx, -hs / 2f - 1, -hs / 2f - 1, hs + 2, hs + 2, 1.5f, col(ColorUtil.withAlpha(ring, (int) (0xE0 * a))));
        var skin = DefaultSkinHelper.getSteve();
        if (b.e != null) {
            PlayerListEntry entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(b.e.getUuid());
            skin = entry != null ? entry.getSkinTextures() : DefaultSkinHelper.getSkinTextures(b.e.getUuid());
        }
        m.translate(-hs / 2f, -hs / 2f);
        PlayerSkinDrawer.draw(ctx, skin, 0, 0, hs, ColorUtil.withAlpha(0xFFFFFFFF, (int) (255 * a * opacity.get())));
        m.translate(hs / 2f, hs / 2f);
        if (height.isOn() && Math.abs(b.dy) > 6) {
            String arrow = b.dy > 0 ? "▲" : "▼";
            Render2D.textScaled(ctx, arrow, hs / 2f - 1, -hs / 2f - 3, 0.55f, col(b.dy > 0 ? 0xFF7CE38B : 0xFFFF8A65), true);
        }
        float ty = hs / 2f + 1.5f;
        if (names.isOn()) {
            String n = b.e == null ? "Player" : b.e.getName().getString();
            Render2D.textScaled(ctx, n, -StellaFont.width(n) * 0.275f, ty, 0.55f, col(ColorUtil.withAlpha(0xFFFFFFFF, (int) (255 * a))), true);
            ty += 5;
        }
        if (distance.isOn()) {
            String s = (int) b.dist + "m";
            Render2D.textScaled(ctx, s, -StellaFont.width(s) * 0.25f, ty, 0.5f, col(ColorUtil.withAlpha(0xFFB9A7D6, (int) (255 * a))), true);
        }
        m.popMatrix();
    }

    private static void ring(DrawContext ctx, double cx, double cy, double r, float w, int color) {
        int seg = Math.max(24, (int) (r * 1.2));
        double px = cx + r, py = cy;
        for (int i = 1; i <= seg; i++) {
            double a = i * Math.PI * 2 / seg, x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
            Render2D.line(ctx, px, py, x, y, w, color);
            px = x; py = y;
        }
    }
}
