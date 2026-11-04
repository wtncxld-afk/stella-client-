package dev.stella.client.modules.combat;

import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudElement;
import dev.stella.client.module.Category;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.render.Projection;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.Render3D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.setting.TextSetting;
import dev.stella.client.util.ChatUtil;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player Detector for DonutSMP.
 * <p>
 * Uses only the player entities the server already sends to this client (Minecraft's normal entity
 * tracking, typically 48-128 blocks depending on the server). For each tracked player it works out
 * whether you can actually see them: hidden behind blocks (a client-side ray from your eyes to theirs
 * hits a solid block), outside your view, or visible. Hidden players get an animated blue square over
 * their chunk, their distance and direction, and a dropdown slides in listing them.
 * <p>
 * It cannot see players the server does not send (beyond tracking range, or vanished), and it never
 * asks the server for anything.
 */
public class PlayerDetector extends HudElement {
    public enum Status { BEHIND_WALL, OUT_OF_VIEW, VISIBLE }

    public record Seen(String name, UUID uuid, double distance, double dx, double dy, double dz, Status status) {}

    // ---- detection
    private final ModeSetting mode = add(new ModeSetting("Detect", "Hidden Only: players you cannot currently see. All Players: every tracked player",
            "Hidden Only", "Hidden Only", "All Players"));
    private final BoolSetting outOfViewCounts = add(new BoolSetting("Out Of View Counts As Hidden", "Players behind you / off-screen count as hidden, not only those behind walls", true))
            .visibleWhen(() -> mode.is("Hidden Only"));
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance (the server decides how far players are sent)", 128, 8, 512, 8));
    private final BoolSetting ignoreInvisible = add(new BoolSetting("Ignore Invisible", "Skip players with the invisibility effect", false));
    private final TextSetting ignoreList = add(new TextSetting("Ignore Names", "Comma-separated names that are never shown (friends)", "", 256));

    // ---- blue chunk
    private final BoolSetting chunks = add(new BoolSetting("Chunk Highlight", "Animated square over every chunk with a detected player", true));
    private final ColorSetting chunkColor = add(new ColorSetting("Chunk Color", "Colour of the chunk square", 0xFF3B82F6)).visibleWhen(chunks::isOn);
    private final NumberSetting chunkOpacity = add(new NumberSetting("Chunk Opacity", "How see-through the chunk square is", 0.6, 0.05, 1, 0.05)).visibleWhen(chunks::isOn);
    private final ModeSetting chunkHeight = add(new ModeSetting("Chunk Height", "Draw the square at the player's feet or at your own height",
            "Their Feet", "Their Feet", "My Feet")).visibleWhen(chunks::isOn);
    private final BoolSetting playerBox = add(new BoolSetting("Player Box", "Outline the detected player's hitbox through walls", true));

    // ---- alerts
    private final BoolSetting chat = add(new BoolSetting("Chat Message", "Chat line (only you see it) when someone is detected", true));
    private final BoolSetting toast = add(new BoolSetting("Notification", "Toast when someone is detected", false));
    private final BoolSetting sound = add(new BoolSetting("Sound", "Play a sound when someone is detected", true));
    private final BoolSetting staffOnly = add(new BoolSetting("Staff Only Alerts", "Only alert for names in staff.json (the list still shows everyone)", false));
    private final NumberSetting cooldown = add(new NumberSetting("Alert Cooldown", "Seconds before the same player can alert again", 30, 0, 600, 5).suffix("s"));

    // ---- dropdown
    private final BoolSetting autoHide = add(new BoolSetting("Hide When Empty", "The dropdown slides in only while someone is detected", true));
    private final NumberSetting rows = add(new NumberSetting("Visible Rows", "Players shown before scrolling", 5, 1, 20, 1));
    private final NumberSetting widthSetting = add(new NumberSetting("Width", "Dropdown width", 180, 140, 360, 5));

    private List<Seen> current = new ArrayList<>();
    private final Set<UUID> detected = new HashSet<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private final Map<UUID, Long> firstSeen = new HashMap<>();
    private int timer, scroll;
    private boolean collapsed;
    private float showAnim;
    private Object lastWorld;

    public PlayerDetector() {
        super("Player Detector", "Finds players you can't see (behind walls / out of view): blue chunks, distance, direction and a dropdown",
                Category.DONUTSMP, 170, 262, false, "Scale", 4.0);
    }

    public List<Seen> current() { return current; }

    @Override protected void onEnable() { detected.clear(); current = new ArrayList<>(); timer = 100; }

    // ------------------------------------------------------------------ detection

    private boolean ignored(String name) {
        for (String n : ignoreList.get().split(",")) if (!n.isBlank() && n.trim().equalsIgnoreCase(name)) return true;
        return false;
    }

    // Yarn leaves these enum constants unnamed in 1.21.11: VISUAL is the 3rd shape type, NONE the 1st fluid mode.
    private static final RaycastContext.ShapeType VISUAL = RaycastContext.ShapeType.values()[2];
    private static final RaycastContext.FluidHandling FLUID_NONE = RaycastContext.FluidHandling.values()[0];

    /** Client-side line-of-sight check against the blocks this client has loaded. */
    private boolean lineOfSight(Vec3d from, Vec3d to) {
        var hit = mc.world.raycast(new RaycastContext(from, to, VISUAL, FLUID_NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private Status statusOf(PlayerEntity p) {
        Vec3d eye = mc.player.getEyePos();
        boolean seen = lineOfSight(eye, p.getEyePos()) || lineOfSight(eye, p.getBoundingBox().getCenter());
        if (!seen) return Status.BEHIND_WALL;
        Vec3d s = Projection.toScreen(p.getBoundingBox().getCenter());
        if (s == null || !Projection.onScreen(s)) return Status.OUT_OF_VIEW;
        return Status.VISIBLE;
    }

    @Override
    public void onTick() {
        if (mc.world != lastWorld) { lastWorld = mc.world; detected.clear(); firstSeen.clear(); current = new ArrayList<>(); }
        if (!inGame() || ++timer < 4) return; // five checks a second keeps it light
        timer = 0;
        double r2 = range.get() * range.get();
        List<Seen> list = new ArrayList<>();
        Set<UUID> now = new HashSet<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            if (ignoreInvisible.isOn() && p.isInvisible()) continue;
            String name = p.getGameProfile().name();
            if (ignored(name)) continue;
            double d2 = mc.player.squaredDistanceTo(p);
            if (d2 > r2) continue;
            Status st = statusOf(p);
            boolean hidden = st == Status.BEHIND_WALL || (st == Status.OUT_OF_VIEW && outOfViewCounts.isOn());
            if (mode.is("Hidden Only") && !hidden) continue;
            Vec3d d = p.getEntityPos().subtract(mc.player.getEntityPos());
            list.add(new Seen(name, p.getUuid(), Math.sqrt(d2), d.x, d.y, d.z, st));
            now.add(p.getUuid());
            firstSeen.putIfAbsent(p.getUuid(), System.currentTimeMillis());
            if (!detected.contains(p.getUuid())) alert(p, Math.sqrt(d2), d, st);
        }
        detected.retainAll(now);
        detected.addAll(now);
        firstSeen.keySet().retainAll(now);
        list.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        current = list;
    }

    private void alert(PlayerEntity p, double dist, Vec3d d, Status st) {
        String name = p.getGameProfile().name();
        if (staffOnly.isOn() && !dev.stella.client.data.StaffDatabase.isStaff(name)) return;
        Long last = lastAlert.get(p.getUuid());
        if (last != null && System.currentTimeMillis() - last < cooldown.get() * 1000) return;
        lastAlert.put(p.getUuid(), System.currentTimeMillis());
        String where = (int) dist + "m " + compass(d.x, d.z) + " - " + statusText(st);
        if (chat.isOn()) ChatUtil.send(Text.literal(name).formatted(Formatting.AQUA)
                .append(Text.literal(" detected ").formatted(Formatting.GRAY))
                .append(Text.literal(where).formatted(Formatting.DARK_AQUA)));
        if (toast.isOn()) NotificationManager.post("Player Detected", name + " " + where, Notification.Type.WARNING);
        if (sound.isOn()) Sounds.ping(0.8f);
    }

    public static String statusText(Status s) {
        return switch (s) { case BEHIND_WALL -> "behind a wall"; case OUT_OF_VIEW -> "out of view"; default -> "visible"; };
    }

    /** Compass direction from you to the target (N = -Z). */
    public static String compass(double dx, double dz) {
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[(int) Math.round(deg / 45) % 8];
    }

    // ------------------------------------------------------------------ world rendering

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame() || current.isEmpty()) return;
        Set<Long> drawn = new HashSet<>();
        for (Seen s : current) {
            PlayerEntity p = mc.world.getPlayerByUuid(s.uuid());
            if (p == null) continue;
            Vec3d pos = p.getLerpedPos(r.tickDelta());
            if (chunks.isOn()) {
                int cx = MathHelper.floor(pos.x) >> 4, cz = MathHelper.floor(pos.z) >> 4;
                if (drawn.add(net.minecraft.util.math.ChunkPos.toLong(cx, cz))) {
                    double y = chunkHeight.is("My Feet") ? mc.player.getY() : pos.y;
                    r.chunkSquare(cx << 4, cz << 4, (cx << 4) + 16, (cz << 4) + 16, y + 0.05, chunkColor.argb(), chunkOpacity.get(), true);
                }
            }
            if (playerBox.isOn()) {
                r.setLineWidth(2f);
                r.boxOutline(p.getBoundingBox().offset(pos.subtract(p.getEntityPos())), ColorUtil.withAlpha(chunkColor.argb(), 0xE0));
            }
        }
    }

    // ------------------------------------------------------------------ dropdown

    private static final int HEAD = 16, ROW = 24;

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        boolean show = editor || !autoHide.isOn() || !current.isEmpty();
        showAnim += ((show ? 1f : 0f) - showAnim) * 0.2f;
        if (showAnim < 0.02f) { setSize(widthSetting.getInt(), HEAD); return; }
        Theme t = ThemeManager.get();
        int w = widthSetting.getInt();
        float a = showAnim;
        // slide in from the right edge
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((1 - a) * 30f, 0);
        long now = System.currentTimeMillis();
        int blue = chunkColor.argb() | 0xFF000000;
        int n = collapsed ? 0 : Math.max(current.isEmpty() ? 1 : 0, Math.min(current.size(), rows.getInt()));
        int bodyH = HEAD + n * ROW + (!collapsed && current.size() > rows.getInt() ? 11 : 0) + (collapsed ? 0 : 3);
        Render2D.shadow(ctx, 0, 0, w, bodyH, 6, 4, ColorUtil.mulAlpha(col(0x70000000), a));
        Render2D.roundRect(ctx, 0, 0, w, bodyH, 6, ColorUtil.mulAlpha(col(t.background()), a));
        Render2D.roundRect(ctx, 0, 0, w, HEAD, 6, ColorUtil.mulAlpha(col(t.header()), a));
        Render2D.hGradient(ctx, 0, HEAD - 1, w, 1, ColorUtil.mulAlpha(col(blue), a), 0);
        float pulse = (float) (0.55 + 0.45 * Math.sin(now / 300.0));
        Render2D.roundRect(ctx, 5, 4, 8, 8, 4, ColorUtil.mulAlpha(col(blue), a * (current.isEmpty() ? 0.4f : pulse)));
        Render2D.roundRect(ctx, 7.5f, 6.5f, 3, 3, 1.5f, ColorUtil.mulAlpha(col(0xFFFFFFFF), a));
        StellaFont.draw(ctx, "PLAYERS NEARBY", 17, 4, ColorUtil.mulAlpha(col(t.text()), a), false);
        String count = String.valueOf(current.size());
        int px = 17 + StellaFont.width("PLAYERS NEARBY") + 4;
        Render2D.roundRect(ctx, px, 3, StellaFont.width(count) + 8, 10, 5, ColorUtil.mulAlpha(col(ColorUtil.withAlpha(blue, 0x55)), a));
        StellaFont.draw(ctx, count, px + 4, 4, ColorUtil.mulAlpha(col(0xFFFFFFFF), a), false);
        Render2D.textScaled(ctx, collapsed ? "▶" : "▼", w - 11, 4.5f, 0.9f, ColorUtil.mulAlpha(col(t.textDim()), a), false);
        int y = HEAD;
        if (!collapsed) {
            scroll = MathHelper.clamp(scroll, 0, Math.max(0, current.size() - rows.getInt()));
            if (current.isEmpty()) {
                StellaFont.draw(ctx, editor ? "Steve  42m NE  (example)" : "Nobody detected", 6, y + 8, ColorUtil.mulAlpha(col(t.textDim()), a), false);
            }
            float yaw = mc.player == null ? 0 : mc.player.getYaw();
            for (int i = scroll; i < Math.min(current.size(), scroll + rows.getInt()); i++) {
                Seen s = current.get(i);
                boolean isNew = firstSeen.containsKey(s.uuid()) && now - firstSeen.get(s.uuid()) < 5000;
                int rowBg = isNew ? ColorUtil.withAlpha(blue, (int) (0x20 + 0x20 * pulse)) : t.row();
                Render2D.roundRect(ctx, 3, y + 1, w - 6, ROW - 2, 4, ColorUtil.mulAlpha(col(rowBg), a));
                Render2D.roundRect(ctx, 3, y + 1, 2, ROW - 2, 1, ColorUtil.mulAlpha(col(statusColor(s.status())), a));
                // head (from the tab list; hidden-from-tab players get a placeholder)
                PlayerListEntry entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(s.uuid());
                if (entry != null) PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), 8, y + 4, 16);
                else Render2D.roundRect(ctx, 8, y + 4, 16, 16, 3, ColorUtil.mulAlpha(col(0xFF3A3346), a));
                StellaFont.draw(ctx, s.name(), 28, y + 3, ColorUtil.mulAlpha(col(0xFFFFFFFF), a), false);
                String tag = s.status() == Status.BEHIND_WALL ? "WALL" : s.status() == Status.OUT_OF_VIEW ? "OUT OF VIEW" : "VISIBLE";
                String dy = Math.abs(s.dy()) < 2 ? "level" : (s.dy() > 0 ? "+" : "") + (int) s.dy() + "y";
                Render2D.textScaled(ctx, tag + "  " + compass(s.dx(), s.dz()) + "  " + dy, 28, y + 13, 0.7f,
                        ColorUtil.mulAlpha(col(statusColor(s.status())), a), false);
                String d = (int) s.distance() + "m";
                int dw = StellaFont.width(d);
                StellaFont.draw(ctx, d, w - 8 - dw, y + 3, ColorUtil.mulAlpha(col(t.text()), a), false);
                // arrow pointing at the player relative to where you look
                float ang = (float) Math.toRadians(Math.toDegrees(Math.atan2(s.dz(), s.dx())) - 90 - yaw);
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(w - 14 - dw, y + 7);
                ctx.getMatrices().rotate(ang);
                Render2D.textScaled(ctx, "▲", -3, -4, 1f, ColorUtil.mulAlpha(col(blue), a), false);
                ctx.getMatrices().popMatrix();
                float close = (float) Math.max(0.04, 1 - s.distance() / range.get());
                Render2D.roundRect(ctx, w - 48, y + 15, 40, 3, 1.5f, ColorUtil.mulAlpha(col(0x40FFFFFF), a));
                Render2D.roundRect(ctx, w - 48, y + 15, 40 * close, 3, 1.5f, ColorUtil.mulAlpha(col(blue), a));
                y += ROW;
            }
            if (current.isEmpty()) y += ROW;
            if (current.size() > rows.getInt()) {
                Render2D.textScaled(ctx, (scroll + 1) + "-" + Math.min(current.size(), scroll + rows.getInt()) + " of " + current.size() + "  (scroll)",
                        6, y + 2, 0.7f, ColorUtil.mulAlpha(col(t.textDim()), a), false);
                y += 11;
            }
            y += 3;
        }
        ctx.getMatrices().popMatrix();
        setSize(w, y);
    }

    private static int statusColor(Status s) {
        return switch (s) { case BEHIND_WALL -> 0xFF60A5FA; case OUT_OF_VIEW -> 0xFFFBBF24; default -> 0xFF4ADE80; };
    }

    @Override
    public boolean onClick(double lx, double ly, int button) {
        if (ly < HEAD) { collapsed = !collapsed; return true; }
        return false;
    }

    @Override
    public boolean onScroll(double lx, double ly, double amount) {
        scroll += amount > 0 ? -1 : 1;
        return true;
    }
}
