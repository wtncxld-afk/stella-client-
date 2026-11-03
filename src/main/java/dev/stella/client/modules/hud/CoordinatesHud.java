package dev.stella.client.modules.hud;

import dev.stella.client.hud.HudElement;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public class CoordinatesHud extends HudElement {
    private final BoolSetting direction = add(new BoolSetting("Direction", "Show facing direction", true));
    private final BoolSetting netherCoords = add(new BoolSetting("Nether Coords", "Show the converted Overworld/Nether position", false));
    private final BoolSetting background = add(new BoolSetting("Background", "Translucent backing", false));
    private final ColorSetting labelColor = add(new ColorSetting("Label Color", "Axis label colour", 0xFFC084FC));
    private final ColorSetting valueColor = add(new ColorSetting("Value Color", "Number colour", 0xFFF3F0F7));

    public CoordinatesHud() { super("Coordinates HUD", "Shows X / Y / Z and facing", 4, 76, true); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        double x = 0, y = 64, z = 0;
        String facing = "North (-Z)";
        boolean nether = false;
        if (mc.player != null) {
            x = mc.player.getX(); y = mc.player.getY(); z = mc.player.getZ();
            Direction d = mc.player.getHorizontalFacing();
            facing = switch (d) {
                case NORTH -> "North (-Z)";
                case SOUTH -> "South (+Z)";
                case WEST -> "West (-X)";
                case EAST -> "East (+X)";
                default -> d.asString();
            };
            nether = mc.world != null && mc.world.getRegistryKey() == net.minecraft.world.World.NETHER;
        }
        int lines = 0;
        int w = 0;
        int lc = col(labelColor.argb()), vc = col(valueColor.argb());
        java.util.List<String[]> out = new java.util.ArrayList<>();
        out.add(new String[]{"XYZ ", String.format("%d, %d, %d", MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z))});
        if (direction.isOn()) out.add(new String[]{"Facing ", facing});
        if (netherCoords.isOn()) {
            double f = nether ? 8 : 0.125;
            out.add(new String[]{nether ? "Overworld " : "Nether ", String.format("%d, %d", MathHelper.floor(x * f), MathHelper.floor(z * f))});
        }
        for (String[] r : out) w = Math.max(w, mc.textRenderer.getWidth(r[0] + r[1]));
        if (background.isOn()) ctx.fill(-2, -2, w + 2, out.size() * 10, col(0x90100D16));
        for (String[] r : out) {
            ctx.drawText(mc.textRenderer, r[0], 0, lines * 10, lc, true);
            ctx.drawText(mc.textRenderer, r[1], mc.textRenderer.getWidth(r[0]), lines * 10, vc, true);
            lines++;
        }
        setSize(w, lines * 10);
    }
}
