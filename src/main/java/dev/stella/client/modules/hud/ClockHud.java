package dev.stella.client.modules.hud;

import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Real time, plus the in-game time of day and day number. */
public class ClockHud extends HudElement {
    private final CardStyle card = card("Modern");
    private final BoolSetting h24 = add(new BoolSetting("24 Hour", "24-hour clock", false));
    private final BoolSetting game = add(new BoolSetting("Game Time", "Also show the in-game time and day", true));

    public ClockHud() { super("Clock HUD", "Real time and in-game time of day", HudElement.CENTER, 2, false); }

    @Override
    public void render(DrawContext ctx, float td, boolean editor) {
        String real = LocalTime.now().format(DateTimeFormatter.ofPattern(h24.isOn() ? "HH:mm" : "h:mm a"));
        String g = "";
        if (game.isOn() && mc.world != null) {
            long t = mc.world.getTimeOfDay();
            long day = t / 24000 + 1, tod = (t + 6000) % 24000; // 0 = midnight
            int hh = (int) (tod / 1000), mm = (int) ((tod % 1000) * 60 / 1000);
            g = String.format("Day %d  %02d:%02d", day, hh, mm);
        }
        int w = Math.max(StellaFont.width(real), (int) (StellaFont.width(g) * 0.75f)) + 4, h = g.isEmpty() ? 10 : 18;
        card.draw(ctx, this, w, h);
        setSize(w + card.padLeft() + card.pad(), h + card.pad() * 2);
        StellaFont.draw(ctx, real, card.padLeft(), card.pad(), col(0xFFF3F0F7), true);
        if (!g.isEmpty()) Render2D.textScaled(ctx, g, card.padLeft(), card.pad() + 10, 0.75f, col(0xFF9A93A6), true);
    }
}
