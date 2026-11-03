package dev.stella.client.modules.pvp;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.InvUtil;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;

/** Keeps a totem in your offhand: as soon as one pops (or the offhand is empty), another is moved in. */
public class AutoTotem extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Always keep a totem in the offhand, or only below a health threshold", "Always", "Always", "Health"));
    private final NumberSetting health = add(new NumberSetting("Health", "Hearts at or below which a totem is equipped (Health mode)", 8, 1, 20, 0.5)).visibleWhen(() -> mode.is("Health"));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks to wait before equipping (0 = instant)", 0, 0, 20, 1));
    private final BoolSetting randomize = add(new BoolSetting("Randomize Delay", "Add 0-2 random ticks to look less robotic", false));
    private final BoolSetting hotbar = add(new BoolSetting("Use Hotbar Totems", "Also take totems from the hotbar", true));
    private int wait = -1;

    public AutoTotem() { super("Auto Totem", "Keeps a totem in your offhand, refilling it the moment one pops", Category.COMBAT); }

    @Override
    public void onTick() {
        if (!inGame() || InvUtil.offhandHas(Items.TOTEM_OF_UNDYING)) { wait = -1; return; }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return; // another container is open
        if (mode.is("Health") && (mc.player.getHealth() + mc.player.getAbsorptionAmount()) / 2f > health.get()) return;
        if (wait < 0) wait = delay.getInt() + (randomize.isOn() ? (int) (Math.random() * 3) : 0);
        if (wait-- > 0) return;
        int slot = InvUtil.anySlot(Items.TOTEM_OF_UNDYING, hotbar.isOn());
        if (slot >= 0) InvUtil.swapToOffhand(mc.player.playerScreenHandler.syncId, slot);
        wait = -1;
    }
}
