package dev.stella.client.hud;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.render.Render2D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * Per-element card look. Each HUD element owns its own CardStyle instance (and
 * therefore its own saved settings) — styles are never shared between elements.
 */
public final class CardStyle {
    public final ModeSetting style;
    public final ColorSetting background;
    public final BoolSetting accentBar;
    public final BoolSetting shadow;
    public final BoolSetting themeColors;

    CardStyle(HudElement owner, String defaultStyle) {
        style = owner.addSetting(new ModeSetting("Style", "Modern = rounded card, Classic = square backing, Minimal = text only",
                defaultStyle, "Modern", "Classic", "Minimal"));
        background = owner.addSetting(new ColorSetting("Card Color", "Card background", 0xC00E0C12)).visibleWhen(() -> !style.is("Minimal"));
        accentBar = owner.addSetting(new BoolSetting("Accent Bar", "Theme-coloured bar on the card's left edge", true)).visibleWhen(() -> style.is("Modern"));
        shadow = owner.addSetting(new BoolSetting("Shadow", "Soft drop shadow", true)).visibleWhen(() -> style.is("Modern"));
        themeColors = owner.addSetting(new BoolSetting("Theme Colors", "Use the GUI theme's accent for highlights", true));
    }

    public int pad() { return style.is("Minimal") ? 0 : 4; }
    public int padLeft() { return style.is("Modern") && accentBar.isOn() ? 7 : pad(); }

    /** Draws the card behind content of size (w, h) measured WITHOUT padding. */
    public void draw(DrawContext ctx, HudElement e, int w, int h) {
        if (style.is("Minimal")) return;
        int tw = w + padLeft() + pad(), th = h + pad() * 2;
        if (style.is("Classic")) {
            ctx.fill(0, 0, tw, th, e.col(background.argb()));
            return;
        }
        if (shadow.isOn()) Render2D.shadow(ctx, 0, 0, tw, th, 4, 3, e.col(0x70000000));
        Render2D.roundRect(ctx, 0, 0, tw, th, 4, e.col(background.argb()));
        Render2D.roundRect(ctx, 0.5f, 0.5f, tw - 1, 1, 0.5f, e.col(0x18FFFFFF)); // top highlight
        if (accentBar.isOn()) {
            int top = ThemeManager.accentAt(0), bottom = ThemeManager.accentAt(0.15);
            for (int i = 0; i < th - 4; i++) {
                int c = ColorUtil.lerp(top, ColorUtil.lerp(bottom, 0xFFFFFFFF, 0.25), i / (double) Math.max(1, th - 5));
                ctx.fill(2, 2 + i, 4, 3 + i, e.col(c));
            }
        }
    }

    public int accent(int fallback) { return themeColors.isOn() ? ThemeManager.get().accent() : fallback; }
    public int accentAt(double offset, int fallback) { return themeColors.isOn() ? ThemeManager.accentAt(offset) : fallback; }
}
