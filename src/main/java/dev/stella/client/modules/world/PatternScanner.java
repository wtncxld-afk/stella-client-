package dev.stella.client.modules.world;

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
import dev.stella.client.setting.ModeSetting;
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
 * Heuristic scanner for world features that are <em>consistent with</em> player
 * activity. Findings are hints, never proof: every finding carries the reasons
 * and a confidence level, and natural structures can trigger false positives.
 */
public class PatternScanner extends Module {
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
    private final NumberSetting range = add(new NumberSetting("Range", "Analysis radius in blocks", 128, 16, 512, 16));
    private final ModeSetting minConfidence = add(new ModeSetting("Min Confidence", "Hide findings below this level", "Low", "Low", "Medium", "High"));
    private final BoolSetting labels = add(new BoolSetting("Labels", "Show reason and confidence labels", true));
    private final BoolSetting notify = add(new BoolSetting("Notify", "Notify on new Medium/High findings", true));

    private final ScanTarget redstoneTarget;
    private final ScanTarget clusterTarget;
    private final ScanTarget budTarget;
    private final ScanTarget deepslateTarget;
    private List<Finding> findings = new ArrayList<>();
    private final Set<String> notified = new HashSet<>();
    private int timer;

    private static final Set<Block> REDSTONE = Set.of(
            Blocks.REDSTONE_WIRE, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON,
            Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_LAMP,
            Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER, Blocks.LEVER, Blocks.DAYLIGHT_DETECTOR, Blocks.TARGET,
            Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK, Blocks.CRAFTER);

    public PatternScanner() {
        super("Pattern Scanner", "Flags suspicious redstone, grown amethyst and rotated deepslate (hints, not proof)", Category.WORLD);
        redstoneTarget = BlockScanner.register(new ScanTarget("Pattern/Redstone", s -> REDSTONE.contains(s.getBlock()), () -> isEnabled() && redstone.isOn()));
        clusterTarget = BlockScanner.register(new ScanTarget("Pattern/Cluster", s -> s.isOf(Blocks.AMETHYST_CLUSTER), () -> isEnabled() && amethyst.isOn()));
        budTarget = BlockScanner.register(new ScanTarget("Pattern/Buds", s -> s.isOf(Blocks.SMALL_AMETHYST_BUD) || s.isOf(Blocks.MEDIUM_AMETHYST_BUD) || s.isOf(Blocks.LARGE_AMETHYST_BUD), () -> isEnabled() && amethyst.isOn()));
        deepslateTarget = BlockScanner.register(new ScanTarget("Pattern/Deepslate",
                s -> s.isOf(Blocks.DEEPSLATE) && s.contains(Properties.AXIS) && s.get(Properties.AXIS) != Direction.Axis.Y,
                () -> isEnabled() && deepslate.isOn()));
        redstone.onChange(redstoneTarget::invalidate);
        amethyst.onChange(() -> { clusterTarget.invalidate(); budTarget.invalidate(); });
        deepslate.onChange(deepslateTarget::invalidate);
    }

    public List<Finding> findings() { return findings; }

    @Override
    protected void onEnable() {
        redstoneTarget.invalidate(); clusterTarget.invalidate(); budTarget.invalidate(); deepslateTarget.invalidate();
        findings = new ArrayList<>();
    }

    @Override
    public void onTick() {
        if (!inGame() || ++timer < 40) return;
        timer = 0;
        BlockPos center = mc.player.getBlockPos();
        double r = range.get();
        List<Finding> out = new ArrayList<>();
        if (redstone.isOn()) analyseRedstone(redstoneTarget.near(center, r, 5000), out);
        if (amethyst.isOn()) analyseAmethyst(clusterTarget.near(center, r, 5000), budTarget.near(center, r, 5000), out);
        if (deepslate.isOn()) analyseDeepslate(deepslateTarget.near(center, r, 5000), out);
        Confidence min = Confidence.valueOf(minConfidence.get().toUpperCase());
        out.removeIf(f -> f.confidence.ordinal() < min.ordinal());
        findings = out;
        if (notify.isOn()) {
            for (Finding f : out) {
                if (f.confidence == Confidence.LOW || !notified.add(f.key())) continue;
                NotificationManager.post("Pattern Scanner",
                        f.type + " (" + f.confidence.name().toLowerCase() + ") at " + (int) f.center().x + " " + (int) f.center().y + " " + (int) f.center().z,
                        f.confidence == Confidence.HIGH ? Notification.Type.WARNING : Notification.Type.INFO);
            }
        }
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

    private static int color(Confidence c) {
        return switch (c) {
            case HIGH -> 0xFFF2777A;
            case MEDIUM -> 0xFFF5D76E;
            case LOW -> 0xFF8FB3FF;
        };
    }

    @Override
    public void onRender3D(Render3D r) {
        for (Finding f : findings) {
            Box box = new Box(f.min.getX(), f.min.getY(), f.min.getZ(), f.max.getX() + 1, f.max.getY() + 1, f.max.getZ() + 1).expand(0.05);
            int c = color(f.confidence);
            r.box(box, c, (c & 0xFFFFFF) | 0x20000000, true, true);
        }
    }

    @Override
    public void onRenderOverlay(DrawContext ctx, float tickDelta) {
        if (!labels.isOn() || !inGame()) return;
        for (Finding f : findings) {
            Vec3d s = Projection.toScreen(f.center());
            if (!Projection.onScreen(s)) continue;
            int dist = (int) Math.sqrt(mc.player.squaredDistanceTo(f.center()));
            Render2D.label(ctx, f.type + " - " + f.confidence.name() + " - " + dist + "m", s.x, s.y - 10, 0.8f, color(f.confidence), 0xA0000000);
            Render2D.label(ctx, f.reasons.get(0), s.x, s.y, 0.65f, 0xFFE6E1EE, 0xA0000000);
        }
    }
}
