package dev.stella.client.modules.hud;

import dev.stella.client.hud.HudElement;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.CombatEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

/** Counts down your combat tag after you hit a player or a player hits you. */
public class CombatTimerHud extends HudElement {
    private final NumberSetting duration = add(new NumberSetting("Duration", "Combat tag length on your server", 15, 5, 60, 1).suffix("s"));
    private long taggedAt = -1;
    private int lastHurt;

    public CombatTimerHud() {
        super("Combat Timer HUD", "Counts down your combat tag after PvP", -9000, 230, false);
        CombatEvents.onAttack(e -> { if (e instanceof PlayerEntity) taggedAt = System.currentTimeMillis(); });
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        int ht = mc.player.hurtTime;
        if (ht > lastHurt && mc.player.getRecentDamageSource() != null && mc.player.getRecentDamageSource().getAttacker() instanceof PlayerEntity) taggedAt = System.currentTimeMillis();
        lastHurt = ht;
        if (mc.player.isDead()) taggedAt = -1;
    }

    /** Starts the tag now (also used by the preview). */
    public void tag() { taggedAt = System.currentTimeMillis(); }

    @Override
    public void render(DrawContext ctx, float td, boolean editor) {
        double left = taggedAt < 0 ? -1 : duration.get() - (System.currentTimeMillis() - taggedAt) / 1000.0;
        if (left <= 0 && !editor) return;
        if (left <= 0) left = duration.get() * 0.6;
        double f = left / duration.get();
        int w = 110, h = 22;
        setSize(w, h);
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0));
        int red = ColorUtil.lerp(0xFFFF3B3B, 0xFFFF8A8A, pulse * (f < 0.3 ? 1 : 0.3f));
        Render2D.roundRect(ctx, 0, 0, w, h, 5, col(0xC0140A10));
        Render2D.roundRect(ctx, 0, 0, w, h, 5, col(ColorUtil.withAlpha(red, 0x30)));
        StellaFont.draw(ctx, "⚔ COMBAT", 6, 4, col(red), true);
        String s = String.format("%.1fs", left);
        StellaFont.draw(ctx, s, w - 6 - StellaFont.width(s), 4, col(0xFFF3F0F7), true);
        Render2D.roundRect(ctx, 6, 15, w - 12, 3, 1.5f, col(0x50000000));
        Render2D.roundRect(ctx, 6, 15, (float) ((w - 12) * f), 3, 1.5f, col(red));
    }
}
