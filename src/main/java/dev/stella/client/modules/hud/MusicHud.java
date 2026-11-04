package dev.stella.client.modules.hud;

import dev.stella.client.StellaClient;
import dev.stella.client.gui.theme.ThemeManager;
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
    private final dev.stella.client.setting.ModeSetting style = add(new dev.stella.client.setting.ModeSetting("Style",
            "Card: large now-playing card. Compact: slim one-line bar", "Card", "Card", "Compact"));
    private final BoolSetting albumColors = add(new BoolSetting("Album Colors", "Tint the card, progress bar and play button with the album art's colour", true));
    private final BoolSetting equalizer = add(new BoolSetting("Equalizer", "Animated bars while music plays", true));
    private final BoolSetting scrollTitle = add(new BoolSetting("Scroll Long Titles", "Long titles scroll instead of being cut off", true));
    private final BoolSetting hideWhenIdle = add(new BoolSetting("Hide When Idle", "Hide the card when nothing is playing", false));
    private final NumberSetting cardWidth = add(new NumberSetting("Width", "Card width", 200, 130, 320, 5));
    private final NumberSetting pollRate = add(new NumberSetting("Refresh Rate", "How often to read the player (ms)", 1000, 250, 5000, 250).suffix("ms"));
    private final KeySetting playPauseKey = add(new KeySetting("Play/Pause Key", "Toggle playback", GLFW.GLFW_KEY_UNKNOWN));
    private final KeySetting nextKey = add(new KeySetting("Next Key", "Skip to the next song", GLFW.GLFW_KEY_UNKNOWN));
    private final KeySetting prevKey = add(new KeySetting("Previous Key", "Go back a song", GLFW.GLFW_KEY_UNKNOWN));

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
            albumTarget = dominantColor(img);
            coverTex = new NativeImageBackedTexture(() -> "stella_music_cover", img);
            mc.getTextureManager().registerTexture(COVER, coverTex);
            hasCover = true;
        } catch (Exception e) {
            hasCover = false;
        }
    }

    // ---- album colour, song-change and press animations
    private int albumTarget = 0xFFA855F7;
    private float albumR = 0xA8, albumG = 0x55, albumB = 0xF7;
    private String lastTitle = "";
    private long titleChangedAt, pressedAt;
    private int pressedButton = -1;

    /** Average of the saturated, mid-bright pixels: the colour the cover "feels" like. */
    private static int dominantColor(NativeImage img) {
        long r = 0, g = 0, b = 0, n = 0;
        int w = img.getWidth(), h = img.getHeight(), step = Math.max(1, Math.min(w, h) / 24);
        for (int y = 0; y < h; y += step) for (int x = 0; x < w; x += step) {
            int c = img.getColorArgb(x, y);
            int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
            int max = Math.max(cr, Math.max(cg, cb)), min = Math.min(cr, Math.min(cg, cb));
            if (max < 40 || max - min < 30) continue; // skip near-black and grey pixels
            r += cr; g += cg; b += cb; n++;
        }
        if (n == 0) return 0xFFA855F7;
        int cr = (int) (r / n), cg = (int) (g / n), cb = (int) (b / n);
        // lift dark covers so the accent is always visible
        float boost = Math.max(1f, 170f / Math.max(cr, Math.max(cg, Math.max(cb, 1))));
        cr = Math.min(255, (int) (cr * boost)); cg = Math.min(255, (int) (cg * boost)); cb = Math.min(255, (int) (cb * boost));
        return 0xFF000000 | (cr << 16) | (cg << 8) | cb;
    }

    private int album() {
        int target = albumColors.isOn() ? albumTarget : (ThemeManager.get().accent() | 0xFF000000);
        float k = 0.06f; // smooth colour transition between songs
        albumR += (((target >> 16) & 0xFF) - albumR) * k;
        albumG += (((target >> 8) & 0xFF) - albumG) * k;
        albumB += ((target & 0xFF) - albumB) * k;
        return 0xFF000000 | ((int) albumR << 16) | ((int) albumG << 8) | (int) albumB;
    }

    /** Marquee: scrolls a long string with a pause at the start. */
    private String marquee(String text, int width) {
        if (!scrollTitle.isOn() || StellaFont.width(text) <= width) return trim(text, width);
        String loop = text + "     •     ";
        long t = System.currentTimeMillis() - titleChangedAt;
        int offset = t < 2500 ? 0 : (int) (((t - 2500) / 140) % loop.length());
        String rolled = (loop + loop).substring(offset);
        return StellaFont.trimToWidth(rolled, width);
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
        if (example) m = new MediaInfo(true, "Song Title That Is Quite Long", "Artist Name", true, 83_000, 214_000, "Spotify", System.currentTimeMillis());
        if (!m.active() && hideWhenIdle.isOn() && !editor) { setSize(10, 10); return; }
        if (!m.title().equals(lastTitle)) { lastTitle = m.title(); titleChangedAt = System.currentTimeMillis(); }
        if (style.is("Compact")) { renderCompact(ctx, m, example); return; }

        long now = System.currentTimeMillis();
        var t = ThemeManager.get();
        int ac = album();
        int w = cardWidth.getInt(), h = 58, cs = showCover.isOn() ? 46 : 0;
        boolean coverReady = hasCover && !example && m.active();

        // card: shadow, dark base, ambient cover wash, album-coloured gradient, soft top highlight
        Render2D.shadow(ctx, 0, 0, w, h, 7, 5, col(0x80000000));
        Render2D.roundRect(ctx, 0, 0, w, h, 7, col(0xF0100C16));
        if (coverReady && showCover.isOn()) {
            ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, COVER, 2, 2, 0f, 0f, w - 4, h - 4, 40, 40, 40, 40, col(0x24FFFFFF));
        }
        Render2D.hGradient(ctx, 0, 0, w * 0.75f, h, col(ColorUtil.withAlpha(ac, 0x55)), 0);
        Render2D.roundRect(ctx, 1, 1, w - 2, 1, 0.5f, col(0x22FFFFFF));

        // cover: breathing while playing, shadow + border
        if (showCover.isOn()) {
            float breathe = m.playing() ? (float) (1 + 0.025 * Math.sin(now / 520.0)) : 1f;
            float cx = 6 + cs / 2f, cy = h / 2f;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx, cy);
            ctx.getMatrices().scale(breathe, breathe);
            ctx.getMatrices().translate(-cs / 2f, -cs / 2f);
            Render2D.shadow(ctx, 0, 0, cs, cs, 4, 4, col(0x90000000));
            if (coverReady) {
                ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, COVER, 0, 0, 0f, 0f, cs, cs, 40, 40, 40, 40, col(0xFFFFFFFF));
            } else {
                Render2D.roundRect(ctx, 0, 0, cs, cs, 5, col(ColorUtil.withAlpha(ac, 0x90)));
                Render2D.textScaled(ctx, "♪", cs / 2f - 5, cs / 2f - 7, 1.8f, col(0xFFFFFFFF), true);
            }
            Render2D.outline(ctx, 0, 0, cs, cs, col(0x30FFFFFF));
            ctx.getMatrices().popMatrix();
        }

        int tx = (showCover.isOn() ? cs + 13 : 8), tw = w - tx - 8;
        if (!m.active()) {
            StellaFont.draw(ctx, "NOTHING PLAYING", tx, 10, col(t.accentSoft()), true);
            String st = MusicService.status();
            Render2D.textScaled(ctx, trim(st.isEmpty() ? "Waiting for the media player..." : st, (int) (tw / 0.7f)), tx, 24, 0.7f, col(t.textDim()), false);
            setSize(w, h);
            return;
        }

        // source label + equalizer
        String src = (m.source() == null || m.source().isEmpty() ? "NOW PLAYING" : m.source().toUpperCase(java.util.Locale.ROOT));
        Render2D.textScaled(ctx, src, tx, 5, 0.6f, col(ColorUtil.lerp(ac, 0xFFFFFFFF, 0.35)), false);
        if (equalizer.isOn()) {
            float ex = tx + StellaFont.width(src) * 0.6f + 5;
            for (int i = 0; i < 4; i++) {
                double level = m.playing() ? 0.35 + 0.65 * Math.abs(Math.sin(now / (170.0 + i * 47) + i * 1.7)) : 0.2;
                float bh = (float) (6 * level);
                Render2D.roundRect(ctx, ex + i * 2.6f, 10 - bh, 1.6f, bh, 0.8f, col(ac));
            }
        }

        // title / artist: slide + fade in when the song changes
        float in = (float) Math.min(1, (now - titleChangedAt) / 350.0);
        float ease = (float) Animation.easeOutCubic(in);
        float slide = (1 - ease) * 10;
        int textA = (int) (255 * ease);
        StellaFont.draw(ctx, marquee(m.title(), tw), (int) (tx + slide), 13, col(ColorUtil.withAlpha(0xFFFFFFFF, textA)), true);
        Render2D.textScaled(ctx, trim(m.artist(), (int) (tw / 0.75f)), tx + slide * 1.4f, 24, 0.75f, col(ColorUtil.withAlpha(t.textDim(), textA)), false);

        // progress: album-coloured fill with a glow and a glowing knob
        if (showProgress.isOn()) {
            long pos = m.livePosition(), dur = m.durationMs();
            double frac = dur > 0 ? pos / (double) dur : 0;
            double shown = progressAnim.target(frac).get();
            float by = 35, fw = (float) (tw * shown);
            Render2D.roundRect(ctx, tx, by, tw, 3, 1.5f, col(0x33FFFFFF));
            Render2D.roundRect(ctx, tx, by - 1, fw, 5, 2.5f, col(ColorUtil.withAlpha(ac, 0x40)));
            Render2D.hGradient(ctx, tx, by, fw, 3, col(ac), col(ColorUtil.lerp(ac, 0xFFFFFFFF, 0.45)));
            Render2D.roundRect(ctx, tx + fw - 4, by - 2.5f, 8, 8, 4, col(ColorUtil.withAlpha(ac, 0x60)));
            Render2D.roundRect(ctx, tx + fw - 2.5f, by - 1, 5, 5, 2.5f, col(0xFFFFFFFF));
            String a2 = time(pos), b2 = dur > 0 ? time(dur) : "--:--";
            Render2D.textScaled(ctx, a2, tx, by + 6, 0.6f, col(t.textDim()), false);
            Render2D.textScaled(ctx, b2, tx + tw - StellaFont.width(b2) * 0.6f, by + 6, 0.6f, col(t.textDim()), false);
        }

        // controls: round album-coloured play button, prev / next; bounce when clicked
        if (showButtons.isOn()) {
            int cx = tx + tw / 2;
            btnY = 44;
            btnX0 = cx - 21;
            float press = pressedButton >= 0 ? (float) Math.max(0, 1 - (now - pressedAt) / 220.0) : 0;
            float pScale = 1 + 0.18f * (float) Math.sin(press * Math.PI) * (pressedButton == 1 ? 1 : 0);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx, btnY + 4);
            ctx.getMatrices().scale(pScale, pScale);
            Render2D.roundRect(ctx, -6.5f, -6.5f, 13, 13, 6.5f, col(ac));
            Render2D.roundRect(ctx, -6.5f, -6.5f, 13, 6, 6.5f, col(0x22FFFFFF));
            if (m.playing()) drawPause(ctx, -3, -4, col(0xFF101010));
            else drawPlay(ctx, -2, -4, col(0xFF101010));
            ctx.getMatrices().popMatrix();
            drawPrev(ctx, cx - 19, btnY + 1, col(pressedButton == 0 && press > 0 ? ac : 0xFFE6E1EE));
            drawNext(ctx, cx + 13, btnY + 1, col(pressedButton == 2 && press > 0 ? ac : 0xFFE6E1EE));
        }
        setSize(w, h);
    }

    /** Slim one-line bar: cover, scrolling title, equalizer and a thin progress line. */
    private void renderCompact(DrawContext ctx, MediaInfo m, boolean example) {
        long now = System.currentTimeMillis();
        int ac = album(), w = cardWidth.getInt(), h = 18;
        Render2D.shadow(ctx, 0, 0, w, h, 5, 3, col(0x70000000));
        Render2D.roundRect(ctx, 0, 0, w, h, 5, col(0xF0100C16));
        Render2D.hGradient(ctx, 0, 0, w * 0.6f, h, col(ColorUtil.withAlpha(ac, 0x40)), 0);
        int x = 3;
        if (showCover.isOn()) {
            if (hasCover && !example && m.active()) ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, COVER, x, 2, 0f, 0f, 14, 14, 40, 40, 40, 40, col(0xFFFFFFFF));
            else Render2D.roundRect(ctx, x, 2, 14, 14, 3, col(ColorUtil.withAlpha(ac, 0x90)));
            x += 18;
        }
        String text = m.active() ? m.title() + "  -  " + m.artist() : "Nothing playing";
        int eqW = equalizer.isOn() && m.active() ? 14 : 0;
        StellaFont.draw(ctx, marquee(text, w - x - 6 - eqW), x, 5, col(0xFFFFFFFF), true);
        if (eqW > 0) {
            for (int i = 0; i < 4; i++) {
                double level = m.playing() ? 0.35 + 0.65 * Math.abs(Math.sin(now / (170.0 + i * 47) + i * 1.7)) : 0.2;
                float bh = (float) (8 * level);
                Render2D.roundRect(ctx, w - 14 + i * 2.6f, 13 - bh, 1.6f, bh, 0.8f, col(ac));
            }
        }
        if (showProgress.isOn() && m.active() && m.durationMs() > 0) {
            double shown = progressAnim.target(m.livePosition() / (double) m.durationMs()).get();
            Render2D.rect(ctx, 4, h - 2, (float) ((w - 8) * shown), 1, col(ac));
        }
        setSize(w, h);
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
        double x = lx, y = ly;
        if (y < btnY - 3 || y > btnY + 11) return false;
        int rel = (int) (x - btnX0);
        if (style.is("Compact")) return false;
        int btn = rel >= 0 && rel < 14 ? 0 : rel >= 14 && rel < 28 ? 1 : rel >= 28 && rel < 42 ? 2 : -1;
        if (btn < 0) return false;
        pressedButton = btn;
        pressedAt = System.currentTimeMillis();
        MusicService.command(btn == 0 ? "prev" : btn == 1 ? "toggle" : "next");
        return true;
    }
}
