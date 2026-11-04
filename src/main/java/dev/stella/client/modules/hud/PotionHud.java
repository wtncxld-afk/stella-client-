package dev.stella.client.modules.hud;

import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Active potion effects with level and time left; flashes when about to run out. */
public class PotionHud extends HudElement {
    private final CardStyle card = card("Modern");
    private final BoolSetting colored = add(new BoolSetting("Effect Colors", "Colour each name like its potion", true));
    private final BoolSetting blink = add(new BoolSetting("Blink When Ending", "Flash effects with less than 10 seconds left", true));

    public PotionHud() { super("Potion HUD", "Active effects with level and time left", HudElement.CENTER, 30, false); }

    private static String formatTicks(int ticks) {
        int sec = Math.max(0, ticks / 20);
        return sec >= 3600 ? String.format("%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60) : String.format("%d:%02d", sec / 60, sec % 60);
    }

    private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        List<String[]> rows = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<Integer> ticks = new ArrayList<>();
        if (mc.player != null) {
            for (StatusEffectInstance e : mc.player.getStatusEffects()) {
                int amp = e.getAmplifier() + 1;
                String name = e.getEffectType().value().getName().getString() + (amp < ROMAN.length ? ROMAN[amp] : " " + amp);
                String time = e.isInfinite() ? "∞" : formatTicks(e.getDuration());
                rows.add(new String[]{name, time});
                colors.add(0xFF000000 | e.getEffectType().value().getColor());
                ticks.add(e.isInfinite() ? Integer.MAX_VALUE : e.getDuration());
            }
        }
        if (rows.isEmpty() && editor) { rows.add(new String[]{"Strength II", "1:30"}); colors.add(0xFFFFC700); ticks.add(1800); }
        int nameW = 0, timeW = 0;
        for (String[] r : rows) { nameW = Math.max(nameW, StellaFont.width(r[0])); timeW = Math.max(timeW, StellaFont.width(r[1])); }
        int w = Math.max(40, nameW + 8 + timeW), h = Math.max(8, rows.size() * 10 - 2);
        if (rows.isEmpty()) { setSize(90, 16); return; } // typical size so a centred default stays centred
        card.draw(ctx, this, w, h);
        int x = card.padLeft(), y = card.pad();
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows.size(); i++) {
            boolean ending = blink.isOn() && ticks.get(i) < 200 && (now / 300) % 2 == 0;
            int nc = colored.isOn() ? colors.get(i) : 0xFFF3F0F7;
            StellaFont.draw(ctx, rows.get(i)[0], x, y, col(ending ? 0x60FFFFFF & nc | 0x60000000 : nc), true);
            String t = rows.get(i)[1];
            StellaFont.draw(ctx, t, x + w - StellaFont.width(t), y, col(ending ? 0xFFFF5555 : 0xFFBFB8C9), true);
            y += 10;
        }
        setSize(w + card.padLeft() + card.pad(), h + card.pad() * 2);
    }
}
