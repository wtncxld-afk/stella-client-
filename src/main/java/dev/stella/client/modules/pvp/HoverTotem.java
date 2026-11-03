package dev.stella.client.modules.pvp;

import dev.stella.client.mixin.HandledScreenAccessor;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.InvUtil;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;

/** In your inventory, hovering the mouse over a totem moves it to your offhand (then the hotbar slot). */
public class HoverTotem extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between moves", 1, 0, 10, 1));
    private final BoolSetting hotbarTotem = add(new BoolSetting("Hotbar Totem", "When the offhand already has one, fill a hotbar slot", true));
    private final NumberSetting hotbarSlot = add(new NumberSetting("Hotbar Slot", "Hotbar slot for the spare totem", 9, 1, 9, 1)).visibleWhen(hotbarTotem::isOn);
    private int cooldown;

    public HoverTotem() { super("Hover Totem", "Hover over a totem in your inventory to equip it instantly", Category.COMBAT); }

    @Override
    public void onTick() {
        if (!inGame() || !(mc.currentScreen instanceof InventoryScreen screen)) return;
        if (cooldown-- > 0) return;
        Slot slot = ((HandledScreenAccessor) screen).stella$focusedSlot();
        if (slot == null || !slot.getStack().isOf(Items.TOTEM_OF_UNDYING) || slot.id == 45) return;
        int sync = mc.player.playerScreenHandler.syncId;
        int hb = hotbarSlot.getInt() - 1;
        if (!InvUtil.offhandHas(Items.TOTEM_OF_UNDYING)) {
            InvUtil.swapToOffhand(sync, slot.id);
            cooldown = delay.getInt();
        } else if (hotbarTotem.isOn() && slot.id != 36 + hb && !mc.player.getInventory().getStack(hb).isOf(Items.TOTEM_OF_UNDYING)) {
            InvUtil.swapToHotbar(sync, slot.id, hb);
            cooldown = delay.getInt();
        }
    }
}
