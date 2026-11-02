package net.okocraft.foliaregionvisualizer.visualizer;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import com.flowpowered.math.vector.Vector2d;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executable specification (test oracle) of {@link SectionsToOutlines#merge(LongSet, int)}.
 * <p>
 * The claim, preconditions and scope are recorded as FV-001 in {@code verification/README.md}.
 * This class only uses mathematical definitions (winding number, 8-connectivity, section boundary) and does not reuse
 * any logic of the production implementation. The result is a list of outlines (a shape and its holes, as passed to
 * BlueMap's {@code ShapeMarker}); together they must fill every input section exactly once and nothing else, and every
 * drawn edge must lie on the boundary of the sections.
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
     * @param outlines the result of {@link SectionsToOutlines#merge(LongSet, int)}
     * @return a violation message, or empty if the result satisfies the specification
     */
    static Optional<String> findViolation(LongSet sections, int shift, List<SectionsToOutlines.Outline> outlines) {
        if (outlines == null) {
            return Optional.of("result must not be null");
        }

        // P0: no outline for no section, and at least one outline otherwise.
        if (sections.isEmpty() != outlines.isEmpty()) {
            return Optional.of("expected " + (sections.isEmpty() ? "no outline" : "at least one outline") + " but got " + outlines.size());
        }

        // Rings of each outline in section units: index 0 is the shape, the others are holes.
        List<List<long[][]>> rings = new ArrayList<>(outlines.size());
        for (int o = 0; o < outlines.size(); o++) {
            SectionsToOutlines.Outline outline = outlines.get(o);
            if (outline == null || outline.shape() == null || outline.holes() == null) {
                return Optional.of("outline " + o + " must have a shape and a hole list");
            }

            List<Vector2d[]> polygons = new ArrayList<>();
            polygons.add(outline.shape());
            polygons.addAll(outline.holes());

            List<long[][]> converted = new ArrayList<>(polygons.size());
            for (int r = 0; r < polygons.size(); r++) {
                String name = "ring " + r + " of outline " + o;
                Optional<long[][]> ring = toSectionUnits(polygons.get(r), shift);
                if (ring.isEmpty()) {
                    return Optional.of(name + " is not a closed axis-aligned polygon on the section grid: " + Arrays.toString(polygons.get(r)));
                }
                converted.add(ring.get());
            }
            rings.add(converted);
        }

        long minX = Long.MAX_VALUE, minZ = Long.MAX_VALUE, maxX = Long.MIN_VALUE, maxZ = Long.MIN_VALUE;
        for (long key : sections) {
            minX = Math.min(minX, sectionX(key));
            minZ = Math.min(minZ, sectionZ(key));
            maxX = Math.max(maxX, sectionX(key) + 1);
            maxZ = Math.max(maxZ, sectionZ(key) + 1);
        }
        for (List<long[][]> outlineRings : rings) {
            for (long[][] ring : outlineRings) {
                for (long[] vertex : ring) {
                    minX = Math.min(minX, vertex[0]);
                    minZ = Math.min(minZ, vertex[1]);
                    maxX = Math.max(maxX, vertex[0]);
                    maxZ = Math.max(maxZ, vertex[1]);
                }
            }
        }

        // P3: a section is filled by an outline when it is inside the shape and inside none of its holes. "Inside" means
        //     a winding number of +1 or -1; any other non-zero value is a violation. A hole must be inside the shape, and
        //     the holes of an outline must not overlap. Every outline fills at least one section.
        // P4: every section in the input is filled by exactly one outline, and every other section by none.
        //     Both are checked for every section in the bounding box of the input and all rings, plus a margin of one.
        List<LongSet> filled = new ArrayList<>(rings.size());
        for (int o = 0; o < rings.size(); o++) {
            filled.add(new LongOpenHashSet());
        }

        for (long x = minX - 1; x <= maxX; x++) {
            for (long z = minZ - 1; z <= maxZ; z++) {
                int filling = 0;

                for (int o = 0; o < rings.size(); o++) {
                    List<long[][]> outlineRings = rings.get(o);
                    int shapeWinding = windingNumber(outlineRings.getFirst(), x, z);
                    if (Math.abs(shapeWinding) > 1) {
                        return Optional.of("winding number of the shape of outline " + o + " around section (" + x + ", " + z + ") is " + shapeWinding);
                    }

                    int coveringHoles = 0;
                    for (int r = 1; r < outlineRings.size(); r++) {
                        int holeWinding = windingNumber(outlineRings.get(r), x, z);
                        if (Math.abs(holeWinding) > 1) {
                            return Optional.of("winding number of ring " + r + " of outline " + o + " around section (" + x + ", " + z + ") is " + holeWinding);
                        }
                        if (holeWinding != 0) {
                            coveringHoles++;
                        }
                    }
                    if (coveringHoles > 1) {
                        return Optional.of("holes of outline " + o + " overlap at section (" + x + ", " + z + ")");
                    }
                    if (coveringHoles == 1 && shapeWinding == 0) {
                        return Optional.of("a hole of outline " + o + " is outside its shape at section (" + x + ", " + z + ")");
                    }

                    if (shapeWinding != 0 && coveringHoles == 0) {
                        filling++;
                        if (Integer.MIN_VALUE <= x && x <= Integer.MAX_VALUE && Integer.MIN_VALUE <= z && z <= Integer.MAX_VALUE) {
                            filled.get(o).add(key((int) x, (int) z));
                        }
                    }
                }

                boolean expectedInside = Integer.MIN_VALUE <= x && x <= Integer.MAX_VALUE
                        && Integer.MIN_VALUE <= z && z <= Integer.MAX_VALUE
                        && sections.contains(key((int) x, (int) z));
                int expected = expectedInside ? 1 : 0;
                if (filling != expected) {
                    return Optional.of("section (" + x + ", " + z + ") is filled by " + filling + " outline(s) but expected " + expected);
                }
            }
        }

        for (int o = 0; o < filled.size(); o++) {
            if (filled.get(o).isEmpty()) {
                return Optional.of("outline " + o + " fills no section");
            }
        }

        // P5: each outline fills exactly one 8-connected component of the input, i.e. the sections filled by each
        //     outline are 8-connected and there are as many outlines as components.
        for (int o = 0; o < filled.size(); o++) {
            if (!isEightConnected(filled.get(o))) {
                return Optional.of("sections filled by outline " + o + " are not 8-connected");
            }
        }
        int components = countEightConnectedComponents(sections);
        if (components != outlines.size()) {
            return Optional.of("expected one outline per 8-connected component (" + components + ") but got " + outlines.size());
        }

        // P8: no line is drawn anywhere but on the boundary of the sections. Splitting every edge of every ring into
        //     unit edges, each unit edge between a section in the input and a section not in the input appears exactly
        //     once, and no other unit edge appears. (BlueMap draws every edge of the shape and its holes.)
        Map<String, Integer> drawn = new HashMap<>();
        for (List<long[][]> outlineRings : rings) {
            for (long[][] ring : outlineRings) {
                for (int i = 0; i < ring.length; i++) {
                    long[] a = ring[i];
                    long[] b = ring[(i + 1) % ring.length];
                    long dx = Long.signum(b[0] - a[0]);
                    long dz = Long.signum(b[1] - a[1]);
                    for (long x = a[0], z = a[1]; x != b[0] || z != b[1]; x += dx, z += dz) {
                        drawn.merge(unitEdge(x, z, x + dx, z + dz), 1, Integer::sum);
                    }
                }
            }
        }

        for (Map.Entry<String, Integer> entry : drawn.entrySet()) {
            if (entry.getValue() != 1) {
                return Optional.of("unit edge " + entry.getKey() + " is drawn " + entry.getValue() + " times");
            }
        }

        int boundaryEdges = 0;
        for (long key : sections) {
            int x = sectionX(key);
            int z = sectionZ(key);
            String[] edges = {unitEdge(x, z, x + 1, z), unitEdge(x, z + 1, x + 1, z + 1), unitEdge(x, z, x, z + 1), unitEdge(x + 1, z, x + 1, z + 1)};
            long[][] neighbours = {{x, z - 1}, {x, z + 1}, {x - 1, z}, {x + 1, z}};
            for (int i = 0; i < 4; i++) {
                if (sections.contains(key((int) neighbours[i][0], (int) neighbours[i][1]))) {
                    continue;
                }
                boundaryEdges++;
                if (!drawn.containsKey(edges[i])) {
                    return Optional.of("boundary unit edge " + edges[i] + " is not drawn");
                }
            }
        }
        if (drawn.size() != boundaryEdges) {
            return Optional.of((drawn.size() - boundaryEdges) + " unit edge(s) are drawn inside or outside the sections");
        }

        return Optional.empty();
    }

    static Optional<String> findViolation(LongSet sections, int shift, SectionsToOutlines.Outline outline) {
        return findViolation(sections, shift, List.of(outline));
    }

    static SectionsToOutlines.Outline outline(Vector2d[] shape, Vector2d[]... holes) {
        return new SectionsToOutlines.Outline(shape, List.of(holes));
    }

    /**
     * P1 and P2 for one ring: at least 4 vertices, every vertex is a section corner (an integer multiple of 2^shift),
     * and every edge, including the closing edge, is axis-aligned and has non-zero length.
     */
    private static Optional<long[][]> toSectionUnits(Vector2d[] polygon, int shift) {
        if (polygon == null || polygon.length < 4) {
            return Optional.empty();
        }

        double unit = 1L << shift;
        long[][] vertices = new long[polygon.length][2];
        for (int i = 0; i < polygon.length; i++) {
            double x = polygon[i].getX() / unit;
            double z = polygon[i].getY() / unit;
            if (x != Math.rint(x) || z != Math.rint(z)) {
                return Optional.empty();
            }
            vertices[i][0] = (long) x;
            vertices[i][1] = (long) z;
        }

        for (int i = 0; i < vertices.length; i++) {
            long[] a = vertices[i];
            long[] b = vertices[(i + 1) % vertices.length];
            boolean horizontal = a[1] == b[1] && a[0] != b[0];
            boolean vertical = a[0] == b[0] && a[1] != b[1];
            if (!horizontal && !vertical) {
                return Optional.empty();
            }
        }

        return Optional.of(vertices);
    }

    private static String unitEdge(long x1, long z1, long x2, long z2) {
        boolean ordered = x1 < x2 || x1 == x2 && z1 < z2;
        return ordered ? "(" + x1 + ", " + z1 + ")-(" + x2 + ", " + z2 + ")" : "(" + x2 + ", " + z2 + ")-(" + x1 + ", " + z1 + ")";
    }

    /**
     * Winding number of the closed ring around the centre {@code (x + 1/2, z + 1/2)} of the section {@code (x, z)}.
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
