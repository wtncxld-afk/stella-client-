package dev.stella.client.render;

import dev.stella.client.StellaClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Stella's UI font: TNH Xuong (SIL Open Font License, see
 * assets/stella/font/XUONG-OFL.txt), loaded as a Minecraft TTF font provider
 * with the vanilla font as fallback for symbols it does not contain. Can be
 * turned off from the THEMES panel ("Custom Font"), which falls back to the
 * vanilla font everywhere.
 */
public final class StellaFont {
    public static final Identifier ID = Identifier.of(StellaClient.MOD_ID, "xuong");
    private static final Style STYLE = Style.EMPTY.withFont(ID);
    private static boolean enabled = true;

    private StellaFont() {}

    public static void setEnabled(boolean on) { enabled = on; }
    public static boolean enabled() { return enabled; }

    private static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

    public static Text text(String s) {
        MutableText t = Text.literal(s);
        return enabled ? t.setStyle(STYLE) : t;
    }

    public static int draw(DrawContext ctx, String s, int x, int y, int color, boolean shadow) {
        return ctx.drawText(tr(), text(s), x, y, color, shadow);
    }

    public static int width(String s) { return enabled ? tr().getWidth(text(s)) : tr().getWidth(s); }

    /** Trims to {@code w} pixels (no ellipsis). */
    public static String trimToWidth(String s, int w) {
        if (width(s) <= w) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (width(s.substring(0, mid)) <= w) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo);
    }
}
