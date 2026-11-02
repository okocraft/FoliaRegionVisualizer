package net.okocraft.foliaregionvisualizer;

import de.bluecolored.bluemap.api.math.Color;
import net.okocraft.foliaregionvisualizer.visualizer.RenderType;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * An immutable snapshot of config.yml.
 *
 * @param markerSetName  the label of the marker set in BlueMap's sidebar
 * @param defaultHidden  whether the marker set is hidden by default
 * @param disabledMaps   the ids of the BlueMap maps that do not show regions
 * @param detailFormat   the detail of a region marker, with the {@code %region_id%}, {@code %region_mspt%} and
 *                       {@code %region_players%} placeholders
 * @param spawnColor     the colour of the spawn region; other regions get colours around the hue circle from it
 * @param renderType     how regions are drawn
 */
public record VisualizerSettings(@NotNull String markerSetName, boolean defaultHidden, @NotNull Set<String> disabledMaps,
                                 @NotNull String detailFormat, @NotNull Color spawnColor, @NotNull RenderType renderType) {

    private static final Pattern RGBA = Pattern.compile("[0-9a-f]{8}");
    private static final Color DEFAULT_SPAWN_COLOR = new Color("#1e90ff1a");

    public static @NotNull VisualizerSettings load(@NotNull ConfigurationSection config) {
        String spawnColor = config.getString("spawn-color", "");

        return new VisualizerSettings(
                config.getString("markerset-name", "Folia Regions"),
                config.getBoolean("default-hidden", true),
                Set.copyOf(config.getStringList("disabled-maps")),
                config.getString("detail-format", "region-%region_id%"),
                RGBA.matcher(spawnColor).matches() ? new Color("#" + spawnColor) : DEFAULT_SPAWN_COLOR,
                config.getString("render-type", "").equalsIgnoreCase("grid") ? RenderType.GRIDS : RenderType.OUTLINES
        );
    }
}
