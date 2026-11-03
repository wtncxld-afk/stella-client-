package dev.stella.client.modules.misc;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.Sounds;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/** Warns once when worn armor or a held tool drops below a durability threshold. */
public class DurabilityWarning extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Threshold", "Warn at or below this durability", 10, 1, 50, 1).suffix("%"));
    private final BoolSetting sound = add(new BoolSetting("Sound", "Play a sound with the warning", true));
    private final Set<String> warned = new HashSet<>();

    public DurabilityWarning() { super("Durability Warning", "Warns before your armor or tools break", Category.MISC, true); }

    @Override protected boolean showTogglePopup() { return false; }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.age % 10 != 0) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack st = mc.player.getEquippedStack(slot);
            String key = slot.name();
            if (st.isEmpty() || !st.isDamageable()) { warned.remove(key); continue; }
            float left = 1f - st.getDamage() / (float) Math.max(1, st.getMaxDamage());
            String id = key + ":" + st.getItem();
            if (left * 100 <= threshold.get()) {
                if (warned.add(id)) {
                    NotificationManager.post("Low durability", st.getName().getString() + " is at " + Math.round(left * 100) + "%", Notification.Type.WARNING, 5000);
                    if (sound.isOn()) Sounds.ping(0.8f);
                }
            } else {
                warned.remove(id);
            }
        }
    }
}
