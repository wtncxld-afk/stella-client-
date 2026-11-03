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
public class StellaMainMenu extends dev.stella.client.gui.StellaScreen {
    /** Set while opening the vanilla title screen on purpose so the redirect mixin lets it through. */
    public static boolean bypass;

    private final List<MenuButton> buttons = new ArrayList<>();
    private int logoTop;
    private CosmicBackground bg;
    private final long openedAt = System.currentTimeMillis();
    private final Animation fade = new Animation(0, 5).target(1);

    public StellaMainMenu() { super(Text.literal("Stella Client")); }

    /** Content is laid out on a virtual canvas and scaled down when the window is too small. */
    private float ui = 1f;
    private int vw, vh;

    private void computeScale() {
        int needed = 128 + buttons.size() * 24 + 30; // logo/title block + buttons + footer
        ui = Math.min(1f, Math.min(height / (float) Math.max(needed, 1), width / 300f));
        vw = (int) (width / ui);
        vh = (int) (height / ui);
    }

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
        computeScale();
        int bw = 170, x = vw / 2 - bw / 2;
        int top = Math.max(4, (vh - (128 + buttons.size() * 24 + 18)) / 2);
        int y = top + 128;
        for (MenuButton b : buttons) {
            b.x = x; b.y = y; b.w = bw;
            y += b.h + 4;
        }
        logoTop = top;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int rawX, int rawY, float delta) {
        double a = fade.get();
        bg.render(ctx, width, height, 1, true);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale((float) (ui), (float) (ui));
        int mx = (int) (rawX / ui), my = (int) (rawY / ui);
        int width = vw, height = vh;
        long age = System.currentTimeMillis() - openedAt;
        double t = age / 1000.0;
        var theme = ThemeManager.get();

        // ---- logo
        int logoY = logoTop;
        float bob = (float) Math.sin(t * 1.4) * 2;
        int glowA = (int) (150 + 80 * Math.sin(t * 2));
        int gs = 72;
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, BrandingHud.GLOW, width / 2 - gs / 2, (int) (logoY - 8 + bob), 0f, 0f, gs, gs, gs, gs,
                ColorUtil.mulAlpha((glowA << 24) | (theme.accent() & 0xFFFFFF), a));
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, BrandingHud.LOGO, width / 2 - 20, (int) (logoY + 8 + bob), 0f, 0f, 40, 40, 40, 40,
                ColorUtil.mulAlpha(0xFFFFFFFF, a));
        // big STELLA with shimmering gradient
        String title = "STELLA";
        float sc = 3.2f;
        int tw = 0;
        for (char c : title.toCharArray()) tw += StellaFont.width(String.valueOf(c)) + 2;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) (width / 2f - tw * sc / 2f), (float) (logoY + 54));
        ctx.getMatrices().scale((float) (sc), (float) (sc));
        int cx = 0;
        for (int i = 0; i < title.length(); i++) {
            double p = (i / (double) (title.length() - 1) + t * 0.25) % 1.0;
            double tri = p < 0.5 ? p * 2 : (1 - p) * 2;
            int c = ColorUtil.lerp(ThemeManager.accentAt(0), 0xFFF5E8FF, tri);
            String ch = String.valueOf(title.charAt(i));
            StellaFont.draw(ctx, ch, cx, 0, ColorUtil.mulAlpha(c, a), true);
            cx += StellaFont.width(ch) + 2;
        }
        ctx.getMatrices().popMatrix();
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
        boolean vHover = mx >= vx && my >= height - 24 && my < height - 14;
        StellaFont.draw(ctx, vanilla, vx, height - 24, ColorUtil.mulAlpha(vHover ? theme.accent() : 0xFF6E6878, a), false);
        ctx.getMatrices().popMatrix();
        super.render(ctx, rawX, rawY, delta);
    }

    @Override
    protected boolean onMouseClicked(double rawX, double rawY, int button) {
        double mx = rawX / ui, my = rawY / ui;
        int width = vw, height = vh;
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
        return super.onMouseClicked(rawX, rawY, button);
    }
}
