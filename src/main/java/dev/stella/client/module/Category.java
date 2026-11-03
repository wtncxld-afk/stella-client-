package dev.stella.client.module;

public enum Category {
    DONUTSMP("DonutSMP"),
    COMBAT("Combat"),
    MISC("Misc"),
    RENDER("Render"),
    VISUALS("Visuals"),
    CLIENT("Client"),
    WORLD("World"),
    HUD("HUD"),
    SETTINGS("Settings");

    public final String displayName;
    Category(String displayName) { this.displayName = displayName; }
}
