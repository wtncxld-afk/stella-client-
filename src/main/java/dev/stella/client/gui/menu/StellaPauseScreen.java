package dev.stella.client.gui.menu;

import dev.stella.client.gui.ClickGuiScreen;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudEditorScreen;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.MenusModule;
import dev.stella.client.modules.hud.BrandingHud;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Stella pause menu: blurred game, star overlay, info card and animated buttons. */
public class StellaPauseScreen extends dev.stella.client.gui.StellaScreen {
    private final List<MenuButton> buttons = new ArrayList<>();
    private CosmicBackground stars;
    private final MenuFx fx = new MenuFx().density(0.6f);
    private final long openedAt = System.currentTimeMillis();
    private final Animation fade = new Animation(0, 9).target(1);

    public StellaPauseScreen() { super(Text.translatable("menu.game")); }

    @Override
    protected void init() {
        if (stars == null) stars = new CosmicBackground(ModuleManager.get(MenusModule.class).stars.getInt() / 2);
        buttons.clear();
        buttons.add(new MenuButton("▶", "BACK TO GAME", () -> client.setScreen(null), true, false));
        buttons.add(new MenuButton("★", "ADVANCEMENTS", () -> client.setScreen(new AdvancementsScreen(client.player.networkHandler.getAdvancementHandler(), this)), false, false));
        buttons.add(new MenuButton("≡", "STATISTICS", () -> client.setScreen(new StatsScreen(this, client.player.getStatHandler())), false, false));
        buttons.add(new MenuButton("⚙", "OPTIONS", () -> client.setScreen(new OptionsScreen(this, client.options)), false, false));
        buttons.add(new MenuButton("✦", "STELLA CLICKGUI", () -> client.setScreen(new ClickGuiScreen()), false, false));
        buttons.add(new MenuButton("▣", "HUD EDITOR", () -> client.setScreen(new HudEditorScreen(this)), false, false));
        buttons.add(new MenuButton("⏻", client.isInSingleplayer() ? "SAVE & QUIT" : "DISCONNECT", this::disconnect, false, true));
        int bw = 160, x = width / 2 - bw / 2, y = height / 2 - (buttons.size() * 24) / 2 + 14;
        for (MenuButton b : buttons) { b.x = x; b.y = y; b.w = bw; y += b.h + 4; }
    }

    /** Same sequence as vanilla's pause-menu disconnect. */
    private void disconnect() {
        boolean singleplayer = client.isInSingleplayer();
        ServerInfo serverInfo = client.getCurrentServerEntry();
        if (singleplayer) client.disconnectWithSavingScreen();
        else client.disconnectWithProgressScreen();
        TitleScreen title = new TitleScreen(false);
        if (singleplayer || serverInfo == null) client.setScreen(title);
        else client.setScreen(new MultiplayerScreen(title));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (client.world != null) applyBlur(ctx);
        double a = fade.get();
        ctx.fillGradient(0, 0, width, height, ColorUtil.mulAlpha(0xB0070410, a), ColorUtil.mulAlpha(0xD0120A22, a));
        if (ModuleManager.get(MenusModule.class).pauseStars.isOn()) stars.render(ctx, width, height, a * 0.8, false, 0, 0, true);
        if (!buttons.isEmpty()) fx.focus(width / 2f, buttons.get(0).y - 33, 22);
        fx.render(ctx, width, height, a);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        double a = fade.get();
        long age = System.currentTimeMillis() - openedAt;
        var theme = ThemeManager.get();
        // the whole panel zooms in slightly as it opens
        double z = Animation.easeOutCubic(Math.min(1, age / 320.0));
        float zs = (float) (0.94 + 0.06 * z);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(width / 2f, height / 2f);
        ctx.getMatrices().scale(zs, zs);
        ctx.getMatrices().translate(-width / 2f, -height / 2f);
        // glass card behind the buttons
        if (!buttons.isEmpty()) {
            MenuButton f = buttons.get(0), l = buttons.get(buttons.size() - 1);
            float px = f.x - 10, py = f.y - 50, pw = f.w + 20, ph = l.y + l.h - py + 10;
            Render2D.shadow(ctx, px, py, pw, ph, 10, 8, ColorUtil.mulAlpha(0x60000000, a));
            Render2D.roundRect(ctx, px, py, pw, ph, 10, ColorUtil.mulAlpha(0x70120E1C, a));
            Render2D.hGradient(ctx, px + 10, py, pw - 20, 1, ColorUtil.mulAlpha(ColorUtil.withAlpha(theme.accent(), 0), a), ColorUtil.mulAlpha(theme.accent(), a));
        }
        // header: logo with a glow pulse, shimmering PAUSED
        int top = buttons.isEmpty() ? height / 2 : buttons.get(0).y - 42;
        double tsec = age / 1000.0;
        int glowA = (int) (120 + 70 * Math.sin(tsec * 2.2));
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, BrandingHud.GLOW, width / 2 - 18, top - 9, 0f, 0f, 36, 36, 36, 36,
                ColorUtil.mulAlpha((glowA << 24) | (theme.accent() & 0xFFFFFF), a));
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, BrandingHud.LOGO, width / 2 - 9, top, 0f, 0f, 18, 18, 18, 18, ColorUtil.mulAlpha(0xFFFFFFFF, a));
        String title = "PAUSED";
        float ts = 1.6f;
        float tx = width / 2f - StellaFont.width(title) * ts / 2f;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            double p = i / (double) (title.length() - 1);
            double sh = Math.max(0, 1 - Math.abs(p - ((age % 2600) / 2600.0 * 1.6 - 0.3)) * 4);
            Render2D.textScaled(ctx, ch, tx, top + 21, ts, ColorUtil.mulAlpha(ColorUtil.lerp(ThemeManager.accentAt(p * 0.4), 0xFFFFFFFF, sh * 0.8), a), true);
            tx += StellaFont.width(ch) * ts;
        }
        // info card on the left of the buttons
        List<String> info = new ArrayList<>();
        if (client.isInSingleplayer()) info.add("SINGLEPLAYER");
        else if (client.getCurrentServerEntry() != null) {
            info.add(client.getCurrentServerEntry().address.toUpperCase());
            if (client.getNetworkHandler() != null) {
                info.add(client.getNetworkHandler().getPlayerList().size() + " PLAYERS ONLINE");
                PlayerListEntry me = client.player == null ? null : client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
                if (me != null) info.add("PING " + me.getLatency() + " MS");
            }
        }
        if (client.player != null) info.add("XYZ " + client.player.getBlockX() + " " + client.player.getBlockY() + " " + client.player.getBlockZ());
        info.add(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        int cw = 0;
        for (String s : info) cw = Math.max(cw, StellaFont.width(s));
        int cx = buttons.isEmpty() ? 10 : buttons.get(0).x + buttons.get(0).w + 14, cy = buttons.isEmpty() ? 10 : buttons.get(0).y;
        if (cx + cw + 16 < width) {
            Render2D.roundRect(ctx, cx, cy, cw + 16, info.size() * 12 + 8, 6, ColorUtil.mulAlpha(0xA0100D16, a));
            Render2D.roundRect(ctx, cx, cy, 2, info.size() * 12 + 8, 1, ColorUtil.mulAlpha(theme.accent(), a));
            for (int i = 0; i < info.size(); i++)
                StellaFont.draw(ctx, info.get(i), cx + 9, cy + 6 + i * 12, ColorUtil.mulAlpha(i == 0 ? theme.text() : theme.textDim(), a), false);
        }
        for (int i = 0; i < buttons.size(); i++) {
            double e = Math.max(0, Math.min(1, (age - i * 45) / 260.0));
            buttons.get(i).render(ctx, mx, my, a, Animation.easeOutCubic(e));
        }
        ctx.getMatrices().popMatrix();
    }

    @Override
    protected boolean onMouseClicked(double mx, double my, int button) {
        for (MenuButton b : buttons) {
            if (b.over(mx, my) && button == 0) { Sounds.menuClick(); fx.burst((float) mx, (float) my, 16, ThemeManager.get().accent()); b.action.run(); return true; }
        }
        return super.onMouseClicked(mx, my, button);
    }
}
