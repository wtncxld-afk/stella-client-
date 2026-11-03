package dev.stella.client.music;

/** Snapshot of what is playing. Times in milliseconds; {@code sampledAt} is System.currentTimeMillis(). */
public record MediaInfo(boolean active, String title, String artist, boolean playing,
                        long positionMs, long durationMs, String source, long sampledAt) {
    public static final MediaInfo NONE = new MediaInfo(false, "", "", false, 0, 0, "", 0);

    /** Position extrapolated to now while playing. */
    public long livePosition() {
        long p = positionMs + (playing ? System.currentTimeMillis() - sampledAt : 0);
        return durationMs > 0 ? Math.min(p, durationMs) : p;
    }
}
