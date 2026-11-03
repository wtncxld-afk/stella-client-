package dev.stella.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Minimal single-line text field used across the ClickGUI. */
public class TextInput {
    private static TextInput focused;

    private String text;
    private final int maxLength;
    private final String placeholder;
    private Consumer<String> onChange = s -> {};
    private Consumer<String> onSubmit = s -> {};
    private int cursor;

    public TextInput(String initial, int maxLength, String placeholder) {
        this.text = initial == null ? "" : initial;
        this.maxLength = maxLength;
        this.placeholder = placeholder;
        this.cursor = this.text.length();
    }

    public TextInput onChange(Consumer<String> c) { this.onChange = c; return this; }
    public TextInput onSubmit(Consumer<String> c) { this.onSubmit = c; return this; }

    public String get() { return text; }
    public void set(String s) { text = s == null ? "" : s; cursor = text.length(); }

    public boolean isFocused() { return focused == this; }
    public void focus() { focused = this; cursor = text.length(); }
    public static void unfocusAll() { focused = null; }
    public static boolean anyFocused() { return focused != null; }
    public static TextInput focused() { return focused; }

    public void render(DrawContext ctx, int x, int y, int w, int h, int bg, int border, int textColor, int dimColor) {
        if (bg != 0) {
            dev.stella.client.render.Render2D.roundRect(ctx, x, y, w, h, 3, bg);
            if (isFocused()) dev.stella.client.render.Render2D.roundRect(ctx, x + 2, y + h - 1, w - 4, 1, 0.5f, border);
        }
        String shown = text.isEmpty() && !isFocused() ? placeholder : text;
        int color = text.isEmpty() && !isFocused() ? dimColor : textColor;
        // keep the end of long text visible
        String visible = shown;
        while (dev.stella.client.render.StellaFont.width(visible) > w - 6 && visible.length() > 1) visible = visible.substring(1);
        dev.stella.client.render.StellaFont.draw(ctx, visible, x + 3, y + (h - 8) / 2, color, false);
        if (isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + 3 + dev.stella.client.render.StellaFont.width(visible);
            ctx.fill(cx, y + 2, cx + 1, y + h - 2, textColor);
        }
    }

    /** @return true if consumed. */
    public boolean keyPressed(int key, int mods) {
        if (!isFocused()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!text.isEmpty()) {
                    text = Screen.hasControlDown() ? "" : text.substring(0, text.length() - 1);
                    onChange.accept(text);
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { onSubmit.accept(text); return true; }
            case GLFW.GLFW_KEY_ESCAPE -> { unfocusAll(); return true; }
            case GLFW.GLFW_KEY_V -> {
                if (Screen.hasControlDown()) {
                    String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                    if (clip != null) insert(clip.replaceAll("[\\r\\n]", ""));
                    return true;
                }
                return true;
            }
            default -> { return true; } // swallow keys while typing so binds don't fire
        }
    }

    public boolean charTyped(char c) {
        if (!isFocused()) return false;
        if (c < 32 || c == 127) return true;
        insert(String.valueOf(c));
        return true;
    }

    private void insert(String s) {
        String n = text + s;
        if (n.length() > maxLength) n = n.substring(0, maxLength);
        text = n;
        cursor = text.length();
        onChange.accept(text);
    }
}
