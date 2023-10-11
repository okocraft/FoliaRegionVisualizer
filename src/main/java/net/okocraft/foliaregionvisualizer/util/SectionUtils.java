package net.okocraft.foliaregionvisualizer.util;

import io.papermc.paper.util.CoordinateUtils;

public final class SectionUtils {

    public static long toSectionKey(int sectionX, int sectionZ) {
        return CoordinateUtils.getChunkKey(sectionX, sectionZ);
    }

    public static int getSectionX(long sectionKey) {
        return CoordinateUtils.getChunkX(sectionKey);
    }

    public static int getSectionZ(long sectionKey) {
        return CoordinateUtils.getChunkZ(sectionKey);
    }

    private SectionUtils() {
        throw new UnsupportedOperationException();
    }
}
