package dev.stella.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reads the sidebar scoreboard as plain text lines (top to bottom), as the server shows it. */
public final class ScoreboardUtil {
    private ScoreboardUtil() {}

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return out;
        Scoreboard sb = mc.world.getScoreboard();
        ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (obj == null) return out;
        List<ScoreboardEntry> entries = new ArrayList<>(sb.getScoreboardEntries(obj));
        entries.removeIf(ScoreboardEntry::hidden);
        entries.sort(Comparator.comparingInt(ScoreboardEntry::value).reversed());
        for (ScoreboardEntry e : entries) {
            Team team = sb.getScoreHolderTeam(e.owner());
            Text name = e.display() != null ? e.display() : Team.decorateName(team, Text.literal(e.owner()));
            out.add(name.getString().replaceAll("§.", "").trim());
        }
        return out;
    }

    /** Parses "$1.25M", "12,500", "3.4k" etc. Returns NaN when nothing numeric is found. */
    public static double parseAmount(String s) {
        var m = java.util.regex.Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbBtT])?").matcher(s);
        if (!m.find()) return Double.NaN;
        double v = Double.parseDouble(m.group(1).replace(",", ""));
        if (m.group(2) != null) switch (Character.toLowerCase(m.group(2).charAt(0))) {
            case 'k' -> v *= 1e3;
            case 'm' -> v *= 1e6;
            case 'b' -> v *= 1e9;
            case 't' -> v *= 1e12;
            default -> { }
        }
        return v;
    }

    public static String format(double v) {
        double a = Math.abs(v);
        if (a >= 1e12) return String.format("%.2fT", v / 1e12);
        if (a >= 1e9) return String.format("%.2fB", v / 1e9);
        if (a >= 1e6) return String.format("%.2fM", v / 1e6);
        if (a >= 1e3) return String.format("%.1fK", v / 1e3);
        return String.format("%.0f", v);
    }
}
