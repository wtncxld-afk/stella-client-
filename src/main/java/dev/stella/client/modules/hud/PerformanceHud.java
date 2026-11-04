package dev.stella.client.modules.hud;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/** FPS with a live frame-time graph, average / 1% low FPS and memory use. */
public class PerformanceHud extends HudElement {
    private final CardStyle card = card("Modern");
    private final BoolSetting graph = add(new BoolSetting("Frame Graph", "Frame-time graph (taller bars = slower frames)", true));
    private final BoolSetting memory = add(new BoolSetting("Memory", "Java memory in use", true));

    private final float[] frames = new float[120];
    private int head;
    private long last;
    private float avgFps, lowFps;
    private long statsAt;

    public PerformanceHud() { super("Performance HUD", "FPS, frame-time graph, 1% lows and memory", -4, 150, false); }

    private static int fpsColor(float fps) {
        if (fps >= 120) return 0xFF4DE38B;
        if (fps >= 60) return ColorUtil.lerp(0xFFFFC94D, 0xFF4DE38B, (fps - 60) / 60f);
        if (fps >= 30) return ColorUtil.lerp(0xFFFF5A5A, 0xFFFFC94D, (fps - 30) / 30f);
        return 0xFFFF5A5A;
    }

    @Override
    public void render(DrawContext ctx, float td, boolean editor) {
        long now = System.nanoTime();
        if (last != 0) { frames[head] = (now - last) / 1_000_000f; head = (head + 1) % frames.length; }
        last = now;
        if (System.currentTimeMillis() - statsAt > 500 || avgFps <= 0) {
            statsAt = System.currentTimeMillis();
            float sum = 0; int n = 0;
            float[] sorted = new float[frames.length];
            for (float f : frames) if (f > 0) sorted[n++] = f;
            for (int i = 0; i < n; i++) sum += sorted[i];
            java.util.Arrays.sort(sorted, 0, n);
            avgFps = n == 0 ? 0 : 1000f / (sum / n);
            float worst = n == 0 ? 0 : sorted[Math.max(0, n - 1 - n / 100)];
            lowFps = worst <= 0 ? 0 : 1000f / worst;
        }
        int w = 128, h = (graph.isOn() ? 52 : 22) + (memory.isOn() ? 12 : 0);
        card.draw(ctx, this, w, h);
        setSize(w + card.padLeft() + card.pad(), h + card.pad() * 2);
        int x = card.padLeft(), y = card.pad();
        int fps = mc.getCurrentFps();
        // big FPS number
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(1.6f, 1.6f);
        StellaFont.draw(ctx, String.valueOf(fps), 0, 0, col(fpsColor(fps)), true);
        m.popMatrix();
        Render2D.textScaled(ctx, "FPS", x + StellaFont.width(String.valueOf(fps)) * 1.6f + 3, y + 5, 0.7f, col(0xFF9A93A6), true);
        String stats = String.format("avg %.0f  1%% %.0f", avgFps, lowFps);
        Render2D.textScaled(ctx, stats, x + w - 4 - StellaFont.width(stats) * 0.7f, y + 1, 0.7f, col(0xFFCFD8E6), true);
        String ms = String.format("%.1f ms", frames[(head + frames.length - 1) % frames.length]);
        Render2D.textScaled(ctx, ms, x + w - 4 - StellaFont.width(ms) * 0.7f, y + 9, 0.7f, col(0xFF9A93A6), true);
        int gy = y + 20;
        if (graph.isOn()) {
            int gh = 28, gw = w - 4;
            Render2D.roundRect(ctx, x, gy, gw, gh, 3, col(0x40000000));
            // 60 FPS and 30 FPS guide lines
            float scale = gh / 50f; // 50 ms tall
            Render2D.rect(ctx, x + 1, gy + gh - 16.7f * scale, gw - 2, 0.5f, col(0x30FFFFFF));
            Render2D.rect(ctx, x + 1, gy + gh - 33.3f * scale, gw - 2, 0.5f, col(0x30FFFFFF));
            float bw = (gw - 2) / (float) frames.length;
            int acc = ThemeManager.get().accent();
            for (int i = 0; i < frames.length; i++) {
                float f = frames[(head + i) % frames.length];
                if (f <= 0) continue;
                float bh = Math.min(gh - 2, f * scale);
                int c = f > 33.3f ? 0xFFFF5A5A : f > 16.7f ? 0xFFFFC94D : ColorUtil.lerp(acc, 0xFF4DE38B, 0.5);
                Render2D.rect(ctx, x + 1 + i * bw, gy + gh - 1 - bh, Math.max(0.6f, bw), bh, col(ColorUtil.withAlpha(c, 0xD0)));
            }
            gy += gh + 4;
        }
        if (memory.isOn()) {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory(), max = rt.maxMemory();
            float f = (float) used / max;
            String mem = String.format("RAM %d / %d MB", used >> 20, max >> 20);
            Render2D.textScaled(ctx, mem, x, gy, 0.65f, col(0xFFCFD8E6), true);
            Render2D.roundRect(ctx, x + 70, gy + 1, w - 76, 4, 2, col(0x50000000));
            Render2D.hGradient(ctx, x + 70, gy + 1, (w - 76) * f, 4, col(0xFF4DE38B), col(ColorUtil.lerp(0xFF4DE38B, 0xFFFF5A5A, f)));
        }
    }
}
