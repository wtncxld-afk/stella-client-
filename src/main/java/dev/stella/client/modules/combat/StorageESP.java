package dev.stella.client.modules.combat;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Projection;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.Render3D;
import dev.stella.client.render.Tracers;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.scan.BlockScanner;
import dev.stella.client.scan.ScanTarget;
import dev.stella.client.setting.BlockListSetting;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/** Highlights storage block entities in loaded chunks, plus any extra configured blocks. */
public class StorageESP extends Module {
    private record Entry(BlockPos pos, String label, int color, Box box) {}

    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance in blocks", 128, 8, 512, 8));
    private final BoolSetting chests = add(new BoolSetting("Chests", "Normal chests", true));
    private final ColorSetting chestColor = add(new ColorSetting("Chest Color", "", 0xFFF5A524)).visibleWhen(chests::isOn);
    private final BoolSetting trapped = add(new BoolSetting("Trapped Chests", "Trapped chests", true));
    private final ColorSetting trappedColor = add(new ColorSetting("Trapped Color", "", 0xFFF2555A)).visibleWhen(trapped::isOn);
    private final BoolSetting barrels = add(new BoolSetting("Barrels", "Barrels", true));
    private final ColorSetting barrelColor = add(new ColorSetting("Barrel Color", "", 0xFFB7834A)).visibleWhen(barrels::isOn);
    private final BoolSetting shulkers = add(new BoolSetting("Shulker Boxes", "All shulker box colours", true));
    private final ColorSetting shulkerColor = add(new ColorSetting("Shulker Color", "", 0xFFD946EF)).visibleWhen(shulkers::isOn);
    private final BoolSetting enderChests = add(new BoolSetting("Ender Chests", "Ender chests", false));
    private final ColorSetting enderColor = add(new ColorSetting("Ender Color", "", 0xFF2DD4BF)).visibleWhen(enderChests::isOn);
    private final BoolSetting hoppers = add(new BoolSetting("Hoppers", "Hoppers", false));
    private final BoolSetting dispensers = add(new BoolSetting("Dispensers/Droppers", "Dispensers and droppers", false));
    private final ColorSetting otherColor = add(new ColorSetting("Other Color", "Hoppers, dispensers and extra blocks", 0xFF94A3B8));
    private final BlockListSetting extra = add(new BlockListSetting("Extra Blocks", "Additional block IDs to treat as storage"));
    private final BoolSetting fill = add(new BoolSetting("Fill", "Translucent fill", true));
    private final NumberSetting fillAlpha = add(new NumberSetting("Fill Alpha", "Fill opacity", 0.18, 0.02, 0.8, 0.02)).visibleWhen(fill::isOn);
    private final BoolSetting outline = add(new BoolSetting("Outline", "Box outline", true));
    private final NumberSetting lineWidth = add(new NumberSetting("Line Width", "Outline thickness", 2, 1, 5, 0.5)).visibleWhen(outline::isOn);
    private final BoolSetting labels = add(new BoolSetting("Labels", "Show block name", false));
    private final BoolSetting distance = add(new BoolSetting("Distance", "Show distance in labels", true)).visibleWhen(labels::isOn);
    private final NumberSetting maxRendered = add(new NumberSetting("Max Rendered", "Cap on highlighted containers", 600, 20, 5000, 20));
    // tracers
    private final BoolSetting tracers = add(new BoolSetting("Tracers", "Lines from the screen to each highlighted block", false));
    private final ModeSetting tracerOrigin = add(new ModeSetting("Tracer Origin", "Screen point tracers start from",
            "Crosshair", Tracers.ORIGINS)).visibleWhen(tracers::isOn);
    private final NumberSetting originX = add(new NumberSetting("Origin X %", "", 50, 0, 100, 1)).visibleWhen(() -> tracers.isOn() && tracerOrigin.is("Custom"));
    private final NumberSetting originY = add(new NumberSetting("Origin Y %", "", 50, 0, 100, 1)).visibleWhen(() -> tracers.isOn() && tracerOrigin.is("Custom"));
    private final BoolSetting tracerBlockColor = add(new BoolSetting("Tracer Uses Block Color", "Colour tracers like their box", true)).visibleWhen(tracers::isOn);
    private final ColorSetting tracerColor = add(new ColorSetting("Tracer Color", "", 0xC0D8B4FE)).visibleWhen(() -> tracers.isOn() && !tracerBlockColor.isOn());
    private final NumberSetting tracerWidth = add(new NumberSetting("Tracer Width", "Line thickness in screen pixels", 1.5, 0.5, 5, 0.5)).visibleWhen(tracers::isOn);
    private final NumberSetting maxTracers = add(new NumberSetting("Max Tracers", "Tracers to the nearest N blocks", 40, 1, 300, 1)).visibleWhen(tracers::isOn);
    private final BoolSetting tracerFade = add(new BoolSetting("Tracer Distance Fade", "Fade tracers to far blocks", true)).visibleWhen(tracers::isOn);

    private final ScanTarget extraTarget;
    private List<Entry> cache = new ArrayList<>();
    private int timer;

    public StorageESP() {
        super("Storage ESP", "Highlights chests, barrels, shulkers and other storage", Category.DONUTSMP);
        extraTarget = BlockScanner.register(new ScanTarget("Storage/Extra", s -> extra.contains(s.getBlock()), () -> isEnabled() && !extra.get().isEmpty()));
        extra.setListener(extraTarget::invalidate);
    }

    @Override protected void onEnable() { extraTarget.invalidate(); timer = 100; }

    @Override
    public void onTick() {
        if (!inGame() || ++timer < 10) return;
        timer = 0;
        List<Entry> out = new ArrayList<>();
        BlockPos center = mc.player.getBlockPos();
        double r2 = range.get() * range.get();
        int cr = (int) Math.ceil(range.get() / 16.0);
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getPos();
                    if (p.getSquaredDistance(center) > r2) continue;
                    Entry e = classify(be);
                    if (e != null) out.add(e);
                }
            }
        }
        for (BlockPos p : extraTarget.near(center, range.get(), maxRendered.getInt())) {
            out.add(new Entry(p, mc.world.getBlockState(p).getBlock().getName().getString(), otherColor.argb(), new Box(p)));
        }
        out.sort((a, b) -> Double.compare(a.pos.getSquaredDistance(center), b.pos.getSquaredDistance(center)));
        if (out.size() > maxRendered.getInt()) out = new ArrayList<>(out.subList(0, maxRendered.getInt()));
        cache = out;
    }

    private Entry classify(BlockEntity be) {
        BlockPos p = be.getPos();
        Box chestBox = new Box(p.getX() + 0.0625, p.getY(), p.getZ() + 0.0625, p.getX() + 0.9375, p.getY() + 0.875, p.getZ() + 0.9375);
        if (be instanceof TrappedChestBlockEntity) return trapped.isOn() ? new Entry(p, "Trapped Chest", trappedColor.argb(), chestBox) : null;
        if (be instanceof ChestBlockEntity) return chests.isOn() ? new Entry(p, "Chest", chestColor.argb(), chestBox) : null;
        if (be instanceof BarrelBlockEntity) return barrels.isOn() ? new Entry(p, "Barrel", barrelColor.argb(), new Box(p)) : null;
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.isOn() ? new Entry(p, "Shulker Box", shulkerColor.argb(), new Box(p)) : null;
        if (be instanceof EnderChestBlockEntity) return enderChests.isOn() ? new Entry(p, "Ender Chest", enderColor.argb(), chestBox) : null;
        if (be instanceof HopperBlockEntity) return hoppers.isOn() ? new Entry(p, "Hopper", otherColor.argb(), new Box(p)) : null;
        if (be instanceof DispenserBlockEntity) return dispensers.isOn() ? new Entry(p, "Dispenser", otherColor.argb(), new Box(p)) : null;
        return null;
    }

    @Override
    public void onRender3D(Render3D r) {
        r.setLineWidth(lineWidth.getFloat());
        for (Entry e : cache) {
            int fillC = dev.stella.client.util.ColorUtil.withAlpha(e.color, (int) (fillAlpha.get() * 255));
            r.box(e.box, e.color, fillC, outline.isOn(), fill.isOn());
        }
        if (tracers.isOn() && inGame()) {
            java.util.Map<BlockPos, Integer> colors = new java.util.HashMap<>();
            List<BlockPos> targets = new ArrayList<>();
            for (Entry e : cache) { targets.add(e.pos); colors.put(e.pos, e.color); }
            drawTracers(r, targets, p -> colors.getOrDefault(p, otherColor.argb()));
        }
    }

    @Override
    public void onRenderOverlay(DrawContext ctx, float tickDelta) {
        if (!inGame()) return;
        if (!labels.isOn()) return;
        int n = 0;
        for (Entry e : cache) {
            if (n++ > 80) break;
            Vec3d s = Projection.toScreen(Vec3d.ofCenter(e.pos).add(0, 0.6, 0));
            if (!Projection.onScreen(s)) continue;
            String text = e.label;
            if (distance.isOn()) text += " " + (int) Math.sqrt(e.pos.getSquaredDistance(mc.player.getEntityPos())) + "m";
            Render2D.label(ctx, text, s.x, s.y, 0.75f, e.color, 0x90000000);
        }
    }

    private void drawTracers(Render3D r, java.util.List<BlockPos> targets, java.util.function.Function<BlockPos, Integer> colorOf) {
        if (!tracers.isOn() || !inGame()) return;
        Vec3d origin = Tracers.start(tracerOrigin.get(), originX.get(), originY.get());
        int n = 0;
        for (BlockPos p : targets) {
            if (n++ >= maxTracers.getInt()) break;
            int c = tracerBlockColor.isOn() ? (colorOf.apply(p) & 0xFFFFFF) | 0xD0000000 : tracerColor.argb();
            if (tracerFade.isOn()) {
                double d = Math.sqrt(p.getSquaredDistance(mc.player.getEntityPos()));
                c = ColorUtil.mulAlpha(c, 1 - 0.7 * Math.min(1, d / range.get()));
            }
            Tracers.draw(r, origin, Vec3d.ofCenter(p), tracerWidth.getFloat(), c);
        }
    }
}
