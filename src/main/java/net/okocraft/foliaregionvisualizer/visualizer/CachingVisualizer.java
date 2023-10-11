package net.okocraft.foliaregionvisualizer.visualizer;

import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.okocraft.foliaregionvisualizer.data.RegionInfo;
import net.okocraft.foliaregionvisualizer.util.SectionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CachingVisualizer {

    private final Long2ObjectMap<CachedData> cacheMap = new Long2ObjectOpenHashMap<>();

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

        var unusedRegionIds = new LongOpenHashSet(this.cacheMap.keySet());
        var unusedMarkers = new ObjectOpenHashSet<>(this.markerSet.getMarkers().keySet());

        int count = 0;
        int regionCount = map.size() + 1;

        for (var entry : map.long2ObjectEntrySet()) {
            long regionId = entry.getLongKey();
            var markerName = "region-" + regionId;

            count++;
            unusedRegionIds.remove(regionId);

            var info = entry.getValue();
            var cache = this.cacheMap.get(regionId);

            if (cache != null && cache.centerLocations.equals(info.getCenterLocations())) {
                var globalId = createGlobalId(markerName);

                switch (this.renderType) {
                    case OUTLINES -> unusedMarkers.remove(globalId);
                    case GRIDS -> unusedMarkers.removeIf(id -> id.startsWith(globalId));
                }

                continue;
            }

            var color = info.isSpawn() ? spawnColor : getColorFromHueCircle(spawnColor, (float) count / regionCount);

            var sections = discoverSections(info);
            var markerBuilder = createMarkerBuilder(markerName, color);

            switch (this.renderType) {
                case OUTLINES -> {
                    var globalId = createGlobalId(markerName);
                    unusedMarkers.remove(globalId);

                    var points = SectionsToOutlines.merge(sections);
                    var marker = markerBuilder.shape(new Shape(points), 0).position(points[0].toVector3(0.0));
                    this.markerSet.getMarkers().put(globalId, marker.build());
                }
                case GRIDS -> SectionsToGrids.render(
                        sections, markerName, markerBuilder,
                        (name, marker) -> {
                            var globalId = createGlobalId(name);
                            this.markerSet.getMarkers().put(globalId, marker);
                            unusedMarkers.remove(globalId);
                        }
                );
            }

            this.cacheMap.put(regionId, new CachedData(info.getCenterLocations()));
        }

        unusedRegionIds.forEach(this.cacheMap::remove);
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

    private static @NotNull LongSet discoverSections(@NotNull RegionInfo info) {
        var discoveredSectionKeys = new LongOpenHashSet(100);

        info.getCenterLocations().forEach(centerBlockPos -> {
            int centerSectionX = SectionUtils.getSectionX(centerBlockPos) >> 8;
            int centerSectionZ = SectionUtils.getSectionZ(centerBlockPos) >> 8;

            for (int offsetX = -4; offsetX <= 4; offsetX++) {
                for (int offsetZ = -4; offsetZ <= 4; offsetZ++) {
                    int sectionX1 = centerSectionX + offsetX;
                    int sectionZ1 = centerSectionZ + offsetZ;
                    long sectionKey = SectionUtils.toSectionKey(sectionX1, sectionZ1);

                    if (info.getRegionSectionKeys().contains(sectionKey)) {
                        discoveredSectionKeys.add(sectionKey);
                    }
                }
            }
        });

        return discoveredSectionKeys;
    }

    private static Color getColorFromHueCircle(Color baseColor, float hueAngle) {
        float[] hsbValues = java.awt.Color.RGBtoHSB(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), null);
        float hue = (hsbValues[0] + hueAngle) % 1.0f;
        return new Color(java.awt.Color.HSBtoRGB(hue, hsbValues[1], hsbValues[2]), baseColor.getAlpha());
    }

    private record CachedData(@NotNull LongList centerLocations) {
    }

    public enum RenderType {
        OUTLINES,
        GRIDS
    }
}
