package net.okocraft.foliaregionvisualizer.util;

import org.bukkit.World;

import java.nio.file.Path;

public final class BlueMapWorldId {
    // See: https://github.com/BlueMap-Minecraft/BlueMap/blob/73644bc160f4e7cb2a789a16a3b80bafe65d39ae/core/src/main/java/de/bluecolored/bluemap/core/world/World.java#L113-L121
    public static String create(Path path, World.Environment environment) {
        path = path.toAbsolutePath().normalize();

        Path workingDir = Path.of("").toAbsolutePath().normalize();
        if (path.startsWith(workingDir))
            path = workingDir.relativize(path);

        String dimension = switch (environment) {
            case NORMAL -> "minecraft:overworld";
            case NETHER -> "minecraft:the_nether";
            case THE_END -> "minecraft:the_end";
            default -> "";
        };

        return path + "#" + dimension;
    }
}
