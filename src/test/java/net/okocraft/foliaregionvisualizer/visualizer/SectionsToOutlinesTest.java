package net.okocraft.foliaregionvisualizer.visualizer;

import com.flowpowered.math.vector.Vector2d;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Tests for FV-001 ({@code verification/README.md}).
 * <p>
 * The specification itself is {@link OutlineSpec#findViolation(LongSet, int, Vector2d[])}.
 */
class SectionsToOutlinesTest {

    /**
     * Shift used by {@code RegionInfo} with the default Folia grid exponent (4 + 4).
     */
    private static final int DEFAULT_SHIFT = 8;

    @Test
    void testSingleSectionBecomesItsFourCorners() {
        LongSet sections = OutlineSpec.sections(new int[]{0, 0});

        Vector2d[] polygon = SectionsToOutlines.merge(sections, 0);

        Assertions.assertArrayEquals(
                new Vector2d[]{new Vector2d(0, 0), new Vector2d(1, 0), new Vector2d(1, 1), new Vector2d(0, 1)},
                sortedCyclically(polygon)
        );
        assertSatisfiesSpecification(sections, 0, polygon);
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

        Vector2d[] polygon = SectionsToOutlines.merge(sections, DEFAULT_SHIFT);

        assertSatisfiesSpecification(sections, DEFAULT_SHIFT, polygon);
    }

    @Test
    void testDiagonallyTouchingSectionsAreBothCovered() {
        LongSet sections = OutlineSpec.sections(new int[]{0, 0}, new int[]{1, 1});

        Vector2d[] polygon = SectionsToOutlines.merge(sections, DEFAULT_SHIFT);

        assertSatisfiesSpecification(sections, DEFAULT_SHIFT, polygon);
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

        Vector2d[] polygon = SectionsToOutlines.merge(sections, DEFAULT_SHIFT);

        assertSatisfiesSpecification(sections, DEFAULT_SHIFT, polygon);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 6, 8, 10})
    void testShiftOnlyScalesTheUnshiftedPolygon(int shift) {
        LongSet sections = OutlineSpec.sections(
                new int[]{0, 0}, new int[]{1, 0}, new int[]{2, 0},
                new int[]{0, 1}, new int[]{2, 1},
                new int[]{0, 2}, new int[]{1, 2}, new int[]{2, 2}, new int[]{3, 3}
        );
        Vector2d[] unshifted = SectionsToOutlines.merge(new LongOpenHashSet(sections), 0);

        Vector2d[] shifted = SectionsToOutlines.merge(sections, shift);

        Vector2d[] expected = new Vector2d[unshifted.length];
        for (int i = 0; i < unshifted.length; i++) {
            expected[i] = unshifted[i].mul(1 << shift);
        }
        Assertions.assertArrayEquals(expected, shifted);
    }

    @Test
    void testEmptySectionsAreRejected() {
        // Precondition violation: Folia never reports a region without sections.
        Assertions.assertThrows(NoSuchElementException.class, () -> SectionsToOutlines.merge(new LongOpenHashSet(), DEFAULT_SHIFT));
    }

    /**
     * Bounded exhaustive check: every non-empty 8-connected subset of a {@code width x height} grid.
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
    void testEveryConnectedSubsetOfSmallGridIsCoveredExactly(int width, int height) {
        int originX = -width / 2;
        int originZ = -height / 2;
        int checked = 0;

        for (int mask = 1; mask < 1 << (width * height); mask++) {
            LongSet sections = new LongOpenHashSet();
            for (int bit = 0; bit < width * height; bit++) {
                if ((mask >> bit & 1) != 0) {
                    sections.add(OutlineSpec.key(originX + bit % width, originZ + bit / width));
                }
            }
            if (!OutlineSpec.isEightConnected(sections)) {
                continue;
            }

            assertSatisfiesSpecification(sections, 0, SectionsToOutlines.merge(new LongOpenHashSet(sections), 0), "mask=" + mask);
            checked++;
        }

        Assertions.assertTrue(checked > 0);
    }

    /**
     * Seeded randomized check on larger regions: the 8-connected component of a random section pattern.
     * A failure message contains the seed and iteration so that the input can be regenerated.
     */
    @ParameterizedTest
    @CsvSource({
            "12, 0.55",
            "12, 0.75",
            "24, 0.6",
            "24, 0.85",
    })
    void testRandomConnectedRegionsAreCoveredExactly(int size, double density) {
        long seed = 0x5EC7_1045L;
        SplittableRandom random = new SplittableRandom(seed);

        for (int iteration = 0; iteration < 1_000; iteration++) {
            int originX = random.nextInt(-1_000, 1_000);
            int originZ = random.nextInt(-1_000, 1_000);
            LongSet sections = randomConnectedRegion(random, originX, originZ, size, density);

            assertSatisfiesSpecification(
                    sections, DEFAULT_SHIFT, SectionsToOutlines.merge(new LongOpenHashSet(sections), DEFAULT_SHIFT),
                    "seed=" + seed + ", size=" + size + ", density=" + density + ", iteration=" + iteration
            );
        }
    }

    private static LongSet randomConnectedRegion(SplittableRandom random, int originX, int originZ, int size, double density) {
        LongSet pattern = new LongOpenHashSet();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (random.nextDouble() < density) {
                    pattern.add(OutlineSpec.key(originX + x, originZ + z));
                }
            }
        }

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

    private static void assertSatisfiesSpecification(LongSet sections, int shift, Vector2d[] polygon) {
        assertSatisfiesSpecification(sections, shift, polygon, "");
    }

    private static void assertSatisfiesSpecification(LongSet sections, int shift, Vector2d[] polygon, String context) {
        Optional<String> violation = OutlineSpec.findViolation(sections, shift, polygon);
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
