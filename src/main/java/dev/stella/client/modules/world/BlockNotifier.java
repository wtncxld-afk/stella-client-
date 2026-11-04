package dev.stella.client.modules.world;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.scan.BlockScanner;
import dev.stella.client.scan.ScanTarget;
import dev.stella.client.setting.BlockListSetting;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ChatUtil;
import dev.stella.client.util.Dimensions;
import dev.stella.client.util.Sounds;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports selected blocks as their chunks arrive from the server. Only chunks
 * the client has actually received can be inspected. Each position is reported
 * once per session (per dimension) even when its chunk unloads and reloads.
 */
public class BlockNotifier extends Module {
    public record Detection(String blockName, String blockId, int x, int y, int z, String dimension, long time) {
        public String key() { return dimension + "|" + x + "|" + y + "|" + z; }
        public String dimensionShort() { return Dimensions.pretty(dimension); }
        public String distanceText() {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || !Dimensions.current().equals(dimension)) return "--";
            return (int) Math.sqrt(mc.player.squaredDistanceTo(x + 0.5, y + 0.5, z + 0.5)) + "m";
        }
    }

    private final BlockListSetting blocks = add(new BlockListSetting("Blocks", "Block IDs to monitor",
            "minecraft:spawner", "minecraft:ancient_debris"));
    private final NumberSetting maxEntries = add(new NumberSetting("Entry Limit", "Maximum stored detections (oldest removed)", 200, 10, 2000, 10));
    private final NumberSetting mergeRadius = add(new NumberSetting("Duplicate Radius", "Ignore same-block detections within N blocks of an existing one (0 = exact position only)", 0, 0, 16, 1));
    private final BoolSetting sound = add(new BoolSetting("Sound", "Play a sound on detection", true));
    private final BoolSetting toast = add(new BoolSetting("Notification", "Show a toast on detection", true));
    private final BoolSetting chat = add(new BoolSetting("Chat Message", "Also print detections in chat", false));
    private final BoolSetting clearOnDimension = add(new BoolSetting("Clear On World Change", "Clear the list when you change server or dimension", false));

    private final List<Detection> detections = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private final ScanTarget target;
    private long lastAlert;
    private int suppressed;
    private String lastDim = "";

    public BlockNotifier() {
        super("Block Notifier", "Alerts with exact coordinates when selected blocks load", Category.WORLD);
        target = BlockScanner.register(new ScanTarget("Block Notifier", s -> blocks.contains(s.getBlock()), this::isEnabled)
                .listener(this::found).maxResults(20000));
        blocks.setListener(target::invalidate);
    }

    @Override protected void onEnable() { target.invalidate(); }

    public List<Detection> detections() { return detections; }

    private void found(BlockPos pos, BlockState state) {
        String dim = Dimensions.current();
        String key = dim + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
        if (!seen.add(key)) return; // chunk reload: already reported
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        int r = mergeRadius.getInt();
        if (r > 0) {
            for (Detection d : detections) {
                if (d.blockId.equals(id) && d.dimension.equals(dim)
                        && Math.abs(d.x - pos.getX()) <= r && Math.abs(d.y - pos.getY()) <= r && Math.abs(d.z - pos.getZ()) <= r) return;
            }
        }
        Detection d = new Detection(state.getBlock().getName().getString(), id, pos.getX(), pos.getY(), pos.getZ(), dim, System.currentTimeMillis());
        detections.add(0, d);
        while (detections.size() > maxEntries.getInt()) detections.remove(detections.size() - 1);

        long now = System.currentTimeMillis();
        if (now - lastAlert > 750) {
            lastAlert = now;
            String extra = suppressed > 0 ? "  (+" + suppressed + " more)" : "";
            suppressed = 0;
            if (toast.isOn()) NotificationManager.post("Block Notifier", d.blockName + " at " + d.x + " " + d.y + " " + d.z + extra, Notification.Type.INFO);
            if (sound.isOn()) Sounds.ping(1.8f);
        } else {
            suppressed++;
        }
        if (chat.isOn()) ChatUtil.info(d.blockName + " (" + d.blockId + ") at " + d.x + " " + d.y + " " + d.z + " in " + d.dimensionShort());
    }

    @Override
    public void onTick() {
        String dim = mc.world == null ? "" : Dimensions.current() + "@" + System.identityHashCode(mc.getNetworkHandler());
        if (!dim.equals(lastDim)) {
            lastDim = dim;
            if (clearOnDimension.isOn()) clearAll();
        }
    }

    public void clearAll() {
        detections.clear();
        seen.clear();
        target.invalidate(); // currently loaded matches are reported again
    }

    public void remove(Detection d) { detections.remove(d); }

    public void copyCoords(Detection d) {
        mc.keyboard.setClipboard(d.x + " " + d.y + " " + d.z);
        NotificationManager.post("Copied", d.x + " " + d.y + " " + d.z, Notification.Type.SUCCESS, 1500);
    }

}
