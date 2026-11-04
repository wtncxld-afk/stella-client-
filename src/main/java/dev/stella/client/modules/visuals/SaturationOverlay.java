package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import net.minecraft.client.gui.DrawContext;

/** Shows your hidden saturation on top of the hunger bar (gold shanks), plus an optional number. */
public class SaturationOverlay extends Module {
    private final BoolSetting number = add(new BoolSetting("Show Number", "Saturation value next to the hunger bar", true));

    public SaturationOverlay() { super("Saturation Overlay", "See your hidden saturation on the hunger bar", Category.VISUALS); }

    /** Called from the InGameHud mixin after vanilla draws the food bar (top = y, right = right edge x). */
    public void draw(DrawContext ctx, int top, int right) {
        if (!isEnabled() || mc.player == null) return;
        float sat = mc.player.getHungerManager().getSaturationLevel();
        for (int i = 0; i < 10; i++) {
            float f = Math.max(0, Math.min(2, sat - i * 2)) / 2f;
            if (f <= 0) break;
            int x = right - i * 8 - 9;
            int w = Math.max(1, Math.round(7 * f));
            ctx.fill(x + 8 - w, top + 1, x + 8, top + 8, 0x90FFC94D);
            ctx.fill(x + 8 - w, top + 1, x + 8, top + 2, 0xD0FFE9A8);
        }
        if (number.isOn()) {
            String s = String.format("%.1f", sat);
            ctx.drawTextWithShadow(mc.textRenderer, s, right + 4, top, 0xFFFFC94D);
        }
    }
}
