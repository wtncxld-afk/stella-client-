package dev.stella.client.modules.misc;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.util.ChatUtil;
import dev.stella.client.util.Dimensions;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Remembers where you died: chat message (only you see it), optionally copied to the clipboard. */
public class DeathCoordinates extends Module {
    private final BoolSetting copy = add(new BoolSetting("Copy To Clipboard", "Copy the coordinates", false));
    private boolean recorded;
    /** "x y z" of your last death (shown in chat), or null. */
    public static String lastDeath;

    public DeathCoordinates() { super("Death Coordinates", "Tells you where you died (chat message only you see)", Category.MISC, true); }

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
        lastDeath = x + " " + y + " " + z;
        if (copy.isOn()) mc.keyboard.setClipboard(x + " " + y + " " + z);
    }
}
