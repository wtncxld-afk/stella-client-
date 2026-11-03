package dev.stella.client.modules.hud;

import dev.stella.client.StellaClient;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.music.MediaInfo;
import dev.stella.client.music.MusicService;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.KeySetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayInputStream;

/**
 * Now-playing card: cover art, title, artist, progress and ⏮ ⏯ ⏭ controls.
 * Works without linking Spotify — it reads the operating system's media
 * session (see {@link MusicService}). Click the buttons with chat open, or
 * bind the control keys.
 */
public class MusicHud extends HudElement {
    private static final Identifier COVER = Identifier.of(StellaClient.MOD_ID, "music_cover");

    private final BoolSetting spotifyOnly = add(new BoolSetting("Spotify Only", "Only show Spotify (off = any media player, e.g. browser)", true));
    private final BoolSetting showCover = add(new BoolSetting("Cover Art", "Show the album cover", true));
    private final BoolSetting showProgress = add(new BoolSetting("Progress Bar", "Show song progress and duration", true));
    private final BoolSetting showButtons = add(new BoolSetting("Controls", "Show previous / play-pause / next buttons", true));
    private final BoolSetting hideWhenIdle = add(new BoolSetting("Hide When Idle", "Hide the card when nothing is playing", false));
    private final NumberSetting cardWidth = add(new NumberSetting("Width", "Card width", 190, 130, 320, 5));
    private final NumberSetting pollRate = add(new NumberSetting("Refresh Rate", "How often to read the player (ms)", 1000, 250, 5000, 250).suffix("ms"));
    private final KeySetting playPauseKey = add(new KeySetting("Play/Pause Key", "Toggle playback", GLFW.GLFW_KEY_UNKNOWN));
    private final KeySetting nextKey = add(new KeySetting("Next Key", "Skip to the next song", GLFW.GLFW_KEY_UNKNOWN));
    private final KeySetting prevKey = add(new KeySetting("Previous Key", "Go back a song", GLFW.GLFW_KEY_UNKNOWN));
    private final CardStyle card = card("Modern");

    private NativeImageBackedTexture coverTex;
    private boolean hasCover;
    private final Animation progressAnim = new Animation(0, 12);
    private final boolean[] keyDown = new boolean[3];
    private int btnY, btnX0;

    public MusicHud() { super("Music HUD", "Spotify / now-playing card with cover, duration and controls (no account linking)", 4, -110, false); }

    @Override protected void onEnable() { MusicService.configure(spotifyOnly.isOn(), pollRate.getInt()); MusicService.start(); }
    @Override protected void onDisable() { MusicService.stop(); }

    @Override
    public void onTick() {
        MusicService.configure(spotifyOnly.isOn(), pollRate.getInt());
        if (mc.currentScreen != null) return;
        long h = mc.getWindow().getHandle();
        KeySetting[] keys = {playPauseKey, nextKey, prevKey};
        String[] cmds = {"toggle", "next", "prev"};
        for (int i = 0; i < 3; i++) {
            boolean down = keys[i].isBound() && dev.stella.client.util.Keys.down(keys[i].key());
            if (down && !keyDown[i]) MusicService.command(cmds[i]);
            keyDown[i] = down;
        }
    }

    private void uploadCover() {
        if (MusicService.takeCoverCleared()) hasCover = false;
        byte[] png = MusicService.takeCover();
        if (png == null) return;
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            coverTex = new NativeImageBackedTexture(() -> "stella_music_cover", img);
            mc.getTextureManager().registerTexture(COVER, coverTex);
            hasCover = true;
        } catch (Exception e) {
            hasCover = false;
        }
    }

    private static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        uploadCover();
        MediaInfo m = MusicService.info();
        boolean example = editor && !m.active();
        if (example) m = new MediaInfo(true, "Song Title", "Artist Name", true, 83_000, 214_000, "Spotify", System.currentTimeMillis());
        if (!m.active() && hideWhenIdle.isOn() && !editor) { setSize(10, 10); return; }

        int w = cardWidth.getInt();
        int cover = showCover.isOn() ? 40 : 0;
        int h = 40;
        card.draw(ctx, this, w, h);
        int px = card.padLeft(), py = card.pad();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) (px), (float) (py));
        var t = ThemeManager.get();

        if (showCover.isOn()) {
            if (hasCover && !example && m.active()) {
                ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, COVER, 0, 0, 0f, 0f, 40, 40, 40, 40, col(0xFFFFFFFF));
            } else {
                Render2D.roundRect(ctx, 0, 0, 40, 40, 3, col(ColorUtil.withAlpha(t.accent(), 0x60)));
                StellaFont.draw(ctx, "♪", 16, 15, col(0xFFFFFFFF), true);
            }
        }
        int tx = cover + (cover > 0 ? 6 : 0);
        int tw = w - tx;
        if (!m.active()) {
            StellaFont.draw(ctx, "NOTHING PLAYING", tx, 4, col(t.accentSoft()), true);
            String st = MusicService.status();
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((float) (tx), 18);
            ctx.getMatrices().scale(0.7f, 0.7f);
            StellaFont.draw(ctx, trim(st.isEmpty() ? "Waiting for the media player..." : st, (int) (tw / 0.7f)), 0, 0, col(t.textDim()), false);
            ctx.getMatrices().popMatrix();
            ctx.getMatrices().popMatrix();
            setSize(w + px + card.pad(), h + py * 2);
            return;
        }
        StellaFont.draw(ctx, trim(m.title(), tw - (showButtons.isOn() ? 0 : 0)), tx, 1, col(0xFFFFFFFF), true);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) (tx), 12);
        ctx.getMatrices().scale(0.75f, 0.75f);
        StellaFont.draw(ctx, trim(m.artist(), (int) (tw / 0.75f)), 0, 0, col(t.textDim()), false);
        ctx.getMatrices().popMatrix();

        if (showProgress.isOn()) {
            long pos = m.livePosition(), dur = m.durationMs();
            double frac = dur > 0 ? pos / (double) dur : 0;
            double shown = progressAnim.target(frac).get();
            int by = 24;
            Render2D.roundRect(ctx, tx, by, tw, 2.5f, 1.25f, col(0x40FFFFFF));
            Render2D.roundRect(ctx, tx, by, (float) (tw * shown), 2.5f, 1.25f, col(ThemeManager.accentAt(0)));
            Render2D.roundRect(ctx, (float) (tx + tw * shown - 2.5f), by - 1.25f, 5, 5, 2.5f, col(0xFFFFFFFF));
            String a = time(pos), b = dur > 0 ? time(dur) : "--:--";
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((float) (tx), (float) (by + 5));
            ctx.getMatrices().scale(0.65f, 0.65f);
            StellaFont.draw(ctx, a, 0, 0, col(t.textDim()), false);
            StellaFont.draw(ctx, b, (int) (tw / 0.65f) - StellaFont.width(b), 0, col(t.textDim()), false);
            ctx.getMatrices().popMatrix();
        }
        if (showButtons.isOn()) {
            int cx = tx + tw / 2;
            btnY = 31;
            btnX0 = cx - 21;
            drawPrev(ctx, cx - 18, btnY + 1, col(0xFFE6E1EE));
            if (m.playing()) drawPause(ctx, cx - 3, btnY, col(0xFFFFFFFF));
            else drawPlay(ctx, cx - 3, btnY, col(0xFFFFFFFF));
            drawNext(ctx, cx + 12, btnY + 1, col(0xFFE6E1EE));
        }
        ctx.getMatrices().popMatrix();
        setSize(w + px + card.pad(), h + py * 2);
    }

    private static void tri(DrawContext ctx, float x, float y, float w, float h, boolean right, int c) {
        int steps = (int) Math.max(4, w * 2);
        for (int i = 0; i < steps; i++) {
            float f = i / (float) steps;
            float hh = h * (right ? 1 - f : f);
            Render2D.rect(ctx, x + w * f, y + (h - hh) / 2f, w / steps + 0.1f, hh, c);
        }
    }

    private static void drawPlay(DrawContext ctx, int x, int y, int c) { tri(ctx, x, y, 7, 8, true, c); }
    private static void drawPause(DrawContext ctx, int x, int y, int c) { ctx.fill(x, y, x + 2, y + 8, c); ctx.fill(x + 4, y, x + 6, y + 8, c); }
    private static void drawNext(DrawContext ctx, int x, int y, int c) { tri(ctx, x, y, 5, 6, true, c); ctx.fill(x + 5, y, x + 6, y + 6, c); }
    private static void drawPrev(DrawContext ctx, int x, int y, int c) { ctx.fill(x, y, x + 1, y + 6, c); tri(ctx, x + 1, y, 5, 6, false, c); }

    private String trim(String s, int w) {
        if (StellaFont.width(s) <= w) return s;
        return StellaFont.trimToWidth(s, Math.max(0, w - StellaFont.width(".."))) + "..";
    }

    /** Buttons are clickable while chat is open. */
    @Override
    public boolean onClick(double lx, double ly, int button) {
        if (!showButtons.isOn() || button != 0) return false;
        double x = lx - card.padLeft(), y = ly - card.pad();
        if (y < btnY - 3 || y > btnY + 11) return false;
        int rel = (int) (x - btnX0);
        if (rel >= 0 && rel < 14) { MusicService.command("prev"); return true; }
        if (rel >= 14 && rel < 28) { MusicService.command("toggle"); return true; }
        if (rel >= 28 && rel < 42) { MusicService.command("next"); return true; }
        return false;
    }
}
