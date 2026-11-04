package dev.stella.client.util;

/** Frame-rate independent exponential smoothing toward a target value. */
public final class Animation {
    private double value;
    private double target;
    private final double speed;
    private long last = System.nanoTime();

    public Animation(double initial, double speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public Animation target(double t) { this.target = t; return this; }

    public double get() {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1_000_000_000.0);
        last = now;
        double k = 1 - Math.exp(-speed * dt * AnimationSettings.speedMultiplier());
        value += (target - value) * k;
        if (Math.abs(target - value) < 0.001) value = target;
        return value;
    }

    public double getTarget() { return target; }
    public void snap(double v) { value = v; target = v; }

    public static double easeOutCubic(double t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - Math.pow(1 - t, 3);
    }

    /** Ease-out with a small overshoot past 1 before settling (springy entrance). */
    public static double easeOutBack(double t) {
        t = Math.max(0, Math.min(1, t));
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }
}
