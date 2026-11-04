package dev.stella.client.modules.hud;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.misc.FriendsModule;
import dev.stella.client.modules.pvp.TotemPopCounter;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.CombatEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;

/** The player you're fighting: head, health (with absorption), armor, totem pops, distance and who's winning. */
public class TargetHud extends HudElement {
    private final CardStyle card = card("Modern");
    private final NumberSetting keep = add(new NumberSetting("Keep Seconds", "How long the last player you hit stays shown", 6, 1, 30, 1).suffix("s"));
    private final BoolSetting crosshair = add(new BoolSetting("Crosshair Target", "Also show a player you're looking at", true));
    private final BoolSetting armor = add(new BoolSetting("Armor", "Show their armor with durability", true));
    private final BoolSetting pops = add(new BoolSetting("Totem Pops", "Show totems popped (needs Totem Pop Counter)", true));
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance", 12, 3, 64, 1).suffix("m"));

    private float shownHealth = -1;
    private long hitFlash;
    private LivingEntity lastShown;

    public TargetHud() { super("Target HUD", "Player you're fighting: health, armor, totem pops, distance", -9000, 300, false); }

    private Object lastWorld;

    private LivingEntity target() {
        if (mc.player == null || mc.world == null) return null;
        if (mc.world != lastWorld) { lastWorld = mc.world; CombatEvents.lastTarget = null; } // never show someone from another world
        if (crosshair.isOn() && mc.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() instanceof PlayerEntity p && !FriendsModule.is(p)) return p;
        if (CombatEvents.lastTarget instanceof LivingEntity le && le.isAlive() && !le.isRemoved() && mc.world.getEntityById(le.getId()) == le
                && System.currentTimeMillis() - CombatEvents.lastAttackAt < keep.get() * 1000 && le.distanceTo(mc.player) <= range.get()) return le;
        return null;
    }

    @Override
    public void render(DrawContext ctx, float td, boolean editor) {
        LivingEntity t = target();
        if (t == null && !editor) { shownHealth = -1; lastShown = null; return; }
        int w = 150, h = armor.isOn() ? 54 : 38;
        card.draw(ctx, this, w, h);
        setSize(w + card.padLeft() + card.pad(), h + card.pad() * 2);
        int x = card.padLeft(), y = card.pad();
        String name = t == null ? "Opponent" : t.getName().getString();
        float hp = t == null ? 14 : t.getHealth(), max = t == null ? 20 : t.getMaxHealth(), abs = t == null ? 4 : t.getAbsorptionAmount();
        if (t != lastShown) { shownHealth = hp; lastShown = t; }
        if (hp < shownHealth - 0.01f) hitFlash = System.currentTimeMillis();
        shownHealth += (hp - shownHealth) * 0.15f;
        // head
        var skin = DefaultSkinHelper.getSteve();
        if (t instanceof PlayerEntity p) {
            var e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            skin = e != null ? e.getSkinTextures() : DefaultSkinHelper.getSkinTextures(p.getUuid());
        }
        float flash = Math.max(0, 1 - (System.currentTimeMillis() - hitFlash) / 300f);
        Render2D.roundRect(ctx, x - 1, y - 1, 32, 32, 3, col(ColorUtil.lerp(ThemeManager.get().accent(), 0xFFFF3B3B, flash)));
        PlayerSkinDrawer.draw(ctx, skin, x, y, 30);
        int tx = x + 36;
        boolean friend = t instanceof PlayerEntity fp && FriendsModule.is(fp);
        StellaFont.draw(ctx, name, tx, y + 1, col(friend ? FriendsModule.get().color.argb() : 0xFFF3F0F7), true);
        String dist = t == null ? "4.2m" : String.format("%.1fm", t.distanceTo(mc.player));
        StellaFont.draw(ctx, dist, tx + w - 38 - StellaFont.width(dist), y + 1, col(0xFF9A93A6), true);
        // health bar
        float bw = w - 40, frac = Math.max(0, Math.min(1, shownHealth / max)), real = Math.max(0, Math.min(1, hp / max));
        Render2D.roundRect(ctx, tx, y + 13, bw, 7, 3, col(0x60000000));
        Render2D.roundRect(ctx, tx, y + 13, bw * frac, 7, 3, col(0xFFFFE0E0));
        int hc = ColorUtil.lerp(0xFFFF4A4A, 0xFF4DE38B, real);
        Render2D.roundRect(ctx, tx, y + 13, bw * real, 7, 3, col(hc));
        if (abs > 0) Render2D.roundRect(ctx, tx, y + 13, bw * Math.min(1, abs / max), 3, 1.5f, col(0xFFFFC94D));
        String hpText = String.format("%.1f", (hp + abs) / 2f) + " ❤";
        Render2D.textScaled(ctx, hpText, tx, y + 23, 0.75f, col(hc), true);
        // who is winning
        if (mc.player != null) {
            float mine = mc.player.getHealth() + mc.player.getAbsorptionAmount(), theirs = hp + abs;
            String st = mine > theirs + 2 ? "WINNING" : theirs > mine + 2 ? "LOSING" : "EVEN";
            int sc = st.equals("WINNING") ? 0xFF4DE38B : st.equals("LOSING") ? 0xFFFF5A5A : 0xFFFFC94D;
            Render2D.textScaled(ctx, st, tx + bw - StellaFont.width(st) * 0.7f, y + 23, 0.7f, col(sc), true);
        }
        if (pops.isOn() && t != null) {
            TotemPopCounter tpc = ModuleManager.get(TotemPopCounter.class);
            int n = tpc == null ? 0 : tpc.pops(t.getUuid());
            if (n > 0) Render2D.textScaled(ctx, n + " pop" + (n == 1 ? "" : "s"), tx + 46, y + 23, 0.7f, col(0xFFFFB347), true);
        }
        if (armor.isOn()) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
            int ax = x;
            for (EquipmentSlot s : slots) {
                ItemStack st = t == null ? ItemStack.EMPTY : t.getEquippedStack(s);
                if (!st.isEmpty()) {
                    ctx.drawItem(st, ax, y + 35);
                    if (st.isDamageable()) {
                        float f = 1 - st.getDamage() / (float) Math.max(1, st.getMaxDamage());
                        Render2D.rect(ctx, ax + 1, y + 51, 14, 1.5f, col(0xC0000000));
                        Render2D.rect(ctx, ax + 1, y + 51, 14 * f, 1.5f, col(ColorUtil.lerp(0xFFFF4A4A, 0xFF4DE38B, f)));
                    }
                }
                ax += 18;
            }
        }
    }
}
