package dev.stella.client.modules.hud;

import dev.stella.client.data.RegionConfig;
import dev.stella.client.data.StaffDatabase;
import dev.stella.client.hud.HudElement;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Compact top-left region grid: numbered square cells coloured by region, then
 * Position, Current Region, a vertical legend and the STAFF list. No card, no
 * rounded container, no heading — plain Minecraft text with tight spacing.
 * Grid data comes from regions.json (a flagged placeholder until verified).
 */
public class RegionMapHud extends HudElement {
    private final NumberSetting cellSize = add(new NumberSetting("Cell Size", "Pixel size of one grid cell", 9, 5, 16, 1));
    private final NumberSetting cellGap = add(new NumberSetting("Cell Gap", "Spacing between cells", 1, 0, 3, 1));
    private final BoolSetting numbers = add(new BoolSetting("Cell Numbers", "Print the number inside each cell", true));
    private final NumberSetting cellAlpha = add(new NumberSetting("Cell Opacity", "Fill opacity of inactive cells", 0.75, 0.1, 1, 0.05));
    private final ColorSetting highlight = add(new ColorSetting("Highlight", "Outline of the cell you are in", 0xFFFFFFFF));
    private final BoolSetting highlightRegion = add(new BoolSetting("Highlight Region", "Brighten every cell of your current region", true));
    private final BoolSetting legend = add(new BoolSetting("Legend", "Show the region legend", true));
    private final BoolSetting staff = add(new BoolSetting("Staff", "Show STAFF count and names (from verified staff.json + tab list)", true));
    private final BoolSetting unverifiedMark = add(new BoolSetting("Unverified Marker", "Append * while regions.json is a placeholder", true));
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
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                RegionConfig.Region reg = RegionConfig.regionAt(c, r);
                int base = reg == null ? 0xFF2A2A2A : reg.color();
                boolean here = c == pc && r == pr;
                boolean sameRegion = highlightRegion.isOn() && current != null && reg == current;
                double a = here ? 1.0 : sameRegion ? Math.min(1, cellAlpha.get() + 0.2) : cellAlpha.get();
                int x = c * step, y = r * step;
                ctx.fill(x, y, x + cs, y + cs, col(ColorUtil.mulAlpha(base, a)));
                if (numbers.isOn()) {
                    String n = String.valueOf(RegionConfig.numbers[r][c]);
                    int tw = mc.textRenderer.getWidth(n);
                    float s = Math.min(0.5f, (cs - 1f) / Math.max(1, tw));
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(x + cs / 2f - tw * s / 2f, y + cs / 2f - 3.5f * s, 0);
                    ctx.getMatrices().scale(s, s, 1);
                    ctx.drawText(mc.textRenderer, n, 0, 0, col(0xFF101010), false);
                    ctx.getMatrices().pop();
                }
                if (here) ctx.drawBorder(x - 1, y - 1, cs + 2, cs + 2, col(highlight.argb()));
            }
        }

        int gridW = cols * step - gap;
        int y = rows * step - gap + 3;
        int tc = col(textColor.argb());
        int w = gridW;

        String pos = "Position: X: " + MathHelper.floor(px) + " Z: " + MathHelper.floor(pz);
        ctx.drawText(mc.textRenderer, pos, 0, y, tc, true);
        w = Math.max(w, mc.textRenderer.getWidth(pos));
        y += 10;

        String mark = RegionConfig.placeholder && unverifiedMark.isOn() ? "*" : "";
        String regionLine = RegionConfig.inGrid(pc, pr)
                ? "Current Region: " + RegionConfig.numbers[pr][pc] + " (" + (current == null ? "None" : current.name()) + ")" + mark
                : "Current Region: - (outside grid)" + mark;
        ctx.drawText(mc.textRenderer, regionLine, 0, y, tc, true);
        w = Math.max(w, mc.textRenderer.getWidth(regionLine));
        y += 11;

        if (legend.isOn()) {
            for (RegionConfig.Region reg : RegionConfig.regions) {
                ctx.fill(0, y + 1, 7, y + 8, col(reg.color()));
                ctx.drawText(mc.textRenderer, reg.name(), 10, y, tc, true);
                w = Math.max(w, 10 + mc.textRenderer.getWidth(reg.name()));
                y += 9;
            }
            y += 2;
        }

        if (staff.isOn()) {
            List<StaffDatabase.StaffMember> online = StaffDatabase.visibleInTab();
            String head = "STAFF " + online.size();
            ctx.drawText(mc.textRenderer, head, 0, y, tc, true);
            w = Math.max(w, mc.textRenderer.getWidth(head));
            y += 10;
            for (StaffDatabase.StaffMember m : online) {
                ctx.drawText(mc.textRenderer, m.name(), 0, y, tc, true);
                w = Math.max(w, mc.textRenderer.getWidth(m.name()));
                y += 9;
            }
        }
        setSize(w, y);
    }
}
