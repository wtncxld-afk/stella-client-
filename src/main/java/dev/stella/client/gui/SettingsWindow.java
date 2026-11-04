package dev.stella.client.gui;

import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.gui.widget.TextInput;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BlockListSetting;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.KeySetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.setting.Setting;
import dev.stella.client.setting.TextSetting;
import dev.stella.client.render.Render2D;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Floating, draggable settings window for one module. */
public class SettingsWindow {
    static final int WIDTH = 172;
    private static final int HEADER = 16;

    final Module module;
    int x, y;
    private double scroll;
    private boolean dragging;
    private double dragX, dragY;
    private NumberSetting draggingSlider;
    private int sliderX, sliderW;
    private ColorSetting draggingColor;
    private int colorPart; // 0 sv, 1 hue, 2 alpha
    private int pickX, pickY;
    private KeySetting listening;
    private final Set<ColorSetting> expanded = new HashSet<>();
    private final Map<Setting<?>, TextInput> inputs = new HashMap<>();
    private final Map<BoolSetting, Animation> anims = new HashMap<>();
    private final Animation openAnim = new Animation(0, 18).target(1);
    /** Springy pop-in scale (slight overshoot). */
    private final dev.stella.client.util.Spring pop = new dev.stella.client.util.Spring(0.82, 320, 17).target(1);
    private int contentHeight;
    private boolean closed;

    SettingsWindow(Module module, int x, int y) {
        this.module = module;
        this.x = x;
        this.y = y;
    }

    boolean isClosed() { return closed; }
    boolean isListening() { return listening != null; }

    private int maxHeight(int sh) { return Math.max(80, sh - y - 6); }

    private List<Setting<?>> visibleSettings() {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> s : module.getSettings()) if (s.isVisible()) out.add(s);
        return out;
    }

    private int heightOf(Setting<?> s) {
        if (s instanceof NumberSetting) return 20;
        if (s instanceof ColorSetting c) return expanded.contains(c) ? 14 + 52 : 14;
        if (s instanceof TextSetting) return 26;
        if (s instanceof BlockListSetting b) return 14 + Math.min(8, b.get().size()) * 11 + 16 + suggestions(b).size() * 10;
        return 14;
    }

    void render(DrawContext ctx, int mx, int my, int sw, int sh) {
        ctx.createNewRootLayer(); // windows draw above panels (text included)
        Theme t = ThemeManager.get();
        double a = openAnim.get();
        x = Math.max(0, Math.min(sw - WIDTH, x));
        y = Math.max(0, Math.min(sh - 40, y));
        int preview = module.previewHeight();
        int descLines = module.getDescription().isEmpty() ? 0 : wrap(module.getDescription(), WIDTH - 10).size();
        int body = descLines * 9 + 4 + (preview > 0 ? preview + 6 : 0);
        for (Setting<?> s : visibleSettings()) body += heightOf(s);
        body += 16; // reset row
        contentHeight = body;
        int h = Math.min(body, maxHeight(sh));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, body - h)));
        int slide = (int) ((1 - a) * 8);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0, (float) (slide));
        float popScale = (float) pop.get();
        ctx.getMatrices().translate(x + WIDTH / 2f, y);
        ctx.getMatrices().scale(popScale, popScale);
        ctx.getMatrices().translate(-(x + WIDTH / 2f), -y);
        if (dev.stella.client.render.Glass.active()) dev.stella.client.render.Glass.panel(ctx, x, y, WIDTH, HEADER + h, 6, HEADER, Math.min(1, a + 0.1), true);
        else {
            Render2D.shadow(ctx, x, y, WIDTH, HEADER + h, 5, 5, ColorUtil.mulAlpha(0x90000000, a));
            Render2D.roundRect(ctx, x - 0.5f, y - 0.5f, WIDTH + 1, HEADER + h + 1, 5.5f, ColorUtil.mulAlpha(t.outline(), a));
            Render2D.roundRect(ctx, x, y, WIDTH, HEADER + h, 5, ColorUtil.mulAlpha(t.background(), Math.min(1, a + 0.05)));
            Render2D.roundRect(ctx, x, y, WIDTH, HEADER, 5, ColorUtil.mulAlpha(t.header(), a));
            ctx.fill(x, y + HEADER - 4, x + WIDTH, y + HEADER, ColorUtil.mulAlpha(t.header(), a));
        }
        GuiDraw.text(ctx, "⚙", x + 6, y + 4, ColorUtil.mulAlpha(t.accent(), a));
        GuiDraw.small(ctx, GuiDraw.trimSmall(module.getName().toUpperCase(java.util.Locale.ROOT), WIDTH - 40, 0.85f), x + 16, y + 5.5f, 0.85f, ColorUtil.mulAlpha(t.text(), a));
        boolean closeHover = GuiDraw.in(mx, my - slide, x + WIDTH - 13, y + 2, 11, 12);
        GuiDraw.text(ctx, "✕", x + WIDTH - 11, y + 4, ColorUtil.mulAlpha(closeHover ? 0xFFF2777A : t.textDim(), a));
        int by = y + HEADER;
        ctx.enableScissor(x, by + slide, x + WIDTH, by + h + slide);

        int cy = by + 2 - (int) scroll;
        for (String line : wrap(module.getDescription(), WIDTH - 10)) {
            GuiDraw.text(ctx, line, x + 5, cy, ColorUtil.mulAlpha(t.textDim(), a));
            cy += 9;
        }
        cy += 2;
        if (preview > 0) {
            module.renderPreview(ctx, x + 5, cy, WIDTH - 10, preview);
            cy += preview + 6;
        }
        for (Setting<?> s : visibleSettings()) {
            renderSetting(ctx, s, cy, mx, my - slide, t, a);
            cy += heightOf(s);
        }
        boolean resetHover = GuiDraw.in(mx, my - slide, x + 4, cy + 2, WIDTH - 8, 12);
        Render2D.roundRect(ctx, x + 4, cy + 2, WIDTH - 8, 12, 3, ColorUtil.mulAlpha(resetHover ? t.rowHover() : t.row(), a));
        String rs = "Reset module settings";
        GuiDraw.text(ctx, rs, x + WIDTH / 2 - GuiDraw.width(rs) / 2, cy + 4, ColorUtil.mulAlpha(t.textDim(), a));
        ctx.disableScissor();
        ctx.getMatrices().popMatrix();
    }

    private void renderSetting(DrawContext ctx, Setting<?> s, int cy, int mx, int my, Theme t, double a) {
        int lx = x + 5, rx = x + WIDTH - 5;
        int tc = ColorUtil.mulAlpha(t.text(), a), dc = ColorUtil.mulAlpha(t.textDim(), a);
        boolean hover = GuiDraw.in(mx, my, x, cy, WIDTH, heightOf(s));
        if (hover) ctx.fill(x + 1, cy, x + WIDTH - 1, cy + heightOf(s), ColorUtil.mulAlpha(0x10FFFFFF, a));
        String name = GuiDraw.trim(s.getName(), WIDTH - 70);
        if (s instanceof BoolSetting b) {
            GuiDraw.text(ctx, name, lx, cy + 3, tc);
            double v = anims.computeIfAbsent(b, k -> new Animation(b.isOn() ? 1 : 0, 18)).target(b.isOn() ? 1 : 0).get();
            GuiDraw.toggle(ctx, t, rx - 14, cy + 3, v, a);
        } else if (s instanceof NumberSetting n) {
            GuiDraw.text(ctx, name, lx, cy + 2, tc);
            String val = n.display();
            GuiDraw.text(ctx, val, rx - GuiDraw.width(val), cy + 2, ColorUtil.mulAlpha(t.accentSoft(), a));
            int bx = lx, bw = WIDTH - 10, byy = cy + 13;
            double frac = (n.get() - n.getMin()) / (n.getMax() - n.getMin());
            Render2D.roundRect(ctx, bx, byy, bw, 3, 1.5f, ColorUtil.mulAlpha(0xFF2A2533, a));
            Render2D.roundRect(ctx, bx, byy, (float) (bw * frac), 3, 1.5f, ColorUtil.mulAlpha(t.accent(), a));
            Render2D.roundRect(ctx, (float) (bx + bw * frac - 2.5), byy - 1.5f, 5, 6, 2.5f, ColorUtil.mulAlpha(0xFFFFFFFF, a));
        } else if (s instanceof ModeSetting m) {
            GuiDraw.text(ctx, name, lx, cy + 3, tc);
            String val = "‹ " + m.get() + " ›";
            GuiDraw.text(ctx, GuiDraw.trim(val, 90), rx - Math.min(90, GuiDraw.width(val)), cy + 3, ColorUtil.mulAlpha(t.accentSoft(), a));
        } else if (s instanceof ColorSetting c) {
            GuiDraw.text(ctx, name, lx, cy + 3, tc);
            ctx.fill(rx - 22, cy + 2, rx, cy + 11, 0xFF000000 | 0x404040);
            ctx.fill(rx - 21, cy + 3, rx - 1, cy + 10, ColorUtil.mulAlpha(c.argb() | 0xFF000000, a));
            ctx.fill(rx - 8, cy + 3, rx - 1, cy + 10, ColorUtil.mulAlpha(c.argb(), a));
            if (expanded.contains(c)) renderPicker(ctx, c, cy + 14, a, t);
        } else if (s instanceof KeySetting k) {
            GuiDraw.text(ctx, name, lx, cy + 3, tc);
            String val = listening == k ? "Press a key..." : "[" + k.keyName() + "]";
            GuiDraw.text(ctx, val, rx - GuiDraw.width(val), cy + 3, ColorUtil.mulAlpha(listening == k ? t.accent() : t.accentSoft(), a));
        } else if (s instanceof TextSetting ts) {
            GuiDraw.text(ctx, name, lx, cy + 2, tc);
            TextInput in = inputs.computeIfAbsent(ts, k -> new TextInput(ts.get(), ts.getMaxLength(), "type...").onChange(ts::set).onSubmit(v -> TextInput.unfocusAll()));
            if (!in.isFocused() && !in.get().equals(ts.get())) in.set(ts.get());
            in.render(ctx, lx, cy + 12, WIDTH - 10, 12, ColorUtil.mulAlpha(0xFF15121B, a), t.accent(), tc, dc);
        } else if (s instanceof BlockListSetting b) {
            GuiDraw.text(ctx, name + " (" + b.get().size() + ")", lx, cy + 3, tc);
            int ly = cy + 14;
            List<String> ids = b.get();
            for (int i = 0; i < Math.min(8, ids.size()); i++) {
                GuiDraw.text(ctx, GuiDraw.trim(ids.get(i).replace("minecraft:", ""), WIDTH - 24), lx + 2, ly, ColorUtil.mulAlpha(t.accentSoft(), a));
                boolean xh = GuiDraw.in(mx, my, rx - 8, ly - 1, 9, 10);
                GuiDraw.text(ctx, "✕", rx - 7, ly, ColorUtil.mulAlpha(xh ? 0xFFF2777A : dc, a));
                ly += 11;
            }
            TextInput in = blockInput(b);
            in.render(ctx, lx, ly + 1, WIDTH - 10, 12, ColorUtil.mulAlpha(0xFF15121B, a), t.accent(), tc, dc);
            ly += 15;
            for (String sug : suggestions(b)) {
                boolean sh = GuiDraw.in(mx, my, lx, ly, WIDTH - 10, 10);
                if (sh) ctx.fill(lx, ly, rx, ly + 10, ColorUtil.mulAlpha(t.rowHover(), a));
                GuiDraw.text(ctx, "+ " + sug.replace("minecraft:", ""), lx + 2, ly + 1, ColorUtil.mulAlpha(sh ? t.text() : dc, a));
                ly += 10;
            }
        }
    }

    private TextInput blockInput(BlockListSetting b) {
        return inputs.computeIfAbsent(b, k -> new TextInput("", 64, "+ add block id (Enter)").onSubmit(v -> {
            if (b.add(v.contains(":") ? v : "minecraft:" + v)) inputs.get(b).set("");
        }));
    }

    private List<String> suggestions(BlockListSetting b) {
        TextInput in = inputs.get(b);
        if (in == null || !in.isFocused() || in.get().length() < 2) return List.of();
        String q = in.get().toLowerCase();
        List<String> out = new ArrayList<>();
        for (var id : Registries.BLOCK.getIds()) {
            String s = id.toString();
            if (s.contains(q) && !b.get().contains(s)) {
                out.add(s);
                if (out.size() >= 5) break;
            }
        }
        return out;
    }

    private void renderPicker(DrawContext ctx, ColorSetting c, int py, double a, Theme t) {
        float[] hsb = ColorUtil.toHsb(c.argb());
        int px = x + 5, w = WIDTH - 10;
        int svW = w - 22, svH = 40;
        // saturation/brightness square
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            for (int j = 0; j < 8; j++) {
                float sat = i / (float) (steps - 1), bri = 1 - j / 7f;
                int col = ColorUtil.fromHsb(hsb[0], sat, bri, 255);
                int x1 = px + svW * i / steps, x2 = px + svW * (i + 1) / steps;
                int y1 = py + svH * j / 8, y2 = py + svH * (j + 1) / 8;
                ctx.fill(x1, y1, x2, y2, ColorUtil.mulAlpha(col, a));
            }
        }
        int sx = px + (int) (hsb[1] * svW), sy = py + (int) ((1 - hsb[2]) * svH);
        ctx.drawStrokedRectangle(sx - 2, sy - 2, 4, 4, 0xFFFFFFFF);
        // hue bar
        int hx = px + svW + 4;
        for (int j = 0; j < svH; j++) ctx.fill(hx, py + j, hx + 7, py + j + 1, ColorUtil.mulAlpha(ColorUtil.fromHsb(j / (float) svH, 1, 1, 255), a));
        int hy = py + (int) (hsb[0] * svH);
        ctx.fill(hx - 1, hy, hx + 8, hy + 1, 0xFFFFFFFF);
        // alpha bar
        int ax = hx + 10;
        for (int j = 0; j < svH; j++) ctx.fill(ax, py + j, ax + 7, py + j + 1, ColorUtil.mulAlpha(ColorUtil.withAlpha(c.argb(), 255 - j * 255 / svH), a));
        int ay = py + (int) ((1 - c.alpha() / 255.0) * svH);
        ctx.fill(ax - 1, ay, ax + 8, ay + 1, 0xFFFFFFFF);
        GuiDraw.text(ctx, String.format("#%08X", c.argb()), px, py + svH + 2, ColorUtil.mulAlpha(t.textDim(), a));
        pickX = px;
        pickY = py;
    }

    // ------------------------------------------------------------------ input

    boolean isOver(double mx, double my, int sh) {
        int h = Math.min(contentHeight, maxHeight(sh));
        return GuiDraw.in(mx, my, x, y, WIDTH, HEADER + h);
    }

    boolean mouseClicked(double mx, double my, int button, int sh) {
        if (!isOver(mx, my, sh)) return false;
        if (GuiDraw.in(mx, my, x + WIDTH - 13, y + 2, 11, 12)) { closed = true; return true; }
        if (my < y + HEADER) {
            dragging = true;
            dragX = mx - x;
            dragY = my - y;
            return true;
        }
        int preview = module.previewHeight();
        int cy = y + HEADER + 2 - (int) scroll + wrap(module.getDescription(), WIDTH - 10).size() * 9 + 2 + (preview > 0 ? preview + 6 : 0);
        for (Setting<?> s : visibleSettings()) {
            int h = heightOf(s);
            if (my >= cy && my < cy + h) { clickSetting(s, mx, my, cy, button); return true; }
            cy += h;
        }
        if (my >= cy + 2 && my < cy + 14 && button == 0) module.resetSettings();
        return true;
    }

    private void clickSetting(Setting<?> s, double mx, double my, int cy, int button) {
        int lx = x + 5, rx = x + WIDTH - 5;
        if (!(s instanceof KeySetting)) listening = null;
        if (s instanceof BoolSetting b) { if (button == 0) b.toggle(); else if (button == 1) b.reset(); }
        else if (s instanceof NumberSetting n) {
            if (button == 1) { n.reset(); return; }
            draggingSlider = n;
            sliderX = lx;
            sliderW = WIDTH - 10;
            updateSlider(mx);
        } else if (s instanceof ModeSetting m) {
            if (button == 1) m.cycle(-1); else m.cycle(mx < x + WIDTH / 2.0 ? -1 : 1);
        } else if (s instanceof ColorSetting c) {
            if (my < cy + 14) {
                if (button == 1) c.reset();
                else if (!expanded.remove(c)) expanded.add(c);
            } else {
                int svW = WIDTH - 10 - 22;
                double rel = mx - lx;
                colorPart = rel < svW + 2 ? 0 : rel < svW + 12 ? 1 : 2;
                draggingColor = c;
                updateColor(mx, my);
            }
        } else if (s instanceof KeySetting k) {
            if (button == 1) { k.set(GLFW.GLFW_KEY_UNKNOWN); listening = null; }
            else listening = listening == k ? null : k;
        } else if (s instanceof TextSetting ts) {
            if (my >= cy + 12) inputs.get(ts).focus();
        } else if (s instanceof BlockListSetting b) {
            int ly = cy + 14;
            List<String> ids = b.get();
            for (int i = 0; i < Math.min(8, ids.size()); i++) {
                if (my >= ly - 1 && my < ly + 10 && mx >= rx - 9) { b.remove(ids.get(i)); return; }
                ly += 11;
            }
            if (my >= ly && my < ly + 14) { blockInput(b).focus(); return; }
            ly += 15;
            for (String sug : suggestions(b)) {
                if (my >= ly && my < ly + 10) { b.add(sug); blockInput(b).set(""); return; }
                ly += 10;
            }
        }
    }

    private void updateSlider(double mx) {
        if (draggingSlider == null) return;
        double frac = Math.max(0, Math.min(1, (mx - sliderX) / sliderW));
        draggingSlider.set(draggingSlider.getMin() + frac * (draggingSlider.getMax() - draggingSlider.getMin()));
    }

    private void updateColor(double mx, double my) {
        ColorSetting c = draggingColor;
        if (c == null) return;
        int svW = WIDTH - 10 - 22, svH = 40;
        float[] hsb = ColorUtil.toHsb(c.argb());
        double fy = Math.max(0, Math.min(1, (my - pickY) / svH));
        switch (colorPart) {
            case 0 -> {
                float sat = (float) Math.max(0, Math.min(1, (mx - pickX) / svW));
                c.set(ColorUtil.fromHsb(hsb[0], sat, (float) (1 - fy), c.alpha()));
            }
            case 1 -> c.set(ColorUtil.fromHsb((float) Math.min(0.999, fy), Math.max(0.05f, hsb[1]), Math.max(0.05f, hsb[2]), c.alpha()));
            default -> c.set(ColorUtil.withAlpha(c.argb(), (int) ((1 - fy) * 255)));
        }
    }

    boolean mouseDragged(double mx, double my) {
        if (dragging) { x = (int) (mx - dragX); y = (int) (my - dragY); return true; }
        if (draggingSlider != null) { updateSlider(mx); return true; }
        if (draggingColor != null) { updateColor(mx, my); return true; }
        return false;
    }

    void mouseReleased() {
        dragging = false;
        draggingSlider = null;
        draggingColor = null;
    }

    boolean mouseScrolled(double mx, double my, double amount, int sh) {
        if (!isOver(mx, my, sh)) return false;
        scroll -= amount * 18;
        return true;
    }

    boolean keyPressed(int key) {
        if (listening == null) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) listening.set(GLFW.GLFW_KEY_UNKNOWN);
        else listening.set(key);
        listening = null;
        return true;
    }

    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (GuiDraw.width(test) > width && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }
}
