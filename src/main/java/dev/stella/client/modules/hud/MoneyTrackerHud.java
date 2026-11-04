package dev.stella.client.modules.hud;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.TextSetting;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.ScoreboardUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayDeque;
import java.util.Locale;

/** Reads your money and shards from the sidebar and shows earnings per hour with a small graph. */
public class MoneyTrackerHud extends HudElement {
    private final CardStyle card = card("Modern");
    private final TextSetting moneyKeys = add(new TextSetting("Money Line", "Sidebar words that mark your money line", "money, balance, $", 120));
    private final TextSetting shardKeys = add(new TextSetting("Shards Line", "Sidebar words that mark your shards line", "shards", 120));

    private record Sample(long t, double money, double shards) {}
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private long lastSample, lastRead;
    private double money = Double.NaN, shards = Double.NaN;
    private Object lastWorld;

    public MoneyTrackerHud() { super("Money Tracker HUD", "Money and shards from the sidebar, earnings per hour and a graph", 4, 345, false); }

    @Override
    public void onTick() {
        if (mc.world != lastWorld) { lastWorld = mc.world; samples.clear(); }
        long now = System.currentTimeMillis();
        if (now - lastRead < 1000) return;
        lastRead = now;
        money = find(moneyKeys.get());
        shards = find(shardKeys.get());
        if (now - lastSample >= 5000 && !(Double.isNaN(money) && Double.isNaN(shards))) {
            lastSample = now;
            samples.addLast(new Sample(now, money, shards));
            while (samples.size() > 360) samples.removeFirst(); // 30 minutes
        }
    }

    private double find(String keys) {
        for (String line : ScoreboardUtil.lines()) {
            String l = line.toLowerCase(Locale.ROOT);
            for (String k : keys.split(",")) {
                String w = k.trim().toLowerCase(Locale.ROOT);
                if (w.isEmpty() || !l.contains(w)) continue;
                String after = l.contains(":") ? l.substring(l.indexOf(':') + 1) : l.contains("$") ? l.substring(l.indexOf('$') + 1) : l;
                double v = ScoreboardUtil.parseAmount(after);
                if (!Double.isNaN(v)) return v;
            }
        }
        return Double.NaN;
    }

    /** Test / preview hook: pretend the sidebar showed these values. */
    public void addSample(long t, double m, double s) { samples.addLast(new Sample(t, m, s)); money = m; shards = s; }

    private double perHour(boolean isMoney) {
        if (samples.size() < 2) return 0;
        Sample a = samples.peekFirst(), b = samples.peekLast();
        double dt = (b.t - a.t) / 3_600_000.0;
        if (dt <= 0) return 0;
        double d = isMoney ? b.money - a.money : b.shards - a.shards;
        return Double.isNaN(d) ? 0 : d / dt;
    }

    @Override
    public void render(DrawContext ctx, float td, boolean editor) {
        boolean has = !Double.isNaN(money) || !Double.isNaN(shards);
        if (!has && !editor) return;
        int w = 132, h = 58;
        card.draw(ctx, this, w, h);
        setSize(w + card.padLeft() + card.pad(), h + card.pad() * 2);
        int x = card.padLeft(), y = card.pad();
        int acc = ThemeManager.get().accent();
        StellaFont.draw(ctx, "$ " + (Double.isNaN(money) ? "-" : ScoreboardUtil.format(money)), x, y, col(0xFF7CE38B), true);
        double mph = perHour(true);
        String r = (mph >= 0 ? "+" : "") + ScoreboardUtil.format(mph) + "/h";
        Render2D.textScaled(ctx, r, x + w - 4 - StellaFont.width(r) * 0.75f, y + 1, 0.75f, col(mph >= 0 ? 0xFF7CE38B : 0xFFFF6B6B), true);
        StellaFont.draw(ctx, "✦ " + (Double.isNaN(shards) ? "-" : ScoreboardUtil.format(shards)), x, y + 11, col(0xFFC9A0FF), true);
        double sph = perHour(false);
        String r2 = (sph >= 0 ? "+" : "") + ScoreboardUtil.format(sph) + "/h";
        Render2D.textScaled(ctx, r2, x + w - 4 - StellaFont.width(r2) * 0.75f, y + 12, 0.75f, col(0xFFC9A0FF), true);
        // money graph (last 30 minutes)
        int gy = y + 24, gh = 30, gw = w - 4;
        Render2D.roundRect(ctx, x, gy, gw, gh, 3, col(0x40000000));
        if (samples.size() >= 2) {
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (Sample s : samples) if (!Double.isNaN(s.money)) { lo = Math.min(lo, s.money); hi = Math.max(hi, s.money); }
            if (hi <= lo) hi = lo + 1;
            int i = 0, n = samples.size();
            double px = 0, py = 0;
            for (Sample s : samples) {
                double sx = x + 2 + (gw - 4) * i / (double) (n - 1), sy = gy + gh - 3 - (gh - 6) * (s.money - lo) / (hi - lo);
                if (i > 0) Render2D.line(ctx, px, py, sx, sy, 1.2f, col(ColorUtil.lerp(acc, 0xFF7CE38B, i / (double) n)));
                px = sx; py = sy; i++;
            }
        } else Render2D.textScaled(ctx, "collecting...", x + 4, gy + 11, 0.7f, col(0x909A93A6), true);
    }
}
