package dev.stella.client.render;

import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.setting.Setting;

import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

/** Shared tracer appearance settings (Neon / Gradient / Solid, glow, fade-in, end marker). */
public final class TracerStyle {
    public static final String[] STYLES = {"Neon", "Gradient", "Solid"};

    public final ModeSetting style;
    public final NumberSetting glow;
    public final BoolSetting fadeIn;
    public final BoolSetting endMarker;

    /** @param add the owning module's {@code add} method; settings appear in the order added. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public TracerStyle(UnaryOperator<Setting<?>> add, BooleanSupplier visible) {
        style = (ModeSetting) add.apply(new ModeSetting("Tracer Style", "Neon: glowing line with a bright core. Gradient: soft glow. Solid: plain line",
                "Neon", STYLES).visibleWhen(visible));
        glow = (NumberSetting) add.apply(new NumberSetting("Tracer Glow", "Strength of the soft glow around tracers", 0.7, 0, 1, 0.05)
                .visibleWhen(() -> visible.getAsBoolean() && !style.is("Solid")));
        fadeIn = (BoolSetting) add.apply(new BoolSetting("Tracer Fade In", "Fade lines in from the start so the crosshair stays clear", true).visibleWhen(visible));
        endMarker = (BoolSetting) add.apply(new BoolSetting("Tracer End Marker", "Small glowing diamond on each target", true).visibleWhen(visible));
    }

    public Render3D.TracerLook look() {
        int mode = style.is("Neon") ? 0 : style.is("Gradient") ? 1 : 2;
        return new Render3D.TracerLook(mode, glow.getFloat(), fadeIn.isOn(), endMarker.isOn());
    }
}
