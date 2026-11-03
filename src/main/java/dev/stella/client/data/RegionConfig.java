package dev.stella.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stella.client.StellaClient;
import dev.stella.client.config.ConfigManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Region Map grid definition loaded from {@code config/stella/regions.json}.
 * <p>
 * DonutSMP's real region boundaries and grid numbers are NOT bundled because
 * they have not been verified. The default file is an explicitly flagged
 * placeholder ("placeholder": true) that users replace with verified values.
 * Each cell is {@code cellSize} blocks square, starting at (originX, originZ).
 */
public final class RegionConfig {
    public record Region(String name, int color) {}

    public static boolean placeholder = true;
    public static int originX = -60000, originZ = -35000, cellSize = 5000, columns = 24, rows = 14;
    public static List<Region> regions = new ArrayList<>();
    /** [row][col] region index (-1 = none). */
    public static int[][] cells = new int[0][0];
    /** [row][col] number printed in the cell. */
    public static int[][] numbers = new int[0][0];

    private RegionConfig() {}

    public static Path file() { return ConfigManager.root().resolve("regions.json"); }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) writeDefault(f);
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            placeholder = !o.has("placeholder") || o.get("placeholder").getAsBoolean();
            originX = o.get("originX").getAsInt();
            originZ = o.get("originZ").getAsInt();
            cellSize = Math.max(1, o.get("cellSize").getAsInt());
            columns = Math.max(1, Math.min(64, o.get("columns").getAsInt()));
            rows = Math.max(1, Math.min(64, o.get("rows").getAsInt()));
            regions = new ArrayList<>();
            for (var e : o.getAsJsonArray("regions")) {
                JsonObject r = e.getAsJsonObject();
                regions.add(new Region(r.get("name").getAsString(), parseColor(r.get("color").getAsString())));
            }
            cells = readGrid(o.getAsJsonArray("cells"), -1);
            numbers = o.has("numbers") ? readGrid(o.getAsJsonArray("numbers"), 0) : sequential();
        } catch (Exception e) {
            StellaClient.LOGGER.error("Invalid regions.json, using built-in placeholder", e);
            defaults();
        }
    }

    private static int[][] readGrid(JsonArray arr, int fill) {
        int[][] g = new int[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                g[r][c] = fill;
                if (arr != null && r < arr.size()) {
                    JsonArray row = arr.get(r).getAsJsonArray();
                    if (c < row.size()) g[r][c] = row.get(c).getAsInt();
                }
            }
        }
        return g;
    }

    private static int[][] sequential() {
        int[][] g = new int[rows][columns];
        int n = 1;
        for (int r = 0; r < rows; r++) for (int c = 0; c < columns; c++) g[r][c] = n++;
        return g;
    }

    private static int parseColor(String s) {
        long v = Long.parseLong(s.replace("#", ""), 16);
        if (s.replace("#", "").length() <= 6) v |= 0xFF000000L;
        return (int) v;
    }

    /** Placeholder layout: six vertical bands, one per legend entry. NOT real DonutSMP data. */
    private static void defaults() {
        placeholder = true;
        originX = -60000; originZ = -35000; cellSize = 5000; columns = 24; rows = 14;
        regions = new ArrayList<>(List.of(
                new Region("EU Central", 0xFF3B82F6),
                new Region("EU West", 0xFF60C3F0),
                new Region("NA East", 0xFF43B047),
                new Region("NA West", 0xFF9ACD32),
                new Region("Asia", 0xFFF2C230),
                new Region("Oceania", 0xFFF28C28)));
        cells = new int[rows][columns];
        for (int r = 0; r < rows; r++) for (int c = 0; c < columns; c++) cells[r][c] = Math.min(5, c * 6 / columns);
        numbers = sequential();
    }

    private static void writeDefault(Path f) {
        defaults();
        JsonObject o = new JsonObject();
        o.addProperty("_readme", "PLACEHOLDER LAYOUT - not real DonutSMP data. Replace originX/originZ/cellSize/columns/rows, "
                + "the region list, 'cells' (region index per cell, -1 = none) and 'numbers' (label per cell) with verified values, "
                + "then set \"placeholder\": false. Use the Region Map HUD's 'Reload regions.json' option to apply.");
        o.addProperty("placeholder", true);
        o.addProperty("originX", originX);
        o.addProperty("originZ", originZ);
        o.addProperty("cellSize", cellSize);
        o.addProperty("columns", columns);
        o.addProperty("rows", rows);
        JsonArray regs = new JsonArray();
        for (Region r : regions) {
            JsonObject ro = new JsonObject();
            ro.addProperty("name", r.name);
            ro.addProperty("color", String.format("#%08X", r.color));
            regs.add(ro);
        }
        o.add("regions", regs);
        JsonArray cellArr = new JsonArray();
        JsonArray numArr = new JsonArray();
        for (int r = 0; r < rows; r++) {
            JsonArray row = new JsonArray();
            JsonArray nrow = new JsonArray();
            for (int c = 0; c < columns; c++) { row.add(cells[r][c]); nrow.add(numbers[r][c]); }
            cellArr.add(row);
            numArr.add(nrow);
        }
        o.add("cells", cellArr);
        o.add("numbers", numArr);
        try { Files.writeString(f, ConfigManager.GSON.toJson(o)); } catch (Exception ignored) { }
    }

    public static int colOf(double x) { return (int) Math.floor((x - originX) / cellSize); }
    public static int rowOf(double z) { return (int) Math.floor((z - originZ) / cellSize); }
    public static boolean inGrid(int col, int row) { return col >= 0 && row >= 0 && col < columns && row < rows; }

    public static Region regionAt(int col, int row) {
        if (!inGrid(col, row)) return null;
        int idx = cells[row][col];
        return idx >= 0 && idx < regions.size() ? regions.get(idx) : null;
    }
}
