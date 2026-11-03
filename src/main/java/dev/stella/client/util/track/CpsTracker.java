package dev.stella.client.util.track;

import java.util.ArrayDeque;

public final class CpsTracker {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private CpsTracker() {}

    public static void click(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) LEFT.addLast(now);
        else if (button == 1) RIGHT.addLast(now);
    }

    private static int count(ArrayDeque<Long> q) {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
        return q.size();
    }

    public static int left() { return count(LEFT); }
    public static int right() { return count(RIGHT); }
}
