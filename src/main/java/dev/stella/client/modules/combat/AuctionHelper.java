package dev.stella.client.modules.combat;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.mixin.HandledScreenAccessor;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.TextSetting;
import dev.stella.client.util.ScoreboardUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Auction House helper: in auction menus, shows the price per item on every listing, marks the cheapest
 * of each item green, and alerts you when something on your watchlist is listed at or under your price.
 * It only reads the item tooltips the server already shows you.
 */
public class AuctionHelper extends Module {
    private final TextSetting titles = add(new TextSetting("Menu Titles", "Menus whose title contains one of these words", "auction, ah, market", 200));
    private final BoolSetting perItem = add(new BoolSetting("Price Per Item", "Show the price divided by the stack size", true));
    private final BoolSetting cheapest = add(new BoolSetting("Highlight Cheapest", "Green outline on the cheapest listing of each item", true));
    private final TextSetting watchlist = add(new TextSetting("Watchlist", "item=max price, e.g. spawner=2m, elytra=500k, totem=8k", "spawner=2m, elytra=500k", 400));

    private final Set<String> alerted = new HashSet<>();

    public AuctionHelper() {
        super("Auction Helper", "Price per item, cheapest listing and watchlist alerts in the Auction House", Category.DONUTSMP);
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof HandledScreen<?> hs)
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> render(hs, ctx));
        });
    }

    private boolean isAuction(Screen s) {
        String t = s.getTitle().getString().toLowerCase(Locale.ROOT);
        for (String k : titles.get().split(",")) {
            String w = k.trim().toLowerCase(Locale.ROOT);
            if (!w.isEmpty() && t.contains(w)) return true;
        }
        return false;
    }

    /** Prices are read once per stack (tooltips are expensive to build every frame). */
    private static final java.util.Map<ItemStack, Double> PRICE_CACHE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static double price(ItemStack st) {
        Double cached = PRICE_CACHE.get(st);
        if (cached != null) return cached;
        double p = readPrice(st);
        PRICE_CACHE.put(st, p);
        return p;
    }

    /** Listing price read from the tooltip ("Price: $1.2k", "$15,000", "Cost 3.4m"). NaN if none. */
    private static double readPrice(ItemStack st) {
        for (Text line : Screen.getTooltipFromItem(net.minecraft.client.MinecraftClient.getInstance(), st)) {
            String l = line.getString().toLowerCase(Locale.ROOT);
            if (l.contains("price") || l.contains("cost") || l.contains("bid") || l.contains("$")) {
                String part = l.contains("$") ? l.substring(l.indexOf('$') + 1) : l.replaceAll("^[^0-9]*", "");
                double v = ScoreboardUtil.parseAmount(part);
                if (!Double.isNaN(v)) return v;
            }
        }
        return Double.NaN;
    }

    private void render(HandledScreen<?> hs, DrawContext ctx) {
        if (!isEnabled() || !isAuction(hs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) hs;
        int ox = acc.stella$x(), oy = acc.stella$y();
        java.util.Map<String, Double> best = new java.util.HashMap<>();
        java.util.List<Object[]> listings = new java.util.ArrayList<>();
        for (Slot slot : hs.getScreenHandler().slots) {
            if (slot.inventory == hs.getScreenHandler().getSlot(hs.getScreenHandler().slots.size() - 1).inventory) continue; // skip your own inventory
            ItemStack st = slot.getStack();
            if (st.isEmpty()) continue;
            double p = price(st);
            if (Double.isNaN(p)) continue;
            double each = p / Math.max(1, st.getCount());
            String key = st.getName().getString().toLowerCase(Locale.ROOT);
            best.merge(key, each, Math::min);
            listings.add(new Object[]{slot, st, each, key});
        }
        for (Object[] o : listings) {
            Slot slot = (Slot) o[0];
            ItemStack st = (ItemStack) o[1];
            double each = (double) o[2];
            String key = (String) o[3];
            int x = ox + slot.x, y = oy + slot.y;
            Double max = watchMax(key);
            boolean watched = max != null && each <= max;
            if (watched) {
                Render2D.outline(ctx, x - 1, y - 1, 18, 18, 0xFFFFC94D);
                String id = key + "@" + (long) each;
                if (alerted.add(id)) NotificationManager.post("Auction Watchlist", st.getName().getString() + " listed for $" + ScoreboardUtil.format(each)
                        + (st.getCount() > 1 ? " each" : ""), Notification.Type.ALERT, 5000);
            } else if (cheapest.isOn() && best.get(key) == each && listings.stream().filter(l -> l[3].equals(key)).count() > 1) {
                Render2D.outline(ctx, x - 1, y - 1, 18, 18, 0xFF4DE38B);
            }
            if (perItem.isOn()) {
                String s = "$" + ScoreboardUtil.format(each);
                var m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(x + 8, y - 1);
                m.scale(0.5f, 0.5f);
                int w = StellaFont.width(s);
                ctx.fill(-w / 2 - 2, -1, w / 2 + 2, 8, 0xC0101018);
                StellaFont.draw(ctx, s, -w / 2, 0, watched ? 0xFFFFC94D : 0xFFE9E4F5, false);
                m.popMatrix();
            }
        }
    }

    private Double watchMax(String itemName) {
        for (String e : watchlist.get().split(",")) {
            int eq = e.indexOf('=');
            if (eq <= 0) continue;
            String k = e.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty() && itemName.contains(k)) {
                double v = ScoreboardUtil.parseAmount(e.substring(eq + 1));
                if (!Double.isNaN(v)) return v;
            }
        }
        return null;
    }
}
