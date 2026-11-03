package dev.stella.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.RotationAxis;

/** Small 2D drawing helpers on top of {@link DrawContext}. */
public final class Render2D {
    private Render2D() {}

    public static TextRenderer font() { return MinecraftClient.getInstance().textRenderer; }

    public static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(w, h, 1);
        ctx.fill(0, 0, 1, 1, color);
        ctx.getMatrices().pop();
    }

    public static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.drawBorder(x, y, w, h, color);
    }

    /** Pseudo-rounded rectangle (corner pixels trimmed) — cheap and crisp at GUI scale. */
    public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) { ctx.fill(x, y, x + w, y + h, color); return; }
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * Rounded rectangle with a real radius, drawn as horizontal spans in
     * sub-pixel (quarter-pixel) steps so corners stay smooth at any GUI scale.
     */
    public static void roundRect(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        if (r < 0.5f) { rect(ctx, x, y, w, h, color); return; }
        rect(ctx, x, y + r, w, h - 2 * r, color);
        float step = 0.25f;
        for (float dy = 0; dy < r; dy += step) {
            float cy = r - dy - step / 2f;
            float inset = r - (float) Math.sqrt(Math.max(0, r * r - cy * cy));
            rect(ctx, x + inset, y + dy, w - inset * 2, step, color);
            rect(ctx, x + inset, y + h - dy - step, w - inset * 2, step, color);
        }
    }

    /** Soft drop shadow: stacked rounded rects with falling alpha. */
    public static void shadow(DrawContext ctx, float x, float y, float w, float h, float r, int layers, int color) {
        int baseA = (color >>> 24) & 0xFF;
        for (int i = layers; i >= 1; i--) {
            int a = baseA * (layers - i + 1) / (layers * (layers + 1) / 2 + 1);
            roundRect(ctx, x - i, y - i + 1, w + i * 2, h + i * 2, r + i, (a << 24) | (color & 0xFFFFFF));
        }
    }

    /** Horizontal gradient bar (left colour to right colour). */
    public static void hGradient(DrawContext ctx, float x, float y, float w, float h, int left, int right) {
        int steps = Math.max(1, (int) Math.min(48, w));
        for (int i = 0; i < steps; i++) {
            float x1 = x + w * i / steps, x2 = x + w * (i + 1) / steps;
            rect(ctx, x1, y, x2 - x1, h, dev.stella.client.util.ColorUtil.lerp(left, right, i / (double) Math.max(1, steps - 1)));
        }
    }

    /** Thick 2D line rendered as a rotated rectangle. */
    public static void line(DrawContext ctx, double x1, double y1, double x2, double y2, float width, int color) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) return;
        float angle = (float) Math.atan2(dy, dx);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x1, y1, 0);
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation(angle));
        ctx.getMatrices().scale((float) len, width, 1);
        ctx.getMatrices().translate(0, -0.5f, 0);
        ctx.fill(0, 0, 1, 1, color);
        ctx.getMatrices().pop();
    }

    public static void text(DrawContext ctx, String s, float x, float y, int color, boolean shadow) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.drawText(font(), s, 0, 0, color, shadow);
        ctx.getMatrices().pop();
    }

    public static void textScaled(DrawContext ctx, String s, float x, float y, float scale, int color, boolean shadow) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.drawText(font(), s, 0, 0, color, shadow);
        ctx.getMatrices().pop();
    }

    public static void centeredText(DrawContext ctx, String s, float cx, float y, int color, boolean shadow) {
        text(ctx, s, cx - font().getWidth(s) / 2f, y, color, shadow);
    }

    /** Label with translucent backing, centred at (cx, y). */
    public static void label(DrawContext ctx, String s, double cx, double y, float scale, int textColor, int bgColor) {
        TextRenderer f = font();
        int w = f.getWidth(s);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        if ((bgColor >>> 24) != 0) ctx.fill(-w / 2 - 2, -1, w / 2 + 2, 9, bgColor);
        ctx.drawText(f, s, -w / 2, 0, textColor, true);
        ctx.getMatrices().pop();
    }
}
