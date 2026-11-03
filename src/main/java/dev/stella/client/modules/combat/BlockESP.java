package dev.stella.client.modules.combat;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Projection;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.Render3D;
import dev.stella.client.render.Tracers;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.scan.BlockScanner;
import dev.stella.client.scan.ScanTarget;
import dev.stella.client.setting.BlockListSetting;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class BlockESP extends Module {
    private final BlockListSetting blocks = add(new BlockListSetting("Blocks", "Block IDs to highlight", "minecraft:spawner"));
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance", 96, 8, 512, 8));
    private final ColorSetting color = add(new ColorSetting("Color", "Outline colour", 0xFFA855F7));
    private final BoolSetting outline = add(new BoolSetting("Outline", "Draw outlines", true));
    private final NumberSetting lineWidth = add(new NumberSetting("Line Width", "Outline thickness", 2, 1, 5, 0.5)).visibleWhen(outline::isOn);
    private final BoolSetting fill = add(new BoolSetting("Fill", "Translucent fill", true));
    private final NumberSetting fillAlpha = add(new NumberSetting("Fill Alpha", "Fill opacity", 0.2, 0.02, 0.8, 0.02)).visibleWhen(fill::isOn);
    private final BoolSetting labels = add(new BoolSetting("Labels", "Block name and distance", false));
    private final NumberSetting maxRendered = add(new NumberSetting("Max Rendered", "Cap on highlighted blocks", 500, 10, 5000, 10));
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

    private final ScanTarget target;

    public BlockESP() {
        super("Block ESP", "Highlights the blocks you choose", Category.DONUTSMP);
        target = BlockScanner.register(new ScanTarget("Block ESP", s -> blocks.contains(s.getBlock()), this::isEnabled));
        blocks.setListener(target::invalidate);
    }

    @Override protected void onEnable() { target.invalidate(); }

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame()) return;
        r.setLineWidth(lineWidth.getFloat());
        int fc = ColorUtil.withAlpha(color.argb(), (int) (fillAlpha.get() * 255));
        for (BlockPos p : target.near(mc.player.getBlockPos(), range.get(), maxRendered.getInt())) {
            r.box(new Box(p), color.argb(), fc, outline.isOn(), fill.isOn());
        }
        if (tracers.isOn()) drawTracers(r, target.near(mc.player.getBlockPos(), range.get(), maxTracers.getInt()), p -> color.argb());
    }

    @Override
    public void onRenderOverlay(DrawContext ctx, float tickDelta) {
        if (!inGame()) return;
        if (!labels.isOn()) return;
        List<BlockPos> list = target.near(mc.player.getBlockPos(), range.get(), 60);
        for (BlockPos p : list) {
            Vec3d s = Projection.toScreen(Vec3d.ofCenter(p));
            if (!Projection.onScreen(s)) continue;
            String name = mc.world.getBlockState(p).getBlock().getName().getString();
            Render2D.label(ctx, name + " " + (int) Math.sqrt(p.getSquaredDistance(mc.player.getPos())) + "m", s.x, s.y, 0.75f, color.argb(), 0x90000000);
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
                double d = Math.sqrt(p.getSquaredDistance(mc.player.getPos()));
                c = ColorUtil.mulAlpha(c, 1 - 0.7 * Math.min(1, d / range.get()));
            }
            Tracers.draw(r, origin, Vec3d.ofCenter(p), tracerWidth.getFloat(), c);
        }
    }
}
