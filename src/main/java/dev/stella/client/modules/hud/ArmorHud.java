package dev.stella.client.modules.hud;

import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Armor and held items with durability bars and percentages. */
public class ArmorHud extends HudElement {
    private final CardStyle card = card("Modern");
    private final ModeSetting layout = add(new ModeSetting("Layout", "Arrange items in a row or a column", "Horizontal", "Horizontal", "Vertical"));
    private final BoolSetting hands = add(new BoolSetting("Held Items", "Also show main hand and offhand", true));
    private final ModeSetting text = add(new ModeSetting("Durability Text", "Show durability as a percentage, remaining uses, or nothing", "Percent", "Percent", "Uses", "None"));
    private final BoolSetting bars = add(new BoolSetting("Durability Bars", "Coloured bar under each item", true));

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public ArmorHud() { super("Armor HUD", "Armor and held items with durability", 4, -150, false); }

    private List<ItemStack> stacks(boolean editor) {
        List<ItemStack> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (EquipmentSlot s : ARMOR) {
            ItemStack st = mc.player.getEquippedStack(s);
            if (!st.isEmpty()) out.add(st);
        }
        if (hands.isOn()) {
            if (!mc.player.getMainHandStack().isEmpty()) out.add(mc.player.getMainHandStack());
            if (!mc.player.getOffHandStack().isEmpty()) out.add(mc.player.getOffHandStack());
        }
        if (out.isEmpty() && editor) {
            out.add(new ItemStack(Items.DIAMOND_HELMET)); out.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
            out.add(new ItemStack(Items.DIAMOND_LEGGINGS)); out.add(new ItemStack(Items.DIAMOND_BOOTS));
        }
        return out;
    }

    private static int durabilityColor(float f) {
        int r = (int) (255 * Math.min(1, 2 * (1 - f))), g = (int) (255 * Math.min(1, 2 * f));
        return 0xFF000000 | (r << 16) | (g << 8) | 0x40;
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        List<ItemStack> list = stacks(editor);
        boolean horizontal = layout.is("Horizontal");
        int cell = horizontal ? 22 : 18;
        int textW = 0;
        if (!horizontal && !text.is("None")) textW = StellaFont.width("100%") + 4;
        int count = Math.max(1, list.size());
        int w = horizontal ? count * cell : 18 + textW;
        int h = horizontal ? (text.is("None") ? 20 : 28) : count * cell;
        card.draw(ctx, this, w, h);
        int ox = card.padLeft(), oy = card.pad();
        for (int i = 0; i < list.size(); i++) {
            ItemStack st = list.get(i);
            int x = ox + (horizontal ? i * cell + 3 : 0), y = oy + (horizontal ? 0 : i * cell);
            ctx.drawItem(st, x, y);
            if (st.getCount() > 1) StellaFont.draw(ctx, String.valueOf(st.getCount()), x + 10, y + 9, col(0xFFFFFFFF), true);
            if (!st.isDamageable()) continue;
            float f = 1f - st.getDamage() / (float) Math.max(1, st.getMaxDamage());
            int c = durabilityColor(f);
            if (bars.isOn()) {
                Render2D.rect(ctx, x + 1, y + 16, 14, 2, col(0xC0000000));
                Render2D.rect(ctx, x + 1, y + 16, 14 * f, 2, col(c));
            }
            if (!text.is("None")) {
                String s = text.is("Percent") ? Math.round(f * 100) + "%" : String.valueOf(st.getMaxDamage() - st.getDamage());
                if (horizontal) Render2D.textScaled(ctx, s, x + 8 - StellaFont.width(s) * 0.35f, y + 20, 0.7f, col(c), true);
                else StellaFont.draw(ctx, s, x + 20, y + 5, col(c), true);
            }
        }
        setSize(w + card.padLeft() + card.pad(), h + card.pad() * 2);
    }
}
