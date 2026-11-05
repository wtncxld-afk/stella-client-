package dev.stella.client.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny always-on profiler: how much CPU time each Stella module / HUD element / render hook takes per frame.
 * Costs two {@link System#nanoTime()} calls per measured section (tens of nanoseconds). Results are averaged
 * over a rolling one-second window and shown in the Performance HUD ("Top Modules").
 */
public final class Profiler {
    private Profiler() {}

    private static final Map<String, long[]> CUR = new HashMap<>();   // name -> {nanos, calls}
    private static Map<String, Double> lastMsPerFrame = Map.of();
    private static int frames;
    private static long windowStart = System.nanoTime();
    private static double lastTotal;

    public static long start() { return System.nanoTime(); }

    public static void end(String name, long t0) {
        long d = System.nanoTime() - t0;
        long[] a = CUR.get(name);
        if (a == null) CUR.put(name, a = new long[2]);
        a[0] += d;
        a[1]++;
    }

    /** Call once per rendered frame. Rolls the window every second. */
    public static void frame() {
        frames++;
        long now = System.nanoTime();
        if (now - windowStart < 1_000_000_000L) return;
        Map<String, Double> out = new HashMap<>();
        double total = 0;
        for (Map.Entry<String, long[]> e : CUR.entrySet()) {
            double ms = e.getValue()[0] / 1e6 / Math.max(1, frames);
            out.put(e.getKey(), ms);
            total += ms;
        }
        lastMsPerFrame = out;
        lastTotal = total;
        CUR.clear();
        frames = 0;
        windowStart = now;
    }

    /** Total Stella CPU time per frame (ms) over the last window. */
    public static double totalMs() { return lastTotal; }

    /** The {@code n} heaviest sections over the last window, heaviest first. */
    public static List<Map.Entry<String, Double>> top(int n) {
        List<Map.Entry<String, Double>> l = new ArrayList<>(lastMsPerFrame.entrySet());
        l.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return l.size() > n ? l.subList(0, n) : l;
    }

    /** One-line report for logs: "total 1.23 ms | Custom Sky 0.80 | Radar HUD 0.12 ...". */
    public static String report(int n) {
        StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT, "total %.3f ms/frame", lastTotal));
        for (Map.Entry<String, Double> e : top(n)) sb.append(String.format(java.util.Locale.ROOT, " | %s %.3f", e.getKey(), e.getValue()));
        return sb.toString();
    }
}
