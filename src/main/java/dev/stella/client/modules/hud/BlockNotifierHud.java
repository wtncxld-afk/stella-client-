package dev.stella.client.modules.hud;

import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudElement;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.world.BlockNotifier;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Movable dropdown listing Block Notifier detections. Open chat to interact:
 * click the header to collapse, left-click an entry to copy its coordinates,
 * right-click to create a waypoint, middle-click to remove it, scroll to page.
 * Right-click the header to clear everything.
 */
public class BlockNotifierHud extends HudElement {
    private final NumberSetting rows = add(new NumberSetting("Visible Rows", "Entries shown before scrolling", 8, 1, 30, 1));
    private final NumberSetting widthSetting = add(new NumberSetting("Width", "Dropdown width", 230, 140, 400, 5));
    private boolean collapsed;
    private int scroll;

    public BlockNotifierHud() { super("Block Notifier HUD", "Dropdown of Block Notifier detections (interact with chat open)", -4, 120, false); }

    private BlockNotifier notifier() { return ModuleManager.get(BlockNotifier.class); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        Theme t = ThemeManager.get();
        BlockNotifier bn = notifier();
        List<BlockNotifier.Detection> list = bn.detections();
        int w = widthSetting.getInt();
        ctx.fill(0, 0, w, 12, col(t.header()));
        ctx.fill(0, 11, w, 12, col(t.accent()));
        String head = (collapsed ? "▶ " : "▼ ") + "Block Notifier  " + list.size() + (bn.isEnabled() ? "" : "  (off)");
        ctx.drawText(mc.textRenderer, head, 3, 2, col(t.text()), false);
        int y = 12;
        if (!collapsed) {
            int max = rows.getInt();
            scroll = MathHelper.clamp(scroll, 0, Math.max(0, list.size() - max));
            if (list.isEmpty()) {
                ctx.fill(0, y, w, y + 11, col(t.background()));
                ctx.drawText(mc.textRenderer, "No detections yet", 3, y + 2, col(t.textDim()), false);
                y += 11;
            }
            for (int i = scroll; i < Math.min(list.size(), scroll + max); i++) {
                BlockNotifier.Detection d = list.get(i);
                ctx.fill(0, y, w, y + 20, col(i % 2 == 0 ? t.background() : t.row()));
                ctx.drawText(mc.textRenderer, trim(d.blockName(), w - 60), 3, y + 1, col(t.accentSoft()), false);
                String dist = d.distanceText();
                ctx.drawText(mc.textRenderer, dist, w - 3 - mc.textRenderer.getWidth(dist), y + 1, col(t.textDim()), false);
                String line2 = d.x() + " " + d.y() + " " + d.z() + "  " + d.dimensionShort() + "  " + d.blockId();
                ctx.getMatrices().push();
                ctx.getMatrices().translate(3, y + 11, 0);
                ctx.getMatrices().scale(0.75f, 0.75f, 1);
                ctx.drawText(mc.textRenderer, trim(line2, (int) ((w - 6) / 0.75f)), 0, 0, col(t.text()), false);
                ctx.getMatrices().pop();
                y += 20;
            }
            if (list.size() > max) {
                String more = (scroll + 1) + "-" + Math.min(list.size(), scroll + max) + " of " + list.size() + " (scroll)";
                ctx.fill(0, y, w, y + 10, col(t.header()));
                ctx.drawText(mc.textRenderer, more, 3, y + 1, col(t.textDim()), false);
                y += 10;
            }
        }
        setSize(w, y);
    }

    private String trim(String s, int w) {
        return mc.textRenderer.getWidth(s) <= w ? s : mc.textRenderer.trimToWidth(s, w - 6) + "..";
    }

    @Override
    public boolean onClick(double lx, double ly, int button) {
        BlockNotifier bn = notifier();
        if (ly < 12) {
            if (button == 1) bn.clearAll();
            else collapsed = !collapsed;
            return true;
        }
        if (collapsed) return false;
        int idx = scroll + (int) ((ly - 12) / 20);
        List<BlockNotifier.Detection> list = bn.detections();
        if (idx < 0 || idx >= list.size() || idx >= scroll + rows.getInt()) return false;
        BlockNotifier.Detection d = list.get(idx);
        switch (button) {
            case 0 -> bn.copyCoords(d);
            case 1 -> bn.createWaypoint(d);
            case 2 -> bn.remove(d);
            default -> { }
        }
        return true;
    }

    @Override
    public boolean onScroll(double lx, double ly, double amount) {
        scroll += amount > 0 ? -1 : 1;
        return true;
    }
}
