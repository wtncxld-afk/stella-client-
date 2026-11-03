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
 * The default 9x9 layout (cell numbers + region colours) was transcribed from
 * the user's reference screenshot of the DonutSMP region map. The geometry
 * (50,000-block cells centred on 0,0) is inferred from the single position
 * shown in that screenshot (X -129978, Z 215368 = region 107) and should be
 * verified in game; every value is editable in regions.json.
 * Each cell is {@code cellSize} blocks square, starting at (originX, originZ).
 */
public final class RegionConfig {
    public record Region(String name, int color) {}

    public static boolean placeholder = true;
    public static final int LAYOUT_VERSION = 2;
    public static int originX = -225000, originZ = -225000, cellSize = 50000, columns = 9, rows = 9;
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
            // replace the old untouched v1 placeholder with the screenshot-based layout
            if ((!o.has("layoutVersion") || o.get("layoutVersion").getAsInt() < LAYOUT_VERSION)
                    && o.has("placeholder") && o.get("placeholder").getAsBoolean()) {
                writeDefault(f);
                o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            }
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

    /** Layout transcribed from the reference screenshot (see class docs). */
    private static final int[][] DEFAULT_NUMBERS = {
            {82, 100, 101, 102, 103, 104, 105, 106, 91},
            {83, 44, 75, 42, 41, 40, 39, 38, 92},
            {84, 45, 14, 13, 12, 11, 10, 37, 93},
            {85, 46, 74, 3, 2, 1, 25, 36, 94},
            {86, 47, 72, 71, 5, 4, 24, 35, 95},
            {87, 51, 17, 9, 8, 7, 23, 34, 96},
            {88, 54, 18, 61, 62, 21, 22, 33, 97},
            {89, 26, 27, 28, 29, 30, 59, 32, 98},
            {90, 107, 108, 109, 110, 111, 112, 113, 99}};
    /** 0 EU Central, 1 EU West, 2 NA East, 3 NA West, 4 Asia, 5 Oceania. */
    private static final int[][] DEFAULT_CELLS = {
            {5, 3, 3, 3, 2, 2, 2, 2, 2},
            {5, 3, 3, 3, 2, 2, 2, 2, 2},
            {5, 3, 3, 3, 2, 2, 2, 2, 2},
            {5, 5, 5, 3, 2, 2, 2, 2, 2},
            {4, 4, 4, 4, 2, 2, 2, 2, 2},
            {4, 1, 1, 0, 0, 0, 0, 0, 2},
            {4, 1, 1, 0, 0, 0, 0, 0, 0},
            {0, 1, 0, 0, 0, 0, 0, 0, 0},
            {0, 1, 1, 1, 1, 1, 1, 1, 0}};

    private static void defaults() {
        placeholder = false;
        originX = -225000; originZ = -225000; cellSize = 50000; columns = 9; rows = 9;
        regions = new ArrayList<>(List.of(
                new Region("EU Central", 0xFF8A9868),
                new Region("EU West", 0xFF3A7356),
                new Region("NA East", 0xFF5A84B0),
                new Region("NA West", 0xFF3A5592),
                new Region("Asia", 0xFFC99A5E),
                new Region("Oceania", 0xFFC27632)));
        cells = new int[rows][columns];
        numbers = new int[rows][columns];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(DEFAULT_CELLS[r], 0, cells[r], 0, columns);
            System.arraycopy(DEFAULT_NUMBERS[r], 0, numbers[r], 0, columns);
        }
    }

    private static void writeDefault(Path f) {
        defaults();
        JsonObject o = new JsonObject();
        o.addProperty("_readme", "Cell numbers and region colours transcribed from a DonutSMP region-map screenshot. "
                + "Geometry (50,000-block cells centred on 0,0) is INFERRED from one position in that screenshot - verify in game "
                + "and edit originX/originZ/cellSize if needed. 'cells' = region index per cell (-1 = none), 'numbers' = label per cell. "
                + "Set \"placeholder\": true to show the * unverified marker. Use the Region Map HUD's 'Reload regions.json' to apply.");
        o.addProperty("layoutVersion", LAYOUT_VERSION);
        o.addProperty("placeholder", false);
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
