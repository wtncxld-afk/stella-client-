package dev.stella.client.scan;

import dev.stella.client.StellaClient;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared, incremental block scanner. Chunks are queued when the client receives
 * them and scanned on the client thread under a per-tick time budget; sections
 * are skipped via their palette ({@link ChunkSection#hasAny}) so empty sections
 * cost almost nothing. Block-update packets keep results current.
 */
public final class BlockScanner {
    private static final List<ScanTarget> TARGETS = new ArrayList<>();
    /** chunk key -> bitmask of target indices still to scan. */
    private static final Long2IntLinkedOpenHashMap QUEUE = new Long2IntLinkedOpenHashMap();
    private static final LongOpenHashSet LOADED = new LongOpenHashSet();
    private static final long BUDGET_NANOS = 3_000_000L;
    private static ClientWorld lastWorld;

    private BlockScanner() {}

    public static ScanTarget register(ScanTarget t) {
        if (TARGETS.size() >= 31) throw new IllegalStateException("Too many scan targets");
        TARGETS.add(t);
        return t;
    }

    public static void init() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            long key = chunk.getPos().toLong();
            LOADED.add(key);
            int mask = 0;
            for (int i = 0; i < TARGETS.size(); i++) {
                TARGETS.get(i).clearChunk(key);
                if (TARGETS.get(i).isActive()) mask |= 1 << i;
            }
            if (mask != 0) QUEUE.put(key, QUEUE.get(key) | mask);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            long key = chunk.getPos().toLong();
            LOADED.remove(key);
            QUEUE.remove(key);
            for (ScanTarget t : TARGETS) t.clearChunk(key);
        });
    }

    static void rescan(ScanTarget target) {
        int idx = TARGETS.indexOf(target);
        if (idx < 0) return;
        target.clearAll();
        if (!target.isActive()) return;
        // nearest chunks first, so things around the player show up immediately
        MinecraftClient mc = MinecraftClient.getInstance();
        int pcx = mc.player != null ? mc.player.getBlockX() >> 4 : 0, pcz = mc.player != null ? mc.player.getBlockZ() >> 4 : 0;
        long[] keys = LOADED.toLongArray();
        Long[] boxed = new Long[keys.length];
        for (int i = 0; i < keys.length; i++) boxed[i] = keys[i];
        java.util.Arrays.sort(boxed, java.util.Comparator.comparingLong((Long k) -> {
            long dx = ChunkPos.getPackedX(k) - pcx, dz = ChunkPos.getPackedZ(k) - pcz;
            return -(dx * dx + dz * dz); // farthest first; each one is moved to the front, so the nearest ends up first
        }));
        for (long key : boxed) QUEUE.putAndMoveToFirst(key, QUEUE.get(key) | (1 << idx));
    }

    public static int queued() { return QUEUE.size(); }

    /** Diagnostics for the smoke test: how the scanner sees one block. */
    public static String debug(BlockPos pos, ScanTarget target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return "chunk not loaded";
        int si = mc.world.getSectionIndex(pos.getY());
        ChunkSection sec = chunk.getSectionArray()[si];
        boolean palette = sec.hasAny(target::test);
        BlockState st = sec.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        return "sectionIndex=" + si + " sectionBaseY=" + (mc.world.sectionIndexToCoord(si) << 4) + " paletteHas=" + palette
                + " sectionState=" + st + " loadedKnown=" + LOADED.contains(chunk.getPos().toLong())
                + " inTarget=" + !target.near(pos, 1, 4).isEmpty() + " targetSize=" + target.size() + " targetIndex=" + TARGETS.indexOf(target);
    }
    public static int loadedChunks() { return LOADED.size(); }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world != lastWorld) {
            lastWorld = world;
            QUEUE.clear();
            LOADED.clear();
            for (ScanTarget t : TARGETS) t.clearAll();
        }
        if (world == null || QUEUE.isEmpty()) return;

        long start = System.nanoTime();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        while (!QUEUE.isEmpty() && System.nanoTime() - start < BUDGET_NANOS) {
            long key = QUEUE.firstLongKey();
            int mask = QUEUE.removeFirstInt();
            WorldChunk chunk = world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
            if (chunk == null) continue;
            try {
                scanChunk(world, chunk, mask, mutable);
            } catch (Throwable t) {
                StellaClient.reportError("chunk scan", t);
            }
        }
    }

    private static void scanChunk(ClientWorld world, WorldChunk chunk, int mask, BlockPos.Mutable mutable) {
        List<ScanTarget> active = new ArrayList<>();
        for (int i = 0; i < TARGETS.size(); i++) {
            if ((mask & (1 << i)) != 0 && TARGETS.get(i).isActive()) active.add(TARGETS.get(i));
        }
        if (active.isEmpty()) return;
        ChunkSection[] sections = chunk.getSectionArray();
        int baseX = chunk.getPos().getStartX();
        int baseZ = chunk.getPos().getStartZ();
        for (int si = 0; si < sections.length; si++) {
            ChunkSection section = sections[si];
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(s -> { for (ScanTarget t : active) if (t.test(s)) return true; return false; })) continue;
            int baseY = world.sectionIndexToCoord(si) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        for (ScanTarget t : active) {
                            if (t.test(state)) {
                                mutable.set(baseX + x, baseY + y, baseZ + z);
                                t.add(mutable, state);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Called from the ClientWorld mixin for every server block update. */
    public static void onBlockUpdate(BlockPos pos, BlockState state) {
        for (ScanTarget t : TARGETS) {
            if (!t.isActive()) continue;
            if (t.test(state)) t.add(pos, state);
            else t.remove(pos);
        }
    }
}
