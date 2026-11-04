package dev.stella.client.modules.combat;

import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudElement;
import dev.stella.client.module.Category;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.Render3D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.scan.BlockScanner;
import dev.stella.client.scan.ScanTarget;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ChatUtil;
import dev.stella.client.util.ColorUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Spawner Finder: a light beam shoots up into the sky from every spawner in loaded chunks,
 * and a movable dropdown lists them with their mob type (read from the spawner the server
 * sent, the same data that draws the spinning mob inside it).
 */
public class SpawnerFinder extends HudElement {
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance", 256, 16, 1024, 16));
    private final BoolSetting beam = add(new BoolSetting("Beam", "Light beam from each spawner up into the sky", true));
    private final ColorSetting beamColor = add(new ColorSetting("Beam Color", "Beam colour", 0xFFFF2A2A)).visibleWhen(beam::isOn);
    private final BoolSetting colorByType = add(new BoolSetting("Color By Mob Type", "Each mob type gets its own beam colour", false)).visibleWhen(beam::isOn);
    private final NumberSetting beamWidth = add(new NumberSetting("Beam Width", "Beam thickness in blocks", 0.6, 0.1, 2, 0.05)).visibleWhen(beam::isOn);
    private final NumberSetting beamAlpha = add(new NumberSetting("Beam Opacity", "How see-through the beam is", 0.75, 0.05, 1, 0.05)).visibleWhen(beam::isOn);
    private final BoolSetting outline = add(new BoolSetting("Outline Spawner", "Box around the spawner block", true));
    private final BoolSetting chat = add(new BoolSetting("Chat Message", "Chat message (only you see it) when a spawner is found", true));
    private final NumberSetting rows = add(new NumberSetting("Visible Rows", "Dropdown entries shown before scrolling", 6, 1, 30, 1));
    private final NumberSetting widthSetting = add(new NumberSetting("Width", "Dropdown width", 170, 120, 400, 5));
    private final BoolSetting copyAll = add(new BoolSetting("Copy All Coordinates", "Toggle to copy every spawner (type + x y z) to the clipboard", false)).onChange(this::copyAllNow);
    private final BoolSetting exportFile = add(new BoolSetting("Export To File", "Toggle to save the list to config/stella/spawners-<date>.txt", false)).onChange(this::exportNow);

    /** Text list of all spawners found: "Zombie 12 -40 88 (35m)". */
    public String listText() {
        StringBuilder sb = new StringBuilder();
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        for (Spawner sp : list) counts.merge(sp.type(), 1, Integer::sum);
        sb.append("Spawners: ").append(list.size());
        counts.forEach((k, v) -> sb.append(" | ").append(v).append("x ").append(k));
        sb.append('\n');
        for (Spawner sp : list) {
            BlockPos b = sp.pos();
            int d = mc.player == null ? 0 : (int) Math.sqrt(mc.player.getBlockPos().getSquaredDistance(b));
            sb.append(sp.type()).append(' ').append(b.getX()).append(' ').append(b.getY()).append(' ').append(b.getZ()).append(" (").append(d).append("m)\n");
        }
        return sb.toString();
    }

    private void copyAllNow() {
        if (!copyAll.isOn()) return;
        copyAll.set(false);
        mc.keyboard.setClipboard(listText());
        dev.stella.client.notification.NotificationManager.post("Spawner Finder", list.size() + " spawners copied", dev.stella.client.notification.Notification.Type.SUCCESS, 2500);
    }

    private void exportNow() {
        if (!exportFile.isOn()) return;
        exportFile.set(false);
        try {
            String name = "spawners-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt";
            java.nio.file.Path f = dev.stella.client.config.ConfigManager.root().resolve(name);
            java.nio.file.Files.writeString(f, listText());
            dev.stella.client.notification.NotificationManager.post("Spawner Finder", "Saved " + name, dev.stella.client.notification.Notification.Type.SUCCESS, 3000);
        } catch (Exception e) {
            dev.stella.client.notification.NotificationManager.post("Spawner Finder", "Export failed: " + e.getMessage(), dev.stella.client.notification.Notification.Type.ALERT, 3000);
        }
    }

    public record Spawner(BlockPos pos, String type, net.minecraft.entity.EntityType<?> entityType) {}

    private final ScanTarget target;
    private final Map<BlockPos, String> types = new HashMap<>();
    private final Map<BlockPos, net.minecraft.entity.EntityType<?>> entityTypes = new HashMap<>();
    private final Map<BlockPos, Long> firstSeen = new HashMap<>();
    private float openAnim = 1f;
    private final Set<BlockPos> announced = new HashSet<>();
    private List<Spawner> list = new ArrayList<>();
    private boolean collapsed;
    private int scroll, timer;

    public SpawnerFinder() {
        super("Spawner Finder", "Beam into the sky above every spawner, plus a dropdown of their mob types", Category.DONUTSMP,
                4, 200, false, "Scale", 4.0);
        target = BlockScanner.register(new ScanTarget("Spawner Finder", s -> s.isOf(Blocks.SPAWNER), this::isEnabled));
    }

    @Override protected void onEnable() { target.invalidate(); types.clear(); timer = 100; }

    public List<Spawner> spawners() { return list; }
    public ScanTarget scanTarget() { return target; }

    /** Mob type of a spawner ("Zombie"), or "Unknown" if the server sent no mob. */
    private String typeAt(BlockPos p) {
        String cached = types.get(p);
        if (cached != null && !cached.equals("Unknown")) return cached;
        String t = "Unknown";
        try {
            BlockEntity be = mc.world.getBlockEntity(p);
            if (be instanceof MobSpawnerBlockEntity sp) {
                Entity e = sp.getLogic().getRenderedEntity(mc.world, p);
                if (e != null) { t = e.getType().getName().getString(); entityTypes.put(p.toImmutable(), e.getType()); }
            }
        } catch (Throwable ignored) { }
        types.put(p, t);
        return t;
    }

    private Object lastWorld;

    @Override
    public void onTick() {
        if (mc.world != lastWorld) { // new server / dimension: forget everything from the old world
            lastWorld = mc.world;
            types.clear(); entityTypes.clear(); firstSeen.clear(); announced.clear(); list = new ArrayList<>();
        }
        if (!inGame() || ++timer < 10) return;
        timer = 0;
        List<Spawner> out = new ArrayList<>();
        for (BlockPos p : target.near(mc.player.getBlockPos(), range.get(), 500)) {
            if (!mc.world.getBlockState(p).isOf(Blocks.SPAWNER)) continue;
            String type = typeAt(p);
            firstSeen.putIfAbsent(p.toImmutable(), System.currentTimeMillis());
            out.add(new Spawner(p, type, entityTypes.get(p)));
            if (chat.isOn() && !type.equals("Unknown") && announced.add(p.toImmutable())) {
                ChatUtil.send(Text.literal(type + " spawner").formatted(Formatting.RED)
                        .append(Text.literal(" found at " + p.getX() + ", " + p.getY() + ", " + p.getZ()).formatted(Formatting.GRAY)));
            }
        }
        BlockPos me = mc.player.getBlockPos();
        out.sort(java.util.Comparator.comparingDouble(sp -> sp.pos.getSquaredDistance(me)));
        list = out;
    }

    /** Distinct, stable colour per mob type. */
    private static int typeColor(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "zombie" -> 0xFF22C55E;
            case "skeleton" -> 0xFFE5E7EB;
            case "spider" -> 0xFFDC2626;
            case "cave spider" -> 0xFF0EA5E9;
            case "blaze" -> 0xFFF59E0B;
            case "silverfish" -> 0xFF94A3B8;
            case "magma cube" -> 0xFFF97316;
            case "creeper" -> 0xFF84CC16;
            case "pig" -> 0xFFF9A8D4;
            default -> ColorUtil.fromHsb((type.hashCode() & 0xFFFF) / 65535f, 0.7f, 1f, 255);
        };
    }

    private int colorFor(Spawner s) {
        return colorByType.isOn() ? typeColor(s.type) : beamColor.argb();
    }

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame()) return;
        int top = mc.world.getTopYInclusive() + 1;
        for (Spawner s : list) {
            int c = colorFor(s);
            if (beam.isOn()) {
                int a = (int) (255 * beamAlpha.get());
                r.beam(s.pos.getX() + 0.5, s.pos.getZ() + 0.5, s.pos.getY() + 1, top, ColorUtil.withAlpha(c, a), beamWidth.get() / 2);
            }
            if (outline.isOn()) {
                r.setLineWidth(2f);
                r.boxOutline(new net.minecraft.util.math.Box(s.pos).expand(0.02), ColorUtil.withAlpha(c, 0xE0));
            }
        }
    }

    // ---- dropdown HUD -------------------------------------------------------------------------

    private static final int HEAD = 16, ROW = 24;

    private int summaryHeight() { return list.isEmpty() ? 0 : 12; }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        Theme t = ThemeManager.get();
        int w = widthSetting.getInt();
        openAnim += ((collapsed ? 0f : 1f) - openAnim) * 0.25f;
        if (Math.abs(openAnim - (collapsed ? 0f : 1f)) < 0.01f) openAnim = collapsed ? 0f : 1f;
        long now = System.currentTimeMillis();

        // body
        Render2D.shadow(ctx, 0, 0, w, height, 6, 4, col(0x70000000));
        Render2D.roundRect(ctx, 0, 0, w, height, 6, col(t.background()));
        // header: gradient bar, icon, title, count pill
        Render2D.roundRect(ctx, 0, 0, w, HEAD, 6, col(t.header()));
        Render2D.hGradient(ctx, 0, HEAD - 1, w, 1, col(t.accent()), col(t.accent() & 0x00FFFFFF));
        float pulse = (float) (0.6 + 0.4 * Math.sin(now / 350.0));
        Render2D.roundRect(ctx, 5, 4, 8, 8, 2, col(ColorUtil.mulAlpha(beamColor.argb() | 0xFF000000, pulse)));
        Render2D.roundRect(ctx, 7, 6, 4, 4, 1, col(0xFFFFFFFF));
        StellaFont.draw(ctx, "SPAWNERS", 17, 4, col(t.text()), false);
        String count = String.valueOf(list.size());
        int cw = StellaFont.width(count) + 8;
        Render2D.roundRect(ctx, 17 + StellaFont.width("SPAWNERS") + 4, 3, cw, 10, 5, col(ColorUtil.withAlpha(t.accent(), 0x55)));
        StellaFont.draw(ctx, count, 17 + StellaFont.width("SPAWNERS") + 8, 4, col(t.accentSoft()), false);
        drawChevron(ctx, w - 10, 8, openAnim, col(t.textDim()));

        int y = HEAD;
        int full = summaryHeight() + Math.max(1, Math.min(list.size(), rows.getInt())) * ROW + (list.size() > rows.getInt() ? 11 : 0) + 3;
        int visible = Math.round(full * openAnim);
        if (openAnim > 0.97f) {
            // summary chips: "2 Zombie  1 Skeleton"
            if (!list.isEmpty()) {
                Map<String, Integer> counts = new java.util.TreeMap<>();
                for (Spawner sp : list) counts.merge(sp.type, 1, Integer::sum);
                int cx = 4;
                for (var e : counts.entrySet()) {
                    String chip = e.getValue() + " " + e.getKey();
                    int chipW = (int) (StellaFont.width(chip) * 0.7f) + 12;
                    if (cx + chipW > w - 4) break;
                    int c = (colorByType.isOn() ? typeColor(e.getKey()) : beamColor.argb()) | 0xFF000000;
                    Render2D.roundRect(ctx, cx, y + 2, chipW, 9, 4.5f, col(ColorUtil.withAlpha(c, 0x30)));
                    Render2D.roundRect(ctx, cx + 3, y + 5, 3, 3, 1.5f, col(c));
                    Render2D.textScaled(ctx, chip, cx + 8, y + 3.5f, 0.7f, col(t.text()), false);
                    cx += chipW + 3;
                }
                y += 12;
            }
            int max = rows.getInt();
            scroll = MathHelper.clamp(scroll, 0, Math.max(0, list.size() - max));
            if (list.isEmpty()) {
                StellaFont.draw(ctx, editor ? "Zombie Spawner (example)" : "No spawners loaded", 6, y + 8, col(t.textDim()), false);
                y += ROW;
            }
            float yaw = mc.player == null ? 0 : mc.player.getYaw();
            for (int i = scroll; i < Math.min(list.size(), scroll + max); i++) {
                Spawner sp = list.get(i);
                int c = colorFor(sp) | 0xFF000000;
                boolean nearest = i == 0;
                // row card
                Render2D.roundRect(ctx, 3, y + 1, w - 6, ROW - 2, 4, col(nearest ? ColorUtil.withAlpha(c, 0x26) : t.row()));
                Render2D.roundRect(ctx, 3, y + 1, 2, ROW - 2, 1, col(c));
                // spawn egg icon
                var egg = sp.entityType == null ? null : net.minecraft.item.SpawnEggItem.forEntity(sp.entityType);
                if (egg != null) ctx.drawItem(new net.minecraft.item.ItemStack(egg), 8, y + 4);
                else Render2D.roundRect(ctx, 10, y + 6, 12, 12, 3, col(ColorUtil.withAlpha(c, 0x80)));
                // name + coordinates
                StellaFont.draw(ctx, sp.type.toUpperCase(Locale.ROOT) + " SPAWNER", 28, y + 3, col(nearest ? 0xFFFFFFFF : t.accentSoft()), false);
                String coords = sp.pos.getX() + "  " + sp.pos.getY() + "  " + sp.pos.getZ();
                Render2D.textScaled(ctx, coords, 28, y + 13, 0.7f, col(t.textDim()), false);
                // "NEW" tag for the first 10 seconds
                Long seen = firstSeen.get(sp.pos);
                if (seen != null && now - seen < 10_000) {
                    int nx = 28 + StellaFont.width(sp.type.toUpperCase(Locale.ROOT) + " SPAWNER") + 4;
                    Render2D.roundRect(ctx, nx, y + 3, 18, 8, 4, col(ColorUtil.mulAlpha(c, 0.4 + 0.6 * pulse)));
                    Render2D.textScaled(ctx, "NEW", nx + 3, y + 4.5f, 0.6f, col(0xFFFFFFFF), false);
                }
                if (mc.player != null) {
                    double dx = sp.pos.getX() + 0.5 - mc.player.getX(), dz = sp.pos.getZ() + 0.5 - mc.player.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz + Math.pow(sp.pos.getY() + 0.5 - mc.player.getY(), 2));
                    String d = (int) dist + "m";
                    int dw = StellaFont.width(d);
                    StellaFont.draw(ctx, d, w - 8 - dw, y + 3, col(t.text()), false);
                    // direction arrow relative to where you are looking
                    float ang = (float) Math.toRadians(Math.toDegrees(Math.atan2(dz, dx)) - 90 - yaw);
                    ctx.getMatrices().pushMatrix();
                    ctx.getMatrices().translate(w - 14 - dw, y + 7);
                    ctx.getMatrices().rotate(ang);
                    Render2D.textScaled(ctx, "▲", -3, -4, 1f, col(c), false);
                    ctx.getMatrices().popMatrix();
                    // closeness bar
                    float close = (float) Math.max(0.04, 1 - dist / range.get());
                    int bx = w - 8 - 40, by = y + 15;
                    Render2D.roundRect(ctx, bx, by, 40, 3, 1.5f, col(0x40FFFFFF));
                    Render2D.roundRect(ctx, bx, by, 40 * close, 3, 1.5f, col(c));
                }
                y += ROW;
            }
            if (list.size() > max) {
                Render2D.textScaled(ctx, (scroll + 1) + "-" + Math.min(list.size(), scroll + max) + " of " + list.size() + "  (scroll)", 6, y + 2, 0.7f, col(t.textDim()), false);
                y += 11;
            }
            y += 3;
            setSize(w, y);
        } else {
            setSize(w, HEAD + visible);
        }
    }

    private static void drawChevron(DrawContext ctx, float cx, float cy, float open, int color) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, cy);
        ctx.getMatrices().rotate((float) Math.toRadians(-90 + 90 * open));
        Render2D.textScaled(ctx, "▼", -3, -4, 0.9f, color, false);
        ctx.getMatrices().popMatrix();
    }

    @Override
    public boolean onClick(double lx, double ly, int button) {
        if (ly < HEAD) { collapsed = !collapsed; return true; }
        if (collapsed) return false;
        int idx = scroll + (int) ((ly - HEAD - summaryHeight()) / ROW);
        if (idx < 0 || idx >= list.size() || idx >= scroll + rows.getInt()) return false;
        BlockPos p = list.get(idx).pos;
        mc.keyboard.setClipboard(p.getX() + " " + p.getY() + " " + p.getZ());
        dev.stella.client.notification.NotificationManager.post("Copied", p.getX() + " " + p.getY() + " " + p.getZ(),
                dev.stella.client.notification.Notification.Type.SUCCESS, 1500);
        return true;
    }

    @Override
    public boolean onScroll(double lx, double ly, double amount) {
        scroll += amount > 0 ? -1 : 1;
        return true;
    }
}
