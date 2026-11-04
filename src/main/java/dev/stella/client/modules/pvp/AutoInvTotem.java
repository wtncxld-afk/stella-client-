package dev.stella.client.modules.pvp;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.InvUtil;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;

/** When you open your inventory, a totem is put in your offhand (and optionally a hotbar slot) for you. */
public class AutoInvTotem extends Module {
    private final NumberSetting openDelay = add(new NumberSetting("Open Delay", "Ticks after opening the inventory before acting", 2, 0, 20, 1));
    private final NumberSetting delay = add(new NumberSetting("Action Delay", "Ticks between moves", 1, 0, 10, 1));
    private final BoolSetting offhand = add(new BoolSetting("Offhand", "Put a totem in the offhand", true));
    private final BoolSetting hotbarTotem = add(new BoolSetting("Hotbar Totem", "Also keep a totem in a hotbar slot", true));
    private final NumberSetting hotbarSlot = add(new NumberSetting("Hotbar Slot", "Hotbar slot for the spare totem", 9, 1, 9, 1)).visibleWhen(hotbarTotem::isOn);
    private final BoolSetting autoClose = add(new BoolSetting("Auto Close", "Close the inventory after moving a totem (never when nothing needed moving)", false));
    private int opened = -1, cooldown;
    private boolean moved;

    public AutoInvTotem() { super("Auto Inv Totem", "Opening your inventory equips totems in your offhand and a hotbar slot", Category.COMBAT); }

    @Override
    public void onTick() {
        if (!inGame() || !(mc.currentScreen instanceof InventoryScreen)) { opened = -1; return; }
        if (opened < 0) { opened = 0; moved = false; cooldown = openDelay.getInt(); }
        if (cooldown-- > 0) return;
        int sync = mc.player.playerScreenHandler.syncId;
        if (offhand.isOn() && !InvUtil.offhandHas(Items.TOTEM_OF_UNDYING)) {
            int slot = InvUtil.anySlot(Items.TOTEM_OF_UNDYING, true);
            if (slot >= 0) { InvUtil.swapToOffhand(sync, slot); moved = true; cooldown = delay.getInt(); return; }
        }
        int hb = hotbarSlot.getInt() - 1;
        if (hotbarTotem.isOn() && !mc.player.getInventory().getStack(hb).isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = InvUtil.anySlot(Items.TOTEM_OF_UNDYING, false);
            if (slot >= 0) { InvUtil.swapToHotbar(sync, slot, hb); moved = true; cooldown = delay.getInt(); return; }
        }
        if (opened == 0) {
            opened = 1; // done for this opening
            if (autoClose.isOn() && moved) mc.player.closeHandledScreen(); // only close if we actually changed something
        }
    }
}
