package dev.stella.client.modules.hud;

import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

/** Shared look for simple "LABEL value" elements. Each subclass is still its own module with its own settings. */
public abstract class TextHud extends HudElement {
    protected final CardStyle card = card("Modern");
    protected final ColorSetting labelColor = add(new ColorSetting("Label Color", "Label colour (when Theme Colors is off)", 0xFFC084FC));
    protected final ColorSetting valueColor = add(new ColorSetting("Value Color", "Colour of the value", 0xFFF3F0F7));
    protected final BoolSetting shadow = add(new BoolSetting("Text Shadow", "Minecraft-style text shadow", true));
    protected final BoolSetting showLabel = add(new BoolSetting("Show Label", "Show the label before the value", true));

    protected TextHud(String name, String description, double x, double y, boolean def) {
        super(name, description, x, y, def);
    }

    protected abstract String label();
    protected abstract String value();
    protected int valueColorFor() { return valueColor.argb(); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        String l = showLabel.isOn() ? label().toUpperCase() : "";
        String v = value();
        String lt = l.isEmpty() ? "" : l + " ";
        int lw = mc.textRenderer.getWidth(lt);
        int w = lw + mc.textRenderer.getWidth(v);
        card.draw(ctx, this, w, 8);
        int px = card.padLeft(), py = card.pad();
        if (!lt.isEmpty()) ctx.drawText(mc.textRenderer, lt, px, py, col(card.accent(labelColor.argb())), shadow.isOn());
        ctx.drawText(mc.textRenderer, v, px + lw, py, col(valueColorFor()), shadow.isOn());
        setSize(w + px + card.pad(), 8 + py * 2);
    }
}
