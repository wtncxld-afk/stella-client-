package dev.stella.client.util;

/**
 * Frame-rate independent damped spring: moves toward its target with a little natural overshoot,
 * which reads as "springy" rather than the flat ease of plain smoothing.
 */
public final class Spring {
    private double value, velocity, target;
    private final double stiffness, damping;
    private long last = System.nanoTime();

    /** @param stiffness higher = faster; @param damping lower = bouncier (critical ~ 2*sqrt(stiffness)) */
    public Spring(double initial, double stiffness, double damping) {
        this.value = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public Spring target(double t) { this.target = t; return this; }
    public double getTarget() { return target; }
    public void snap(double v) { value = v; target = v; velocity = 0; }

    public double get() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - last) / 1_000_000_000.0) * AnimationSettings.speedMultiplier();
        last = now;
        // integrate in small steps for stability
        int steps = Math.max(1, (int) Math.ceil(dt / 0.004));
        double h = dt / steps;
        for (int i = 0; i < steps; i++) {
            double force = stiffness * (target - value) - damping * velocity;
            velocity += force * h;
            value += velocity * h;
        }
        if (Math.abs(target - value) < 0.0005 && Math.abs(velocity) < 0.001) { value = target; velocity = 0; }
        return value;
    }
}
