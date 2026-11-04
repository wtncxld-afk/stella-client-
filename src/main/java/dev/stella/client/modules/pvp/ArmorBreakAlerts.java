package dev.stella.client.modules.pvp;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.modules.misc.FriendsModule;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/** Alerts you when a nearby enemy's armor (as the server shows it to you) is about to break. */
public class ArmorBreakAlerts extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Threshold", "Alert at or below this durability", 15, 1, 50, 1).suffix("%"));
    private final NumberSetting range = add(new NumberSetting("Range", "Only players this close", 16, 4, 64, 1).suffix("m"));
    private final BoolSetting self = add(new BoolSetting("Your Own Armor", "Also warn about your own armor", false));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final Set<String> warned = new HashSet<>();

    public ArmorBreakAlerts() { super("Armor Break Alerts", "Warns when an enemy's armor is almost broken", Category.COMBAT); }

    @Override
    public void onTick() {
        if (!inGame()) return;
        Set<String> low = new HashSet<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if ((p == mc.player && !self.isOn()) || FriendsModule.is(p) || p.distanceTo(mc.player) > range.get()) continue;
            for (EquipmentSlot s : SLOTS) {
                ItemStack st = p.getEquippedStack(s);
                if (st.isEmpty() || !st.isDamageable()) continue;
                double pct = 100.0 * (st.getMaxDamage() - st.getDamage()) / st.getMaxDamage();
                if (pct > threshold.get()) continue;
                String key = p.getUuid() + ":" + s.getName();
                low.add(key);
                if (warned.add(key)) {
                    String who = p == mc.player ? "Your" : p.getGameProfile().name() + "'s";
                    NotificationManager.post("Armor Break", who + " " + st.getName().getString() + " is at " + (int) pct + "%",
                            p == mc.player ? Notification.Type.WARNING : Notification.Type.ALERT, 3500);
                }
            }
        }
        warned.retainAll(low); // repaired / replaced armor can warn again later
    }
}
