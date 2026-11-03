package dev.stella.client.modules.hud;

import dev.stella.client.StellaClient;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudElement;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/** Transparent "[star] STELLA" watermark with a soft pulsing glow — no background card. */
public class BrandingHud extends HudElement {
    public static final Identifier LOGO = Identifier.of(StellaClient.MOD_ID, "textures/gui/star.png");
    public static final Identifier GLOW = Identifier.of(StellaClient.MOD_ID, "textures/gui/star_glow.png");

    private final BoolSetting themeColors = add(new BoolSetting("Theme Colors", "Gradient follows the GUI theme", true));
    private final ColorSetting color1 = add(new ColorSetting("Gradient Start", "Left text colour", 0xFFA855F7)).visibleWhen(() -> !this.themeColors.isOn());
    private final ColorSetting color2 = add(new ColorSetting("Gradient End", "Right text colour", 0xFFE9D5FF)).visibleWhen(() -> !this.themeColors.isOn());
    private final BoolSetting glow = add(new BoolSetting("Glow", "Neon glow behind the star", true));
    private final BoolSetting animated = add(new BoolSetting("Animated", "Shimmer the gradient and pulse the glow", true));
    private final BoolSetting showVersion = add(new BoolSetting("Show Version", "Append the client version", false));

    public BrandingHud() { super("Stella Branding HUD", "Transparent star logo with STELLA text", -4, 4, true); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        double time = animated.isOn() ? (System.currentTimeMillis() % 4000) / 4000.0 : 0;
        int a = (int) (255 * opacity.get());
        int c1 = themeColors.isOn() ? ThemeManager.accentAt(0) : color1.argb();
        int c2 = themeColors.isOn() ? ColorUtil.lerp(ThemeManager.accentAt(0.2), 0xFFFFFFFF, 0.6) : color2.argb();
        if (glow.isOn()) {
            double pulse = animated.isOn() ? 0.65 + 0.35 * Math.sin(time * Math.PI * 2) : 0.8;
            int ga = (int) (a * pulse);
            int tint = c1 & 0xFFFFFF;
            ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, GLOW, -6, -6, 0f, 0f, 28, 28, 28, 28, (ga << 24) | tint);
        }
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, LOGO, 0, 0, 0f, 0f, 16, 16, 16, 16, (a << 24) | 0xFFFFFF);
        String text = "STELLA";
        int x = 20;
        for (int i = 0; i < text.length(); i++) {
            double p = (i / (double) (text.length() - 1) + time) % 1.0;
            double tri = p < 0.5 ? p * 2 : (1 - p) * 2;
            int c = ColorUtil.lerp(c1, c2, tri);
            String ch = String.valueOf(text.charAt(i));
            dev.stella.client.render.StellaFont.draw(ctx, ch, x, 4, col(c), true);
            x += dev.stella.client.render.StellaFont.width(ch) + 1;
        }
        if (showVersion.isOn()) {
            String v = " " + StellaClient.VERSION;
            dev.stella.client.render.StellaFont.draw(ctx, v, x, 4, col(0xFF8E879A), true);
            x += dev.stella.client.render.StellaFont.width(v);
        }
        setSize(x, 16);
    }
}
