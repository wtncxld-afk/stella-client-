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
    private final NumberSetting beamWidth = add(new NumberSetting("Beam Width", "Beam thickness in blocks", 0.5, 0.1, 2, 0.05)).visibleWhen(beam::isOn);
    private final NumberSetting beamAlpha = add(new NumberSetting("Beam Opacity", "How see-through the beam is", 0.75, 0.05, 1, 0.05)).visibleWhen(beam::isOn);
    private final BoolSetting outline = add(new BoolSetting("Outline Spawner", "Box around the spawner block", true));
    private final BoolSetting chat = add(new BoolSetting("Chat Message", "Chat message (only you see it) when a spawner is found", true));
    private final NumberSetting rows = add(new NumberSetting("Visible Rows", "Dropdown entries shown before scrolling", 6, 1, 30, 1));
    private final NumberSetting widthSetting = add(new NumberSetting("Width", "Dropdown width", 170, 120, 400, 5));

    public record Spawner(BlockPos pos, String type) {}

    private final ScanTarget target;
    private final Map<BlockPos, String> types = new HashMap<>();
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

    /** Mob type of a spawner ("Zombie"), or "Unknown" if the server sent no mob. */
    private String typeAt(BlockPos p) {
        String cached = types.get(p);
        if (cached != null && !cached.equals("Unknown")) return cached;
        String t = "Unknown";
        try {
            BlockEntity be = mc.world.getBlockEntity(p);
            if (be instanceof MobSpawnerBlockEntity sp) {
                Entity e = sp.getLogic().getRenderedEntity(mc.world, p);
                if (e != null) t = e.getType().getName().getString();
            }
        } catch (Throwable ignored) { }
        types.put(p, t);
        return t;
    }

    @Override
    public void onTick() {
        if (!inGame() || ++timer < 10) return;
        timer = 0;
        List<Spawner> out = new ArrayList<>();
        for (BlockPos p : target.near(mc.player.getBlockPos(), range.get(), 500)) {
            if (!mc.world.getBlockState(p).isOf(Blocks.SPAWNER)) continue;
            String type = typeAt(p);
            out.add(new Spawner(p, type));
            if (chat.isOn() && !type.equals("Unknown") && announced.add(p.toImmutable())) {
                ChatUtil.send(Text.literal(type + " spawner").formatted(Formatting.RED)
                        .append(Text.literal(" found at " + p.getX() + ", " + p.getY() + ", " + p.getZ()).formatted(Formatting.GRAY)));
            }
        }
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
        double pulse = 0.85 + 0.15 * Math.sin(System.currentTimeMillis() / 400.0);
        for (Spawner s : list) {
            int c = colorFor(s);
            if (beam.isOn()) {
                int a = (int) (255 * beamAlpha.get() * pulse);
                r.beam(s.pos.getX() + 0.5, s.pos.getZ() + 0.5, s.pos.getY() + 1, top, ColorUtil.withAlpha(c, a), beamWidth.get() / 2);
            }
            if (outline.isOn()) {
                r.setLineWidth(2f);
                r.boxOutline(new net.minecraft.util.math.Box(s.pos).expand(0.02), ColorUtil.withAlpha(c, 0xE0));
            }
        }
    }

    // ---- dropdown HUD -------------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        Theme t = ThemeManager.get();
        int w = widthSetting.getInt();
        Render2D.shadow(ctx, 0, 0, w, height, 4, 3, col(0x60000000));
        Render2D.roundRect(ctx, 0, 0, w, height, 4, col(t.background()));
        Render2D.roundRect(ctx, 0, 0, w, 12, 4, col(t.header()));
        ctx.fill(0, 11, w, 12, col(t.accent()));
        String head = (collapsed ? "▶ " : "▼ ") + "SPAWNERS  " + list.size();
        StellaFont.draw(ctx, head, 4, 2, col(t.text()), false);
        int y = 12;
        if (!collapsed) {
            int max = rows.getInt();
            scroll = MathHelper.clamp(scroll, 0, Math.max(0, list.size() - max));
            if (list.isEmpty()) {
                StellaFont.draw(ctx, editor ? "Zombie Spawner (example)" : "No spawners loaded", 3, y + 2, col(t.textDim()), false);
                y += 11;
            }
            for (int i = scroll; i < Math.min(list.size(), scroll + max); i++) {
                Spawner s = list.get(i);
                if (i % 2 == 1) ctx.fill(1, y, w - 1, y + 20, col(t.row()));
                int c = colorFor(s) | 0xFF000000;
                Render2D.roundRect(ctx, 3, y + 3, 4, 4, 2, col(c));
                StellaFont.draw(ctx, s.type.toUpperCase(Locale.ROOT) + " SPAWNER", 10, y + 1, col(t.accentSoft()), false);
                String dist = mc.player == null ? "" : (int) Math.sqrt(s.pos.getSquaredDistance(mc.player.getEntityPos())) + "m";
                StellaFont.draw(ctx, dist, w - 3 - StellaFont.width(dist), y + 1, col(t.textDim()), false);
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(10, (float) (y + 11));
                ctx.getMatrices().scale(0.75f, 0.75f);
                StellaFont.draw(ctx, s.pos.getX() + " " + s.pos.getY() + " " + s.pos.getZ(), 0, 0, col(t.text()), false);
                ctx.getMatrices().popMatrix();
                y += 20;
            }
            if (list.size() > max) {
                StellaFont.draw(ctx, (scroll + 1) + "-" + Math.min(list.size(), scroll + max) + " of " + list.size() + " (scroll)", 3, y + 1, col(t.textDim()), false);
                y += 10;
            }
        }
        setSize(w, y);
    }

    @Override
    public boolean onClick(double lx, double ly, int button) {
        if (ly < 12) { collapsed = !collapsed; return true; }
        if (collapsed) return false;
        int idx = scroll + (int) ((ly - 12) / 20);
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
