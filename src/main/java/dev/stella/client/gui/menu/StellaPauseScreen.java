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
        if (client.world != null) client.world.disconnect();
        if (singleplayer) client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
        else client.disconnect();
        TitleScreen title = new TitleScreen(false);
        if (singleplayer || serverInfo == null) client.setScreen(title);
        else client.setScreen(new MultiplayerScreen(title));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (client.world != null) applyBlur(ctx);
        double a = fade.get();
        ctx.fillGradient(0, 0, width, height, ColorUtil.mulAlpha(0xB0070410, a), ColorUtil.mulAlpha(0xD0120A22, a));
        if (ModuleManager.get(MenusModule.class).pauseStars.isOn()) stars.render(ctx, width, height, a * 0.8, false);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        double a = fade.get();
        long age = System.currentTimeMillis() - openedAt;
        var theme = ThemeManager.get();
        // header
        int top = buttons.isEmpty() ? height / 2 : buttons.get(0).y - 42;
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, BrandingHud.LOGO, width / 2 - 9, top, 0f, 0f, 18, 18, 18, 18, ColorUtil.mulAlpha(0xFFFFFFFF, a));
        String title = "PAUSED";
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) (width / 2f - StellaFont.width(title) * 1.6f / 2f), (float) (top + 21));
        ctx.getMatrices().scale(1.6f, 1.6f);
        StellaFont.draw(ctx, title, 0, 0, ColorUtil.mulAlpha(ThemeManager.accentAt(0), a), true);
        ctx.getMatrices().popMatrix();
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
    }

    @Override
    protected boolean onMouseClicked(double mx, double my, int button) {
        for (MenuButton b : buttons) {
            if (b.over(mx, my) && button == 0) { Sounds.menuClick(); b.action.run(); return true; }
        }
        return super.onMouseClicked(mx, my, button);
    }
}
