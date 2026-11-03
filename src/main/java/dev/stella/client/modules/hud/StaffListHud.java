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
    private final dev.stella.client.hud.CardStyle card = card("Modern");
    private final ColorSetting headerColor = add(new ColorSetting("Header Color", "Title colour (when Theme Colors is off)", 0xFFA855F7));
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
        String header = "STAFF  " + visible + "/" + all.size() + " in tab";
        int w = dev.stella.client.render.StellaFont.width(header);
        List<String[]> shown = rows.subList(0, Math.min(rows.size(), maxRows.getInt()));
        for (String[] r : shown) w = Math.max(w, dev.stella.client.render.StellaFont.width(r[0] + " [" + r[1] + "]  " + r[2]));
        String empty = all.isEmpty() ? "No verified staff - edit config/stella/staff.json" : "No matches";
        if (shown.isEmpty()) w = Math.max(w, dev.stella.client.render.StellaFont.width(empty));
        int h = 12 + Math.max(1, shown.size()) * 10 - 2;
        card.draw(ctx, this, w, h);
        int px = card.padLeft(), py = card.pad();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(px, py, 0);
        dev.stella.client.render.StellaFont.draw(ctx, header, 0, 0, col(card.accent(headerColor.argb())), true);
        int y = 12;
        if (shown.isEmpty()) {
            dev.stella.client.render.StellaFont.draw(ctx, empty, 0, y, col(0xFF8E879A), false);
        }
        for (String[] r : shown) {
            int x = 0;
            dev.stella.client.render.StellaFont.draw(ctx, r[0], x, y, col(nameColor.argb()), true);
            x += dev.stella.client.render.StellaFont.width(r[0] + " ");
            dev.stella.client.render.StellaFont.draw(ctx, "[" + r[1] + "]", x, y, col(rankColor.argb()), true);
            x += dev.stella.client.render.StellaFont.width("[" + r[1] + "]  ");
            dev.stella.client.render.StellaFont.draw(ctx, r[2], x, y, col(r[3].equals("1") ? 0xFF7CF29A : 0xFF6E6878), false);
            y += 10;
        }
        ctx.getMatrices().pop();
        setSize(w + px + card.pad(), h + py * 2);
    }
}
