package dev.stella.client.util;

import net.minecraft.client.option.SimpleOption;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lets modules temporarily override a vanilla option's value without writing it
 * to options.txt (the SimpleOption mixin consults this map in getValue()).
 * Disabling the module removes the override and vanilla's value returns.
 */
public final class OptionOverrides {
    private static final Map<SimpleOption<?>, Supplier<?>> MAP = new IdentityHashMap<>();
    private OptionOverrides() {}

    public static <T> void set(SimpleOption<T> option, Supplier<T> value) { MAP.put(option, value); }
    public static void clear(SimpleOption<?> option) { MAP.remove(option); }

    /** @return override value, or null when the option is not overridden. */
    public static Object get(SimpleOption<?> option) {
        if (MAP.isEmpty()) return null;
        Supplier<?> s = MAP.get(option);
        return s == null ? null : s.get();
    }
}
