package net.okocraft.foliaregionvisualizer.visualizer;

import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Shape;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.okocraft.foliaregionvisualizer.util.SectionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

final class SectionsToGrids {

    static void render(@NotNull LongSet sections, @NotNull String baseName, @NotNull ShapeMarker.Builder markerBuilder, @NotNull BiConsumer<String, ShapeMarker> markerAdder) {
        sections.forEach(sectionKey -> processSection(sectionKey, baseName, markerBuilder, markerAdder));
    }

    private static void processSection(long sectionKey, @NotNull String baseName, @NotNull ShapeMarker.Builder markerBuilder, @NotNull BiConsumer<String, ShapeMarker> markerAdder) {
        int sectionX = SectionUtils.getSectionX(sectionKey);
        int sectionZ = SectionUtils.getSectionZ(sectionKey);

        var name = baseName + "_" + sectionX + "_" + sectionZ;
        var points = toGrid(sectionX, sectionZ);
        var marker = markerBuilder.label(name).detail(name).shape(new Shape(points), 0f).position(points[0].toVector3(0.0)).build();

        markerAdder.accept(name, marker);
    }

    @Contract("_, _ -> new")
    private static @NotNull Vector2d @NotNull [] toGrid(int sectionX, int sectionZ) {
        return new Vector2d[]{
                new Vector2d(sectionX << 8, sectionZ << 8),
                new Vector2d(sectionX + 1 << 8, sectionZ << 8),
                new Vector2d(sectionX + 1 << 8, sectionZ + 1 << 8),
                new Vector2d(sectionX << 8, sectionZ + 1 << 8),
        };
    }

    private SectionsToGrids() {
        throw new UnsupportedOperationException();
    }
}
