package dev.stella.client.modules.hud;

import dev.stella.client.data.RegionConfig;
import dev.stella.client.data.StaffDatabase;
import dev.stella.client.hud.HudElement;
import dev.stella.client.render.Render2D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Compact top-left region grid matching the DonutSMP region-map reference:
 * opaque numbered cells, "Position" and "Current Region" lines, a swatch
 * legend and the STAFF list with player heads. No card, no heading.
 */
public class RegionMapHud extends HudElement {
    private final NumberSetting cellSize = add(new NumberSetting("Cell Size", "Pixel size of one grid cell", 13, 6, 24, 1));
    private final NumberSetting cellGap = add(new NumberSetting("Cell Gap", "Spacing between cells", 1, 0, 3, 1));
    private final BoolSetting numbers = add(new BoolSetting("Cell Numbers", "Print the number inside each cell", true));
    private final ColorSetting numberColor = add(new ColorSetting("Number Color", "Cell number colour", 0xFFF2F2F2)).visibleWhen(numbers::isOn);
    private final NumberSetting cellAlpha = add(new NumberSetting("Cell Opacity", "Fill opacity of the cells", 1.0, 0.1, 1, 0.05));
    private final BoolSetting playerArrow = add(new BoolSetting("Player Arrow", "Red arrow at your exact position and facing", true));
    private final ColorSetting arrowColor = add(new ColorSetting("Arrow Color", "", 0xFFE53935)).visibleWhen(playerArrow::isOn);
    private final BoolSetting outlineCell = add(new BoolSetting("Outline Current Cell", "Thin outline around the cell you are in", false));
    private final ColorSetting highlight = add(new ColorSetting("Outline Color", "", 0xFFFFFFFF)).visibleWhen(outlineCell::isOn);
    private final BoolSetting legend = add(new BoolSetting("Legend", "Show the region legend", true));
    private final BoolSetting staff = add(new BoolSetting("Staff", "STAFF count and names (verified staff.json + tab list)", true));
    private final BoolSetting staffHeads = add(new BoolSetting("Staff Heads", "Player heads next to staff names", true)).visibleWhen(staff::isOn);
    private final BoolSetting unverifiedMark = add(new BoolSetting("Unverified Marker", "Append * while regions.json is marked as a placeholder", true));
    private final ColorSetting textColor = add(new ColorSetting("Text Color", "Info text colour", 0xFFFFFFFF));
    private final BoolSetting reload = add(new BoolSetting("Reload regions.json", "Toggle to re-read config/stella/regions.json", false))
            .onChange(this::reloadNow);

    public RegionMapHud() { super("Region Map HUD", "Numbered region grid with position, region, legend and staff", 2, 2, false); }

    private void reloadNow() {
        if (reload.isOn()) {
            RegionConfig.load();
            reload.set(false);
        }
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        int cs = cellSize.getInt(), gap = cellGap.getInt();
        int cols = RegionConfig.columns, rows = RegionConfig.rows;
        double px = mc.player != null ? mc.player.getX() : 0, pz = mc.player != null ? mc.player.getZ() : 0;
        int pc = RegionConfig.colOf(px), pr = RegionConfig.rowOf(pz);
        RegionConfig.Region current = RegionConfig.regionAt(pc, pr);
        int step = cs + gap;

        // ---- grid
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                RegionConfig.Region reg = RegionConfig.regionAt(c, r);
                int base = reg == null ? 0xFF2A2A2A : reg.color();
                int x = c * step, y = r * step;
                ctx.fill(x, y, x + cs, y + cs, col(ColorUtil.mulAlpha(base, cellAlpha.get())));
                if (numbers.isOn()) {
                    String n = String.valueOf(RegionConfig.numbers[r][c]);
                    int tw = mc.textRenderer.getWidth(n);
                    float s = Math.min(0.62f, (cs - 2f) / Math.max(1, tw));
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(x + cs / 2f - tw * s / 2f, y + cs / 2f - 3.5f * s, 0);
                    ctx.getMatrices().scale(s, s, 1);
                    ctx.drawText(mc.textRenderer, n, 0, 0, col(numberColor.argb()), false);
                    ctx.getMatrices().pop();
                }
                if (outlineCell.isOn() && c == pc && r == pr) ctx.drawBorder(x - 1, y - 1, cs + 2, cs + 2, col(highlight.argb()));
            }
        }
        if (playerArrow.isOn() && RegionConfig.inGrid(pc, pr)) {
            double fx = (px - RegionConfig.originX) / RegionConfig.cellSize, fz = (pz - RegionConfig.originZ) / RegionConfig.cellSize;
            double ax = pc * step + (fx - pc) * cs, az = pr * step + (fz - pr) * cs;
            float yaw = mc.player != null ? mc.player.getYaw(tickDelta) : 180f;
            drawArrow(ctx, ax, az, yaw, Math.max(4f, cs * 0.45f), col(arrowColor.argb()));
        }

        int gridW = cols * step - gap;
        int y = rows * step - gap + 3;
        int tc = col(textColor.argb());
        int w = gridW;

        String pos = "Position: X: " + MathHelper.floor(px) + ", Z: " + MathHelper.floor(pz);
        ctx.drawText(mc.textRenderer, pos, 0, y, tc, true);
        w = Math.max(w, mc.textRenderer.getWidth(pos));
        y += 9;

        String mark = RegionConfig.placeholder && unverifiedMark.isOn() ? "*" : "";
        String regionLine = RegionConfig.inGrid(pc, pr)
                ? "Current Region: " + RegionConfig.numbers[pr][pc] + " (" + (current == null ? "None" : current.name()) + ")" + mark
                : "Current Region: - (outside grid)" + mark;
        ctx.drawText(mc.textRenderer, regionLine, 0, y, tc, true);
        w = Math.max(w, mc.textRenderer.getWidth(regionLine));
        y += 11;

        if (legend.isOn()) {
            for (RegionConfig.Region reg : RegionConfig.regions) {
                ctx.fill(0, y, 8, y + 8, col(reg.color()));
                ctx.drawText(mc.textRenderer, reg.name(), 11, y, tc, true);
                w = Math.max(w, 11 + mc.textRenderer.getWidth(reg.name()));
                y += 9;
            }
            y += 5;
        }

        if (staff.isOn()) {
            List<StaffDatabase.StaffMember> online = StaffDatabase.visibleInTab();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, y, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            ctx.drawText(mc.textRenderer, "STAFF", 0, 1, col(0xFF9A9A9A), true);
            ctx.getMatrices().pop();
            ctx.drawText(mc.textRenderer, String.valueOf(online.size()), 26, y, tc, true);
            y += 12;
            for (StaffDatabase.StaffMember m : online) {
                int rowW = 14 + mc.textRenderer.getWidth(m.name()) + 6;
                Render2D.roundRect(ctx, -1, y - 2, rowW, 13, 3, col(0x70000000));
                PlayerListEntry e = StaffDatabase.tabEntry(m);
                int tx = 2;
                if (staffHeads.isOn() && e != null) {
                    PlayerSkinDrawer.draw(ctx, e.getSkinTextures(), 1, y, 9);
                    tx = 13;
                }
                ctx.drawText(mc.textRenderer, m.name(), tx, y + 1, tc, true);
                w = Math.max(w, rowW);
                y += 15;
            }
        }
        setSize(w, y);
    }

    /** Small filled arrow pointing along the player's yaw (0 = south / +Z, 90 = west). */
    private static void drawArrow(DrawContext ctx, double x, double y, float yaw, float size, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 50);
        ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(yaw + 180f));
        // filled triangle from horizontal spans: tip at (0,-size), base at y=+size*0.6
        float half = size * 0.55f;
        int steps = Math.max(4, (int) (size * 2));
        for (int i = 0; i < steps; i++) {
            float t = i / (float) steps;
            float yy = -size + t * size * 1.6f;
            float hw = half * t;
            Render2D.rect(ctx, -hw, yy, hw * 2, size * 1.6f / steps + 0.2f, color);
        }
        ctx.getMatrices().pop();
    }
}
