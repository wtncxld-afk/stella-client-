package dev.stella.client.modules.hud;

import dev.stella.client.data.StaffDatabase;
import dev.stella.client.hud.HudElement;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.setting.TextSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows the user-verified staff roster from staff.json. "In tab" = present in
 * the server's tab list. Staff not in the tab list are shown as "not visible"
 * because a normal client cannot distinguish offline from vanished.
 */
public class StaffListHud extends HudElement {
    private final TextSetting search = add(new TextSetting("Search", "Filter names or ranks", "", 24));
    private final BoolSetting onlyVisible = add(new BoolSetting("Only In Tab", "Hide staff not present in the tab list", false));
    private final NumberSetting maxRows = add(new NumberSetting("Max Rows", "Maximum names listed", 12, 1, 50, 1));
    private final BoolSetting background = add(new BoolSetting("Background", "Translucent backing", true));
    private final ColorSetting headerColor = add(new ColorSetting("Header Color", "Title colour", 0xFFA855F7));
    private final ColorSetting nameColor = add(new ColorSetting("Name Color", "Name colour", 0xFFF3F0F7));
    private final ColorSetting rankColor = add(new ColorSetting("Rank Color", "Rank colour", 0xFFC4B5FD));

    public StaffListHud() { super("Staff List HUD", "Verified staff roster with tab-list visibility", -4, 84, false); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        List<StaffDatabase.StaffMember> all = StaffDatabase.all();
        String q = search.get().trim().toLowerCase(Locale.ROOT);
        List<String[]> rows = new ArrayList<>();
        int visible = 0;
        for (StaffDatabase.StaffMember m : all) {
            boolean inTab = StaffDatabase.tabEntry(m) != null;
            if (inTab) visible++;
            if (onlyVisible.isOn() && !inTab) continue;
            if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q) && !m.rank().toLowerCase(Locale.ROOT).contains(q)) continue;
            rows.add(new String[]{m.name(), m.rank(), inTab ? "in tab" : "not visible", inTab ? "1" : "0"});
        }
        String header = "Staff  " + visible + "/" + all.size() + " in tab";
        int w = mc.textRenderer.getWidth(header);
        List<String[]> shown = rows.subList(0, Math.min(rows.size(), maxRows.getInt()));
        for (String[] r : shown) w = Math.max(w, mc.textRenderer.getWidth(r[0] + " [" + r[1] + "]  " + r[2]));
        String empty = all.isEmpty() ? "No verified staff - edit config/stella/staff.json" : "No matches";
        if (shown.isEmpty()) w = Math.max(w, mc.textRenderer.getWidth(empty));
        int h = 12 + Math.max(1, shown.size()) * 10;
        if (background.isOn()) ctx.fill(-3, -3, w + 3, h + 1, col(0xA00D0B12));
        ctx.drawText(mc.textRenderer, header, 0, 0, col(headerColor.argb()), true);
        int y = 12;
        if (shown.isEmpty()) {
            ctx.drawText(mc.textRenderer, empty, 0, y, col(0xFF8E879A), false);
        }
        for (String[] r : shown) {
            int x = 0;
            ctx.drawText(mc.textRenderer, r[0], x, y, col(nameColor.argb()), true);
            x += mc.textRenderer.getWidth(r[0] + " ");
            ctx.drawText(mc.textRenderer, "[" + r[1] + "]", x, y, col(rankColor.argb()), true);
            x += mc.textRenderer.getWidth("[" + r[1] + "]  ");
            ctx.drawText(mc.textRenderer, r[2], x, y, col(r[3].equals("1") ? 0xFF7CF29A : 0xFF6E6878), false);
            y += 10;
        }
        setSize(w, h);
    }
}
