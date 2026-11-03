package dev.stella.client.scan;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * A set of block positions matching a predicate, maintained by {@link BlockScanner}
 * for chunks the client has actually received. Positions in chunks the server
 * never sent are, by definition, unknown.
 */
public class ScanTarget {
    public interface Listener {
        void onFound(BlockPos pos, BlockState state);
    }

    public final String name;
    private final Predicate<BlockState> filter;
    private final BooleanSupplier active;
    private final Long2ObjectOpenHashMap<LongSet> byChunk = new Long2ObjectOpenHashMap<>();
    private Listener listener;
    private int total;
    private int maxResults = 50_000;

    public ScanTarget(String name, Predicate<BlockState> filter, BooleanSupplier active) {
        this.name = name;
        this.filter = filter;
        this.active = active;
    }

    public ScanTarget listener(Listener l) { this.listener = l; return this; }
    public ScanTarget maxResults(int max) { this.maxResults = max; return this; }

    public boolean isActive() { return active.getAsBoolean(); }
    public boolean test(BlockState s) { return filter.test(s); }
    public int size() { return total; }

    synchronized void clearChunk(long chunk) {
        LongSet s = byChunk.remove(chunk);
        if (s != null) total -= s.size();
    }

    synchronized void clearAll() {
        byChunk.clear();
        total = 0;
    }

    synchronized void add(BlockPos pos, BlockState state) {
        if (total >= maxResults) return;
        long chunk = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        LongSet set = byChunk.computeIfAbsent(chunk, k -> new LongOpenHashSet());
        if (set.add(pos.asLong())) {
            total++;
            if (listener != null) listener.onFound(pos.toImmutable(), state);
        }
    }

    synchronized void remove(BlockPos pos) {
        long chunk = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        LongSet set = byChunk.get(chunk);
        if (set != null && set.remove(pos.asLong())) {
            total--;
            if (set.isEmpty()) byChunk.remove(chunk);
        }
    }

    /** Positions within {@code range} blocks (horizontal chunk filter + exact sphere), nearest first. */
    public synchronized List<BlockPos> near(BlockPos center, double range, int limit) {
        List<BlockPos> out = new ArrayList<>();
        int cr = (int) Math.ceil(range / 16.0) + 1;
        int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
        double r2 = range * range;
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                LongSet set = byChunk.get(ChunkPos.toLong(ccx + dx, ccz + dz));
                if (set == null) continue;
                for (long l : set) {
                    BlockPos p = BlockPos.fromLong(l);
                    if (p.getSquaredDistance(center) <= r2) out.add(p);
                }
            }
        }
        out.sort((a, b) -> Double.compare(a.getSquaredDistance(center), b.getSquaredDistance(center)));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** Requests a full rescan of all loaded chunks (call after the filter changed). */
    public void invalidate() { BlockScanner.rescan(this); }
}
