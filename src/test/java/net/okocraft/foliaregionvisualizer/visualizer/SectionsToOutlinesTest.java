package net.okocraft.foliaregionvisualizer.visualizer;

import com.flowpowered.math.vector.Vector2d;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Tests for FV-001 ({@code verification/README.md}).
 * <p>
 * The specification itself is {@link OutlineSpec#findViolation(LongSet, int, List)}.
 */
class SectionsToOutlinesTest {

    /**
     * Shift used by {@code RegionInfo} with the default Folia grid exponent (4 + 4).
     */
    private static final int DEFAULT_SHIFT = 8;

    @Test
    void testSingleSectionBecomesItsFourCorners() {
        LongSet sections = OutlineSpec.sections(new int[]{0, 0});

        List<SectionsToOutlines.Outline> outlines = SectionsToOutlines.merge(sections, 0);

        Assertions.assertEquals(1, outlines.size());
        Assertions.assertArrayEquals(
                new Vector2d[]{new Vector2d(0, 0), new Vector2d(1, 0), new Vector2d(1, 1), new Vector2d(0, 1)},
                sortedCyclically(outlines.getFirst().shape())
        );
        Assertions.assertEquals(List.of(), outlines.getFirst().holes());
        assertSatisfiesSpecification(sections, 0, outlines);
    }

    @Test
    void testRingKeepsTheCentreSectionOutside() {
        LongSet sections = new LongOpenHashSet();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x != 1 || z != 1) {
                    sections.add(OutlineSpec.key(x, z));
                }
            }
        }

        List<SectionsToOutlines.Outline> outlines = SectionsToOutlines.merge(sections, DEFAULT_SHIFT);

        Assertions.assertEquals(1, outlines.size());
        Assertions.assertEquals(1, outlines.getFirst().holes().size());
        assertSatisfiesSpecification(sections, DEFAULT_SHIFT, outlines);
    }

    @Test
    void testSectionsTouchingOnlyAtCornersAreSeparateOutlines() {
        // Regression for BlueMap: as one shape whose hole (1, 1) touches the outer ring at all four corners, three.js
        // left part of the region unfilled.
        // . # . .
        // . . # .
        // . # . #
        // # . # .
        LongSet sections = OutlineSpec.sections(
                new int[]{1, 0}, new int[]{2, 1}, new int[]{1, 2}, new int[]{3, 2}, new int[]{0, 3}, new int[]{2, 3}
        );

        List<SectionsToOutlines.Outline> outlines = SectionsToOutlines.merge(sections, 0);

        Assertions.assertEquals(6, outlines.size());
        outlines.forEach(outline -> Assertions.assertEquals(List.of(), outline.holes()));
        assertSatisfiesSpecification(sections, 0, outlines);
    }

    @Test
    void testHoleTouchingTheOuterBoundaryAtACornerIsPartOfTheOuterRing() {
        // # # #
        // # . #
        // # # .
        LongSet sections = OutlineSpec.sections(
                new int[]{0, 0}, new int[]{1, 0}, new int[]{2, 0}, new int[]{0, 1}, new int[]{2, 1}, new int[]{0, 2}, new int[]{1, 2}
        );

        List<SectionsToOutlines.Outline> outlines = SectionsToOutlines.merge(sections, 0);

        Assertions.assertEquals(1, outlines.size());
        Assertions.assertEquals(List.of(), outlines.getFirst().holes());
        assertSatisfiesSpecification(sections, 0, outlines);
    }

    @Test
    void testDiagonallyTouchingSectionsAreSeparateOutlines() {
        LongSet sections = OutlineSpec.sections(new int[]{0, 0}, new int[]{1, 1});

        List<SectionsToOutlines.Outline> outlines = SectionsToOutlines.merge(sections, DEFAULT_SHIFT);

        Assertions.assertEquals(2, outlines.size());
        assertSatisfiesSpecification(sections, DEFAULT_SHIFT, outlines);
    }

    @Test
    void testSeparatedSectionsAreAllCovered() {
        // Regression: the second section was dropped when the sections were not connected.
        LongSet sections = OutlineSpec.sections(new int[]{0, 0}, new int[]{2, 0});

        List<SectionsToOutlines.Outline> outlines = SectionsToOutlines.merge(sections, 0);

        Assertions.assertEquals(2, outlines.size());
        assertSatisfiesSpecification(sections, 0, outlines);
    }

    @Test
    void testIslandInsideAHoleIsCoveredSeparately() {
        // 5x5 ring with a 3x3 hole, and one section in the middle of the hole.
        LongSet sections = new LongOpenHashSet();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (x == 0 || z == 0 || x == 4 || z == 4) {
                    sections.add(OutlineSpec.key(x, z));
                }
            }
        }
        sections.add(OutlineSpec.key(2, 2));

        List<SectionsToOutlines.Outline> outlines = SectionsToOutlines.merge(sections, DEFAULT_SHIFT);

        Assertions.assertEquals(2, outlines.size());
        Assertions.assertEquals(1, outlines.get(0).holes().size());
        Assertions.assertEquals(List.of(), outlines.get(1).holes());
        assertSatisfiesSpecification(sections, DEFAULT_SHIFT, outlines);
    }

    @Test
    void testFirstOutlineCoversTheSmallestSection() {
        // The first outline gets the region's marker id, so the order must not depend on hash iteration order.
        LongSet sections = OutlineSpec.sections(new int[]{10, -5}, new int[]{-3, 7}, new int[]{-3, 4}, new int[]{0, 0});

        List<SectionsToOutlines.Outline> outlines = SectionsToOutlines.merge(sections, 0);

        Assertions.assertEquals(4, outlines.size());
        Assertions.assertTrue(OutlineSpec.findViolation(OutlineSpec.sections(new int[]{-3, 4}), 0, outlines.getFirst()).isEmpty());
        Assertions.assertTrue(OutlineSpec.findViolation(OutlineSpec.sections(new int[]{-3, 7}), 0, outlines.get(1)).isEmpty());
        Assertions.assertTrue(OutlineSpec.findViolation(OutlineSpec.sections(new int[]{0, 0}), 0, outlines.get(2)).isEmpty());
        Assertions.assertTrue(OutlineSpec.findViolation(OutlineSpec.sections(new int[]{10, -5}), 0, outlines.get(3)).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            // section coordinates on both sides of the origin and close to the world border (30,000,000 blocks)
            "-1, -1",
            "-117188, -117188",
            "117187, 117187",
            "-117188, 117187",
    })
    void testSectionsAtWorldExtremesWithDefaultShift(int sectionX, int sectionZ) {
        LongSet sections = OutlineSpec.sections(
                new int[]{sectionX, sectionZ},
                new int[]{sectionX - 1, sectionZ},
                new int[]{sectionX, sectionZ - 1}
        );

        List<SectionsToOutlines.Outline> outlines = SectionsToOutlines.merge(sections, DEFAULT_SHIFT);

        assertSatisfiesSpecification(sections, DEFAULT_SHIFT, outlines);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 6, 8, 10})
    void testShiftOnlyScalesTheUnshiftedOutline(int shift) {
        LongSet sections = OutlineSpec.sections(
                new int[]{0, 0}, new int[]{1, 0}, new int[]{2, 0},
                new int[]{0, 1}, new int[]{2, 1},
                new int[]{0, 2}, new int[]{1, 2}, new int[]{2, 2}, new int[]{3, 3}
        );
        List<SectionsToOutlines.Outline> unshifted = SectionsToOutlines.merge(new LongOpenHashSet(sections), 0);

        List<SectionsToOutlines.Outline> shifted = SectionsToOutlines.merge(sections, shift);

        Assertions.assertEquals(unshifted.size(), shifted.size());
        for (int o = 0; o < unshifted.size(); o++) {
            Assertions.assertArrayEquals(scaled(unshifted.get(o).shape(), shift), shifted.get(o).shape());
            Assertions.assertEquals(unshifted.get(o).holes().size(), shifted.get(o).holes().size());
            for (int h = 0; h < unshifted.get(o).holes().size(); h++) {
                Assertions.assertArrayEquals(scaled(unshifted.get(o).holes().get(h), shift), shifted.get(o).holes().get(h));
            }
        }
    }

    @Test
    void testEmptySectionsProduceNoOutline() {
        Assertions.assertEquals(List.of(), SectionsToOutlines.merge(new LongOpenHashSet(), DEFAULT_SHIFT));
    }

    /**
     * Bounded exhaustive check: every subset of a {@code width x height} grid.
     * The grid straddles the origin so that negative section coordinates are included.
     */
    @ParameterizedTest
    @CsvSource({
            "4, 4", // 65,535 subsets
            "3, 5", // 32,767 subsets
            "5, 3", // 32,767 subsets
            "2, 8", // 65,535 subsets
            "8, 2", // 65,535 subsets
    })
    void testEverySubsetOfSmallGridIsCoveredExactly(int width, int height) {
        int originX = -width / 2;
        int originZ = -height / 2;

        for (int mask = 0; mask < 1 << (width * height); mask++) {
            LongSet sections = new LongOpenHashSet();
            for (int bit = 0; bit < width * height; bit++) {
                if ((mask >> bit & 1) != 0) {
                    sections.add(OutlineSpec.key(originX + bit % width, originZ + bit / width));
                }
            }

            assertSatisfiesSpecification(sections, 0, SectionsToOutlines.merge(new LongOpenHashSet(sections), 0), "mask=" + mask);
        }
    }

    /**
     * Seeded randomized check on larger regions: random section patterns (connected or not) and the 8-connected component
     * of such a pattern.
     * A failure message contains the seed and iteration so that the input can be regenerated.
     */
    @ParameterizedTest
    @CsvSource({
            "12, 0.3",
            "12, 0.55",
            "12, 0.75",
            "24, 0.4",
            "24, 0.6",
            "24, 0.85",
    })
    void testRandomRegionsAreCoveredExactly(int size, double density) {
        long seed = 0x5EC7_1045L;
        SplittableRandom random = new SplittableRandom(seed);

        for (int iteration = 0; iteration < 1_000; iteration++) {
            int originX = random.nextInt(-1_000, 1_000);
            int originZ = random.nextInt(-1_000, 1_000);
            LongSet pattern = randomPattern(random, originX, originZ, size, density);
            LongSet sections = iteration % 2 == 0 ? pattern : connectedComponentOfCentre(pattern, originX, originZ, size);

            assertSatisfiesSpecification(
                    sections, DEFAULT_SHIFT, SectionsToOutlines.merge(new LongOpenHashSet(sections), DEFAULT_SHIFT),
                    "seed=" + seed + ", size=" + size + ", density=" + density + ", iteration=" + iteration
            );
        }
    }

    private static LongSet randomPattern(SplittableRandom random, int originX, int originZ, int size, double density) {
        LongSet pattern = new LongOpenHashSet();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (random.nextDouble() < density) {
                    pattern.add(OutlineSpec.key(originX + x, originZ + z));
                }
            }
        }
        return pattern;
    }

    private static LongSet connectedComponentOfCentre(LongSet pattern, int originX, int originZ, int size) {
        pattern = new LongOpenHashSet(pattern);
        long start = OutlineSpec.key(originX + size / 2, originZ + size / 2);
        pattern.add(start);

        LongSet component = new LongOpenHashSet();
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
        component.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            long current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long neighbour = OutlineSpec.key(OutlineSpec.sectionX(current) + dx, OutlineSpec.sectionZ(current) + dz);
                    if (pattern.contains(neighbour) && component.add(neighbour)) {
                        queue.add(neighbour);
                    }
                }
            }
        }
        return component;
    }

    private static Vector2d[] scaled(Vector2d[] polygon, int shift) {
        Vector2d[] result = new Vector2d[polygon.length];
        for (int i = 0; i < polygon.length; i++) {
            result[i] = polygon[i].mul(1 << shift);
        }
        return result;
    }

    private static void assertSatisfiesSpecification(LongSet sections, int shift, List<SectionsToOutlines.Outline> outlines) {
        assertSatisfiesSpecification(sections, shift, outlines, "");
    }

    private static void assertSatisfiesSpecification(LongSet sections, int shift, List<SectionsToOutlines.Outline> outlines, String context) {
        Optional<String> violation = OutlineSpec.findViolation(sections, shift, outlines);
        if (violation.isPresent()) {
            Assertions.fail(violation.get() + " (" + context + ", sections=" + describe(sections) + ")");
        }
    }

    private static String describe(LongSet sections) {
        StringBuilder builder = new StringBuilder("[");
        sections.forEach(key -> builder.append('(').append(OutlineSpec.sectionX(key)).append(", ").append(OutlineSpec.sectionZ(key)).append(')'));
        return builder.append(']').toString();
    }

    /**
     * Rotates the polygon so that the vertex with the smallest (x, z) comes first.
     */
    private static Vector2d[] sortedCyclically(Vector2d[] polygon) {
        int first = 0;
        for (int i = 1; i < polygon.length; i++) {
            Vector2d v = polygon[i];
            Vector2d f = polygon[first];
            if (v.getX() < f.getX() || v.getX() == f.getX() && v.getY() < f.getY()) {
                first = i;
            }
        }
        Vector2d[] rotated = new Vector2d[polygon.length];
        for (int i = 0; i < polygon.length; i++) {
            rotated[i] = polygon[(first + i) % polygon.length];
        }
        return rotated;
    }
}
