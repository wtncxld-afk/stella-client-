package dev.stella.client.hud;

import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.setting.SettingsDirty;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Drag to move, drag the corner handle (or scroll) to resize, right-click to
 * reset one element, middle-click to toggle it. The side list toggles elements.
 * Every change affects only the element under the cursor.
 */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudElement dragging;
    private HudElement resizing;
    private double dragOffX, dragOffY;
    private double resizeStartScale, resizeStartX;
    private HudElement selected;
    private boolean snap = true;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Stella HUD Editor"));
        this.parent = parent;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        Theme t = ThemeManager.get();
        // centre guides
        ctx.fill(width / 2, 0, width / 2 + 1, height, 0x18FFFFFF);
        ctx.fill(0, height / 2, width, height / 2 + 1, 0x18FFFFFF);

        List<HudElement> elements = HudManager.elements();
        for (HudElement h : elements) {
            if (!h.isEnabled()) continue;
            HudManager.renderElement(ctx, h, delta, true);
            int x = (int) h.x(), y = (int) h.y();
            int w = Math.round(h.scaledWidth()), hh = Math.round(h.scaledHeight());
            boolean hover = isOver(h, mouseX, mouseY);
            int border = h == selected ? t.accent() : hover ? t.accentSoft() : 0x60FFFFFF;
            ctx.drawBorder(x - 1, y - 1, w + 2, hh + 2, border);
            ctx.fill(x + w - 3, y + hh - 3, x + w + 1, y + hh + 1, t.accent()); // resize handle
            if (hover || h == selected) {
                String label = h.getName() + "  " + String.format("%.2fx", h.scale.get());
                int ly = y - 11 < 0 ? y + hh + 2 : y - 11;
                ctx.fill(x - 1, ly - 1, x + textRenderer.getWidth(label) + 3, ly + 9, 0xC0000000);
                ctx.drawText(textRenderer, label, x + 1, ly, t.accentSoft(), false);
            }
        }

        // element list on the left
        int lx = 6, ly = 26;
        ctx.fill(lx - 4, 4, lx + 120, ly + elements.size() * 12 + 4, t.background());
        ctx.drawText(textRenderer, "✦ HUD Editor", lx, 9, t.accent(), true);
        for (HudElement h : elements) {
            boolean hover = mouseX >= lx - 4 && mouseX <= lx + 120 && mouseY >= ly - 2 && mouseY < ly + 10;
            if (hover) ctx.fill(lx - 4, ly - 2, lx + 120, ly + 10, t.rowHover());
            ctx.fill(lx, ly + 1, lx + 6, ly + 7, h.isEnabled() ? t.accent() : 0xFF3A3542);
            ctx.drawText(textRenderer, h.getName(), lx + 10, ly, h.isEnabled() ? t.text() : t.textDim(), false);
            ly += 12;
        }

        String help = "Drag: move  •  Corner/scroll: resize  •  Right-click: reset  •  Middle-click: hide  •  S: snap " + (snap ? "on" : "off") + "  •  Esc: done";
        int hw = textRenderer.getWidth(help);
        ctx.fill(width / 2 - hw / 2 - 4, height - 16, width / 2 + hw / 2 + 4, height - 3, 0xB0000000);
        ctx.drawText(textRenderer, help, width / 2 - hw / 2, height - 13, t.textDim(), false);
    }

    private boolean isOver(HudElement h, double mx, double my) {
        return mx >= h.x() - 1 && my >= h.y() - 1 && mx <= h.x() + h.scaledWidth() + 1 && my <= h.y() + h.scaledHeight() + 1;
    }

    private HudElement topAt(double mx, double my) {
        List<HudElement> els = HudManager.elements();
        for (int i = els.size() - 1; i >= 0; i--) {
            HudElement h = els.get(i);
            if (h.isEnabled() && isOver(h, mx, my)) return h;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // side list
        int lx = 6, ly = 26;
        for (HudElement h : HudManager.elements()) {
            if (mx >= lx - 4 && mx <= lx + 120 && my >= ly - 2 && my < ly + 10) {
                h.toggle();
                return true;
            }
            ly += 12;
        }
        HudElement h = topAt(mx, my);
        if (h == null) { selected = null; return super.mouseClicked(mx, my, button); }
        selected = h;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { h.resetLayout(); return true; }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) { h.setEnabled(false); return true; }
        float hx = h.x() + h.scaledWidth(), hy = h.y() + h.scaledHeight();
        if (mx >= hx - 5 && my >= hy - 5) {
            resizing = h;
            resizeStartScale = h.scale.get();
            resizeStartX = mx;
        } else {
            dragging = h;
            dragOffX = mx - h.x();
            dragOffY = my - h.y();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            double nx = mx - dragOffX, ny = my - dragOffY;
            if (snap) {
                double w = dragging.scaledWidth(), hh = dragging.scaledHeight();
                if (Math.abs(nx) < 4) nx = 0;
                if (Math.abs(ny) < 4) ny = 0;
                if (Math.abs(nx + w - width) < 4) nx = width - w;
                if (Math.abs(ny + hh - height) < 4) ny = height - hh;
                if (Math.abs(nx + w / 2 - width / 2.0) < 4) nx = width / 2.0 - w / 2;
                if (Math.abs(ny + hh / 2 - height / 2.0) < 4) ny = height / 2.0 - hh / 2;
            }
            dragging.setPos(nx, ny);
            return true;
        }
        if (resizing != null) {
            double base = Math.max(10, resizing.getWidth());
            double ns = resizeStartScale + (mx - resizeStartX) / base;
            resizing.scale.set(ns);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null || resizing != null) SettingsDirty.mark();
        dragging = null;
        resizing = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        HudElement e = topAt(mx, my);
        if (e != null) {
            e.scale.set(e.scale.get() + (v > 0 ? 0.05 : -0.05));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_S) { snap = !snap; return true; }
        if (selected != null) {
            double step = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1;
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> { selected.setPos(selected.x() - step, selected.y()); return true; }
                case GLFW.GLFW_KEY_RIGHT -> { selected.setPos(selected.x() + step, selected.y()); return true; }
                case GLFW.GLFW_KEY_UP -> { selected.setPos(selected.x(), selected.y() - step); return true; }
                case GLFW.GLFW_KEY_DOWN -> { selected.setPos(selected.x(), selected.y() + step); return true; }
                default -> { }
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
