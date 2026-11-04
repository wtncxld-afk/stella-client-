package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.modules.cosmetics.Cosmetics;
import dev.stella.client.render.Render3D;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Shows where an arrow, trident, pearl or snowball will land, with a marker on the landing block. */
public class Trajectories extends Module {
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme accent, rainbow or a custom colour", "Theme", "Theme", "Rainbow", "Custom"));
    private final ColorSetting custom = add(new ColorSetting("Custom Color", "", 0xFF4FD8FF)).visibleWhen(() -> colorMode.is("Custom"));

    private int useTicks;

    @Override
    public void onTick() { useTicks = mc.player != null && mc.player.isUsingItem() ? useTicks + 1 : 0; }

    public Trajectories() { super("Trajectories", "Predicted path and landing spot for bows, tridents, pearls and throwables", Category.VISUALS); }

    /** Launch speed and gravity for the held item, or null. */
    private double[] physics(ItemStack st) {
        if (st.getItem() instanceof BowItem) {
            int use = useTicks;
            if (!mc.player.isUsingItem()) return null;
            float p = BowItem.getPullProgress(use);
            return p < 0.1f ? null : new double[]{3.0 * p, 0.05, 0.99};
        }
        if (st.getItem() instanceof CrossbowItem) return CrossbowItem.isCharged(st) ? new double[]{3.15, 0.05, 0.99} : null;
        if (st.isOf(Items.TRIDENT)) return mc.player.isUsingItem() ? new double[]{2.5, 0.05, 0.99} : null;
        if (st.isOf(Items.ENDER_PEARL) || st.isOf(Items.SNOWBALL) || st.isOf(Items.EGG)) return new double[]{1.5, 0.03, 0.99};
        if (st.isOf(Items.SPLASH_POTION) || st.isOf(Items.LINGERING_POTION)) return new double[]{0.5, 0.05, 0.99};
        if (st.isOf(Items.EXPERIENCE_BOTTLE)) return new double[]{0.7, 0.07, 0.99};
        return null;
    }

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame()) return;
        double[] ph = physics(mc.player.getMainHandStack());
        if (ph == null) ph = physics(mc.player.getOffHandStack());
        if (ph == null) return;
        float td = r.tickDelta();
        Vec3d pos = mc.player.getCameraPosVec(td).add(0, -0.1, 0);
        float yaw = mc.player.getYaw(td), pitch = mc.player.getPitch(td);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3d vel = new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)).multiply(ph[0]);
        Vec3d prev = pos;
        HitResult hit = null;
        r.setLineWidth(2.5f);
        for (int i = 0; i < 300; i++) {
            Vec3d next = prev.add(vel);
            hit = mc.world.raycast(new RaycastContext(prev, next, RaycastContext.ShapeType.values()[0], RaycastContext.FluidHandling.values()[0], mc.player));
            Vec3d end = hit.getType() == HitResult.Type.MISS ? next : hit.getPos();
            int c = ColorUtil.withAlpha(Cosmetics.color(colorMode.get(), custom.get(), i / 60.0), i < 2 ? 0x40 : 0xE0);
            r.line(prev, end, c);
            if (hit.getType() != HitResult.Type.MISS) break;
            prev = next;
            vel = vel.multiply(ph[2]).add(0, -ph[1], 0);
            if (prev.y < mc.world.getBottomY() - 5) break;
        }
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            int c = Cosmetics.color(colorMode.get(), custom.get(), 0.6);
            Box b = new Box(bh.getBlockPos()).expand(0.01);
            r.box(b, ColorUtil.withAlpha(c, 0xF0), ColorUtil.withAlpha(c, 0x40), true, true);
        }
    }
}
