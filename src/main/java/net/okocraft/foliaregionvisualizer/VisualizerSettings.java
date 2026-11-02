package net.okocraft.foliaregionvisualizer;

import de.bluecolored.bluemap.api.math.Color;
import net.okocraft.foliaregionvisualizer.visualizer.CachingVisualizer;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.regex.Pattern;

public record VisualizerSettings(@NotNull String markerSetName, boolean defaultHidden, @NotNull Set<String> disabledMaps,
                                 @NotNull String detailFormat, @NotNull Color spawnColor,
                                 @NotNull CachingVisualizer.RenderType renderType) {

    private static final Pattern RGBA_REGEX = Pattern.compile("[0-9a-f]{8}");

    public static @NotNull VisualizerSettings load(@NotNull ConfigurationSection config) {
        return new VisualizerSettings(
                config.getString("markerset-name", "Folia Regions"),
                config.getBoolean("default-hidden", true),
                Set.copyOf(config.getStringList("disabled-maps")),
                config.getString("detail-format", "region-%region_id%"),
                readSpawnColor(config),
                readRenderType(config)
        );
    }

    private static Color readSpawnColor(ConfigurationSection config) {
        String rgb = config.getString("spawn-color", "");
        return RGBA_REGEX.matcher(rgb).matches() ? new Color("#" + rgb) : new Color("#1e90ff1a");
    }

    private static CachingVisualizer.RenderType readRenderType(ConfigurationSection config) {
        String value = config.getString("render-type", "");

        if (value.equalsIgnoreCase("grid")) {
            return CachingVisualizer.RenderType.GRIDS;
        } else {
            return CachingVisualizer.RenderType.OUTLINES;
        }
    }
}
