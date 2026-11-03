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

    public static void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(MinecraftClient.getInstance().textRenderer, s, x, y, color, false);
    }

    public static int width(String s) { return MinecraftClient.getInstance().textRenderer.getWidth(s); }

    public static String trim(String s, int w) {
        var tr = MinecraftClient.getInstance().textRenderer;
        if (tr.getWidth(s) <= w) return s;
        return tr.trimToWidth(s, Math.max(0, w - tr.getWidth(".."))) + "..";
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
