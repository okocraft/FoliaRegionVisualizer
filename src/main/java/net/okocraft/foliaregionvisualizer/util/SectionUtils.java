package net.okocraft.foliaregionvisualizer.util;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;

public final class SectionUtils {

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
