package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.modules.cosmetics.Cosmetics;
import dev.stella.client.render.Render3D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Replaces the black block outline with a coloured, thicker, optionally filled and glowing one. */
public class BlockOutline extends Module {
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme accent, rainbow or a custom colour", "Theme", "Theme", "Rainbow", "Custom"));
    private final ColorSetting custom = add(new ColorSetting("Custom Color", "", 0xFFB46CFF)).visibleWhen(() -> colorMode.is("Custom"));
    private final NumberSetting width = add(new NumberSetting("Thickness", "Outline thickness", 2.5, 1, 6, 0.5));
    private final BoolSetting fill = add(new BoolSetting("Fill", "Translucent fill on the block", true));
    private final NumberSetting fillAlpha = add(new NumberSetting("Fill Opacity", "", 0.18, 0.02, 0.6, 0.02)).visibleWhen(fill::isOn);
    private final BoolSetting pulse = add(new BoolSetting("Pulse", "Gently pulse the fill", true));

    public BlockOutline() { super("Block Outline", "Coloured, thicker block outline with fill", Category.VISUALS); }

    /** True when the vanilla outline should be hidden. */
    public boolean replacesVanilla() { return isEnabled(); }

    @Override
    public void onRender3D(Render3D r) {
        if (!inGame() || !(mc.crosshairTarget instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = bh.getBlockPos();
        var shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;
        Box box = shape.getBoundingBox().offset(pos).expand(0.002);
        int c = Cosmetics.color(colorMode.get(), custom.get(), 0.2);
        double a = fillAlpha.get() * (pulse.isOn() ? 0.75 + 0.25 * Math.sin(System.currentTimeMillis() / 300.0) : 1);
        r.setLineWidth(width.getFloat());
        r.box(box, ColorUtil.withAlpha(c, 0xF0), ColorUtil.withAlpha(c, (int) (255 * a)), true, fill.isOn());
        r.setLineWidth(2.5f);
    }
}
