package net.okocraft.foliaregionvisualizer.visualizer;

import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.okocraft.foliaregionvisualizer.data.RegionInfo;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CachingVisualizer {

    private final UUID worldUid;
    private final MarkerSet markerSet;
    private final Color spawnColor;
    private final RenderType renderType;

    public CachingVisualizer(@NotNull UUID worldUid, @NotNull MarkerSet markerSet, @NotNull Color spawnColor, @NotNull RenderType renderType) {
        this.worldUid = worldUid;
        this.markerSet = markerSet;
        this.spawnColor = spawnColor;
        this.renderType = renderType;
    }

    public void update(@NotNull ServerLevel level) {
        var map = RegionInfo.collectFrom(level);

        var unusedMarkers = new ObjectOpenHashSet<>(this.markerSet.getMarkers().keySet());

        int count = 0;
        int regionCount = map.size() + 1;

        for (var entry : map.long2ObjectEntrySet()) {
            long regionId = entry.getLongKey();
            var markerName = "region-" + regionId;

            count++;

            var info = entry.getValue();

            var color = info.isSpawn() ? spawnColor : getColorFromHueCircle(spawnColor, (float) count / regionCount);

            var sections = info.getRegionSectionKeys();
            var markerBuilder = createMarkerBuilder(markerName, color);

            switch (this.renderType) {
                case OUTLINES -> {
                    var globalId = createGlobalId(markerName);
                    unusedMarkers.remove(globalId);

                    var points = SectionsToOutlines.merge(sections, info.getShift());
                    var marker = markerBuilder.shape(new Shape(points), 0).position(points[0].toVector3(0.0));
                    this.markerSet.getMarkers().put(globalId, marker.build());
                }
                case GRIDS -> SectionsToGrids.render(
                        sections, markerName, info.getShift(), markerBuilder,
                        (name, marker) -> {
                            var globalId = createGlobalId(name);
                            this.markerSet.getMarkers().put(globalId, marker);
                            unusedMarkers.remove(globalId);
                        }
                );
            }

        }

        unusedMarkers.forEach(this.markerSet.getMarkers()::remove);
    }

    private @NotNull String createGlobalId(@NotNull String baseName) {
        return "!FoliaRegionVisualizer#" + this.worldUid + ":" + baseName;
    }

    private static @NotNull ShapeMarker.Builder createMarkerBuilder(@NotNull String name, @NotNull Color color) {
        return ShapeMarker.builder()
                .label(name)
                .detail(name)
                .lineColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(color.getAlpha() + 0.3f, 1.0f)))
                .fillColor(color)
                .depthTestEnabled(false);
    }

    private static Color getColorFromHueCircle(Color baseColor, float hueAngle) {
        float[] hsbValues = java.awt.Color.RGBtoHSB(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), null);
        float hue = (hsbValues[0] + hueAngle) % 1.0f;
        return new Color(java.awt.Color.HSBtoRGB(hue, hsbValues[1], hsbValues[2]), baseColor.getAlpha());
    }

    public enum RenderType {
        OUTLINES,
        GRIDS
    }
}
