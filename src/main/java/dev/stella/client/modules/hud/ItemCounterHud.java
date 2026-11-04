package dev.stella.client.modules.hud;

import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/** How many totems, crystals, pearls, gapples... you carry (inventory + offhand). */
public class ItemCounterHud extends HudElement {
    private final CardStyle card = card("Modern");
    private final BoolSetting totems = add(new BoolSetting("Totems", "", true));
    private final BoolSetting crystals = add(new BoolSetting("End Crystals", "", true));
    private final BoolSetting obsidian = add(new BoolSetting("Obsidian", "", true));
    private final BoolSetting anchors = add(new BoolSetting("Respawn Anchors", "", true));
    private final BoolSetting glowstone = add(new BoolSetting("Glowstone", "", true));
    private final BoolSetting pearls = add(new BoolSetting("Ender Pearls", "", true));
    private final BoolSetting gapples = add(new BoolSetting("Golden Apples", "", true));
    private final BoolSetting xp = add(new BoolSetting("XP Bottles", "", true));
    private final BoolSetting hideZero = add(new BoolSetting("Hide Zero", "Hide items you have none of", true));
    private final BoolSetting warn = add(new BoolSetting("Low Totem Warning", "Count turns red at 1 or fewer totems", true));

    public ItemCounterHud() { super("Item Counter HUD", "Counts totems, crystals, pearls and other PvP items you carry", HudElement.CENTER, -66, false); }

    private int count(Item item) {
        if (mc.player == null) return 0;
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) { ItemStack s = inv.getStack(i); if (s.isOf(item)) n += s.getCount(); }
        return n;
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        List<Item> items = new ArrayList<>();
        if (totems.isOn()) items.add(Items.TOTEM_OF_UNDYING);
        if (crystals.isOn()) items.add(Items.END_CRYSTAL);
        if (obsidian.isOn()) items.add(Items.OBSIDIAN);
        if (anchors.isOn()) items.add(Items.RESPAWN_ANCHOR);
        if (glowstone.isOn()) items.add(Items.GLOWSTONE);
        if (pearls.isOn()) items.add(Items.ENDER_PEARL);
        if (gapples.isOn()) { items.add(Items.GOLDEN_APPLE); items.add(Items.ENCHANTED_GOLDEN_APPLE); }
        if (xp.isOn()) items.add(Items.EXPERIENCE_BOTTLE);
        List<Item> shown = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Item it : items) {
            int c = count(it);
            if (c == 0 && hideZero.isOn() && !editor) continue;
            shown.add(it); counts.add(c);
        }
        if (shown.isEmpty()) { setSize(items.size() * 24 + 11, 24); return; } // typical size so a centred default stays centred
        int cell = 24;
        int w = shown.size() * cell, h = 16;
        card.draw(ctx, this, w, h);
        int x = card.padLeft(), y = card.pad();
        for (int i = 0; i < shown.size(); i++) {
            ctx.drawItem(new ItemStack(shown.get(i)), x + i * cell, y);
            int c = counts.get(i);
            String s = String.valueOf(c);
            int color = shown.get(i) == Items.TOTEM_OF_UNDYING && warn.isOn() && c <= 1 ? 0xFFFF5555 : 0xFFFFFFFF;
            StellaFont.draw(ctx, s, x + i * cell + 17 - StellaFont.width(s) + 6, y + 9, col(color), true);
        }
        setSize(w + card.padLeft() + card.pad(), h + card.pad() * 2);
    }
}
