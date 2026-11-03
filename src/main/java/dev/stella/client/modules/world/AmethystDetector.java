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
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.Sounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Highlights fully grown amethyst clusters, optionally smaller buds in separate colours. */
public class AmethystDetector extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Render distance in blocks", 96, 8, 512, 8));
    private final BoolSetting showBuds = add(new BoolSetting("Show Buds", "Also highlight small / medium / large buds", false));
    private final ColorSetting clusterColor = add(new ColorSetting("Cluster Color", "Mature cluster colour", 0xFFD946EF));
    private final ColorSetting largeColor = add(new ColorSetting("Large Bud Color", "Large bud colour", 0xFFA78BFA)).visibleWhen(showBuds::isOn);
    private final ColorSetting smallColor = add(new ColorSetting("Small/Medium Color", "Small and medium bud colour", 0xFF6D5A9E)).visibleWhen(showBuds::isOn);
    private final BoolSetting fill = add(new BoolSetting("Fill", "Translucent box fill", true));
    private final BoolSetting labels = add(new BoolSetting("Labels", "Show type and distance labels", false));
    private final BoolSetting notify = add(new BoolSetting("Notify", "Notify when new mature clusters are found", true));
    private final NumberSetting maxRendered = add(new NumberSetting("Max Rendered", "Cap on highlighted blocks", 400, 20, 4000, 20));

    private final ScanTarget target;
    private int foundSinceAlert;
    private long lastAlert;

    public AmethystDetector() {
        super("Amethyst Detector", "Detects fully grown amethyst clusters (and optionally buds)", Category.WORLD);
        target = BlockScanner.register(new ScanTarget("Amethyst", this::matches, this::isEnabled).listener(this::onFound));
        showBuds.onChange(target::invalidate);
    }

    private boolean matches(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.AMETHYST_CLUSTER) return true;
        return showBuds.isOn() && (b == Blocks.LARGE_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.SMALL_AMETHYST_BUD);
    }

    private void onFound(BlockPos pos, BlockState state) {
        if (state.getBlock() == Blocks.AMETHYST_CLUSTER) foundSinceAlert++;
    }

    @Override protected void onEnable() { target.invalidate(); foundSinceAlert = 0; }

    @Override
    public void onTick() {
        if (!notify.isOn() || foundSinceAlert == 0) return;
        long now = System.currentTimeMillis();
        if (now - lastAlert > 5000) {
            NotificationManager.post("Amethyst Detector", foundSinceAlert + " mature cluster" + (foundSinceAlert == 1 ? "" : "s") + " detected", Notification.Type.INFO);
            Sounds.ping(1.4f);
            foundSinceAlert = 0;
            lastAlert = now;
        }
    }

    private int colorFor(Block b) {
        if (b == Blocks.AMETHYST_CLUSTER) return clusterColor.argb();
        if (b == Blocks.LARGE_AMETHYST_BUD) return largeColor.argb();
        return smallColor.argb();
    }

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame()) return;
        for (BlockPos p : target.near(mc.player.getBlockPos(), range.get(), maxRendered.getInt())) {
            int c = colorFor(mc.world.getBlockState(p).getBlock());
            Box box = new Box(p).contract(0.2);
            r.box(box, c, (c & 0xFFFFFF) | 0x30000000, true, fill.isOn());
        }
    }

    @Override
    public void onRenderOverlay(DrawContext ctx, float tickDelta) {
        if (!labels.isOn() || !inGame()) return;
        List<BlockPos> list = target.near(mc.player.getBlockPos(), range.get(), 60);
        for (BlockPos p : list) {
            Vec3d s = Projection.toScreen(Vec3d.ofCenter(p));
            if (!Projection.onScreen(s)) continue;
            Block b = mc.world.getBlockState(p).getBlock();
            String kind = b == Blocks.AMETHYST_CLUSTER ? "Cluster" : b == Blocks.LARGE_AMETHYST_BUD ? "Large bud" : b == Blocks.MEDIUM_AMETHYST_BUD ? "Medium bud" : "Small bud";
            int dist = (int) Math.sqrt(p.getSquaredDistance(mc.player.getEntityPos()));
            Render2D.label(ctx, kind + " " + dist + "m", s.x, s.y - 4, 0.8f, colorFor(b), 0x90000000);
        }
    }
}
