package dev.stella.client.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/**
 * Base class of every configurable value. Settings serialise themselves to JSON
 * so the config system never needs to know concrete types.
 */
public abstract class Setting<T> {
    private final String name;
    private final String description;
    protected final T defaultValue;
    protected T value;
    private BooleanSupplier visibility = () -> true;
    private boolean hidden;
    private Runnable onChange;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = copy(defaultValue);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T get() { return value; }
    public T getDefault() { return defaultValue; }

    public void set(T newValue) {
        this.value = sanitize(newValue);
        if (onChange != null) onChange.run();
        SettingsDirty.mark();
    }

    public void reset() { set(copy(defaultValue)); }

    protected T sanitize(T v) { return v; }
    protected T copy(T v) { return v; }

    /** Settings that only make sense when another option is enabled. */
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier supplier) {
        this.visibility = supplier;
        @SuppressWarnings("unchecked") S self = (S) this;
        return self;
    }

    /** Hidden settings are persisted but never shown in the GUI (e.g. HUD x/y). */
    public <S extends Setting<T>> S hide() {
        this.hidden = true;
        @SuppressWarnings("unchecked") S self = (S) this;
        return self;
    }

    public <S extends Setting<T>> S onChange(Runnable r) {
        this.onChange = r;
        @SuppressWarnings("unchecked") S self = (S) this;
        return self;
    }

    public boolean isVisible() { return !hidden && visibility.getAsBoolean(); }
    public boolean isHidden() { return hidden; }

    public abstract JsonElement toJson();
    public abstract void fromJson(JsonElement element);
}
