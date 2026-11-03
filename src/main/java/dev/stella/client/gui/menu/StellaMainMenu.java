package dev.stella.client.gui.menu;

import dev.stella.client.StellaClient;
import dev.stella.client.gui.ClickGuiScreen;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.MenusModule;
import dev.stella.client.modules.hud.BrandingHud;
import dev.stella.client.render.StellaFont;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Stella title screen: animated cosmic background, glowing logo and animated buttons. */
public class StellaMainMenu extends Screen {
    /** Set while opening the vanilla title screen on purpose so the redirect mixin lets it through. */
    public static boolean bypass;

    private final List<MenuButton> buttons = new ArrayList<>();
    private CosmicBackground bg;
    private final long openedAt = System.currentTimeMillis();
    private final Animation fade = new Animation(0, 5).target(1);

    public StellaMainMenu() { super(Text.literal("Stella Client")); }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        MenusModule cfg = ModuleManager.get(MenusModule.class);
        if (bg == null) bg = new CosmicBackground(cfg.stars.getInt());
        buttons.clear();
        if (cfg.quickJoin.isOn()) {
            String addr = cfg.quickJoinAddress.get().trim();
            buttons.add(new MenuButton("♦", "PLAY " + cfg.quickJoinName.get().toUpperCase(Locale.ROOT), () -> {
                if (!ServerAddress.isValid(addr)) return;
                ConnectScreen.connect(this, client, ServerAddress.parse(addr),
                        new ServerInfo(cfg.quickJoinName.get(), addr, ServerInfo.ServerType.OTHER), false, null);
            }, true, false));
        }
        buttons.add(new MenuButton("▶", "SINGLEPLAYER", () -> client.setScreen(new SelectWorldScreen(this)), false, false));
        buttons.add(new MenuButton("◉", "MULTIPLAYER", () -> client.setScreen(new MultiplayerScreen(this)), false, false));
        buttons.add(new MenuButton("✦", "STELLA CLICKGUI", () -> client.setScreen(new ClickGuiScreen()), false, false));
        buttons.add(new MenuButton("⚙", "OPTIONS", () -> client.setScreen(new OptionsScreen(this, client.options)), false, false));
        buttons.add(new MenuButton("✕", "QUIT GAME", () -> client.scheduleStop(), false, true));
        int bw = 170, x = width / 2 - bw / 2, y = height / 2 - 8;
        for (MenuButton b : buttons) {
            b.x = x; b.y = y; b.w = bw;
            y += b.h + 4;
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        double a = fade.get();
        bg.render(ctx, width, height, 1, true);
        long age = System.currentTimeMillis() - openedAt;
        double t = age / 1000.0;
        var theme = ThemeManager.get();

        // ---- logo
        int logoY = height / 2 - 100;
        float bob = (float) Math.sin(t * 1.4) * 2;
        int glowA = (int) (150 + 80 * Math.sin(t * 2));
        int gs = 72;
        ctx.drawTexture(RenderLayer::getGuiTextured, BrandingHud.GLOW, width / 2 - gs / 2, (int) (logoY - 8 + bob), 0f, 0f, gs, gs, gs, gs,
                ColorUtil.mulAlpha((glowA << 24) | (theme.accent() & 0xFFFFFF), a));
        ctx.drawTexture(RenderLayer::getGuiTextured, BrandingHud.LOGO, width / 2 - 20, (int) (logoY + 8 + bob), 0f, 0f, 40, 40, 40, 40,
                ColorUtil.mulAlpha(0xFFFFFFFF, a));
        // big STELLA with shimmering gradient
        String title = "STELLA";
        float sc = 3.2f;
        int tw = 0;
        for (char c : title.toCharArray()) tw += StellaFont.width(String.valueOf(c)) + 2;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f - tw * sc / 2f, logoY + 54, 0);
        ctx.getMatrices().scale(sc, sc, 1);
        int cx = 0;
        for (int i = 0; i < title.length(); i++) {
            double p = (i / (double) (title.length() - 1) + t * 0.25) % 1.0;
            double tri = p < 0.5 ? p * 2 : (1 - p) * 2;
            int c = ColorUtil.lerp(ThemeManager.accentAt(0), 0xFFF5E8FF, tri);
            String ch = String.valueOf(title.charAt(i));
            StellaFont.draw(ctx, ch, cx, 0, ColorUtil.mulAlpha(c, a), true);
            cx += StellaFont.width(ch) + 2;
        }
        ctx.getMatrices().pop();
        String sub = "C L I E N T";
        StellaFont.draw(ctx, sub, width / 2 - StellaFont.width(sub) / 2, logoY + 86, ColorUtil.mulAlpha(theme.accentSoft(), a), false);

        // ---- buttons (staggered entrance)
        for (int i = 0; i < buttons.size(); i++) {
            double e = Math.max(0, Math.min(1, (age - 250 - i * 70) / 350.0));
            buttons.get(i).render(ctx, mx, my, a, dev.stella.client.util.Animation.easeOutCubic(e));
        }

        // ---- footer
        String user = "Logged in as " + client.getSession().getUsername();
        StellaFont.draw(ctx, user, 6, height - 12, ColorUtil.mulAlpha(theme.textDim(), a), false);
        String ver = "Stella " + StellaClient.VERSION + "  ·  Minecraft 1.21.4  ·  Fabric";
        StellaFont.draw(ctx, ver, width - 6 - StellaFont.width(ver), height - 12, ColorUtil.mulAlpha(theme.textDim(), a), false);
        String vanilla = "Vanilla menu";
        int vx = width - 6 - StellaFont.width(vanilla);
        boolean vh = mx >= vx && my >= height - 24 && my < height - 14;
        StellaFont.draw(ctx, vanilla, vx, height - 24, ColorUtil.mulAlpha(vh ? theme.accent() : 0xFF6E6878, a), false);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (MenuButton b : buttons) {
            if (b.over(mx, my) && button == 0) {
                Sounds.menuClick();
                b.action.run();
                return true;
            }
        }
        String vanilla = "Vanilla menu";
        int vx = width - 6 - StellaFont.width(vanilla);
        if (mx >= vx && my >= height - 24 && my < height - 14) {
            bypass = true;
            client.setScreen(new TitleScreen(false));
            bypass = false;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
