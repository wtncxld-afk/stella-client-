package dev.stella.client.modules.hud;

import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class CoordinatesHud extends HudElement {
    private final CardStyle card = card("Modern");
    private final BoolSetting direction = add(new BoolSetting("Direction", "Show facing direction", true));
    private final BoolSetting netherCoords = add(new BoolSetting("Nether Coords", "Show the converted Overworld/Nether position", false));
    private final BoolSetting separate = add(new BoolSetting("Separate Axes", "One line per axis instead of X, Y, Z on one line", false));
    private final ColorSetting labelColor = add(new ColorSetting("Label Color", "Axis label colour (when Theme Colors is off)", 0xFFC084FC));
    private final ColorSetting valueColor = add(new ColorSetting("Value Color", "Number colour", 0xFFF3F0F7));

    public CoordinatesHud() { super("Coordinates HUD", "Shows X / Y / Z and facing", 4, 336, true); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        double x = 0, y = 64, z = 0;
        Direction d = Direction.NORTH;
        boolean nether = false;
        if (mc.player != null) {
            x = mc.player.getX(); y = mc.player.getY(); z = mc.player.getZ();
            d = mc.player.getHorizontalFacing();
            nether = mc.world != null && mc.world.getRegistryKey() == net.minecraft.world.World.NETHER;
        }
        String facing = switch (d) {
            case NORTH -> "North -Z";
            case SOUTH -> "South +Z";
            case WEST -> "West -X";
            case EAST -> "East +X";
            default -> d.asString();
        };
        List<String[]> out = new ArrayList<>();
        if (separate.isOn()) {
            out.add(new String[]{"X ", String.valueOf(MathHelper.floor(x))});
            out.add(new String[]{"Y ", String.valueOf(MathHelper.floor(y))});
            out.add(new String[]{"Z ", String.valueOf(MathHelper.floor(z))});
        } else {
            out.add(new String[]{"XYZ ", MathHelper.floor(x) + ", " + MathHelper.floor(y) + ", " + MathHelper.floor(z)});
        }
        if (direction.isOn()) out.add(new String[]{"FACING ", facing});
        if (netherCoords.isOn()) {
            double f = nether ? 8 : 0.125;
            out.add(new String[]{nether ? "OVERWORLD " : "NETHER ", MathHelper.floor(x * f) + ", " + MathHelper.floor(z * f)});
        }
        int w = 0;
        for (String[] r : out) w = Math.max(w, dev.stella.client.render.StellaFont.width(r[0] + r[1]));
        int h = out.size() * 10 - 2;
        card.draw(ctx, this, w, h);
        int px = card.padLeft(), py = card.pad();
        int lc = col(card.accent(labelColor.argb())), vc = col(valueColor.argb());
        for (int i = 0; i < out.size(); i++) {
            String[] r = out.get(i);
            dev.stella.client.render.StellaFont.draw(ctx, r[0], px, py + i * 10, lc, true);
            dev.stella.client.render.StellaFont.draw(ctx, r[1], px + dev.stella.client.render.StellaFont.width(r[0]), py + i * 10, vc, true);
        }
        setSize(w + px + card.pad(), h + py * 2);
    }
}
