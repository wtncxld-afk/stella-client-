package dev.stella.client.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Editable list of block identifiers (e.g. "minecraft:spawner"). Resolution to
 * {@link Block} instances is cached and rebuilt whenever the list changes.
 */
public class BlockListSetting extends Setting<List<String>> {
    private Set<Block> resolved;
    private Runnable listener;

    public BlockListSetting(String name, String description, String... defaults) {
        super(name, description, List.of(defaults));
    }

    @Override protected List<String> copy(List<String> v) { return new ArrayList<>(v); }

    @Override
    protected List<String> sanitize(List<String> v) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : v) {
            Identifier id = Identifier.tryParse(s.trim().toLowerCase(java.util.Locale.ROOT));
            if (id != null) out.add(id.toString());
        }
        resolved = null;
        return new ArrayList<>(out);
    }

    public void setListener(Runnable r) { this.listener = r; }

    @Override
    public void set(List<String> newValue) {
        super.set(newValue);
        resolved = null;
        if (listener != null) listener.run();
    }

    public boolean add(String id) {
        Identifier parsed = Identifier.tryParse(id.trim().toLowerCase(java.util.Locale.ROOT));
        if (parsed == null || !Registries.BLOCK.containsId(parsed)) return false;
        List<String> list = copy(value);
        if (list.contains(parsed.toString())) return false;
        list.add(parsed.toString());
        set(list);
        return true;
    }

    public void remove(String id) {
        List<String> list = copy(value);
        list.remove(id);
        set(list);
    }

    public Set<Block> blocks() {
        if (resolved == null) {
            Set<Block> s = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (String id : value) {
                Identifier parsed = Identifier.tryParse(id);
                if (parsed != null && Registries.BLOCK.containsId(parsed)) s.add(Registries.BLOCK.get(parsed));
            }
            resolved = s;
        }
        return resolved;
    }

    public boolean contains(Block b) { return blocks().contains(b); }

    @Override
    public JsonElement toJson() {
        JsonArray arr = new JsonArray();
        for (String s : value) arr.add(s);
        return arr;
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e == null || !e.isJsonArray()) return;
        List<String> list = new ArrayList<>();
        for (JsonElement el : e.getAsJsonArray()) list.add(el.getAsString());
        value = sanitize(list);
        resolved = null;
        if (listener != null) listener.run();
    }
}
