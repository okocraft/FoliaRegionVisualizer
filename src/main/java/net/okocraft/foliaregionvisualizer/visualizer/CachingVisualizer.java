package net.okocraft.foliaregionvisualizer.visualizer;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.okocraft.foliaregionvisualizer.VisualizerSettings;
import net.okocraft.foliaregionvisualizer.data.RegionInfo;
import net.okocraft.foliaregionvisualizer.util.SectionUtils;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the markers of one world in sync with its Folia regions: every update puts the markers of the current regions
 * into the marker set and removes the markers of regions that no longer exist.
 */
public class CachingVisualizer {

    private final UUID worldUid;
    private final MarkerSet markerSet;
    private final VisualizerSettings settings;

    public CachingVisualizer(@NotNull UUID worldUid, @NotNull MarkerSet markerSet, @NotNull VisualizerSettings settings) {
        this.worldUid = worldUid;
        this.markerSet = markerSet;
        this.settings = settings;
    }

    public void update(@NotNull ServerLevel level) {
        Long2ObjectMap<RegionInfo> regions = RegionInfo.collectFrom(level);
        Map<String, Marker> markers = this.markerSet.getMarkers();
        Set<String> unusedMarkerIds = new HashSet<>(markers.keySet());

        int count = 0;
        int colorSteps = regions.size() + 1;

        for (Long2ObjectMap.Entry<RegionInfo> entry : regions.long2ObjectEntrySet()) {
            long regionId = entry.getLongKey();
            RegionInfo region = entry.getValue();

            String name = "region-" + regionId;
            Color color = region.spawn() ? this.settings.spawnColor() : colorOnHueCircle(this.settings.spawnColor(), (float) ++count / colorSteps);
            ShapeMarker.Builder builder = createMarkerBuilder(name, formatDetail(regionId, region), color);

            switch (this.settings.renderType()) {
                case OUTLINES -> {
                    List<SectionsToOutlines.Outline> outlines = SectionsToOutlines.merge(region.sectionKeys(), region.shift());
                    for (int i = 0; i < outlines.size(); i++) {
                        SectionsToOutlines.Outline outline = outlines.get(i);
                        // The builder is shared between the parts and holes(...) appends, so clear the previous holes.
                        builder.shape(new Shape(outline.shape()), 0).clearHoles().position(firstPoint(outline.shape()));
                        for (Vector2d[] hole : outline.holes()) {
                            builder.holes(new Shape(hole));
                        }
                        // The first part keeps the region's name, so a region in one piece keeps its marker id.
                        put(markers, unusedMarkerIds, i == 0 ? name : name + "_" + i, builder.build());
                    }
                }
                case GRIDS -> region.sectionKeys().forEach(sectionKey -> {
                    String sectionName = name + "_" + SectionUtils.getSectionX(sectionKey) + "_" + SectionUtils.getSectionZ(sectionKey);
                    Vector2d[] square = SectionsToGrids.square(sectionKey, region.shift());
                    builder.label(sectionName).detail(sectionName).shape(new Shape(square), 0).position(firstPoint(square));
                    put(markers, unusedMarkerIds, sectionName, builder.build());
                });
            }
        }

        unusedMarkerIds.forEach(markers::remove);
    }

    private void put(@NotNull Map<String, Marker> markers, @NotNull Set<String> unusedMarkerIds, @NotNull String name, @NotNull ShapeMarker marker) {
        String id = "!FoliaRegionVisualizer#" + this.worldUid + ":" + name;
        markers.put(id, marker);
        unusedMarkerIds.remove(id);
    }

    private @NotNull String formatDetail(long regionId, @NotNull RegionInfo region) {
        return this.settings.detailFormat()
                .replace("%region_id%", String.valueOf(regionId))
                .replace("%region_mspt%", BigDecimal.valueOf(region.mspt()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                .replace("%region_players%", String.valueOf(region.players()));
    }

    private static @NotNull Vector3d firstPoint(@NotNull Vector2d[] points) {
        return new Vector3d(points[0].getX(), 0.0, points[0].getY());
    }

    private static @NotNull ShapeMarker.Builder createMarkerBuilder(@NotNull String name, @NotNull String detail, @NotNull Color color) {
        return ShapeMarker.builder()
                .label(name)
                .detail(detail)
                .lineColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(color.getAlpha() + 0.3f, 1.0f)))
                .fillColor(color)
                .depthTestEnabled(false);
    }

    private static @NotNull Color colorOnHueCircle(@NotNull Color baseColor, float hueAngle) {
        float[] hsb = java.awt.Color.RGBtoHSB(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), null);
        float hue = (hsb[0] + hueAngle) % 1.0f;
        return new Color(java.awt.Color.HSBtoRGB(hue, hsb[1], hsb[2]), baseColor.getAlpha());
    }
}
