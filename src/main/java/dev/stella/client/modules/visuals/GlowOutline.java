package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;

/**
 * Glowing outline (the vanilla "glowing" effect outline, drawn client-side only)
 * around dropped items, and optionally players and mobs. Visible through walls.
 */
public class GlowOutline extends Module {
    private final BoolSetting items = add(new BoolSetting("Items", "Outline dropped items", true));
    private final ModeSetting itemColorMode = add(new ModeSetting("Item Color", "Rarity colours (white/yellow/aqua/pink) or one custom colour",
            "Rarity", "Rarity", "Custom", "Theme")).visibleWhen(items::isOn);
    private final ColorSetting itemColor = add(new ColorSetting("Custom Item Color", "", 0xFFD8B4FE)).visibleWhen(() -> items.isOn() && itemColorMode.is("Custom"));
    private final BoolSetting players = add(new BoolSetting("Players", "Outline other players", false));
    private final ColorSetting playerColor = add(new ColorSetting("Player Color", "", 0xFFA855F7)).visibleWhen(players::isOn);
    private final BoolSetting hostiles = add(new BoolSetting("Hostile Mobs", "Outline hostile mobs", false));
    private final ColorSetting hostileColor = add(new ColorSetting("Hostile Color", "", 0xFFF2555A)).visibleWhen(hostiles::isOn);
    private final BoolSetting passives = add(new BoolSetting("Passive Mobs", "Outline animals", false));
    private final ColorSetting passiveColor = add(new ColorSetting("Passive Color", "", 0xFF6EE7A0)).visibleWhen(passives::isOn);
    private final NumberSetting range = add(new NumberSetting("Range", "Only outline within this distance", 64, 8, 256, 8));

    public GlowOutline() { super("Glow Outline", "Glowing outlines around items (and optionally players / mobs)", Category.VISUALS); }

    private boolean inRange(Entity e) {
        return mc.player != null && mc.player.squaredDistanceTo(e) <= range.get() * range.get();
    }

    public boolean shouldGlow(Entity e) {
        if (!isEnabled() || e == mc.player || !inRange(e)) return false;
        if (e instanceof ItemEntity) return items.isOn();
        if (e instanceof PlayerEntity) return players.isOn();
        if (e instanceof HostileEntity) return hostiles.isOn();
        if (e instanceof PassiveEntity) return passives.isOn();
        return false;
    }

    /** @return RGB colour, or -1 to keep vanilla. */
    public int color(Entity e) {
        if (!shouldGlow(e)) return -1;
        if (e instanceof ItemEntity item) {
            return switch (itemColorMode.get()) {
                case "Custom" -> itemColor.argb() & 0xFFFFFF;
                case "Theme" -> dev.stella.client.gui.theme.ThemeManager.get().accent() & 0xFFFFFF;
                default -> {
                    Formatting f = item.getStack().getRarity().getFormatting();
                    Integer c = f.getColorValue();
                    yield c == null ? 0xFFFFFF : c;
                }
            };
        }
        if (e instanceof PlayerEntity) return playerColor.argb() & 0xFFFFFF;
        if (e instanceof HostileEntity) return hostileColor.argb() & 0xFFFFFF;
        return passiveColor.argb() & 0xFFFFFF;
    }
}
