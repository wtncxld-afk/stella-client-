package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Render2D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

public class CustomCrosshair extends Module {
    private final ModeSetting preset = add(new ModeSetting("Preset", "Load a preset (then tweak freely)", "Custom",
            "Custom", "Classic", "Precision", "Stella Dot", "Circle", "Sniper")).onChange(this::applyPreset);
    private final ModeSetting style = add(new ModeSetting("Style", "Crosshair shape", "Cross", "Cross", "Dot", "Circle", "T-Shape", "Cross + Dot", "Square"));
    private final NumberSetting size = add(new NumberSetting("Size", "Arm length", 5, 1, 20, 0.5));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", "Line thickness", 1, 0.5, 5, 0.5));
    private final NumberSetting gap = add(new NumberSetting("Gap", "Space at the centre", 2, 0, 10, 0.5));
    private final ColorSetting color = add(new ColorSetting("Color", "Crosshair colour", 0xFFE9D5FF));
    private final NumberSetting opacity = add(new NumberSetting("Opacity", "", 1, 0.1, 1, 0.05));
    private final BoolSetting outline = add(new BoolSetting("Outline", "Dark outline for contrast", true));
    private final ColorSetting outlineColor = add(new ColorSetting("Outline Color", "", 0xC0000000)).visibleWhen(outline::isOn);
    private final BoolSetting thirdPerson = add(new BoolSetting("Show In Third Person", "Draw even when not in first person", false));

    public CustomCrosshair() { super("Custom Crosshair", "Configurable crosshair with presets and live preview", Category.VISUALS); }

    private void applyPreset() {
        switch (preset.get()) {
            case "Classic" -> { style.set("Cross"); size.set(5.0); thickness.set(1.0); gap.set(0.0); color.set(0xFFFFFFFF); }
            case "Precision" -> { style.set("Cross + Dot"); size.set(3.0); thickness.set(0.5); gap.set(2.0); color.set(0xFF7CF29A); }
            case "Stella Dot" -> { style.set("Dot"); size.set(2.0); thickness.set(1.0); gap.set(0.0); color.set(0xFFA855F7); }
            case "Circle" -> { style.set("Circle"); size.set(5.0); thickness.set(1.0); gap.set(0.0); color.set(0xFFD8B4FE); }
            case "Sniper" -> { style.set("T-Shape"); size.set(12.0); thickness.set(0.5); gap.set(3.0); color.set(0xFFF2777A); }
            default -> { }
        }
    }

    public boolean shouldReplace() {
        return isEnabled() && (thirdPerson.isOn() || mc.options.getPerspective().isFirstPerson());
    }

    public void render(DrawContext ctx, float cx, float cy) {
        int c = ColorUtil.mulAlpha(color.argb(), opacity.get());
        int oc = ColorUtil.mulAlpha(outlineColor.argb(), opacity.get());
        float s = size.getFloat(), t = thickness.getFloat(), g = gap.getFloat();
        if (outline.isOn()) shape(ctx, cx, cy, s, t, g, oc, 0.5f);
        shape(ctx, cx, cy, s, t, g, c, 0);
    }

    private void shape(DrawContext ctx, float cx, float cy, float s, float t, float g, int c, float o) {
        float h = t / 2f;
        switch (style.get()) {
            case "Dot" -> Render2D.rect(ctx, cx - s / 2 - o, cy - s / 2 - o, s + o * 2, s + o * 2, c);
            case "Circle" -> {
                int seg = 32;
                for (int i = 0; i < seg; i++) {
                    double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                    Render2D.line(ctx, cx + Math.cos(a1) * s, cy + Math.sin(a1) * s, cx + Math.cos(a2) * s, cy + Math.sin(a2) * s, t + o * 2, c);
                }
            }
            case "Square" -> {
                Render2D.rect(ctx, cx - s - o, cy - s - o, s * 2 + o * 2, t + o * 2, c);
                Render2D.rect(ctx, cx - s - o, cy + s - t - o, s * 2 + o * 2, t + o * 2, c);
                Render2D.rect(ctx, cx - s - o, cy - s - o, t + o * 2, s * 2 + o * 2, c);
                Render2D.rect(ctx, cx + s - t - o, cy - s - o, t + o * 2, s * 2 + o * 2, c);
            }
            default -> {
                boolean top = !style.is("T-Shape");
                Render2D.rect(ctx, cx - g - s - o, cy - h - o, s + o * 2, t + o * 2, c); // left
                Render2D.rect(ctx, cx + g - o, cy - h - o, s + o * 2, t + o * 2, c);     // right
                if (top) Render2D.rect(ctx, cx - h - o, cy - g - s - o, t + o * 2, s + o * 2, c); // top
                Render2D.rect(ctx, cx - h - o, cy + g - o, t + o * 2, s + o * 2, c);     // bottom
                if (style.is("Cross + Dot")) Render2D.rect(ctx, cx - h - o, cy - h - o, t + o * 2, t + o * 2, c);
            }
        }
    }

    // ---- live preview shown in the ClickGUI settings window
    @Override public int previewHeight() { return 44; }

    @Override
    public void renderPreview(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, 0xFF3B4A5C);
        ctx.fill(x, y + h / 2, x + w, y + h, 0xFF4E7A3A);
        render(ctx, x + w / 2f, y + h / 2f);
    }
}
