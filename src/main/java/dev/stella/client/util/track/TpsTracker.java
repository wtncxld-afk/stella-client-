package dev.stella.client.util.track;

/**
 * Estimates server TPS from world-time update packets. Vanilla servers send one
 * roughly every 20 ticks containing the total world time; dividing the world
 * tick delta by the wall-clock delta gives ticks per second. This is an
 * estimate — network jitter and non-vanilla server behaviour affect it, so the
 * value is only reported once enough samples exist.
 */
public final class TpsTracker {
    private static final double[] SAMPLES = new double[10];
    private static int count, index;
    private static long lastWorldTime = -1, lastNanos;

    private TpsTracker() {}

    public static void onTimeUpdate(long worldTime) {
        long now = System.nanoTime();
        if (lastWorldTime >= 0) {
            double secs = (now - lastNanos) / 1e9;
            long ticks = worldTime - lastWorldTime;
            if (secs > 0.2 && ticks > 0 && ticks < 2000) {
                SAMPLES[index] = Math.min(20.0, ticks / secs);
                index = (index + 1) % SAMPLES.length;
                count = Math.min(count + 1, SAMPLES.length);
            }
        }
        lastWorldTime = worldTime;
        lastNanos = now;
    }

    public static void reset() { count = 0; index = 0; lastWorldTime = -1; }

    /** @return estimated TPS, or -1 when there is not enough data yet. */
    public static double tps() {
        if (count < 3) return -1;
        double s = 0;
        for (int i = 0; i < count; i++) s += SAMPLES[i];
        return s / count;
    }

    /** Seconds since the last time packet (large values suggest lag or a stalled server). */
    public static double secondsSinceUpdate() {
        return lastWorldTime < 0 ? -1 : (System.nanoTime() - lastNanos) / 1e9;
    }
}
