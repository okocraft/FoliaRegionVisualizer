package net.okocraft.foliaregionvisualizer.visualizer;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import com.flowpowered.math.vector.Vector2d;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Optional;

/**
 * Executable specification (test oracle) of {@link SectionsToOutlines#merge(LongSet, int)}.
 * <p>
 * The claim, preconditions and scope are recorded as FV-001 in {@code verification/README.md}.
 * This class only uses mathematical definitions (winding number, 8-connectivity) and does not reuse any logic of the
 * production implementation.
 */
final class OutlineSpec {

    private OutlineSpec() {
        throw new UnsupportedOperationException();
    }

    static long key(int sectionX, int sectionZ) {
        return CoordinateUtils.getChunkKey(sectionX, sectionZ);
    }

    static int sectionX(long key) {
        return CoordinateUtils.getChunkX(key);
    }

    static int sectionZ(long key) {
        return CoordinateUtils.getChunkZ(key);
    }

    static LongSet sections(int[]... coordinates) {
        LongSet sections = new LongOpenHashSet();
        for (int[] coordinate : coordinates) {
            sections.add(key(coordinate[0], coordinate[1]));
        }
        return sections;
    }

    /**
     * Precondition of FV-001: the set is non-empty and 8-connected (two sections are adjacent when their Chebyshev
     * distance is 1).
     */
    static boolean isEightConnected(LongSet sections) {
        if (sections.isEmpty()) {
            return false;
        }

        LongSet seen = new LongOpenHashSet();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long first = sections.iterator().nextLong();
        seen.add(first);
        queue.add(first);

        while (!queue.isEmpty()) {
            long current = queue.poll();
            int x = sectionX(current);
            int z = sectionZ(current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long neighbour = key(x + dx, z + dz);
                    if (sections.contains(neighbour) && seen.add(neighbour)) {
                        queue.add(neighbour);
                    }
                }
            }
        }

        return seen.size() == sections.size();
    }

    /**
     * Checks every postcondition of FV-001 and returns a description of the first violation, if any.
     *
     * @param sections the input of {@link SectionsToOutlines#merge(LongSet, int)}
     * @param shift    the shift passed to {@link SectionsToOutlines#merge(LongSet, int)}
     * @param polygon  the result of {@link SectionsToOutlines#merge(LongSet, int)}
     * @return a violation message, or empty if the result satisfies the specification
     */
    static Optional<String> findViolation(LongSet sections, int shift, Vector2d[] polygon) {
        if (polygon == null || polygon.length < 4) {
            return Optional.of("polygon must have at least 4 vertices: " + Arrays.toString(polygon));
        }

        double unit = 1L << shift;
        long[][] vertices = new long[polygon.length][2];

        // P1: every vertex is a section corner, i.e. an integer multiple of 2^shift.
        for (int i = 0; i < polygon.length; i++) {
            double x = polygon[i].getX() / unit;
            double z = polygon[i].getY() / unit;
            if (x != Math.rint(x) || z != Math.rint(z)) {
                return Optional.of("vertex " + i + " is not a section corner: " + polygon[i]);
            }
            vertices[i][0] = (long) x;
            vertices[i][1] = (long) z;
        }

        // P2: the closed polygon is rectilinear and has no zero-length edge.
        for (int i = 0; i < vertices.length; i++) {
            long[] a = vertices[i];
            long[] b = vertices[(i + 1) % vertices.length];
            boolean horizontal = a[1] == b[1] && a[0] != b[0];
            boolean vertical = a[0] == b[0] && a[1] != b[1];
            if (!horizontal && !vertical) {
                return Optional.of("edge " + i + " is not a non-degenerate axis-aligned edge: " + Arrays.toString(a) + " -> " + Arrays.toString(b));
            }
        }

        // P3: the winding number around the centre of a section is w if the section is in the input and 0 otherwise,
        //     where w is +1 or -1 (the orientation) and is the same for every section. This must hold for every
        //     section in the bounding box of the input and the polygon, plus a margin of one.
        long anySection = sections.iterator().nextLong();
        int orientation = windingNumber(vertices, sectionX(anySection), sectionZ(anySection));
        if (orientation != 1 && orientation != -1) {
            return Optional.of("winding number around section (" + sectionX(anySection) + ", " + sectionZ(anySection) + ") is " + orientation + " but expected +1 or -1");
        }

        long minX = Long.MAX_VALUE, minZ = Long.MAX_VALUE, maxX = Long.MIN_VALUE, maxZ = Long.MIN_VALUE;
        for (long key : sections) {
            minX = Math.min(minX, sectionX(key));
            minZ = Math.min(minZ, sectionZ(key));
            maxX = Math.max(maxX, sectionX(key) + 1);
            maxZ = Math.max(maxZ, sectionZ(key) + 1);
        }
        for (long[] vertex : vertices) {
            minX = Math.min(minX, vertex[0]);
            minZ = Math.min(minZ, vertex[1]);
            maxX = Math.max(maxX, vertex[0]);
            maxZ = Math.max(maxZ, vertex[1]);
        }

        for (long x = minX - 1; x <= maxX; x++) {
            for (long z = minZ - 1; z <= maxZ; z++) {
                boolean expectedInside = Integer.MIN_VALUE <= x && x <= Integer.MAX_VALUE
                        && Integer.MIN_VALUE <= z && z <= Integer.MAX_VALUE
                        && sections.contains(key((int) x, (int) z));
                int winding = windingNumber(vertices, x, z);
                int expected = expectedInside ? orientation : 0;
                if (winding != expected) {
                    return Optional.of("winding number around section (" + x + ", " + z + ") is " + winding + " but expected " + expected);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Winding number of the closed polygon around the centre {@code (x + 1/2, z + 1/2)} of the section {@code (x, z)}.
     * <p>
     * Counts signed crossings of vertical edges with the ray from the centre towards +X. Because every vertex is an
     * integer point, the centre is never on an edge and the ray never passes through a vertex. The sign of the result
     * depends on the orientation of the polygon.
     */
    static int windingNumber(long[][] vertices, long x, long z) {
        int winding = 0;
        for (int i = 0; i < vertices.length; i++) {
            long[] a = vertices[i];
            long[] b = vertices[(i + 1) % vertices.length];
            if (a[0] != b[0] || a[0] <= x) {
                continue;
            }
            long low = Math.min(a[1], b[1]);
            long high = Math.max(a[1], b[1]);
            if (low <= z && z < high) {
                winding += b[1] < a[1] ? 1 : -1;
            }
        }
        return winding;
    }
}
