package net.okocraft.foliaregionvisualizer.visualizer;

import com.flowpowered.math.vector.Vector2d;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Checks that the FV-001 oracle is not vacuous: it must reject polygons that do not describe the input sections.
 */
class OutlineSpecTest {

    private static final LongSet TWO_SECTIONS = OutlineSpec.sections(new int[]{0, 0}, new int[]{1, 0});

    @Test
    void testAcceptsExactOutlineInEitherOrientation() {
        Vector2d[] clockwise = {v(0, 0), v(2, 0), v(2, 1), v(0, 1)};
        Vector2d[] anticlockwise = {v(0, 0), v(0, 1), v(2, 1), v(2, 0)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, clockwise).isEmpty());
        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, anticlockwise).isEmpty());
    }

    @Test
    void testRejectsOutlineMissingASection() {
        Vector2d[] polygon = {v(0, 0), v(1, 0), v(1, 1), v(0, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, polygon).isPresent());
    }

    @Test
    void testRejectsOutlineCoveringAnExtraSection() {
        Vector2d[] polygon = {v(0, 0), v(3, 0), v(3, 1), v(0, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, polygon).isPresent());
    }

    @Test
    void testRejectsOutlineCoveringASectionTwice() {
        Vector2d[] polygon = {v(0, 0), v(2, 0), v(2, 1), v(0, 1), v(0, 0), v(2, 0), v(2, 1), v(0, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, polygon).isPresent());
    }

    @Test
    void testRejectsDiagonalEdge() {
        Vector2d[] polygon = {v(0, 0), v(2, 0), v(2, 1), v(1, 1), v(0, 0)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, polygon).isPresent());
    }

    @Test
    void testRejectsVertexOffTheSectionGrid() {
        Vector2d[] polygon = {v(0, 0), v(512, 0), v(512, 256), v(0, 256)};
        Vector2d[] shifted = {v(0, 0), v(512, 0), v(512, 300), v(0, 300)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 8, polygon).isEmpty());
        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 8, shifted).isPresent());
    }

    @Test
    void testRejectsOneOutlineBridgingTwoComponents() {
        LongSet separated = OutlineSpec.sections(new int[]{0, 0}, new int[]{2, 0});
        // Covers (0, 0) and (2, 0) but not (1, 0), joined by a zero-width bridge along z = 0.
        Vector2d[] bridged = {v(0, 0), v(3, 0), v(3, 1), v(2, 1), v(2, 0), v(1, 0), v(1, 1), v(0, 1)};
        Vector2d[] first = {v(0, 0), v(1, 0), v(1, 1), v(0, 1)};
        Vector2d[] second = {v(2, 0), v(3, 0), v(3, 1), v(2, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(separated, 0, bridged).isPresent());
        Assertions.assertTrue(OutlineSpec.findViolation(separated, 0, List.of(first, second)).isEmpty());
    }

    @Test
    void testRejectsTwoOutlinesCoveringTheSameSection() {
        Vector2d[] polygon = {v(0, 0), v(2, 0), v(2, 1), v(0, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, List.of(polygon, polygon)).isPresent());
    }

    @Test
    void testRejectsMissingOrUnexpectedPolygonList() {
        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, List.of()).isPresent());
        Assertions.assertTrue(OutlineSpec.findViolation(OutlineSpec.sections(), 0, List.of()).isEmpty());
    }

    @Test
    void testEightConnectivityTreatsDiagonalNeighboursAsConnected() {
        Assertions.assertTrue(OutlineSpec.isEightConnected(OutlineSpec.sections(new int[]{0, 0}, new int[]{1, 1})));
        Assertions.assertFalse(OutlineSpec.isEightConnected(OutlineSpec.sections(new int[]{0, 0}, new int[]{2, 0})));
        Assertions.assertFalse(OutlineSpec.isEightConnected(OutlineSpec.sections()));
    }

    private static Vector2d v(double x, double z) {
        return new Vector2d(x, z);
    }
}
