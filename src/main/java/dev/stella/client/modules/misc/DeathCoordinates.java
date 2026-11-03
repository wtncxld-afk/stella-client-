package dev.stella.client.modules.misc;

import dev.stella.client.data.Waypoints;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.util.ChatUtil;
import dev.stella.client.util.Dimensions;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Remembers where you died: chat message (only you see it) and an optional waypoint. */
public class DeathCoordinates extends Module {
    private final BoolSetting waypoint = add(new BoolSetting("Create Waypoint", "Add a 'Death' waypoint at the spot", true));
    private final BoolSetting copy = add(new BoolSetting("Copy To Clipboard", "Copy the coordinates", false));
    private boolean recorded;

    public DeathCoordinates() { super("Death Coordinates", "Saves where you died (chat message and waypoint)", Category.MISC, true); }

    @Override protected boolean showTogglePopup() { return false; }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (!mc.player.isDead()) { recorded = false; return; }
        if (recorded) return;
        recorded = true;
        int x = mc.player.getBlockX(), y = mc.player.getBlockY(), z = mc.player.getBlockZ();
        String dim = Dimensions.current();
        ChatUtil.send(Text.literal("You died at ").formatted(Formatting.GRAY)
                .append(Text.literal(x + " " + y + " " + z).formatted(Formatting.RED))
                .append(Text.literal(" (" + Dimensions.pretty(dim) + ")").formatted(Formatting.DARK_GRAY)));
        if (waypoint.isOn()) Waypoints.add(new Waypoints.Waypoint("Death", x, y, z, dim, 0xFFFF5555));
        if (copy.isOn()) mc.keyboard.setClipboard(x + " " + y + " " + z);
    }
}
