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
    private final NumberSetting rows = add(new NumberSetting("Visible Rows", "Entries shown before scrolling", 5, 1, 30, 1));
    private final NumberSetting widthSetting = add(new NumberSetting("Width", "Dropdown width", 190, 140, 400, 5));
    private boolean collapsed;
    private int scroll;

    public BlockNotifierHud() { super("Block Notifier HUD", "Dropdown of Block Notifier detections (interact with chat open)", 4, -84, false); }

    private BlockNotifier notifier() { return ModuleManager.get(BlockNotifier.class); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        Theme t = ThemeManager.get();
        BlockNotifier bn = notifier();
        List<BlockNotifier.Detection> list = bn.detections();
        int w = widthSetting.getInt();
        dev.stella.client.render.Render2D.shadow(ctx, 0, 0, w, height, 4, 3, col(0x60000000));
        dev.stella.client.render.Render2D.roundRect(ctx, 0, 0, w, height, 4, col(t.background()));
        dev.stella.client.render.Render2D.roundRect(ctx, 0, 0, w, 12, 4, col(t.header()));
        ctx.fill(0, 11, w, 12, col(t.accent()));
        String head = (collapsed ? "▶ " : "▼ ") + "BLOCK NOTIFIER  " + list.size() + (bn.isEnabled() ? "" : "  (off)");
        dev.stella.client.render.StellaFont.draw(ctx, head, 4, 2, col(t.text()), false);
        int y = 12;
        if (!collapsed) {
            int max = rows.getInt();
            scroll = MathHelper.clamp(scroll, 0, Math.max(0, list.size() - max));
            if (list.isEmpty()) {
                dev.stella.client.render.StellaFont.draw(ctx, "No detections yet", 3, y + 2, col(t.textDim()), false);
                y += 11;
            }
            for (int i = scroll; i < Math.min(list.size(), scroll + max); i++) {
                BlockNotifier.Detection d = list.get(i);
                if (i % 2 == 1) ctx.fill(1, y, w - 1, y + 20, col(t.row()));
                dev.stella.client.render.StellaFont.draw(ctx, trim(d.blockName(), w - 60), 3, y + 1, col(t.accentSoft()), false);
                String dist = d.distanceText();
                dev.stella.client.render.StellaFont.draw(ctx, dist, w - 3 - dev.stella.client.render.StellaFont.width(dist), y + 1, col(t.textDim()), false);
                String line2 = d.x() + " " + d.y() + " " + d.z() + "  " + d.dimensionShort() + "  " + d.blockId();
                ctx.getMatrices().push();
                ctx.getMatrices().translate(3, y + 11, 0);
                ctx.getMatrices().scale(0.75f, 0.75f, 1);
                dev.stella.client.render.StellaFont.draw(ctx, trim(line2, (int) ((w - 6) / 0.75f)), 0, 0, col(t.text()), false);
                ctx.getMatrices().pop();
                y += 20;
            }
            if (list.size() > max) {
                String more = (scroll + 1) + "-" + Math.min(list.size(), scroll + max) + " of " + list.size() + " (scroll)";
                dev.stella.client.render.StellaFont.draw(ctx, more, 3, y + 1, col(t.textDim()), false);
                y += 10;
            }
        }
        setSize(w, y);
    }

    private String trim(String s, int w) {
        return dev.stella.client.render.StellaFont.width(s) <= w ? s : dev.stella.client.render.StellaFont.trimToWidth(s, w - 6) + "..";
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
