package net.okocraft.foliaregionvisualizer;

import de.bluecolored.bluemap.api.math.Color;
import net.okocraft.foliaregionvisualizer.visualizer.CachingVisualizer.RenderType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

class VisualizerSettingsTest {

    @Test
    void testBundledConfigIsLoaded() throws Exception {
        YamlConfiguration config;
        try (InputStream in = VisualizerSettingsTest.class.getResourceAsStream("/config.yml")) {
            Assertions.assertNotNull(in);
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        VisualizerSettings settings = VisualizerSettings.load(config);

        Assertions.assertEquals(
                new VisualizerSettings("Folia Regions", true, Set.of(), "region-%region_id%", new Color("#1e90ff4d"), RenderType.OUTLINES),
                settings
        );
    }

    @Test
    void testDefaultsAreUsedForAnEmptyConfig() {
        VisualizerSettings settings = VisualizerSettings.load(new YamlConfiguration());

        Assertions.assertEquals(
                new VisualizerSettings("Folia Regions", true, Set.of(), "region-%region_id%", new Color("#1e90ff1a"), RenderType.OUTLINES),
                settings
        );
    }

    @Test
    void testValuesAreRead() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("markerset-name", "Regions");
        config.set("default-hidden", false);
        config.set("disabled-maps", List.of("world_nether", "world_the_end"));
        config.set("detail-format", "%region_id%: %region_mspt% ms");
        config.set("spawn-color", "ff000080");
        config.set("render-type", "grid");

        VisualizerSettings settings = VisualizerSettings.load(config);

        Assertions.assertEquals(
                new VisualizerSettings("Regions", false, Set.of("world_nether", "world_the_end"), "%region_id%: %region_mspt% ms", new Color("#ff000080"), RenderType.GRIDS),
                settings
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1e90ff", "#1e90ff4d", "1e90ff4d00", "zz90ff4d"})
    void testInvalidSpawnColorFallsBackToTheDefault(String value) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("spawn-color", value);

        Assertions.assertEquals(new Color("#1e90ff1a"), VisualizerSettings.load(config).spawnColor());
    }

    @ParameterizedTest
    @ValueSource(strings = {"grid", "GRID", "Grid"})
    void testGridRenderTypeIsCaseInsensitive(String value) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("render-type", value);

        Assertions.assertEquals(RenderType.GRIDS, VisualizerSettings.load(config).renderType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"outline", "grids", "unknown"})
    void testOtherRenderTypesFallBackToOutlines(String value) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("render-type", value);

        Assertions.assertEquals(RenderType.OUTLINES, VisualizerSettings.load(config).renderType());
    }
}
