package net.okocraft.foliaregionvisualizer.visualizer;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Shape;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.okocraft.foliaregionvisualizer.util.SectionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

final class SectionsToGrids {

    static void render(@NotNull LongSet sections, @NotNull String baseName, int shift, @NotNull ShapeMarker.Builder markerBuilder, @NotNull BiConsumer<String, ShapeMarker> markerAdder) {
        sections.forEach(sectionKey -> processSection(sectionKey, baseName, shift, markerBuilder, markerAdder));
    }

    private static void processSection(long sectionKey, @NotNull String baseName, int shift, @NotNull ShapeMarker.Builder markerBuilder, @NotNull BiConsumer<String, ShapeMarker> markerAdder) {
        int sectionX = SectionUtils.getSectionX(sectionKey);
        int sectionZ = SectionUtils.getSectionZ(sectionKey);

        String name = baseName + "_" + sectionX + "_" + sectionZ;
        Vector2d[] points = toGrid(sectionX, sectionZ, shift);
        Vector3d pos = new Vector3d(points[0].getX(),0.0,points[0].getY());
        ShapeMarker marker = markerBuilder.label(name).detail(name).shape(new Shape(points), 0f).position(pos).build();

        markerAdder.accept(name, marker);
    }

    @Contract("_, _, _ -> new")
    private static @NotNull Vector2d @NotNull [] toGrid(int sectionX, int sectionZ, int shift) {
        return new Vector2d[]{
                new Vector2d(sectionX << shift, sectionZ << shift),
                new Vector2d(sectionX + 1 << shift, sectionZ << shift),
                new Vector2d(sectionX + 1 << shift, sectionZ + 1 << shift),
                new Vector2d(sectionX << shift, sectionZ + 1 << shift),
        };
    }

    private SectionsToGrids() {
        throw new UnsupportedOperationException();
    }
}
