package dev.stella.client.gui;

import dev.stella.client.gui.theme.Theme;
import dev.stella.client.render.Render2D;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Shared ClickGUI drawing primitives. */
public final class GuiDraw {
    private GuiDraw() {}

    /** Pill toggle switch; {@code t} is the animated 0..1 state. */
    public static void toggle(DrawContext ctx, Theme th, int x, int y, double t, double alpha) {
        int w = 14, h = 7;
        int off = 0xFF2A2533;
        int bg = ColorUtil.lerp(off, th.accent(), t);
        Render2D.roundRect(ctx, x, y, w, h, ColorUtil.mulAlpha(bg, alpha));
        int knobX = (int) Math.round(x + 1 + t * (w - 7));
        Render2D.roundRect(ctx, knobX, y + 1, 5, 5, ColorUtil.mulAlpha(t > 0.5 ? 0xFFFFFFFF : 0xFF9A93A6, alpha));
    }

    /** Tiny procedural gear (7x7). */
    public static void gear(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x + 2, y + 1, x + 5, y + 6, color);
        ctx.fill(x + 1, y + 2, x + 6, y + 5, color);
        ctx.fill(x + 3, y, x + 4, y + 7, color);
        ctx.fill(x, y + 3, x + 7, y + 4, color);
        ctx.fill(x + 3, y + 3, x + 4, y + 4, 0xFF141019);
    }

    /** Mini pill switch (10x5) used inside compact module rows. */
    public static void miniToggle(DrawContext ctx, Theme th, int x, int y, double t, double alpha, boolean onAccentRow) {
        int off = onAccentRow ? 0x40FFFFFF : 0xFF2A2533;
        int on = onAccentRow ? 0xFFFFFFFF : th.accent();
        Render2D.roundRect(ctx, x, y, 10f, 5f, 2.5f, ColorUtil.mulAlpha(ColorUtil.lerp(off, on, t * 0.35 + (onAccentRow ? 0 : t * 0.65)), alpha));
        float kx = (float) (x + 0.5 + t * 5);
        Render2D.roundRect(ctx, kx, y + 0.5f, 4f, 4f, 2f, ColorUtil.mulAlpha(onAccentRow ? 0xFFFFFFFF : (t > 0.5 ? 0xFFFFFFFF : 0xFF8C8696), alpha));
    }

    /** Square checkbox with a tick. */
    public static void checkbox(DrawContext ctx, Theme th, int x, int y, boolean on, double alpha) {
        Render2D.roundRect(ctx, x, y, 7f, 7f, 1.5f, ColorUtil.mulAlpha(on ? th.accent() : 0xFF2A2533, alpha));
        if (on) {
            int c = ColorUtil.mulAlpha(0xFFFFFFFF, alpha);
            ctx.fill(x + 1, y + 3, x + 2, y + 5, c);
            ctx.fill(x + 2, y + 4, x + 3, y + 6, c);
            ctx.fill(x + 3, y + 3, x + 4, y + 5, c);
            ctx.fill(x + 4, y + 2, x + 5, y + 4, c);
            ctx.fill(x + 5, y + 1, x + 6, y + 3, c);
        }
    }

    /** Round radio button. */
    public static void radio(DrawContext ctx, int x, int y, int color, boolean on, double alpha) {
        Render2D.roundRect(ctx, x, y, 7f, 7f, 3.5f, ColorUtil.mulAlpha(color, alpha));
        if (!on) Render2D.roundRect(ctx, x + 1.5f, y + 1.5f, 4f, 4f, 2f, ColorUtil.mulAlpha(0xFF111015, alpha));
        else Render2D.roundRect(ctx, x + 2f, y + 2f, 3f, 3f, 1.5f, ColorUtil.mulAlpha(0xFFFFFFFF, alpha));
    }

    /** Text drawn at a scale (for compact uppercase rows). */
    public static void small(DrawContext ctx, String s, float x, float y, float scale, int color) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) (x), (float) (y));
        ctx.getMatrices().scale((float) (scale), (float) (scale));
        dev.stella.client.render.StellaFont.draw(ctx, s, 0, 0, color, false);
        ctx.getMatrices().popMatrix();
    }

    public static int smallWidth(String s, float scale) { return (int) Math.ceil(width(s) * scale); }

    public static String trimSmall(String s, int w, float scale) { return trim(s, (int) (w / scale)); }

    /** Magnifier icon for the search bar. */
    public static void magnifier(DrawContext ctx, int x, int y, int c) {
        ctx.drawStrokedRectangle(x, y, 5, 5, c);
        ctx.fill(x + 4, y + 4, x + 5, y + 5, c);
        ctx.fill(x + 5, y + 5, x + 6, y + 6, c);
        ctx.fill(x + 6, y + 6, x + 7, y + 7, c);
    }

    public static String icon(dev.stella.client.module.Category c) {
        return switch (c) {
            case DONUTSMP -> "♦";
            case COMBAT -> "⚔";
            case MISC -> "✦";
            case RENDER -> "◉";
            case VISUALS -> "◈";
            case COSMETICS -> "✧";
            case CLIENT -> ">_";
            case WORLD -> "⛰";
            case HUD -> "▣";
            case SETTINGS -> "⚙";
        };
    }

    public static void text(DrawContext ctx, String s, int x, int y, int color) {
        dev.stella.client.render.StellaFont.draw(ctx, s, x, y, color, false);
    }

    public static int width(String s) { return dev.stella.client.render.StellaFont.width(s); }

    public static String trim(String s, int w) {
        if (dev.stella.client.render.StellaFont.width(s) <= w) return s;
        return dev.stella.client.render.StellaFont.trimToWidth(s, Math.max(0, w - dev.stella.client.render.StellaFont.width(".."))) + "..";
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
