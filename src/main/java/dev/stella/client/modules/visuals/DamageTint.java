package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * Recolours the red "hurt" flash on entities by rewriting the vanilla overlay
 * texture's hurt rows. Disabling restores the vanilla colour.
 */
public class DamageTint extends Module {
    private static final int VANILLA = 0xB2FF0000;
    private final ModeSetting mode = add(new ModeSetting("Mode", "Custom colour or no tint", "Custom", "Custom", "None")).onChange(this::apply);
    private final ColorSetting color = add(new ColorSetting("Color", "Hurt tint colour (alpha = strength)", 0xB2A855F7)).visibleWhen(() -> mode.is("Custom")).onChange(this::apply);

    private static NativeImageBackedTexture overlay;

    public DamageTint() { super("Damage Tint", "Change or remove the red hurt flash", Category.VISUALS); }

    /** Called from the OverlayTexture mixin once the texture exists. */
    public static void setOverlay(NativeImageBackedTexture tex) {
        overlay = tex;
        DamageTint m = dev.stella.client.module.ModuleManager.get(DamageTint.class);
        if (m != null && m.isEnabled()) m.apply();
    }

    @Override protected void onEnable() { apply(); }
    @Override protected void onDisable() { write(VANILLA); }

    private void apply() {
        if (!isEnabled()) return;
        write(mode.is("None") ? 0x00000000 : color.argb());
    }

    private static void write(int argb) {
        if (overlay == null) return;
        NativeImage img = overlay.getImage();
        if (img == null) return;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) img.setColorArgb(x, y, argb);
        overlay.upload();
    }
}
