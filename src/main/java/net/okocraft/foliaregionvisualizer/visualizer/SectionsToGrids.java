package net.okocraft.foliaregionvisualizer.visualizer;

import com.flowpowered.math.vector.Vector2d;
import net.okocraft.foliaregionvisualizer.util.SectionUtils;
import org.jetbrains.annotations.NotNull;

final class SectionsToGrids {

    /**
     * Returns the four corners of a section in block coordinates, counterclockwise from its smallest corner.
     *
     * @param sectionKey the section key
     * @param shift      the shift to convert section coordinates to block coordinates
     * @return the corners of the section
     */
    static @NotNull Vector2d @NotNull [] square(long sectionKey, int shift) {
        int x = SectionUtils.getSectionX(sectionKey);
        int z = SectionUtils.getSectionZ(sectionKey);
        return new Vector2d[]{
                new Vector2d(x << shift, z << shift),
                new Vector2d(x + 1 << shift, z << shift),
                new Vector2d(x + 1 << shift, z + 1 << shift),
                new Vector2d(x << shift, z + 1 << shift),
        };
    }

    private SectionsToGrids() {
        throw new UnsupportedOperationException();
    }
}
