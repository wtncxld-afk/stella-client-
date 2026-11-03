package dev.stella.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.screen.slot.SlotActionType;

/** Inventory helpers. Slot ids are PlayerScreenHandler ids: 9-35 main, 36-44 hotbar, 45 offhand. */
public final class InvUtil {
    public static final int OFFHAND_BUTTON = 40;

    private InvUtil() {}

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    /** Hotbar index 0-8 holding the item, or -1. */
    public static int hotbar(Item item) {
        PlayerInventory inv = mc().player.getInventory();
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isOf(item)) return i;
        return -1;
    }

    /** Screen-handler slot id (9-44) holding the item, main inventory first, or -1. */
    public static int anySlot(Item item, boolean includeHotbar) {
        PlayerInventory inv = mc().player.getInventory();
        for (int i = 9; i < 36; i++) if (inv.getStack(i).isOf(item)) return i;
        if (includeHotbar) for (int i = 0; i < 9; i++) if (inv.getStack(i).isOf(item)) return 36 + i;
        return -1;
    }

    public static boolean offhandHas(Item item) { return mc().player.getOffHandStack().isOf(item); }

    /** Swaps a screen-handler slot with the offhand (the vanilla "F" swap) in the given screen handler. */
    public static void swapToOffhand(int syncId, int slotId) {
        mc().interactionManager.clickSlot(syncId, slotId, OFFHAND_BUTTON, SlotActionType.SWAP, mc().player);
    }

    /** Swaps a screen-handler slot with a hotbar slot (0-8). */
    public static void swapToHotbar(int syncId, int slotId, int hotbar) {
        mc().interactionManager.clickSlot(syncId, slotId, hotbar, SlotActionType.SWAP, mc().player);
    }

    public static void select(int hotbar) {
        if (hotbar < 0 || hotbar > 8) return;
        mc().player.getInventory().setSelectedSlot(hotbar);
    }

    public static int selected() { return mc().player.getInventory().getSelectedSlot(); }
}
