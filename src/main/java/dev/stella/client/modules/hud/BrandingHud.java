package dev.stella.client.modules.hud;

import dev.stella.client.StellaClient;
import dev.stella.client.hud.HudElement;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/** Transparent "[star] STELLA" watermark — no background card. */
public class BrandingHud extends HudElement {
    public static final Identifier LOGO = Identifier.of(StellaClient.MOD_ID, "textures/gui/star.png");

    private final ColorSetting color1 = add(new ColorSetting("Gradient Start", "Left text colour", 0xFFA855F7));
    private final ColorSetting color2 = add(new ColorSetting("Gradient End", "Right text colour", 0xFFE9D5FF));
    private final BoolSetting animated = add(new BoolSetting("Animated", "Slowly shimmer the gradient", true));
    private final BoolSetting showVersion = add(new BoolSetting("Show Version", "Append the client version", false));

    public BrandingHud() { super("Stella Branding HUD", "Transparent star logo with STELLA text", -4, 4, true); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        int alpha = (int) (255 * opacity.get());
        ctx.drawTexture(RenderLayer::getGuiTextured, LOGO, 0, 0, 0f, 0f, 16, 16, 16, 16, ColorUtil.withAlpha(0xFFFFFF, alpha));
        String text = "STELLA";
        int x = 19;
        double t = animated.isOn() ? (System.currentTimeMillis() % 4000) / 4000.0 : 0;
        for (int i = 0; i < text.length(); i++) {
            double p = (i / (double) (text.length() - 1) + t) % 1.0;
            double tri = p < 0.5 ? p * 2 : (1 - p) * 2;
            int c = ColorUtil.lerp(color1.argb(), color2.argb(), tri);
            String ch = String.valueOf(text.charAt(i));
            ctx.drawText(mc.textRenderer, ch, x, 4, col(c), true);
            x += mc.textRenderer.getWidth(ch) + 1;
        }
        if (showVersion.isOn()) {
            String v = " " + StellaClient.VERSION;
            ctx.drawText(mc.textRenderer, v, x, 4, col(0xFF8E879A), true);
            x += mc.textRenderer.getWidth(v);
        }
        setSize(x, 16);
    }
}
