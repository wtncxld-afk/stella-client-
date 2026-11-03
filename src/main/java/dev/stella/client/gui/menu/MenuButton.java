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
        int tc = ColorUtil.lerp(primary ? 0xFFFFFFFF : t.text(), 0xFFFFFFFF, hv);
        int textX = (int) (bx + 10 + hv * 3);
        if (!icon.isEmpty()) {
            StellaFont.draw(ctx, icon, textX, y + 6, ColorUtil.mulAlpha(hv > 0.5 ? 0xFFFFFFFF : fill, a), false);
            textX += 14;
        }
        StellaFont.draw(ctx, label, textX, y + 6, ColorUtil.mulAlpha(tc, a), true);
        if (hv > 0.05) StellaFont.draw(ctx, "›", (int) (bx + w - 12 + hv * 2), y + 6, ColorUtil.mulAlpha(0xFFFFFFFF, a * hv), false);
    }
}
