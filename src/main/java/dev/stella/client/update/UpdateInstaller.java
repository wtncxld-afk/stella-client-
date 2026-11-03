package dev.stella.client.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Stand-alone helper started by {@link AutoUpdater} when the game closes with a
 * verified update waiting. It runs from the downloaded jar (not the installed one),
 * waits for the Minecraft process to exit, copies the new jar into the mods folder
 * and deletes the old Stella jar. It only touches the two paths it is given.
 * <p>
 * Usage: {@code java -cp <staged.jar> dev.stella.client.update.UpdateInstaller <pid> <oldJar> <stagedJar> <targetJar>}
 */
public final class UpdateInstaller {
    private UpdateInstaller() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) return;
        long pid = Long.parseLong(args[0]);
        Path oldJar = Path.of(args[1]), staged = Path.of(args[2]), target = Path.of(args[3]);
        if (!oldJar.getFileName().toString().endsWith(".jar") || !target.getFileName().toString().endsWith(".jar")) return;
        if (!oldJar.getParent().equals(target.getParent())) return;

        ProcessHandle.of(pid).ifPresent(p -> p.onExit().join());
        // Windows can hold the file lock for a moment after the process exits.
        for (int attempt = 0; attempt < 60; attempt++) {
            try {
                Files.copy(staged, target, StandardCopyOption.REPLACE_EXISTING);
                if (!oldJar.equals(target)) Files.deleteIfExists(oldJar);
                return;
            } catch (Exception e) {
                Thread.sleep(1000);
            }
        }
    }
}
