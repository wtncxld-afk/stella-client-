package dev.stella.client.modules.combat;

import dev.stella.client.config.ConfigManager;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.setting.TextSetting;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * Saves a report of the base around you to a text file: every container and spawner in loaded chunks
 * within range (types, counts and coordinates). Uses only what your client has loaded.
 */
public class BaseSaver extends Module {
    private final TextSetting name = add(new TextSetting("Base Name", "Name used in the file", "base", 40));
    private final NumberSetting radius = add(new NumberSetting("Radius", "Blocks around you to include", 64, 16, 256, 8).suffix("m"));
    private final BoolSetting save = add(new BoolSetting("Save Now", "Toggle to write the report to config/stella/bases/", false)).onChange(this::saveNow);

    public BaseSaver() { super("Base Saver", "Save a report of containers and spawners around you to a file", Category.DONUTSMP, true); }

    @Override protected boolean showTogglePopup() { return false; }

    public Path saveNow() {
        if (!save.isOn() && !forced) return null;
        forced = false;
        if (save.isOn()) save.set(false); // the onChange re-entry returns right away
        if (!inGame()) return null;
        try {
            int r = radius.getInt();
            BlockPos me = mc.player.getBlockPos();
            Map<String, Integer> counts = new TreeMap<>();
            StringBuilder list = new StringBuilder();
            int cr = (r >> 4) + 1;
            ChunkPos cp = mc.player.getChunkPos();
            for (int cx = cp.x - cr; cx <= cp.x + cr; cx++)
                for (int cz = cp.z - cr; cz <= cp.z + cr; cz++) {
                    WorldChunk ch = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (ch == null) continue;
                    for (BlockEntity be : ch.getBlockEntities().values()) {
                        BlockPos p = be.getPos();
                        if (p.getSquaredDistance(me) > (double) r * r) continue;
                        String id = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                        if (!(id.contains("chest") || id.contains("barrel") || id.contains("shulker") || id.contains("hopper") || id.contains("spawner")
                                || id.contains("dispenser") || id.contains("dropper") || id.contains("furnace") || id.contains("smoker"))) continue;
                        counts.merge(id, 1, Integer::sum);
                        list.append(id).append(' ').append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ()).append('\n');
                    }
                }
            StringBuilder out = new StringBuilder();
            out.append("Stella Base Saver - ").append(name.get()).append('\n');
            out.append("Saved ").append(LocalDateTime.now()).append(" around ").append(me.getX()).append(' ').append(me.getY()).append(' ').append(me.getZ())
                    .append(" (radius ").append(r).append(")\n\n");
            counts.forEach((k, v) -> out.append(v).append("x ").append(k).append('\n'));
            SpawnerFinder sf = ModuleManager.get(SpawnerFinder.class);
            if (sf != null && sf.isEnabled()) out.append("\nSpawner Finder:\n").append(sf.listText());
            out.append("\nBlocks:\n").append(list);
            Path dir = ConfigManager.root().resolve("bases");
            Files.createDirectories(dir);
            Path f = dir.resolve(name.get().replaceAll("[^A-Za-z0-9_-]", "_") + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt");
            Files.writeString(f, out.toString());
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            NotificationManager.post("Base Saver", "Saved " + total + " blocks to bases/" + f.getFileName(), Notification.Type.SUCCESS, 3500);
            return f;
        } catch (Exception e) {
            NotificationManager.post("Base Saver", "Save failed: " + e.getMessage(), Notification.Type.ALERT, 3500);
            return null;
        }
    }

    private boolean forced;

    /** Saves right away (smoke test / other code). */
    public Path saveNowForced() { forced = true; return saveNow(); }
}
