package dev.stella.client.modules.combat;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Projection;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.Render3D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.scan.BlockScanner;
import dev.stella.client.scan.ScanTarget;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Highlights redstone machinery in the chunks the server has sent you - pistons, observers, repeaters,
 * comparators, target blocks, dispensers / droppers, hoppers, redstone wire / torches and other
 * components - and labels each one with its live block state like the F3 screen does (facing, powered,
 * delay, mode, signal strength...). A facing arrow shows which way each block points, and a summary
 * lists how many of each type are around you. Groups each have their own toggle and colour.
 */
public class BlockEntityDebug extends Module {
    /** One group of blocks with its own toggle and colour. */
    private record Group(String name, BoolSetting on, ColorSetting color, Set<Block> blocks) {}

    private final List<Group> groups = new ArrayList<>();
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance", 64, 8, 256, 8));
    private final NumberSetting maxRendered = add(new NumberSetting("Max Rendered", "Cap on highlighted blocks", 600, 20, 5000, 20));
    private final BoolSetting fill = add(new BoolSetting("Fill", "Translucent fill", true));
    private final NumberSetting fillAlpha = add(new NumberSetting("Fill Alpha", "Fill opacity", 0.18, 0.02, 0.8, 0.02)).visibleWhen(fill::isOn);
    private final BoolSetting outline = add(new BoolSetting("Outline", "Box outline", true));
    private final NumberSetting lineWidth = add(new NumberSetting("Line Width", "Outline thickness", 1.5, 1, 5, 0.5)).visibleWhen(outline::isOn);
    private final BoolSetting arrows = add(new BoolSetting("Facing Arrows", "Arrow showing which way each block faces", true));
    private final BoolSetting poweredGlow = add(new BoolSetting("Highlight Powered", "Powered / active blocks get a brighter box", true));
    private final BoolSetting labels = add(new BoolSetting("State Labels", "Show each block's state (facing, powered, delay...)", true));
    private final NumberSetting labelRange = add(new NumberSetting("Label Range", "Only label blocks closer than this", 20, 4, 64, 2)).visibleWhen(labels::isOn);
    private final NumberSetting maxLabels = add(new NumberSetting("Max Labels", "Labels on the nearest N blocks", 40, 5, 200, 5)).visibleWhen(labels::isOn);
    private final NumberSetting labelScale = add(new NumberSetting("Label Scale", "Label size", 0.7, 0.4, 1.5, 0.05)).visibleWhen(labels::isOn);
    private final BoolSetting summary = add(new BoolSetting("Summary", "Count of each type around you (left side of the screen)", true));

    private final ScanTarget target;
    private List<BlockPos> cache = new ArrayList<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private int timer;

    public BlockEntityDebug() {
        super("Block Entity Debug", "Shows pistons, observers, repeaters, target blocks and other redstone with their live state", Category.DONUTSMP);
        group("Pistons", 0xFF22C55E, Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.PISTON_HEAD, Blocks.MOVING_PISTON);
        group("Observers", 0xFFEF4444, Blocks.OBSERVER);
        group("Repeaters", 0xFFF97316, Blocks.REPEATER);
        group("Comparators", 0xFFFACC15, Blocks.COMPARATOR);
        group("Target Blocks", 0xFFF472B6, Blocks.TARGET);
        group("Dispensers / Droppers", 0xFF38BDF8, Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTER);
        group("Hoppers", 0xFF94A3B8, Blocks.HOPPER);
        group("Redstone", 0xFFDC2626, Blocks.REDSTONE_WIRE, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
                Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_LAMP);
        group("Other Components", 0xFFA855F7, Blocks.NOTE_BLOCK, Blocks.DAYLIGHT_DETECTOR, Blocks.SCULK_SENSOR,
                Blocks.CALIBRATED_SCULK_SENSOR, Blocks.TRIPWIRE_HOOK, Blocks.LEVER, Blocks.LIGHTNING_ROD, Blocks.BELL);
        // scan for every supported block; the group toggles only filter what is drawn, so no rescan is needed
        target = BlockScanner.register(new ScanTarget("Block Entity Debug", s -> groupOf(s.getBlock()) != null, this::isEnabled));
    }

    private void group(String name, int color, Block... blocks) {
        BoolSetting on = add(new BoolSetting(name, "Highlight " + name.toLowerCase(Locale.ROOT), true));
        ColorSetting c = add(new ColorSetting(name + " Color", "", color)).visibleWhen(on::isOn);
        groups.add(new Group(name, on, c, Set.of(blocks)));
    }

    private Group groupOf(Block b) {
        for (Group g : groups) if (g.blocks.contains(b)) return g;
        return null;
    }

    @Override protected void onEnable() { target.invalidate(); timer = 100; }

    /** Blocks currently highlighted (used by the CI test). */
    public int trackedCount() { return cache.size(); }

    @Override
    public void onTick() {
        if (!inGame() || ++timer < 10) return;
        timer = 0;
        List<BlockPos> out = new ArrayList<>();
        counts.clear();
        for (Group g : groups) if (g.on.isOn()) counts.put(g.name, 0);
        for (BlockPos p : target.near(mc.player.getBlockPos(), range.get(), 50_000)) {
            Group g = groupOf(mc.world.getBlockState(p).getBlock());
            if (g == null || !g.on.isOn()) continue;
            counts.merge(g.name, 1, Integer::sum);
            if (out.size() < maxRendered.getInt()) out.add(p);
        }
        cache = out;
    }

    // ------------------------------------------------------------------ state helpers

    private static boolean active(BlockState s) {
        if (s.contains(Properties.POWERED) && s.get(Properties.POWERED)) return true;
        if (s.contains(Properties.EXTENDED) && s.get(Properties.EXTENDED)) return true;
        if (s.contains(Properties.TRIGGERED) && s.get(Properties.TRIGGERED)) return true;
        if (s.contains(Properties.LIT) && s.get(Properties.LIT)) return true;
        if (s.contains(Properties.POWER) && s.get(Properties.POWER) > 0) return true;
        return s.isOf(Blocks.REDSTONE_BLOCK) || s.isOf(Blocks.MOVING_PISTON);
    }

    private static Direction facing(BlockState s) {
        if (s.contains(Properties.FACING)) return s.get(Properties.FACING);
        if (s.contains(Properties.HORIZONTAL_FACING)) return s.get(Properties.HORIZONTAL_FACING);
        if (s.contains(Properties.HOPPER_FACING)) return s.get(Properties.HOPPER_FACING);
        return null;
    }

    /** F3-style summary of the interesting properties: "Repeater · north · 3t · powered". */
    private static String describe(BlockState s) {
        StringBuilder sb = new StringBuilder(s.getBlock().getName().getString());
        for (Property<?> p : s.getProperties()) {
            String name = p.getName();
            String v = value(s, p);
            switch (name) {
                case "waterlogged", "north", "south", "east", "west", "up", "down", "attached", "disarmed",
                     "short", "instrument", "note", "sculk_sensor_phase", "attachment", "type" -> { continue; }
                default -> { }
            }
            if ("true".equals(v)) sb.append(" · ").append(name);
            else if ("false".equals(v)) { if ("extended".equals(name)) sb.append(" · retracted"); }
            else if ("delay".equals(name)) sb.append(" · ").append(v).append("t");
            else if ("power".equals(name)) sb.append(" · signal ").append(v);
            else sb.append(" · ").append(v);
        }
        return sb.toString();
    }

    private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) { return p.name(s.get(p)); }

    // ------------------------------------------------------------------ rendering

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame()) return;
        r.setLineWidth(lineWidth.getFloat());
        for (BlockPos p : cache) {
            BlockState s = mc.world.getBlockState(p);
            Group g = groupOf(s.getBlock());
            if (g == null || !g.on.isOn()) continue;
            boolean on = poweredGlow.isOn() && active(s);
            int c = on ? ColorUtil.lerp(g.color.argb(), 0xFFFFFFFF, 0.35) : g.color.argb();
            int fc = ColorUtil.withAlpha(c, (int) (fillAlpha.get() * 255 * (on ? 1.6 : 1)));
            Box box = s.isOf(Blocks.REDSTONE_WIRE) ? new Box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 0.1, p.getZ() + 1)
                    : s.isOf(Blocks.REPEATER) || s.isOf(Blocks.COMPARATOR) || s.isOf(Blocks.DAYLIGHT_DETECTOR)
                    ? new Box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 0.125, p.getZ() + 1) : new Box(p);
            r.box(box, c, fc, outline.isOn(), fill.isOn());
            if (arrows.isOn()) {
                Direction d = facing(s);
                if (d != null) arrow(r, box.getCenter(), d, c);
            }
        }
    }

    /** Shaft from the centre out of the face plus a two-line head. */
    private static void arrow(Render3D r, Vec3d center, Direction d, int color) {
        Vec3d dir = new Vec3d(d.getOffsetX(), d.getOffsetY(), d.getOffsetZ());
        Vec3d tip = center.add(dir.multiply(0.75));
        r.line(center, tip, color);
        // a perpendicular for the arrow head
        Vec3d side = Math.abs(dir.y) > 0.5 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d back = tip.subtract(dir.multiply(0.22));
        r.line(tip, back.add(side.multiply(0.14)), color);
        r.line(tip, back.subtract(side.multiply(0.14)), color);
    }

    @Override
    public void onRenderOverlay(DrawContext ctx, float tickDelta) {
        if (!inGame()) return;
        if (labels.isOn()) {
            double lr2 = labelRange.get() * labelRange.get();
            int n = 0;
            for (BlockPos p : cache) {
                if (n >= maxLabels.getInt()) break;
                if (p.getSquaredDistance(mc.player.getEntityPos()) > lr2) continue;
                BlockState s = mc.world.getBlockState(p);
                Group g = groupOf(s.getBlock());
                if (g == null || !g.on.isOn()) continue;
                Vec3d sp = Projection.toScreen(Vec3d.ofCenter(p).add(0, 0.7, 0));
                if (!Projection.onScreen(sp)) continue;
                Render2D.label(ctx, describe(s), sp.x, sp.y, labelScale.getFloat(), ColorUtil.lerp(g.color.argb(), 0xFFFFFFFF, 0.55), 0xA0000000);
                n++;
            }
        }
        if (summary.isOn() && !counts.isEmpty()) {
            int x = 4, y = mc.getWindow().getScaledHeight() / 2 - counts.size() * 5 - 8;
            int w = StellaFont.width("BLOCK ENTITY DEBUG") + 8;
            for (Map.Entry<String, Integer> e : counts.entrySet()) w = Math.max(w, StellaFont.width(e.getKey() + "  " + e.getValue()) + 14);
            Render2D.roundRect(ctx, x, y, w, counts.size() * 10 + 15, 4, 0x90100D16);
            StellaFont.draw(ctx, "BLOCK ENTITY DEBUG", x + 4, y + 3, 0xFFE9D5FF, true);
            int yy = y + 14;
            for (Group g : groups) {
                Integer c = counts.get(g.name);
                if (c == null) continue;
                Render2D.rect(ctx, x + 4, yy + 2, 4, 4, g.color.argb());
                StellaFont.draw(ctx, g.name, x + 11, yy, c > 0 ? 0xFFF3F0F7 : 0xFF6E6878, false);
                String cs = String.valueOf(c);
                StellaFont.draw(ctx, cs, x + w - 4 - StellaFont.width(cs), yy, c > 0 ? g.color.argb() : 0xFF6E6878, true);
                yy += 10;
            }
        }
    }
}
