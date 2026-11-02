package net.okocraft.foliaregionvisualizer.visualizer;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import com.flowpowered.math.vector.Vector2d;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Executable specification (test oracle) of {@link SectionsToOutlines#merge(LongSet, int)}.
 * <p>
 * The claim, preconditions and scope are recorded as FV-001 in {@code verification/README.md}.
 * This class only uses mathematical definitions (winding number, 8-connectivity) and does not reuse any logic of the
 * production implementation. The result is a list of polygons; together they must cover every input section exactly
 * once and nothing else.
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

    static boolean isEightConnected(LongSet sections) {
        return countEightConnectedComponents(sections) == 1;
    }

    /**
     * Number of 8-connected components (two sections are adjacent when their Chebyshev distance is 1).
     */
    static int countEightConnectedComponents(LongSet sections) {
        LongSet seen = new LongOpenHashSet();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        int components = 0;

        for (long start : sections) {
            if (!seen.add(start)) {
                continue;
            }
            components++;
            queue.add(start);

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
        }

        return components;
    }

    /**
     * Checks every postcondition of FV-001 and returns a description of the first violation, if any.
     *
     * @param sections the input of {@link SectionsToOutlines#merge(LongSet, int)}
     * @param shift    the shift passed to {@link SectionsToOutlines#merge(LongSet, int)}
     * @param polygons the result of {@link SectionsToOutlines#merge(LongSet, int)}
     * @return a violation message, or empty if the result satisfies the specification
     */
    static Optional<String> findViolation(LongSet sections, int shift, List<Vector2d[]> polygons) {
        if (polygons == null) {
            return Optional.of("result must not be null");
        }

        // P0: no polygon for no section, and at least one polygon otherwise.
        if (sections.isEmpty() != polygons.isEmpty()) {
            return Optional.of("expected " + (sections.isEmpty() ? "no polygon" : "at least one polygon") + " but got " + polygons.size());
        }

        double unit = 1L << shift;
        List<long[][]> lattices = new ArrayList<>(polygons.size());

        for (int p = 0; p < polygons.size(); p++) {
            Vector2d[] polygon = polygons.get(p);
            if (polygon == null || polygon.length < 4) {
                return Optional.of("polygon " + p + " must have at least 4 vertices: " + Arrays.toString(polygon));
            }

            long[][] vertices = new long[polygon.length][2];

            // P1: every vertex is a section corner, i.e. an integer multiple of 2^shift.
            for (int i = 0; i < polygon.length; i++) {
                double x = polygon[i].getX() / unit;
                double z = polygon[i].getY() / unit;
                if (x != Math.rint(x) || z != Math.rint(z)) {
                    return Optional.of("vertex " + i + " of polygon " + p + " is not a section corner: " + polygon[i]);
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
                    return Optional.of("edge " + i + " of polygon " + p + " is not a non-degenerate axis-aligned edge: " + Arrays.toString(a) + " -> " + Arrays.toString(b));
                }
            }

            lattices.add(vertices);
        }

        // P3: for each polygon there is an orientation w (+1 or -1) such that its winding number around the centre of
        //     every section is either 0 or w, and it covers (winding number w) at least one section.
        // P4: every section in the input is covered by exactly one polygon, and every other section by none.
        //     Both are checked for every section in the bounding box of the input and the polygons, plus a margin of one.
        long minX = Long.MAX_VALUE, minZ = Long.MAX_VALUE, maxX = Long.MIN_VALUE, maxZ = Long.MIN_VALUE;
        for (long key : sections) {
            minX = Math.min(minX, sectionX(key));
            minZ = Math.min(minZ, sectionZ(key));
            maxX = Math.max(maxX, sectionX(key) + 1);
            maxZ = Math.max(maxZ, sectionZ(key) + 1);
        }
        for (long[][] vertices : lattices) {
            for (long[] vertex : vertices) {
                minX = Math.min(minX, vertex[0]);
                minZ = Math.min(minZ, vertex[1]);
                maxX = Math.max(maxX, vertex[0]);
                maxZ = Math.max(maxZ, vertex[1]);
            }
        }

        int[] orientations = new int[lattices.size()];
        List<LongSet> covered = new ArrayList<>(lattices.size());
        for (int p = 0; p < lattices.size(); p++) {
            covered.add(new LongOpenHashSet());
        }

        for (long x = minX - 1; x <= maxX; x++) {
            for (long z = minZ - 1; z <= maxZ; z++) {
                int covering = 0;

                for (int p = 0; p < lattices.size(); p++) {
                    int winding = windingNumber(lattices.get(p), x, z);
                    if (winding == 0) {
                        continue;
                    }
                    if (winding != 1 && winding != -1 || orientations[p] != 0 && orientations[p] != winding) {
                        return Optional.of("winding number of polygon " + p + " around section (" + x + ", " + z + ") is " + winding
                                + " but expected 0 or " + (orientations[p] != 0 ? orientations[p] : "+1/-1"));
                    }
                    orientations[p] = winding;
                    covering++;
                    if (Integer.MIN_VALUE <= x && x <= Integer.MAX_VALUE && Integer.MIN_VALUE <= z && z <= Integer.MAX_VALUE) {
                        covered.get(p).add(key((int) x, (int) z));
                    }
                }

                boolean expectedInside = Integer.MIN_VALUE <= x && x <= Integer.MAX_VALUE
                        && Integer.MIN_VALUE <= z && z <= Integer.MAX_VALUE
                        && sections.contains(key((int) x, (int) z));
                int expected = expectedInside ? 1 : 0;
                if (covering != expected) {
                    return Optional.of("section (" + x + ", " + z + ") is covered by " + covering + " polygon(s) but expected " + expected);
                }
            }
        }

        for (int p = 0; p < orientations.length; p++) {
            if (orientations[p] == 0) {
                return Optional.of("polygon " + p + " covers no section");
            }
        }

        // P5: each polygon covers exactly one 8-connected component of the input, i.e. the sections covered by each
        //     polygon are 8-connected and there are as many polygons as components.
        for (int p = 0; p < covered.size(); p++) {
            if (!isEightConnected(covered.get(p))) {
                return Optional.of("sections covered by polygon " + p + " are not 8-connected");
            }
        }
        int components = countEightConnectedComponents(sections);
        if (components != polygons.size()) {
            return Optional.of("expected one polygon per 8-connected component (" + components + ") but got " + polygons.size());
        }

        return Optional.empty();
    }

    static Optional<String> findViolation(LongSet sections, int shift, Vector2d[] polygon) {
        return findViolation(sections, shift, List.<Vector2d[]>of(polygon));
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
