package dev.stella.client.modules.world;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Projection;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.Render3D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;

/**
 * Light Debug (default): highlights the top of every block lit by a light source, coloured from dim to
 * bright by light level, optionally with the number. Mob Spawns mode instead marks where hostile mobs can spawn. Since 1.18 they need block light 0:
 * red = block light 0 (spawns whenever it's dark), yellow = block light 0 but open sky (only at night),
 * green = lit (safe). Scans a few times a second; drawing reuses the cached result.
 */
public class LightLevelOverlay extends Module {
    // Yarn leaves the enum constants unnamed in 1.21.11: SKY is first, BLOCK second.
    private static final LightType SKY = LightType.values()[0];
    private static final LightType BLOCK = LightType.values()[1];

    private final ModeSetting mode = add(new ModeSetting("Mode",
            "Light Debug: highlight every block lit by a light source, coloured by brightness. Mob Spawns: mark where mobs can spawn",
            "Light Debug", "Light Debug", "Mob Spawns"));
    private final NumberSetting minLevel = add(new NumberSetting("Minimum Light", "Dimmest light level that is highlighted (Light Debug)", 1, 1, 15, 1))
            .visibleWhen(() -> mode.is("Light Debug"));
    private final BoolSetting includeSky = add(new BoolSetting("Include Sunlight", "Also count sky light (otherwise only light from torches, lava, glowstone...)", false))
            .visibleWhen(() -> mode.is("Light Debug"));
    private final ColorSetting dimColor = add(new ColorSetting("Dim Color", "Colour of the weakest light", 0xFF7A3CFF)).visibleWhen(() -> mode.is("Light Debug"));
    private final ColorSetting brightColor = add(new ColorSetting("Bright Color", "Colour of full light (level 15)", 0xFFFFE066)).visibleWhen(() -> mode.is("Light Debug"));
    private final NumberSetting radius = add(new NumberSetting("Radius", "Horizontal scan radius in blocks", 16, 4, 32, 1));
    private final NumberSetting height = add(new NumberSetting("Height", "Blocks above and below you that are scanned", 8, 2, 24, 1));
    private final ModeSetting style = add(new ModeSetting("Style", "Coloured squares, the light number, or both", "Squares", "Squares", "Numbers", "Both"));
    private final BoolSetting showSafe = add(new BoolSetting("Show Safe Blocks", "Also mark lit (safe) blocks in green", false)).visibleWhen(() -> mode.is("Mob Spawns"));
    private final BoolSetting showNight = add(new BoolSetting("Show Night-Only", "Mark blocks where mobs spawn only at night (open sky) in yellow", true)).visibleWhen(() -> mode.is("Mob Spawns"));
    private final ColorSetting dangerColor = add(new ColorSetting("Danger Color", "Mobs can spawn whenever it's dark", 0xFFFF3B3B)).visibleWhen(() -> mode.is("Mob Spawns"));
    private final ColorSetting nightColor = add(new ColorSetting("Night Color", "Mobs spawn here only at night", 0xFFFFC53D)).visibleWhen(() -> mode.is("Mob Spawns") && showNight.isOn());
    private final ColorSetting safeColor = add(new ColorSetting("Safe Color", "Lit, no spawns", 0xFF3BE37A)).visibleWhen(() -> mode.is("Mob Spawns") && showSafe.isOn());
    private final NumberSetting opacity = add(new NumberSetting("Opacity", "How see-through the squares are", 0.45, 0.05, 1, 0.05));

    private record Spot(BlockPos pos, int block, int sky, int color) {}

    private List<Spot> spots = new ArrayList<>();
    private int timer;

    public LightLevelOverlay() { super("Light Level Overlay", "Light debug: highlights every block lit by a light source, coloured by brightness (or shows mob spawns)", Category.WORLD); }

    @Override protected void onEnable() { timer = 100; spots = new ArrayList<>(); }

    @Override
    public void onTick() {
        if (!inGame() || ++timer < 5) return;
        timer = 0;
        List<Spot> out = new ArrayList<>();
        BlockPos me = mc.player.getBlockPos();
        int r = radius.getInt(), h = height.getInt();
        BlockPos.Mutable p = new BlockPos.Mutable(), below = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int dy = -h; dy <= h; dy++) {
                    p.set(me.getX() + dx, me.getY() + dy, me.getZ() + dz);
                    BlockState state = mc.world.getBlockState(p);
                    if (!state.getCollisionShape(mc.world, p).isEmpty() || !state.getFluidState().isEmpty()) continue; // needs room for a mob
                    below.set(p.getX(), p.getY() - 1, p.getZ());
                    BlockState floor = mc.world.getBlockState(below);
                    if (!floor.isSideSolidFullSquare(mc.world, below, Direction.UP)) continue; // needs a solid floor
                    int block = mc.world.getLightLevel(BLOCK, p), sky = mc.world.getLightLevel(SKY, p);
                    int color;
                    if (mode.is("Light Debug")) {
                        int level = includeSky.isOn() ? Math.max(block, sky) : block;
                        if (level < minLevel.getInt()) continue;
                        color = ColorUtil.lerp(dimColor.argb(), brightColor.argb(), (level - 1) / 14.0);
                        out.add(new Spot(p.toImmutable(), level, sky, color));
                        if (out.size() > 6000) break;
                        continue;
                    }
                    if (block > 0) { if (!showSafe.isOn()) continue; color = safeColor.argb(); }
                    else if (sky > 7) { if (!showNight.isOn()) continue; color = nightColor.argb(); }
                    else color = dangerColor.argb();
                    out.add(new Spot(p.toImmutable(), block, sky, color));
                    if (out.size() > 6000) break;
                }
            }
        }
        spots = out;
        lastCount = out.size();
    }

    /** Diagnostics for the smoke test. */
    public static volatile int lastCount;

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame() || style.is("Numbers")) return;
        for (Spot s : spots) {
            double x = s.pos.getX(), y = s.pos.getY() + 0.02, z = s.pos.getZ();
            r.quadXZDepth(x + 0.1, z + 0.1, x + 0.9, z + 0.9, y, ColorUtil.withAlpha(s.color, (int) (255 * opacity.get())));
        }
    }

    @Override
    public void onRenderOverlay(DrawContext ctx, float tickDelta) {
        if (!inGame() || style.is("Squares")) return;
        int n = 0;
        for (Spot s : spots) {
            if (mc.player.getBlockPos().getSquaredDistance(s.pos) > 12 * 12) continue; // numbers only up close
            if (!mc.world.raycast(new net.minecraft.world.RaycastContext(mc.player.getEyePos(), Vec3d.ofCenter(s.pos),
                    net.minecraft.world.RaycastContext.ShapeType.values()[2], net.minecraft.world.RaycastContext.FluidHandling.values()[0], mc.player))
                    .getType().equals(net.minecraft.util.hit.HitResult.Type.MISS)) continue; // no numbers through walls
            Vec3d sp = Projection.toScreen(Vec3d.ofBottomCenter(s.pos).add(0, 0.05, 0));
            if (!Projection.onScreen(sp)) continue;
            Render2D.label(ctx, String.valueOf(s.block), sp.x, sp.y - 4, 0.75f, s.color | 0xFF000000, 0x80000000);
            if (++n > 250) break;
        }
    }
}
