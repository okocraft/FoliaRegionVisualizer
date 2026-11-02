package net.okocraft.foliaregionvisualizer.visualizer;

import com.flowpowered.math.vector.Vector2d;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Checks that the FV-001 oracle is not vacuous: it must reject outlines that do not describe the input sections.
 */
class OutlineSpecTest {

    private static final LongSet TWO_SECTIONS = OutlineSpec.sections(new int[]{0, 0}, new int[]{1, 0});

    @Test
    void testAcceptsExactOutlineInEitherOrientation() {
        Vector2d[] anticlockwise = {v(0, 0), v(2, 0), v(2, 1), v(0, 1)};
        Vector2d[] clockwise = {v(0, 0), v(0, 1), v(2, 1), v(2, 0)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, OutlineSpec.outline(anticlockwise)).isEmpty());
        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, OutlineSpec.outline(clockwise)).isEmpty());
    }

    @Test
    void testRejectsOutlineMissingASection() {
        Vector2d[] shape = {v(0, 0), v(1, 0), v(1, 1), v(0, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, OutlineSpec.outline(shape)).isPresent());
    }

    @Test
    void testRejectsOutlineCoveringAnExtraSection() {
        Vector2d[] shape = {v(0, 0), v(3, 0), v(3, 1), v(0, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, OutlineSpec.outline(shape)).isPresent());
    }

    @Test
    void testRejectsOutlineCoveringASectionTwice() {
        Vector2d[] twice = {v(0, 0), v(2, 0), v(2, 1), v(0, 1), v(0, 0), v(2, 0), v(2, 1), v(0, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, OutlineSpec.outline(twice)).isPresent());
    }

    @Test
    void testRejectsTwoOutlinesCoveringTheSameSection() {
        Vector2d[] shape = {v(0, 0), v(2, 0), v(2, 1), v(0, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, List.of(OutlineSpec.outline(shape), OutlineSpec.outline(shape))).isPresent());
    }

    @Test
    void testRejectsDiagonalEdge() {
        Vector2d[] shape = {v(0, 0), v(2, 0), v(2, 1), v(1, 1), v(0, 0)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, OutlineSpec.outline(shape)).isPresent());
    }

    @Test
    void testRejectsVertexOffTheSectionGrid() {
        Vector2d[] onGrid = {v(0, 0), v(512, 0), v(512, 256), v(0, 256)};
        Vector2d[] offGrid = {v(0, 0), v(512, 0), v(512, 300), v(0, 300)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 8, OutlineSpec.outline(onGrid)).isEmpty());
        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 8, OutlineSpec.outline(offGrid)).isPresent());
    }

    @Test
    void testAcceptsRingAsShapeWithHole() {
        Vector2d[] shape = {v(0, 0), v(3, 0), v(3, 3), v(0, 3)};
        Vector2d[] hole = {v(1, 1), v(1, 2), v(2, 2), v(2, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(ring3x3(), 0, OutlineSpec.outline(shape, hole)).isEmpty());
    }

    @Test
    void testRejectsRingDrawnWithBridge() {
        // The fill is correct, but the bridge (1, 0)-(1, 1) is drawn inside the sections.
        Vector2d[] bridged = {v(0, 0), v(1, 0), v(1, 1), v(1, 2), v(2, 2), v(2, 1), v(1, 1), v(1, 0), v(3, 0), v(3, 3), v(0, 3)};

        Assertions.assertTrue(OutlineSpec.findViolation(ring3x3(), 0, OutlineSpec.outline(bridged)).isPresent());
    }

    @Test
    void testRejectsHoleOutsideTheShape() {
        Vector2d[] shape = {v(0, 0), v(2, 0), v(2, 1), v(0, 1)};
        Vector2d[] hole = {v(5, 5), v(5, 6), v(6, 6), v(6, 5)};

        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, OutlineSpec.outline(shape, hole)).isPresent());
    }

    @Test
    void testRejectsOneOutlineCoveringTwoComponents() {
        LongSet separated = OutlineSpec.sections(new int[]{0, 0}, new int[]{2, 0});
        Vector2d[] both = {v(0, 0), v(3, 0), v(3, 1), v(0, 1)};
        Vector2d[] gap = {v(1, 0), v(1, 1), v(2, 1), v(2, 0)};
        Vector2d[] first = {v(0, 0), v(1, 0), v(1, 1), v(0, 1)};
        Vector2d[] second = {v(2, 0), v(3, 0), v(3, 1), v(2, 1)};

        Assertions.assertTrue(OutlineSpec.findViolation(separated, 0, OutlineSpec.outline(both, gap)).isPresent());
        Assertions.assertTrue(OutlineSpec.findViolation(separated, 0, List.of(OutlineSpec.outline(first), OutlineSpec.outline(second))).isEmpty());
    }

    @Test
    void testRejectsMissingOrUnexpectedOutlineList() {
        Assertions.assertTrue(OutlineSpec.findViolation(TWO_SECTIONS, 0, List.of()).isPresent());
        Assertions.assertTrue(OutlineSpec.findViolation(OutlineSpec.sections(), 0, List.of()).isEmpty());
    }

    @Test
    void testFourConnectivityRequiresASharedEdge() {
        Assertions.assertTrue(OutlineSpec.isFourConnected(OutlineSpec.sections(new int[]{0, 0}, new int[]{1, 0})));
        Assertions.assertFalse(OutlineSpec.isFourConnected(OutlineSpec.sections(new int[]{0, 0}, new int[]{1, 1})));
        Assertions.assertFalse(OutlineSpec.isFourConnected(OutlineSpec.sections(new int[]{0, 0}, new int[]{2, 0})));
        Assertions.assertFalse(OutlineSpec.isFourConnected(OutlineSpec.sections()));
    }

    @Test
    void testRejectsOneOutlineCoveringDiagonallyTouchingSections() {
        LongSet diagonal = OutlineSpec.sections(new int[]{0, 0}, new int[]{1, 1});
        // One ring that passes through the shared corner (1, 1) twice.
        Vector2d[] pinched = {v(0, 0), v(1, 0), v(1, 1), v(2, 1), v(2, 2), v(1, 2), v(1, 1), v(0, 1)};
        Vector2d[] first = {v(0, 0), v(1, 0), v(1, 1), v(0, 1)};
        Vector2d[] second = {v(1, 1), v(2, 1), v(2, 2), v(1, 2)};

        Assertions.assertTrue(OutlineSpec.findViolation(diagonal, 0, OutlineSpec.outline(pinched)).isPresent());
        Assertions.assertTrue(OutlineSpec.findViolation(diagonal, 0, List.of(OutlineSpec.outline(first), OutlineSpec.outline(second))).isEmpty());
    }

    private static LongSet ring3x3() {
        LongSet sections = new LongOpenHashSet();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x != 1 || z != 1) {
                    sections.add(OutlineSpec.key(x, z));
                }
            }
        }
        return sections;
    }

    private static Vector2d v(double x, double z) {
        return new Vector2d(x, z);
    }
}
