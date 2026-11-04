package dev.stella.client.modules.hud;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.util.track.CpsTracker;
import net.minecraft.client.gui.DrawContext;

/** Left / right clicks per second over the last 15 seconds as a graph. */
public class CpsGraphHud extends HudElement {
    private final CardStyle card = card("Modern");
    private final int[] left = new int[60], right = new int[60];
    private int head;
    private long last;

    public CpsGraphHud() { super("CPS Graph HUD", "Graph of your left / right CPS over 15 seconds", 4, 175, false); }

    @Override
    public void render(DrawContext ctx, float td, boolean editor) {
        long now = System.currentTimeMillis();
        if (now - last >= 250) {
            last = now;
            head = (head + 1) % left.length;
            left[head] = editor && CpsTracker.left() == 0 ? (int) (6 + 4 * Math.sin(now / 900.0)) : CpsTracker.left();
            right[head] = editor && CpsTracker.right() == 0 ? (int) (3 + 2 * Math.cos(now / 700.0)) : CpsTracker.right();
        }
        int w = 120, h = 44;
        card.draw(ctx, this, w, h);
        setSize(w + card.padLeft() + card.pad(), h + card.pad() * 2);
        int x = card.padLeft(), y = card.pad();
        int max = 4;
        for (int i = 0; i < left.length; i++) max = Math.max(max, Math.max(left[i], right[i]));
        StellaFont.draw(ctx, "CPS", x, y, col(0xFFF3F0F7), true);
        String cur = left[head] + " | " + right[head];
        StellaFont.draw(ctx, cur, x + w - 4 - StellaFont.width(cur), y, col(ThemeManager.get().accent()), true);
        int gy = y + 12, gh = h - 14;
        Render2D.roundRect(ctx, x, gy, w - 4, gh, 3, col(0x40000000));
        series(ctx, left, x, gy, w - 4, gh, max, ThemeManager.get().accent());
        series(ctx, right, x, gy, w - 4, gh, max, 0xFF4FD8FF);
    }

    private void series(DrawContext ctx, int[] v, int x, int y, int w, int h, int max, int color) {
        int n = v.length;
        double px = 0, py = 0;
        for (int k = 0; k < n; k++) {
            int idx = (head + 1 + k) % n;
            double sx = x + 2 + (w - 4) * k / (double) (n - 1), sy = y + h - 2 - (h - 4) * v[idx] / (double) max;
            if (k > 0) Render2D.line(ctx, px, py, sx, sy, 1.2f, col(color));
            px = sx; py = sy;
        }
    }
}
