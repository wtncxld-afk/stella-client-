package dev.stella.client.modules.hud;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudElement;
import dev.stella.client.render.Render2D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.track.CpsTracker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

public class KeystrokesHud extends HudElement {
    private final BoolSetting showMouse = add(new BoolSetting("Mouse Buttons", "Show LMB / RMB", true));
    private final BoolSetting showCps = add(new BoolSetting("CPS", "Show clicks per second on mouse keys", true)).visibleWhen(showMouse::isOn);
    private final BoolSetting showSpace = add(new BoolSetting("Space Bar", "Show the jump key", true));
    private final NumberSetting keySize = add(new NumberSetting("Key Size", "Size of each key", 22, 14, 34, 1));
    private final NumberSetting rounding = add(new NumberSetting("Rounding", "Corner radius", 4, 0, 8, 0.5));
    private final BoolSetting themeColors = add(new BoolSetting("Theme Colors", "Pressed colour follows the GUI theme", true));
    private final ColorSetting keyColor = add(new ColorSetting("Key Color", "Released key background", 0xB00E0C12));
    private final ColorSetting pressedColor = add(new ColorSetting("Pressed Color", "Pressed key background", 0xE0A855F7)).visibleWhen(() -> !this.themeColors.isOn());
    private final ColorSetting textColor = add(new ColorSetting("Text Color", "Key label colour", 0xFFF3F0F7));
    private final BoolSetting glow = add(new BoolSetting("Glow", "Glow around pressed keys", true));

    private final Animation[] anims = new Animation[7];

    public KeystrokesHud() {
        super("Keystrokes HUD", "WASD, space and mouse buttons with optional CPS", 4, -4, false);
        for (int i = 0; i < anims.length; i++) anims[i] = new Animation(0, 22);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        int s = keySize.getInt(), g = 2;
        key(ctx, 0, "W", mc.options.forwardKey, s + g, 0, s, s);
        key(ctx, 1, "A", mc.options.leftKey, 0, s + g, s, s);
        key(ctx, 2, "S", mc.options.backKey, s + g, s + g, s, s);
        key(ctx, 3, "D", mc.options.rightKey, (s + g) * 2, s + g, s, s);
        int y = (s + g) * 2;
        int full = s * 3 + g * 2;
        if (showMouse.isOn()) {
            int half = (full - g) / 2;
            int mh = Math.max(12, s - 4);
            key(ctx, 4, showCps.isOn() ? "LMB|" + CpsTracker.left() + " CPS" : "LMB", mc.options.attackKey, 0, y, half, mh);
            key(ctx, 5, showCps.isOn() ? "RMB|" + CpsTracker.right() + " CPS" : "RMB", mc.options.useKey, half + g, y, half, mh);
            y += mh + g;
        }
        if (showSpace.isOn()) {
            key(ctx, 6, "", mc.options.jumpKey, 0, y, full, 11);
            int p = (int) (anims[6].get() * 255);
            ctx.fill(full / 2 - 12, y + 5, full / 2 + 12, y + 6, col(ColorUtil.lerp(textColor.argb(), 0xFFFFFFFF, p / 255.0)));
            y += 11;
        }
        setSize(full, y);
    }

    private void key(DrawContext ctx, int idx, String label, KeyBinding kb, int x, int y, int w, int h) {
        double p = anims[idx].target(kb.isPressed() ? 1 : 0).get();
        int pressed = themeColors.isOn() ? ColorUtil.withAlpha(ThemeManager.accentAt(idx * 0.05), 0xE0) : pressedColor.argb();
        float r = rounding.getFloat();
        if (glow.isOn() && p > 0.02) Render2D.shadow(ctx, x, y, w, h, r, 3, col(ColorUtil.withAlpha(pressed, (int) (0x90 * p))));
        Render2D.roundRect(ctx, x, y, w, h, r, col(ColorUtil.lerp(keyColor.argb(), pressed, p)));
        if (label.isEmpty()) return;
        // scale the key down slightly while pressed for a tactile feel
        float sc = (float) (1 - 0.06 * p);
        String[] parts = label.split("\\|");
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + w / 2f, y + h / 2f, 0);
        ctx.getMatrices().scale(sc, sc, 1);
        int tc = col(textColor.argb());
        if (parts.length == 2) {
            dev.stella.client.render.StellaFont.draw(ctx, parts[0], -dev.stella.client.render.StellaFont.width(parts[0]) / 2, -8, tc, true);
            ctx.getMatrices().scale(0.65f, 0.65f, 1);
            dev.stella.client.render.StellaFont.draw(ctx, parts[1], -dev.stella.client.render.StellaFont.width(parts[1]) / 2, 2, col(ColorUtil.withAlpha(textColor.argb(), 0xC0)), false);
        } else {
            dev.stella.client.render.StellaFont.draw(ctx, label, -dev.stella.client.render.StellaFont.width(label) / 2, -4, tc, true);
        }
        ctx.getMatrices().pop();
    }
}
