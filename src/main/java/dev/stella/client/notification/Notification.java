package dev.stella.client.notification;

public record Notification(String title, String message, Type type, long createdAt, long durationMs) {
    public enum Type { INFO, SUCCESS, WARNING, ALERT, ENABLED, DISABLED }

    public float progress(long now) {
        return Math.min(1f, (now - createdAt) / (float) durationMs);
    }

    public boolean expired(long now) { return now - createdAt > durationMs; }
}
