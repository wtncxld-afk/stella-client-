package dev.stella.client.modules.combat;

import dev.stella.client.data.RegionConfig;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.TextSetting;

/** Pops up which Region Map region you just walked into, with your own notes per region. */
public class RegionAlerts extends Module {
    private final BoolSetting glitchedOnly = add(new BoolSetting("Only Glitched", "Only alert for Glitched regions", false));
    private final TextSetting notes = add(new TextSetting("Notes", "Your notes per region number, e.g. 47=main base; 12=kelp farm", "", 600));
    private int lastCol = Integer.MIN_VALUE, lastRow = Integer.MIN_VALUE;

    public RegionAlerts() { super("Region Alerts", "Popup when you enter a Region Map region, with your notes", Category.DONUTSMP); }

    @Override protected void onEnable() { lastCol = lastRow = Integer.MIN_VALUE; }

    public static int number(int col, int row) {
        if (RegionConfig.numbers.length > row && row >= 0 && RegionConfig.numbers[row].length > col && col >= 0 && RegionConfig.numbers[row][col] > 0)
            return RegionConfig.numbers[row][col];
        return RegionConfig.sequentialNumber(row, col);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        int col = RegionConfig.colOf(mc.player.getX()), row = RegionConfig.rowOf(mc.player.getZ());
        if (col == lastCol && row == lastRow) return;
        boolean first = lastCol == Integer.MIN_VALUE;
        lastCol = col; lastRow = row;
        if (first || !RegionConfig.inGrid(col, row)) return;
        RegionConfig.Region r = RegionConfig.regionAt(col, row);
        String kind = r == null ? "Unmarked" : r.name();
        if (glitchedOnly.isOn() && !kind.toLowerCase().contains("glitch")) return;
        int n = number(col, row);
        String note = noteFor(n);
        NotificationManager.post("Region " + n, kind + (note.isEmpty() ? "" : " - " + note),
                kind.toLowerCase().contains("glitch") ? Notification.Type.WARNING : Notification.Type.INFO, 4000);
    }

    private String noteFor(int n) {
        for (String e : notes.get().split(";")) {
            int eq = e.indexOf('=');
            if (eq > 0 && e.substring(0, eq).trim().equals(String.valueOf(n))) return e.substring(eq + 1).trim();
        }
        return "";
    }
}
