package dev.stella.client.modules.combat;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.render.Projection;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.Render3D;
import dev.stella.client.scan.BlockScanner;
import dev.stella.client.scan.ScanTarget;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.render.Tracers;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sus Chunk Finder: flags chunks whose loaded blocks are <em>consistent with</em>
 * player activity (redstone builds, fully grown amethyst, sideways deepslate)
 * and draws a red box over each suspected chunk. Findings are hints, never
 * proof: every chunk carries its reasons and a confidence level, and natural
 * structures can trigger false positives.
 */
public class SusChunkFinder extends Module {
    public enum Confidence { LOW, MEDIUM, HIGH }

    public record Finding(String type, BlockPos min, BlockPos max, int count, Confidence confidence, List<String> reasons) {
        public Vec3d center() {
            return new Vec3d((min.getX() + max.getX() + 1) / 2.0, (min.getY() + max.getY() + 1) / 2.0, (min.getZ() + max.getZ() + 1) / 2.0);
        }
        public String key() { return type + "@" + (min.getX() >> 3) + "," + (min.getY() >> 3) + "," + (min.getZ() >> 3); }
    }

    private final BoolSetting redstone = add(new BoolSetting("Redstone", "Look for clusters of redstone components", true));
    private final BoolSetting amethyst = add(new BoolSetting("Grown Amethyst", "Look for geodes with many fully grown clusters", true));
    private final BoolSetting deepslate = add(new BoolSetting("Rotated Deepslate", "Look for deepslate placed sideways", true));
    private final BoolSetting unnatural = add(new BoolSetting("Unnatural Blocks", "Look for blocks world generation never places (concrete, observers...)", true));
    private final dev.stella.client.setting.BlockListSetting unnaturalBlocks = add(new dev.stella.client.setting.BlockListSetting("Unnatural Block List",
            "Blocks that only players place. Edit to add your own", defaultUnnatural())).visibleWhen(unnatural::isOn);
    private final NumberSetting range = add(new NumberSetting("Range", "Analysis radius in blocks", 128, 16, 512, 16));
    private final BoolSetting belowY = add(new BoolSetting("Only Below Y", "Only count suspicious blocks at or below Max Y", true));
    private final NumberSetting maxY = add(new NumberSetting("Max Y", "Highest Y-level that is checked", 0, -64, 320, 1)).visibleWhen(belowY::isOn);
    private final NumberSetting minYSetting = add(new NumberSetting("Min Y", "Lowest Y-level that is checked", -64, -64, 320, 1));
    private final ModeSetting minConfidence = add(new ModeSetting("Min Confidence", "Hide findings below this level", "Low", "Low", "Medium", "High"));
    private final ModeSetting boxMode = add(new ModeSetting("Box Mode", "Flat translucent square at a Y-level, a column around the detections, or the full chunk height",
            "Flat", "Flat", "Column", "Full Height"));
    private final NumberSetting flatY = add(new NumberSetting("Flat Y", "Y-level of the flat square (Flat mode)", 64, -64, 320, 1)).visibleWhen(() -> boxMode.is("Flat"));
    private final BoolSetting flatAtPlayer = add(new BoolSetting("Flat At Player Y", "Put the flat square at your feet instead of Flat Y", false)).visibleWhen(() -> boxMode.is("Flat"));
    private final ColorSetting color = add(new ColorSetting("Color", "Chunk fill colour (alpha = opacity)", 0xB4FF4A33));
    private final BoolSetting outline = add(new BoolSetting("Outline", "Draw an outline around each chunk", false));
    private final BoolSetting confidenceFill = add(new BoolSetting("Confidence Opacity", "Fainter squares for lower confidence", false));
    private final BoolSetting showBlocks = add(new BoolSetting("Show Blocks", "Also outline the exact suspicious blocks", false));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", "Lines to each sus chunk", false));
    private final NumberSetting tracerWidth = add(new NumberSetting("Tracer Width", "Line thickness in screen pixels", 2, 0.5, 5, 0.5)).visibleWhen(tracers::isOn);
    private final BoolSetting labels = add(new BoolSetting("Labels", "Show reason and confidence labels", false));
    private final BoolSetting notify = add(new BoolSetting("Notify", "Notify on new Medium/High findings", true));

    private final ScanTarget redstoneTarget;
    private final ScanTarget clusterTarget;
    private final ScanTarget budTarget;
    private final ScanTarget deepslateTarget;
    private final ScanTarget unnaturalTarget;
    private List<Finding> findings = new ArrayList<>();
    private final Set<String> notified = new HashSet<>();
    private int timer;

    private static final Set<Block> REDSTONE = Set.of(
            Blocks.REDSTONE_WIRE, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON,
            Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_LAMP,
            Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER, Blocks.LEVER, Blocks.DAYLIGHT_DETECTOR, Blocks.TARGET,
            Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK, Blocks.CRAFTER);

    public SusChunkFinder() {
        super("Sus Chunk Finder", "Red boxes over chunks with suspicious redstone, grown amethyst or sideways deepslate (hints, not proof)", Category.DONUTSMP);
        redstoneTarget = BlockScanner.register(new ScanTarget("Sus/Redstone", s -> REDSTONE.contains(s.getBlock()), () -> isEnabled() && redstone.isOn()));
        clusterTarget = BlockScanner.register(new ScanTarget("Sus/Cluster", s -> s.isOf(Blocks.AMETHYST_CLUSTER), () -> isEnabled() && amethyst.isOn()));
        budTarget = BlockScanner.register(new ScanTarget("Sus/Buds", s -> s.isOf(Blocks.SMALL_AMETHYST_BUD) || s.isOf(Blocks.MEDIUM_AMETHYST_BUD) || s.isOf(Blocks.LARGE_AMETHYST_BUD), () -> isEnabled() && amethyst.isOn()));
        deepslateTarget = BlockScanner.register(new ScanTarget("Sus/Deepslate",
                s -> s.isOf(Blocks.DEEPSLATE) && s.contains(Properties.AXIS) && s.get(Properties.AXIS) != Direction.Axis.Y,
                () -> isEnabled() && deepslate.isOn()));
        unnaturalTarget = BlockScanner.register(new ScanTarget("Sus/Unnatural", st -> unnaturalBlocks.contains(st.getBlock()), () -> isEnabled() && unnatural.isOn()));
        unnaturalBlocks.setListener(unnaturalTarget::invalidate);
        unnatural.onChange(unnaturalTarget::invalidate);
        redstone.onChange(redstoneTarget::invalidate);
        amethyst.onChange(() -> { clusterTarget.invalidate(); budTarget.invalidate(); });
        deepslate.onChange(deepslateTarget::invalidate);
    }

    public List<Finding> findings() { return findings; }

    @Override
    protected void onEnable() {
        redstoneTarget.invalidate(); clusterTarget.invalidate(); budTarget.invalidate(); deepslateTarget.invalidate();
        unnaturalTarget.invalidate();
        findings = new ArrayList<>();
    }

    @Override
    public void onTick() {
        if (!inGame() || ++timer < 40) return;
        timer = 0;
        BlockPos center = mc.player.getBlockPos();
        double r = range.get();
        List<Finding> out = new ArrayList<>();
        if (redstone.isOn()) analyseRedstone(inY(redstoneTarget.near(center, r, 5000)), out);
        if (amethyst.isOn()) analyseAmethyst(inY(clusterTarget.near(center, r, 5000)), inY(budTarget.near(center, r, 5000)), out);
        if (deepslate.isOn()) analyseDeepslate(inY(deepslateTarget.near(center, r, 5000)), out);
        if (unnatural.isOn()) analyseUnnatural(inY(unnaturalTarget.near(center, r, 5000)), out);
        Confidence min = Confidence.valueOf(minConfidence.get().toUpperCase());
        out.removeIf(f -> f.confidence.ordinal() < min.ordinal());
        findings = out;
        if (notify.isOn()) {
            for (Finding f : out) {
                if (f.confidence == Confidence.LOW || !notified.add(f.key())) continue;
                NotificationManager.post("Sus Chunk Finder",
                        f.type + " (" + f.confidence.name().toLowerCase() + ") at " + (int) f.center().x + " " + (int) f.center().y + " " + (int) f.center().z,
                        f.confidence == Confidence.HIGH ? Notification.Type.WARNING : Notification.Type.INFO);
            }
        }
    }

    /** Applies the user's Y-level window. */
    private List<BlockPos> inY(List<BlockPos> list) {
        int lo = minYSetting.getInt(), hi = belowY.isOn() ? maxY.getInt() : Integer.MAX_VALUE;
        list.removeIf(p -> p.getY() < lo || p.getY() > hi);
        return list;
    }

    /** Groups positions into 16x16x16 buckets. */
    private static Map<Long, List<BlockPos>> bucket(List<BlockPos> list) {
        Map<Long, List<BlockPos>> m = new HashMap<>();
        for (BlockPos p : list) {
            long k = BlockPos.asLong(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4);
            m.computeIfAbsent(k, x -> new ArrayList<>()).add(p);
        }
        return m;
    }

    private static BlockPos[] bounds(List<BlockPos> ps) {
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
        for (BlockPos p : ps) {
            x1 = Math.min(x1, p.getX()); y1 = Math.min(y1, p.getY()); z1 = Math.min(z1, p.getZ());
            x2 = Math.max(x2, p.getX()); y2 = Math.max(y2, p.getY()); z2 = Math.max(z2, p.getZ());
        }
        return new BlockPos[]{new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2)};
    }

    private void analyseRedstone(List<BlockPos> list, List<Finding> out) {
        for (List<BlockPos> group : bucket(list).values()) {
            Set<Block> kinds = new HashSet<>();
            for (BlockPos p : group) kinds.add(mc.world.getBlockState(p).getBlock());
            int n = group.size();
            if (n < 3) continue;
            List<String> reasons = new ArrayList<>();
            reasons.add(n + " redstone components, " + kinds.size() + " types");
            Confidence c = Confidence.LOW;
            if (n >= 6 && kinds.size() >= 3) c = Confidence.MEDIUM;
            if (n >= 14 && kinds.size() >= 4) c = Confidence.HIGH;
            if (kinds.contains(Blocks.OBSERVER) || kinds.contains(Blocks.COMPARATOR)) {
                reasons.add("observers/comparators do not generate naturally");
                if (c == Confidence.LOW) c = Confidence.MEDIUM;
            } else {
                reasons.add("could be a natural structure (temple, trial chamber, mineshaft)");
            }
            BlockPos[] b = bounds(group);
            out.add(new Finding("Redstone", b[0], b[1], n, c, reasons));
        }
    }

    private void analyseAmethyst(List<BlockPos> clusters, List<BlockPos> buds, List<Finding> out) {
        Map<Long, List<BlockPos>> budBuckets = bucket(buds);
        for (Map.Entry<Long, List<BlockPos>> e : bucket(clusters).entrySet()) {
            int mature = e.getValue().size();
            int small = budBuckets.getOrDefault(e.getKey(), List.of()).size();
            if (mature < 4) continue;
            double ratio = mature / (double) Math.max(1, mature + small);
            List<String> reasons = new ArrayList<>();
            reasons.add(mature + " mature clusters vs " + small + " buds (" + (int) (ratio * 100) + "% mature)");
            reasons.add("clusters only grow while the chunk is loaded; geodes also generate some mature clusters");
            Confidence c = ratio > 0.8 && mature >= 12 ? Confidence.MEDIUM : Confidence.LOW;
            BlockPos[] b = bounds(e.getValue());
            out.add(new Finding("Grown Amethyst", b[0], b[1], mature, c, reasons));
        }
    }

    /** Blocks no world-generation feature or structure places in the Overworld or Nether. */
    private static String[] defaultUnnatural() {
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
                "cyan", "purple", "blue", "brown", "green", "red", "black"};
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String c : colors) { ids.add("minecraft:" + c + "_concrete"); ids.add("minecraft:" + c + "_concrete_powder"); }
        ids.addAll(java.util.List.of("minecraft:observer", "minecraft:comparator", "minecraft:redstone_block", "minecraft:slime_block",
                "minecraft:honey_block", "minecraft:crafter", "minecraft:target", "minecraft:hopper", "minecraft:dropper",
                "minecraft:daylight_detector", "minecraft:note_block", "minecraft:jukebox", "minecraft:beacon",
                "minecraft:enchanting_table", "minecraft:ender_chest", "minecraft:netherite_block", "minecraft:diamond_block",
                "minecraft:emerald_block", "minecraft:lapis_block", "minecraft:iron_block", "minecraft:respawn_anchor",
                "minecraft:lodestone", "minecraft:conduit"));
        return ids.toArray(new String[0]);
    }

    private void analyseUnnatural(List<BlockPos> list, List<Finding> out) {
        if (mc.world != null && mc.world.getRegistryKey() == net.minecraft.world.World.END) return; // End cities are natural there
        for (List<BlockPos> group : bucket(list).values()) {
            int n = group.size();
            java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
            for (BlockPos p : group) kinds.merge(mc.world.getBlockState(p).getBlock().getName().getString(), 1, Integer::sum);
            StringBuilder names = new StringBuilder();
            int shown = 0;
            for (var e : kinds.entrySet()) {
                if (shown++ == 3) { names.append(", ..."); break; }
                if (names.length() > 0) names.append(", ");
                names.append(e.getValue()).append("x ").append(e.getKey());
            }
            List<String> reasons = new ArrayList<>();
            reasons.add(n + " player-made block" + (n == 1 ? "" : "s") + ": " + names);
            reasons.add("these blocks are never placed by world generation");
            Confidence c = n >= 4 || kinds.size() >= 2 ? Confidence.HIGH : Confidence.MEDIUM;
            BlockPos[] b = bounds(group);
            out.add(new Finding("Unnatural Blocks", b[0], b[1], n, c, reasons));
        }
    }

    private void analyseDeepslate(List<BlockPos> list, List<Finding> out) {
        for (List<BlockPos> group : bucket(list).values()) {
            int n = group.size();
            List<String> reasons = new ArrayList<>();
            reasons.add(n + " deepslate block" + (n == 1 ? "" : "s") + " with a horizontal axis");
            reasons.add("world generation places deepslate vertically; sideways deepslate is usually player-placed");
            Confidence c = n >= 3 ? Confidence.HIGH : Confidence.MEDIUM;
            BlockPos[] b = bounds(group);
            out.add(new Finding("Rotated Deepslate", b[0], b[1], n, c, reasons));
        }
    }

    /** One suspected chunk with all of its findings. */
    public record SusChunk(int cx, int cz, int minY, int maxY, Confidence confidence, List<Finding> findings) {}

    private List<SusChunk> chunks() {
        Map<Long, List<Finding>> byChunk = new HashMap<>();
        for (Finding f : findings) {
            Vec3d c = f.center();
            long key = net.minecraft.util.math.ChunkPos.toLong((int) Math.floor(c.x) >> 4, (int) Math.floor(c.z) >> 4);
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }
        List<SusChunk> out = new ArrayList<>();
        for (Map.Entry<Long, List<Finding>> e : byChunk.entrySet()) {
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            Confidence best = Confidence.LOW;
            for (Finding f : e.getValue()) {
                minY = Math.min(minY, f.min.getY());
                maxY = Math.max(maxY, f.max.getY());
                if (f.confidence.ordinal() > best.ordinal()) best = f.confidence;
            }
            // several independent signals in one chunk raise confidence
            if (e.getValue().size() >= 2 && best == Confidence.MEDIUM) best = Confidence.HIGH;
            out.add(new SusChunk(net.minecraft.util.math.ChunkPos.getPackedX(e.getKey()), net.minecraft.util.math.ChunkPos.getPackedZ(e.getKey()),
                    minY, maxY, best, e.getValue()));
        }
        return out;
    }

    private double alphaFor(Confidence c) {
        if (!confidenceFill.isOn()) return 1;
        return switch (c) { case HIGH -> 1.0; case MEDIUM -> 0.7; case LOW -> 0.45; };
    }

    private Box chunkBox(SusChunk c) {
        double x1 = c.cx << 4, z1 = c.cz << 4;
        double y1, y2;
        switch (boxMode.get()) {
            case "Flat" -> {
                double y = flatAtPlayer.isOn() && mc.player != null ? Math.floor(mc.player.getY()) : flatY.get();
                y1 = y; y2 = y + 0.05;
            }
            case "Full Height" -> { y1 = mc.world.getBottomY(); y2 = mc.world.getTopYInclusive() + 1; }
            default -> { y1 = c.minY - 1; y2 = c.maxY + 3; }
        }
        return new Box(x1, y1, z1, x1 + 16, y2, z1 + 16);
    }

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame()) return;
        r.setLineWidth(2.5f);
        Vec3d origin = Tracers.start("Crosshair", 50, 50);
        for (SusChunk c : chunks()) {
            if (tracers.isOn()) {
                Box tb = chunkBox(c);
                Vec3d center = new Vec3d((tb.minX + tb.maxX) / 2, Math.min(tb.maxY, Math.max(tb.minY, mc.player.getY())) + 1, (tb.minZ + tb.maxZ) / 2);
                Tracers.draw(r, origin, center, tracerWidth.getFloat(), ColorUtil.mulAlpha(color.argb(), 0.85));
            }
            double a = alphaFor(c.confidence);
            int outline = ColorUtil.mulAlpha(color.argb(), Math.max(0.6, a));
            int fill = ColorUtil.mulAlpha(color.argb(), a);
            Box box = chunkBox(c);
            if (boxMode.is("Flat")) {
                r.quadXZ(box.minX, box.minZ, box.maxX, box.maxZ, box.minY, fill);
                if (this.outline.isOn()) r.boxOutline(new Box(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ), outline);
            } else {
                // never tint the whole screen: inside a sus chunk only the outline is drawn
                boolean inside = box.expand(0.5).contains(r.cameraPos());
                r.box(box, outline, fill, this.outline.isOn() || inside, !inside);
            }
            if (showBlocks.isOn()) {
                for (Finding f : c.findings) {
                    Box b = new Box(f.min.getX(), f.min.getY(), f.min.getZ(), f.max.getX() + 1, f.max.getY() + 1, f.max.getZ() + 1).expand(0.03);
                    r.box(b, ColorUtil.withAlpha(color.argb(), 0xE0), 0, true, false);
                }
            }
        }
    }

    @Override
    public void onRenderOverlay(DrawContext ctx, float tickDelta) {
        if (!inGame()) return;
        for (SusChunk c : chunks()) {
            Box b = chunkBox(c);
            Vec3d center = new Vec3d((b.minX + b.maxX) / 2, Math.min(b.maxY, Math.max(b.minY, mc.player.getY())) + 1, (b.minZ + b.maxZ) / 2);
            if (!labels.isOn()) continue;
            Vec3d s = Projection.toScreen(center.add(0, 1.5, 0));
            if (!Projection.onScreen(s)) continue;
            int dist = (int) Math.sqrt(mc.player.squaredDistanceTo(center));
            Render2D.label(ctx, "SUS CHUNK  " + c.confidence.name() + "  " + dist + "m", s.x, s.y - 11, 0.85f, 0xFFFF5555, 0xB0000000);
            int line = 0;
            for (Finding f : c.findings) {
                if (line >= 3) break;
                Render2D.label(ctx, f.type + ": " + f.reasons.get(0), s.x, s.y + line * 8, 0.6f, 0xFFF2E6E6, 0xB0000000);
                line++;
            }
        }
    }
}
