package net.okocraft.foliaregionvisualizer.visualizer;

import com.flowpowered.math.vector.Vector2d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class SectionsToGridsTest {

    @Test
    void testSquareIsTheSectionInBlockCoordinates() {
        Vector2d[] square = SectionsToGrids.square(OutlineSpec.key(-1, 2), 8);

        Assertions.assertArrayEquals(
                new Vector2d[]{new Vector2d(-256, 512), new Vector2d(0, 512), new Vector2d(0, 768), new Vector2d(-256, 768)},
                square
        );
    }

    @Test
    void testSquareSatisfiesTheOutlineSpecification() {
        Vector2d[] square = SectionsToGrids.square(OutlineSpec.key(5, -7), 8);

        Assertions.assertEquals(
                Optional.empty(),
                OutlineSpec.findViolation(OutlineSpec.sections(new int[]{5, -7}), 8, List.of(new SectionsToOutlines.Outline(square, List.of())))
        );
    }
}
