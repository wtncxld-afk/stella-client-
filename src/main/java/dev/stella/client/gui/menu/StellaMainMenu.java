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
    private final MenuFx fx = new MenuFx();
    private static final String[] TAGLINES = {"Built for DonutSMP", "Now with knives!", "Shiny since day one", "Petals included",
            "Find every spawner", "Liquid glass, anywhere", "Stella says hi", "Faster than your FPS", "Star-powered"};
    private final String tagline = TAGLINES[(int) (Math.random() * TAGLINES.length)];
    private boolean introBurst;
    private double nextArc = 2.0;
    private long lastFrame;
    private final long openedAt = System.currentTimeMillis();
    private final Animation fade = new Animation(0, 5).target(1);

    public StellaMainMenu() { super(Text.literal(dev.stella.client.Brand.FULL)); }

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
        buttons.add(new MenuButton("✦", dev.stella.client.Brand.UPPER + " CLICKGUI", () -> client.setScreen(new ClickGuiScreen()), false, false));
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
        if (dev.stella.client.Brand.ISRAELI) { // flag layout: button column on the left, between the stripes
            int bx = 18, bw2 = 150, by = (int) (vh * 40f / 160f) + 8;
            for (MenuButton b : buttons) { b.x = bx; b.y = by; b.w = bw2; by += b.h + 4; }
        }
    }

    /**
     * Israeli Client main menu: the waving flag of Israel fills the screen, the white title sits on the top
     * blue stripe next to a Star of David, and white buttons with blue text run down the left so the flag's
     * star stays visible in the middle.
     */
    private void renderIsraeli(DrawContext ctx, int rawX, int rawY, float delta) {
        double a = fade.get();
        IsraelFlag.render(ctx, width, height, 1);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(ui, ui);
        int mx = (int) (rawX / ui), my = (int) (rawY / ui);
        int width = vw, height = vh;
        long age = System.currentTimeMillis() - openedAt;
        double t = age / 1000.0;
        float sTop = height * 15f / 160f, sH = height * 25f / 160f;
        float logoCx = 18 + sH * 0.38f, logoCy = sTop + sH / 2f;
        fx.focus(logoCx, logoCy, sH * 0.6f).render(ctx, width, height, a);
        // logo pops in with a ring
        double pop = dev.stella.client.util.Animation.easeOutBack(Math.min(1, age / 650.0));
        if (!introBurst && age > 380) { introBurst = true; fx.burst(logoCx, logoCy, 30, 0xFFFFFFFF); }
        if (age < 1200) {
            double k = Math.min(1, age / 1200.0);
            float rr = (float) (6 + k * 70);
            int segs = 48;
            for (int i = 0; i < segs; i++) {
                double a1 = i * Math.PI * 2 / segs, a2 = (i + 1) * Math.PI * 2 / segs;
                dev.stella.client.render.Render2D.line(ctx, logoCx + Math.cos(a1) * rr, logoCy + Math.sin(a1) * rr,
                        logoCx + Math.cos(a2) * rr, logoCy + Math.sin(a2) * rr, (float) (2 * (1 - k)) + 0.3f,
                        ColorUtil.withAlpha(0xFFFFFFFF, (int) (200 * (1 - k))));
            }
        }
        IsraelFlag.logo(ctx, logoCx, logoCy, (float) (sH * 0.36f * pop), 0xFFFFFFFF, 0x70A8DAFF, a);
        // title: white letters dropping in one by one, a light-blue shimmer running across
        String title = dev.stella.client.Brand.UPPER;
        float sc = Math.max(1.2f, Math.min(2.6f, sH * 0.6f / 9f));
        float tx = logoCx + sH * 0.5f + 4, ty = sTop + (sH - 9 * sc) / 2f + 1;
        float cx = tx;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            double le = Math.max(0, Math.min(1, (age - 250 - i * 60) / 360.0));
            float drop = (float) ((1 - dev.stella.client.util.Animation.easeOutBack(le)) * -6);
            double p = i / (double) Math.max(1, title.length() - 1);
            double sh = Math.max(0, 1 - Math.abs(p - ((age % 2600) / 2600.0 * 1.6 - 0.3)) * 4);
            dev.stella.client.render.Render2D.textScaled(ctx, ch, cx, ty + drop, sc, ColorUtil.mulAlpha(ColorUtil.lerp(0xFFFFFFFF, 0xFFA8DAFF, sh), a * le), true);
            cx += StellaFont.width(ch) * sc + sc;
        }
        double subIn = Math.max(0, Math.min(1, (age - 700) / 400.0));
        dev.stella.client.render.Render2D.textScaled(ctx, "C L I E N T", cx + 6, ty + 9 * sc - 9 * 1.1f, 1.1f, ColorUtil.mulAlpha(0xFFD6E8FF, a * subIn), true);
        // buttons (staggered entrance)
        for (int i = 0; i < buttons.size(); i++) {
            double e = Math.max(0, Math.min(1, (age - 250 - i * 70) / 350.0));
            buttons.get(i).render(ctx, mx, my, a, dev.stella.client.util.Animation.easeOutCubic(e));
        }
        // footer in flag blue (the bottom edge of the flag is white)
        int blue = IsraelFlag.BLUE;
        StellaFont.draw(ctx, "Logged in as " + client.getSession().getUsername(), 6, height - 12, ColorUtil.mulAlpha(blue, a), false);
        String ver = dev.stella.client.Brand.FULL + " " + StellaClient.VERSION + "  ·  Minecraft 1.21.11  ·  Fabric";
        StellaFont.draw(ctx, ver, width - 6 - StellaFont.width(ver), height - 12, ColorUtil.mulAlpha(blue, a), false);
        String vanilla = "Vanilla menu";
        int vx = width - 6 - StellaFont.width(vanilla);
        boolean vHover = mx >= vx && my >= height - 24 && my < height - 14;
        StellaFont.draw(ctx, vanilla, vx, height - 24, ColorUtil.mulAlpha(vHover ? 0xFF3D8BFF : 0xFF5A6E9A, a), false);
        ctx.getMatrices().popMatrix();
        super.render(ctx, rawX, rawY, delta);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int rawX, int rawY, float delta) {
        if (dev.stella.client.Brand.ISRAELI) { renderIsraeli(ctx, rawX, rawY, delta); return; }
        double a = fade.get();
        MenusModule cfg = ModuleManager.get(MenusModule.class);
        float pxm = 0, pym = 0;
        if (cfg.parallax.isOn() && width > 0 && height > 0) { pxm = rawX / (float) width * 2 - 1; pym = rawY / (float) height * 2 - 1; }
        bg.render(ctx, width, height, 1, true, pxm, pym, true);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale((float) (ui), (float) (ui));
        int mx = (int) (rawX / ui), my = (int) (rawY / ui);
        int width = vw, height = vh;
        long age = System.currentTimeMillis() - openedAt;
        double t = age / 1000.0;
        var theme = ThemeManager.get();

        // ---- particles (behind the logo and buttons), lightning crackles around the logo
        float logoCx = width / 2f, logoCy = logoTop + 28;
        fx.focus(logoCx, logoCy, 34).render(ctx, width, height, a);
        long nowMs = System.currentTimeMillis();
        float dtf = lastFrame == 0 ? 0.016f : Math.min(0.05f, (nowMs - lastFrame) / 1000f);
        lastFrame = nowMs;
        nextArc -= dtf;
        if (nextArc <= 0 && age > 900) { // arc from the logo to a letter of STELLA
            double ang = Math.random() * Math.PI * 2;
            fx.boltBetween(logoCx + (float) Math.cos(ang) * 16, logoCy + (float) Math.sin(ang) * 16,
                    logoCx + (float) Math.cos(ang) * 46, logoCy + (float) Math.sin(ang) * 30, ColorUtil.lerp(theme.accent(), 0xFFFFFFFF, 0.4));
            nextArc = 2.5 + Math.random() * 3.5;
        }
        // intro: logo pops in with a shockwave and a spark burst
        double pop = dev.stella.client.util.Animation.easeOutBack(Math.min(1, age / 650.0));
        if (!introBurst && age > 380) { introBurst = true; fx.burst(logoCx, logoCy, 36, theme.accent()); }
        if (age < 1400) {
            double k = Math.min(1, age / 1400.0);
            float rr = (float) (10 + k * 110);
            int segs = 64;
            for (int i = 0; i < segs; i++) {
                double a1 = i * Math.PI * 2 / segs, a2 = (i + 1) * Math.PI * 2 / segs;
                dev.stella.client.render.Render2D.line(ctx, logoCx + Math.cos(a1) * rr, logoCy + Math.sin(a1) * rr * 0.8,
                        logoCx + Math.cos(a2) * rr, logoCy + Math.sin(a2) * rr * 0.8, (float) (2.5 * (1 - k)) + 0.3f,
                        ColorUtil.withAlpha(ColorUtil.lerp(theme.accent(), 0xFFFFFFFF, 0.5), (int) (200 * (1 - k))));
            }
        }

        // ---- logo
        int logoY = logoTop;
        float bob = (float) Math.sin(t * 1.4) * 2;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(logoCx, logoCy);
        ctx.getMatrices().scale((float) pop, (float) pop);
        ctx.getMatrices().rotate((float) ((1 - Math.min(1, age / 650.0)) * -0.6));
        ctx.getMatrices().translate(-logoCx, -logoCy);
        int glowA = (int) (150 + 80 * Math.sin(t * 2));
        int gs = 72;
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, BrandingHud.GLOW, width / 2 - gs / 2, (int) (logoY - 8 + bob), 0f, 0f, gs, gs, gs, gs,
                ColorUtil.mulAlpha((glowA << 24) | (theme.accent() & 0xFFFFFF), a));
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, BrandingHud.LOGO, width / 2 - 20, (int) (logoY + 8 + bob), 0f, 0f, 40, 40, 40, 40,
                ColorUtil.mulAlpha(0xFFFFFFFF, a));
        ctx.getMatrices().popMatrix();
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
            double le = Math.max(0, Math.min(1, (age - 300 - i * 70) / 380.0));
            float drop = (float) ((1 - dev.stella.client.util.Animation.easeOutBack(le)) * -6);
            float wave = (float) Math.sin(t * 2.2 - i * 0.6) * 0.6f;
            StellaFont.draw(ctx, ch, cx, (int) (drop + wave), ColorUtil.mulAlpha(c, a * le), true);
            cx += StellaFont.width(ch) + 2;
        }
        ctx.getMatrices().popMatrix();
        String sub = "C L I E N T";
        double subIn = Math.max(0, Math.min(1, (age - 750) / 400.0));
        StellaFont.draw(ctx, sub, width / 2 - StellaFont.width(sub) / 2, logoY + 86, ColorUtil.mulAlpha(theme.accentSoft(), a * subIn), false);
        // tagline: tilted, pulsing, typed out
        int typed = (int) Math.max(0, Math.min(tagline.length(), (age - 1100) / 45));
        if (typed > 0) {
            String tg = tagline.substring(0, typed);
            float ps = (float) (0.85 + 0.06 * Math.sin(t * 5));
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(width / 2f + tw * sc / 2f - 6, logoY + 58);
            ctx.getMatrices().rotate(-0.33f);
            ctx.getMatrices().scale(ps, ps);
            StellaFont.draw(ctx, tg, -StellaFont.width(tg) / 2, 0, ColorUtil.mulAlpha(0xFFFDE68A, a), true);
            ctx.getMatrices().popMatrix();
        }

        // ---- buttons (staggered entrance)
        for (int i = 0; i < buttons.size(); i++) {
            double e = Math.max(0, Math.min(1, (age - 250 - i * 70) / 350.0));
            buttons.get(i).render(ctx, mx, my, a, dev.stella.client.util.Animation.easeOutCubic(e));
        }

        // ---- footer
        String user = "Logged in as " + client.getSession().getUsername();
        StellaFont.draw(ctx, user, 6, height - 12, ColorUtil.mulAlpha(theme.textDim(), a), false);
        String ver = dev.stella.client.Brand.NAME + " " + StellaClient.VERSION + "  ·  Minecraft 1.21.11  ·  Fabric";
        String upd = dev.stella.client.update.AutoUpdater.statusLine();
        if (upd != null) StellaFont.draw(ctx, upd, 6, height - 24, ColorUtil.mulAlpha(theme.accent(), a), false);
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
                fx.burst((float) mx, (float) my, 18, ThemeManager.get().accent());
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
