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
 * The default 9x9 layout comes from two user-supplied references: an in-game
 * screenshot of the DonutSMP region map (cell IDs and colours) and a region
 * diagram spanning X -225k..+225k (region assignment, 1-81 order, and the
 * 50,000-block cell geometry centred on 0,0). Every value is editable in
 * regions.json.
 * Each cell is {@code cellSize} blocks square, starting at (originX, originZ).
 */
public final class RegionConfig {
    public record Region(String name, int color) {}

    public static boolean placeholder = true;
    public static final int LAYOUT_VERSION = 4;
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
            int ver = o.has("layoutVersion") ? o.get("layoutVersion").getAsInt() : 1;
            boolean generated = o.has("generatedDefault") && o.get("generatedDefault").getAsBoolean();
            boolean oldPlaceholder = ver < 2 && o.has("placeholder") && o.get("placeholder").getAsBoolean();
            if (ver < LAYOUT_VERSION && (oldPlaceholder || generated || ver == 2)) {
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
    /**
     * Category per cell, from the user's annotated map: 0 = Glitched (no RTP spots),
     * 1 = Good for media / baltop, 2 = Good for media / other bases, -1 = unmarked.
     */
    private static final int[][] DEFAULT_CELLS = {
            {-1, -1, 0, -1, 0, 0, -1, -1, -1},
            {-1, -1, -1, -1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, 2, -1, -1, -1, -1},
            {2, -1, -1, -1, 1, 1, -1, -1, -1},
            {-1, -1, -1, -1, 1, 2, -1, 2, -1},
            {2, -1, -1, -1, -1, -1, -1, -1, 0},
            {0, -1, -1, 2, 2, 1, 2, 2, 2},
            {0, -1, -1, 2, -1, -1, 2, 2, 2}};

    private static void defaults() {
        placeholder = false;
        originX = -225000; originZ = -225000; cellSize = 50000; columns = 9; rows = 9;
        regions = new ArrayList<>(List.of(
                new Region("Glitched (no rtp spots)", 0xFFA35CE0),
                new Region("Good for media / baltop", 0xFF3478E1),
                new Region("Good for media / other bases", 0xFFA6A6AE)));
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
        o.addProperty("_readme", "9x9 grid of 50,000-block cells spanning -225k..+225k on X and Z, from user-supplied DonutSMP references. "
                + "'numbers' = in-game cell IDs (transcribed from a low-resolution screenshot - correct any that differ), "
                + "'cells' = category per cell (0 glitched / no RTP spots, 1 good for media / baltop, 2 good for media / other bases, -1 unmarked). The HUD can also show plain 1-81 numbering. "
                + "Set \"placeholder\": true to show the * unverified marker. Use the Region Map HUD's 'Reload regions.json' to apply.");
        o.addProperty("layoutVersion", LAYOUT_VERSION);
        o.addProperty("generatedDefault", true);
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

    /** Plain row-major numbering (1..rows*columns), as used on region diagrams. */
    public static int sequentialNumber(int row, int col) { return row * columns + col + 1; }

    public static int colOf(double x) { return (int) Math.floor((x - originX) / cellSize); }
    public static int rowOf(double z) { return (int) Math.floor((z - originZ) / cellSize); }
    public static boolean inGrid(int col, int row) { return col >= 0 && row >= 0 && col < columns && row < rows; }

    public static Region regionAt(int col, int row) {
        if (!inGrid(col, row)) return null;
        int idx = cells[row][col];
        return idx >= 0 && idx < regions.size() ? regions.get(idx) : null;
    }
}
