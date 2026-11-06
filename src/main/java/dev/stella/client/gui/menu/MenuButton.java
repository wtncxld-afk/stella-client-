package dev.stella.client.gui.menu;

import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/** Rounded glassy menu button with hover fill and entrance slide. */
public class MenuButton {
    public final String label;
    public final String icon;
    public final Runnable action;
    public final boolean primary, danger;
    public int x, y, w, h = 20;
    private final Animation hover = new Animation(0, 18);

    public MenuButton(String icon, String label, Runnable action, boolean primary, boolean danger) {
        this.icon = icon;
        this.label = label;
        this.action = action;
        this.primary = primary;
        this.danger = danger;
    }

    public boolean over(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    /** @param enter 0..1 entrance progress (slides in from the left) */
    public void render(DrawContext ctx, int mx, int my, double alpha, double enter) {
        if (dev.stella.client.Brand.ISRAELI) { renderWhite(ctx, mx, my, alpha, enter); return; }
        Theme t = ThemeManager.get();
        double hv = hover.target(over(mx, my) ? 1 : 0).get();
        float ox = (float) ((1 - enter) * -30);
        double a = alpha * enter;
        int base = danger ? 0xFF3A1418 : 0xFF17141D;
        int fill = danger ? 0xFFE5484D : t.accent();
        float bx = x + ox;
        if (hv > 0.02 || primary) Render2D.shadow(ctx, bx, y, w, h, 6, 4, ColorUtil.mulAlpha(ColorUtil.withAlpha(fill, 0x70), a * Math.max(hv, primary ? 0.5 : 0)));
        Render2D.roundRect(ctx, bx, y, w, h, 6, ColorUtil.mulAlpha(ColorUtil.withAlpha(base, 0xC8), a));
        // hover fill sweeps in from the left
        float fw = (float) (w * (primary ? Math.max(0.0, hv) : hv));
        if (primary) Render2D.roundRect(ctx, bx, y, w, h, 6, ColorUtil.mulAlpha(ColorUtil.withAlpha(fill, 0x55), a));
        if (fw > 1) Render2D.roundRect(ctx, bx, y, fw, h, 6, ColorUtil.mulAlpha(ColorUtil.withAlpha(fill, 0xD8), a));
        Render2D.roundRect(ctx, bx + 0.5f, y + 0.5f, w - 1, 1, 0.5f, ColorUtil.mulAlpha(0x22FFFFFF, a));
        // light sweep across the button while hovered, and a soft pulsing edge on the primary one
        if (hv > 0.05) {
            double k = (System.currentTimeMillis() % 1600) / 1600.0;
            float sx = (float) (bx - 20 + k * (w + 40));
            for (int i = 0; i < 6; i++) {
                float lx = sx + i * 1.5f;
                if (lx > bx + 3 && lx < bx + w - 3)
                    Render2D.line(ctx, lx + 3, y + 1, lx - 3, y + h - 1, 1.5f, ColorUtil.mulAlpha(0x1CFFFFFF, a * hv * Math.sin(Math.PI * i / 5.0)));
            }
        }
        if (primary) {
            double p = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 500.0);
            Render2D.roundRect(ctx, bx + w * 0.15f, y + h - 1, w * 0.7f, 1, 0.5f, ColorUtil.mulAlpha(ColorUtil.withAlpha(0xFFFFFFFF, (int) (40 + 60 * p)), a));
        }
        int tc = ColorUtil.lerp(primary ? 0xFFFFFFFF : t.text(), 0xFFFFFFFF, hv);
        int textX = (int) (bx + 10 + hv * 3);
        if (!icon.isEmpty()) {
            StellaFont.draw(ctx, icon, textX, y + 6, ColorUtil.mulAlpha(hv > 0.5 ? 0xFFFFFFFF : fill, a), false);
            textX += 14;
        }
        StellaFont.draw(ctx, label, textX, y + 6, ColorUtil.mulAlpha(tc, a), true);
        if (hv > 0.05) StellaFont.draw(ctx, "›", (int) (bx + w - 12 + hv * 2), y + 6, ColorUtil.mulAlpha(0xFFFFFFFF, a * hv), false);
    }

    /** Israeli Client style: white card, blue text; hover fills it with flag blue and turns the text white. */
    private void renderWhite(DrawContext ctx, int mx, int my, double alpha, double enter) {
        double hv = hover.target(over(mx, my) ? 1 : 0).get();
        float ox = (float) ((1 - enter) * -30);
        double a = alpha * enter;
        int blue = IsraelFlag.BLUE, fill = danger ? 0xFFE5484D : blue;
        float bx = x + ox;
        Render2D.shadow(ctx, bx, y, w, h, 6, 4, ColorUtil.mulAlpha(0x40001A4D, a * (0.6 + 0.4 * hv)));
        Render2D.roundRect(ctx, bx, y, w, h, 6, ColorUtil.mulAlpha(primary ? 0xFFE6F3FF : 0xF8FFFFFF, a));
        Render2D.roundRect(ctx, bx, y + h - 1.5f, w, 1.5f, 0.75f, ColorUtil.mulAlpha(ColorUtil.withAlpha(fill, primary ? 0xC0 : 0x50), a));
        float fw = (float) (w * hv);
        if (fw > 1) Render2D.roundRect(ctx, bx, y, fw, h, 6, ColorUtil.mulAlpha(ColorUtil.withAlpha(fill, 0xF0), a));
        int tc = ColorUtil.lerp(danger ? 0xFFC0262D : blue, 0xFFFFFFFF, hv);
        int textX = (int) (bx + 10 + hv * 3);
        if (!icon.isEmpty()) {
            StellaFont.draw(ctx, icon, textX, y + 6, ColorUtil.mulAlpha(tc, a), false);
            textX += 14;
        }
        StellaFont.draw(ctx, label, textX, y + 6, ColorUtil.mulAlpha(tc, a), false);
        if (hv > 0.05) StellaFont.draw(ctx, "›", (int) (bx + w - 12 + hv * 2), y + 6, ColorUtil.mulAlpha(0xFFFFFFFF, a * hv), false);
    }
}
