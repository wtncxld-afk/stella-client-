package dev.stella.client.modules.hud;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudElement;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.render.Glass;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * List of the modules you have on, right-aligned and sorted longest-first, with a theme gradient, an
 * accent bar and a slide-in / slide-out animation when you toggle something.
 */
public class ModuleListHud extends HudElement {
    private final ModeSetting colors = add(new ModeSetting("Colors", "Theme gradient, rainbow wave or plain white", "Theme", "Theme", "Rainbow", "White"));
    private final BoolSetting background = add(new BoolSetting("Background", "Dark strip behind each name", true));
    private final BoolSetting bar = add(new BoolSetting("Accent Bar", "Coloured bar on the right edge", true));
    private final BoolSetting hideHud = add(new BoolSetting("Hide HUD Modules", "Don't list HUD elements", true));
    private final BoolSetting hideClient = add(new BoolSetting("Hide Client Modules", "Don't list Client / Settings modules (GUI, menus, config...)", true));
    private final BoolSetting lowercase = add(new BoolSetting("Lowercase", "lowercase names", false));

    private final Map<Module, Float> anim = new HashMap<>();
    private long last;
    private int listHeight;

    public ModuleListHud() { super("Module List HUD", "List of enabled modules down the top-right corner", -4, 26, true); }

    @Override protected double defaultScale() { return 0.62; }

    /** Height of the list as drawn (scaled), for elements stacked below it. */
    public float scaledListHeight() { return listHeight * scale.getFloat(); }

    private boolean listed(Module m) {
        if (m == this || m.getName().equals("Test Environment")) return false;
        Category c = m.getCategory();
        if (hideHud.isOn() && (c == Category.HUD || m instanceof HudElement && c != Category.DONUTSMP)) return false;
        return !hideClient.isOn() || (c != Category.CLIENT && c != Category.SETTINGS);
    }

    private String name(Module m) { return lowercase.isOn() ? m.getName().toLowerCase(java.util.Locale.ROOT) : m.getName(); }

    @Override
    public void render(DrawContext ctx, float td, boolean editor) {
        long now = System.currentTimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        List<Module> shown = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (!listed(m)) continue;
            float target = m.isEnabled() ? 1f : 0f;
            float a = anim.getOrDefault(m, 0f);
            a += (target - a) * Math.min(1f, dt * 12f);
            if (Math.abs(target - a) < 0.01f) a = target;
            anim.put(m, a);
            if (a > 0.01f) shown.add(m);
        }
        if (shown.isEmpty() && editor) for (Module m : ModuleManager.all()) if (listed(m) && shown.size() < 5) { shown.add(m); anim.put(m, 1f); }
        shown.sort((x, y) -> Integer.compare(StellaFont.width(name(y)), StellaFont.width(name(x))));
        int rowH = 11, y = 0;
        // leave room for the music card below (and the screen edge): extra names fold into "+N more"
        int hidden = 0;
        if (mc.getWindow() != null && y() >= 0) {
            float avail = mc.getWindow().getScaledHeight() - y() - 6;
            MusicHud music = ModuleManager.get(MusicHud.class);
            if (music != null && music.followsList()) avail -= music.scaledHeight() + 4;
            int maxRows = Math.max(3, (int) (avail / (rowH * scale.getFloat())));
            if (shown.size() > maxRows) {
                hidden = shown.size() - (maxRows - 1);
                shown = new ArrayList<>(shown.subList(0, maxRows - 1));
            }
        }
        String more = hidden > 0 ? "+" + hidden + " more" : null;
        int maxW = 40;
        for (Module m : shown) maxW = Math.max(maxW, StellaFont.width(name(m)) + 8);
        if (more != null) maxW = Math.max(maxW, StellaFont.width(more) + 8);
        boolean glass = Glass.active();
        for (int i = 0; i < shown.size(); i++) {
            Module m = shown.get(i);
            float a = anim.get(m);
            float e = 1 - (1 - a) * (1 - a);
            String n = name(m);
            int w = StellaFont.width(n) + 8;
            float x = maxW - w + (1 - e) * (w + 6);           // slides in from the right edge
            int c = switch (colors.get()) {
                case "Rainbow" -> ColorUtil.fromHsb((float) (((now % 4000L) / 4000.0 + i * 0.06) % 1.0), 0.6f, 1f, 255);
                case "White" -> 0xFFF3F0F7;
                default -> ThemeManager.accentAt(i * 0.07);
            };
            if (background.isOn()) Render2D.rect(ctx, x, y, w, rowH * e, col(ColorUtil.withAlpha(glass ? 0x2A3448 : 0x0E0C12, (int) ((glass ? 0x70 : 0xA8) * e))));
            if (bar.isOn()) Render2D.rect(ctx, maxW, y, 1.5f, rowH * e, col(ColorUtil.withAlpha(c, (int) (255 * e))));
            if (e > 0.3f) StellaFont.draw(ctx, n, (int) (x + 4), y + 2, col(ColorUtil.withAlpha(c, (int) (255 * e))), true);
            y += Math.round(rowH * e);
        }
        if (more != null) {
            int w = StellaFont.width(more) + 8;
            if (background.isOn()) Render2D.rect(ctx, maxW - w, y, w, rowH, col(ColorUtil.withAlpha(glass ? 0x2A3448 : 0x0E0C12, glass ? 0x70 : 0xA8)));
            StellaFont.draw(ctx, more, maxW - w + 4, y + 2, col(0xFF9A93A8), true);
            y += rowH;
        }
        listHeight = Math.max(1, y);
        float oldRight = x() + scaledWidth();
        boolean wasSized = isSized();
        setSize(maxW + 2, Math.max(11, y));
        // keep the right edge pinned when names come and go (if the list sits on the right half)
        if (wasSized && !editor && x() >= 0 && mc.getWindow() != null && x() + scaledWidth() / 2 > mc.getWindow().getScaledWidth() / 2f)
            posX.set((double) (oldRight - scaledWidth()));
    }
}
