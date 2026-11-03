package dev.stella.client.modules.hud;

import dev.stella.client.hud.HudElement;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.track.CpsTracker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

public class KeystrokesHud extends HudElement {
    private final BoolSetting showMouse = add(new BoolSetting("Mouse Buttons", "Show LMB / RMB", true));
    private final BoolSetting showCps = add(new BoolSetting("CPS", "Show clicks per second on mouse keys", true)).visibleWhen(showMouse::isOn);
    private final BoolSetting showSpace = add(new BoolSetting("Space Bar", "Show the jump key", true));
    private final ColorSetting keyColor = add(new ColorSetting("Key Color", "Released key background", 0x90100D16));
    private final ColorSetting pressedColor = add(new ColorSetting("Pressed Color", "Pressed key background", 0xD0A855F7));
    private final ColorSetting textColor = add(new ColorSetting("Text Color", "Key label colour", 0xFFF3F0F7));

    private final Animation[] anims = new Animation[7];

    public KeystrokesHud() {
        super("Keystrokes HUD", "WASD, space and mouse buttons with optional CPS", 4, -4, false);
        for (int i = 0; i < anims.length; i++) anims[i] = new Animation(0, 18);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        int s = 22, g = 2;
        key(ctx, 0, "W", mc.options.forwardKey, s + g, 0, s, s);
        key(ctx, 1, "A", mc.options.leftKey, 0, s + g, s, s);
        key(ctx, 2, "S", mc.options.backKey, s + g, s + g, s, s);
        key(ctx, 3, "D", mc.options.rightKey, (s + g) * 2, s + g, s, s);
        int y = (s + g) * 2;
        int full = s * 3 + g * 2;
        if (showMouse.isOn()) {
            int half = (full - g) / 2;
            String l = showCps.isOn() ? "LMB " + CpsTracker.left() : "LMB";
            String r = showCps.isOn() ? "RMB " + CpsTracker.right() : "RMB";
            key(ctx, 4, l, mc.options.attackKey, 0, y, half, s - 4);
            key(ctx, 5, r, mc.options.useKey, half + g, y, half, s - 4);
            y += s - 4 + g;
        }
        if (showSpace.isOn()) {
            key(ctx, 6, "———", mc.options.jumpKey, 0, y, full, 12);
            y += 12;
        }
        setSize(full, y);
    }

    private void key(DrawContext ctx, int idx, String label, KeyBinding kb, int x, int y, int w, int h) {
        double p = anims[idx].target(kb.isPressed() ? 1 : 0).get();
        int bg = ColorUtil.lerp(keyColor.argb(), pressedColor.argb(), p);
        ctx.fill(x, y, x + w, y + h, col(bg));
        int tw = mc.textRenderer.getWidth(label);
        ctx.drawText(mc.textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, col(textColor.argb()), true);
    }
}
