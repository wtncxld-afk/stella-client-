package dev.stella.client.hud;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * Independent HUD element. Each element owns its own position, scale, opacity
 * and colours — moving, resizing or disabling one never touches another.
 */
public abstract class HudElement extends Module {
    public final NumberSetting posX;
    public final NumberSetting posY;
    public final NumberSetting scale;
    public final NumberSetting opacity;
    private final double defX, defY;
    protected int width = 40, height = 12;

    protected HudElement(String name, String description, double defaultX, double defaultY, boolean defaultEnabled) {
        super(name, description, Category.HUD, defaultEnabled);
        this.defX = defaultX;
        this.defY = defaultY;
        posX = add(new NumberSetting("X", "Horizontal position", defaultX, -10000, 10000, 0.5)).hide();
        posY = add(new NumberSetting("Y", "Vertical position", defaultY, -10000, 10000, 0.5)).hide();
        scale = add(new NumberSetting("Scale", "Size of this element", 1.0, 0.3, 4.0, 0.05).suffix("x"));
        opacity = add(new NumberSetting("Opacity", "Transparency of this element", 1.0, 0.05, 1.0, 0.05));
    }

    /** Draw at local origin (0,0); called inside a transform for position/scale. */
    public abstract void render(DrawContext ctx, float tickDelta, boolean editor);

    public void setSize(int w, int h) { this.width = Math.max(4, w); this.height = Math.max(4, h); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public float scaledWidth() { return (float) (width * scale.get()); }
    public float scaledHeight() { return (float) (height * scale.get()); }

    public float x() { return posX.getFloat(); }
    public float y() { return posY.getFloat(); }

    public void setPos(double x, double y) { posX.set(Math.max(0, x)); posY.set(Math.max(0, y)); }

    public void resetLayout() {
        posX.set(defX);
        posY.set(defY);
        scale.reset();
        opacity.reset();
    }

    /** Applies this element's opacity to a colour. Text needs alpha >= 4 to render. */
    protected int col(int argb) {
        int c = ColorUtil.mulAlpha(argb, opacity.get());
        if (((c >>> 24) & 0xFF) < 5) c = ColorUtil.withAlpha(c, 5);
        return c;
    }

    /** Elements that react to clicks while chat is open (e.g. Block Notifier list). */
    public boolean onClick(double localX, double localY, int button) { return false; }
    public boolean onScroll(double localX, double localY, double amount) { return false; }
}
