package dev.stella.client.modules.hud;

import dev.stella.client.hud.HudElement;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

/** Shared look for simple "Label: value" HUD elements. Each subclass is still its own module. */
public abstract class TextHud extends HudElement {
    protected final ColorSetting labelColor = add(new ColorSetting("Label Color", "Colour of the label", 0xFFC084FC));
    protected final ColorSetting valueColor = add(new ColorSetting("Value Color", "Colour of the value", 0xFFF3F0F7));
    protected final BoolSetting background = add(new BoolSetting("Background", "Draw a translucent backing", false));
    protected final ColorSetting backgroundColor = add(new ColorSetting("Background Color", "Backing colour", 0x90100D16)).visibleWhen(background::isOn);
    protected final BoolSetting shadow = add(new BoolSetting("Text Shadow", "Minecraft-style text shadow", true));

    protected TextHud(String name, String description, double x, double y, boolean def) {
        super(name, description, x, y, def);
    }

    protected abstract String label();
    protected abstract String value();
    protected int valueColorFor() { return valueColor.argb(); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        String l = label();
        String v = value();
        String lt = l.isEmpty() ? "" : l + " ";
        int lw = mc.textRenderer.getWidth(lt);
        int w = lw + mc.textRenderer.getWidth(v);
        if (background.isOn()) ctx.fill(-2, -2, w + 2, 10, col(backgroundColor.argb()));
        if (!lt.isEmpty()) ctx.drawText(mc.textRenderer, lt, 0, 0, col(labelColor.argb()), shadow.isOn());
        ctx.drawText(mc.textRenderer, v, lw, 0, col(valueColorFor()), shadow.isOn());
        setSize(w + (background.isOn() ? 2 : 0), 10);
    }
}
