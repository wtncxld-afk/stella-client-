package dev.stella.client.modules.pvp;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.util.ChatUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Counts totem pops of nearby players (from the server's totem animation packet) and announces them. */
public class TotemPopCounter extends Module {
    private final BoolSetting chat = add(new BoolSetting("Chat Message", "Chat line (only you see it) on every pop", true));
    private final BoolSetting self = add(new BoolSetting("Count Yourself", "Also count your own pops", true));
    private final BoolSetting announceDeath = add(new BoolSetting("Death Message", "Say how many totems a player popped before dying", true));

    private final Map<UUID, Integer> pops = new HashMap<>();

    public TotemPopCounter() { super("Totem Pop Counter", "Counts how many totems nearby players pop", Category.COMBAT); }

    public int pops(UUID id) { return pops.getOrDefault(id, 0); }

    /** Called from the network mixin for entity status 35 (totem used). */
    public void onPop(Entity e) {
        if (!isEnabled() || !(e instanceof PlayerEntity p)) return;
        if (p == mc.player && !self.isOn()) return;
        int n = pops.merge(p.getUuid(), 1, Integer::sum);
        if (chat.isOn()) ChatUtil.send(Text.literal(p.getGameProfile().name()).formatted(Formatting.RED)
                .append(Text.literal(" popped ").formatted(Formatting.GRAY))
                .append(Text.literal(n + (n == 1 ? " totem" : " totems")).formatted(Formatting.GOLD)));
    }

    /** Called from the network mixin for entity status 3 (death). */
    public void onDeath(Entity e) {
        if (!(e instanceof PlayerEntity p)) return;
        Integer n = pops.remove(p.getUuid());
        if (n == null || !isEnabled() || !announceDeath.isOn()) return;
        ChatUtil.send(Text.literal(p.getGameProfile().name()).formatted(Formatting.RED)
                .append(Text.literal(" died after popping ").formatted(Formatting.GRAY))
                .append(Text.literal(n + (n == 1 ? " totem" : " totems")).formatted(Formatting.GOLD)));
    }

    private Object lastWorld;

    @Override
    public void onTick() {
        if (mc.world != lastWorld) { lastWorld = mc.world; pops.clear(); }
    }

    @Override protected void onDisable() { pops.clear(); }
}
