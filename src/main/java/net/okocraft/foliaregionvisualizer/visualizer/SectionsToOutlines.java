package net.okocraft.foliaregionvisualizer.visualizer;

import com.flowpowered.math.vector.Vector2d;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.okocraft.foliaregionvisualizer.util.SectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SectionsToOutlines {

    // Directions of boundary edges, in counterclockwise order on the (x, z) plane.
    private static final int PLUS_X = 0;
    private static final int PLUS_Z = 1;
    private static final int MINUS_X = 2;
    private static final int MINUS_Z = 3;
    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DZ = {0, 1, 0, -1};

    /**
     * Converts sections into outlines, one for each 4-connected component of the sections.
     * <p>
     * Two sections are connected when they share an edge. A Folia region is not always connected (e.g. with a grid
     * exponent of 2 or less), and sections that touch only at a corner are outlined separately as well: BlueMap
     * triangulates a shape whose rings touch at a vertex incorrectly in some cases (see FV-001), while two shapes that
     * touch at a corner are drawn exactly as one. The outlines are ordered by the smallest section (X first, then Z) of
     * their component.
     * <p>
     * Each outline consists of the outer boundary and the boundaries of its holes, so that they can be passed to
     * BlueMap's {@code ShapeMarker} as the shape and its holes. Every edge of every ring lies on the boundary of the
     * sections; no edge crosses the inside or the outside of the sections.
     *
     * @param sections the section keys
     * @param shift    the shift to convert section coordinates to block coordinates
     * @return the outlines, or an empty list if {@code sections} is empty
     */
    static List<Outline> merge(LongSet sections, int shift) {
        List<LongSet> components = splitIntoConnectedComponents(sections);
        List<Outline> result = new ArrayList<>(components.size());

        for (LongSet component : components) {
            result.add(outline(component, shift));
        }

        return result;
    }

    /**
     * The outline of a 4-connected component.
     *
     * @param shape the outer boundary
     * @param holes the boundaries of the holes, ordered by their smallest vertex (X first, then Z)
     */
    record Outline(Vector2d[] shape, List<Vector2d[]> holes) {
    }

    private static List<LongSet> splitIntoConnectedComponents(LongSet sections) {
        LongSet remaining = new LongOpenHashSet(sections);
        List<Component> components = new ArrayList<>();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        while (!remaining.isEmpty()) {
            long start = remaining.iterator().nextLong();
            remaining.remove(start);
            queue.enqueue(start);

            LongSet component = new LongOpenHashSet();
            component.add(start);
            int minX = SectionUtils.getSectionX(start);
            int minZ = SectionUtils.getSectionZ(start);

            while (!queue.isEmpty()) {
                long current = queue.dequeueLong();
                int x = SectionUtils.getSectionX(current);
                int z = SectionUtils.getSectionZ(current);

                if (x < minX || x == minX && z < minZ) {
                    minX = x;
                    minZ = z;
                }

                for (int direction = 0; direction < 4; direction++) {
                    long neighbour = SectionUtils.getSectionKey(x + DX[direction], z + DZ[direction]);
                    if (remaining.remove(neighbour)) {
                        component.add(neighbour);
                        queue.enqueue(neighbour);
                    }
                }
            }

            components.add(new Component(component, minX, minZ));
        }

        components.sort(Comparator.comparingInt(Component::minX).thenComparingInt(Component::minZ));

        List<LongSet> result = new ArrayList<>(components.size());
        for (Component component : components) {
            result.add(component.sections());
        }
        return result;
    }

    private record Component(LongSet sections, int minX, int minZ) {
    }

    /**
     * Traces the boundary of non-empty, 4-connected sections.
     * <p>
     * Every section contributes its four edges counterclockwise (the section is on the left of each edge), and edges
     * shared by two sections cancel out. The remaining edges form closed rings: the outer boundary is counterclockwise
     * (positive area) and every hole is clockwise (negative area).
     * <p>
     * At a vertex where two sections touch only diagonally, two rings meet. Taking the left turn there keeps the two
     * diagonal empty sections in the same ring, which matches the 4-connectivity of the sections (and the
     * 8-connectivity of the empty space), so that a component has exactly one outer ring. A hole that touches the
     * outer boundary at such a vertex becomes a part of the outer ring, and holes that touch each other become one ring.
     */
    private static Outline outline(LongSet sections, int shift) {
        Long2IntMap outgoing = new Long2IntOpenHashMap();

        sections.forEach(sectionKey -> {
            int x = SectionUtils.getSectionX(sectionKey);
            int z = SectionUtils.getSectionZ(sectionKey);
            addEdge(outgoing, x, z, PLUS_X);
            addEdge(outgoing, x + 1, z, PLUS_Z);
            addEdge(outgoing, x + 1, z + 1, MINUS_X);
            addEdge(outgoing, x, z + 1, MINUS_Z);
        });

        Long2IntMap allOutgoing = new Long2IntOpenHashMap(outgoing);
        Vector2d[] shape = null;
        List<LongList> holes = new ArrayList<>();

        while (!outgoing.isEmpty()) {
            LongList ring = traceRing(outgoing, allOutgoing);

            if (signedDoubleArea(ring) > 0) {
                if (shape != null) {
                    throw new IllegalStateException("4-connected sections must have exactly one outer boundary");
                }
                shape = toVectors(ring, shift);
            } else {
                holes.add(ring);
            }
        }

        if (shape == null) {
            throw new IllegalStateException("4-connected sections must have exactly one outer boundary");
        }

        holes.sort(Comparator.comparingInt((LongList ring) -> SectionUtils.getSectionX(ring.getLong(0)))
                .thenComparingInt(ring -> SectionUtils.getSectionZ(ring.getLong(0))));

        List<Vector2d[]> holeVectors = new ArrayList<>(holes.size());
        for (LongList hole : holes) {
            holeVectors.add(toVectors(hole, shift));
        }

        return new Outline(shape, holeVectors);
    }

    private static void addEdge(Long2IntMap outgoing, int x, int z, int direction) {
        long reverseStart = SectionUtils.getSectionKey(x + DX[direction], z + DZ[direction]);
        int reverseBit = 1 << ((direction + 2) & 3);
        int reverseMask = outgoing.get(reverseStart);

        if ((reverseMask & reverseBit) != 0) {
            // The edge is shared with an adjacent section, so it is not on the boundary.
            if (reverseMask == reverseBit) {
                outgoing.remove(reverseStart);
            } else {
                outgoing.put(reverseStart, reverseMask & ~reverseBit);
            }
        } else {
            long start = SectionUtils.getSectionKey(x, z);
            outgoing.put(start, outgoing.get(start) | (1 << direction));
        }
    }

    /**
     * Removes one ring from {@code outgoing} and returns its corners, starting from the smallest corner (X first, then Z).
     */
    private static LongList traceRing(Long2IntMap outgoing, Long2IntMap allOutgoing) {
        Long2IntMap.Entry first = outgoing.long2IntEntrySet().iterator().next();
        long startVertex = first.getLongKey();
        int startDirection = Integer.numberOfTrailingZeros(first.getIntValue());

        LongList corners = new LongArrayList();
        long vertex = startVertex;
        int direction = startDirection;

        do {
            removeEdge(outgoing, vertex, direction);
            int x = SectionUtils.getSectionX(vertex) + DX[direction];
            int z = SectionUtils.getSectionZ(vertex) + DZ[direction];
            vertex = SectionUtils.getSectionKey(x, z);

            int next = nextDirection(allOutgoing.get(vertex), direction);
            if (next != direction) {
                corners.add(vertex);
            }
            direction = next;
        } while (vertex != startVertex || direction != startDirection);

        int smallest = 0;
        for (int i = 1; i < corners.size(); i++) {
            if (isSmaller(corners.getLong(i), corners.getLong(smallest))) {
                smallest = i;
            }
        }

        LongList rotated = new LongArrayList(corners.size());
        rotated.addAll(corners.subList(smallest, corners.size()));
        rotated.addAll(corners.subList(0, smallest));
        return rotated;
    }

    private static void removeEdge(Long2IntMap outgoing, long vertex, int direction) {
        int mask = outgoing.get(vertex) & ~(1 << direction);
        if (mask == 0) {
            outgoing.remove(vertex);
        } else {
            outgoing.put(vertex, mask);
        }
    }

    /**
     * Chooses the outgoing edge after arriving in {@code incoming} direction: left turn, straight, then right turn.
     * Only a vertex where sections touch diagonally has two outgoing edges, and there the left turn is taken.
     */
    private static int nextDirection(int outgoingMask, int incoming) {
        int left = (incoming + 1) & 3;
        if ((outgoingMask & (1 << left)) != 0) {
            return left;
        }
        if ((outgoingMask & (1 << incoming)) != 0) {
            return incoming;
        }
        int right = (incoming + 3) & 3;
        if ((outgoingMask & (1 << right)) != 0) {
            return right;
        }
        throw new IllegalStateException("The boundary is not closed");
    }

    private static boolean isSmaller(long vertex, long other) {
        int x = SectionUtils.getSectionX(vertex);
        int otherX = SectionUtils.getSectionX(other);
        return x < otherX || x == otherX && SectionUtils.getSectionZ(vertex) < SectionUtils.getSectionZ(other);
    }

    private static long signedDoubleArea(LongList ring) {
        long area = 0;
        for (int i = 0, size = ring.size(); i < size; i++) {
            long a = ring.getLong(i);
            long b = ring.getLong((i + 1) % size);
            area += (long) SectionUtils.getSectionX(a) * SectionUtils.getSectionZ(b)
                    - (long) SectionUtils.getSectionX(b) * SectionUtils.getSectionZ(a);
        }
        return area;
    }

    private static Vector2d[] toVectors(LongList ring, int shift) {
        Vector2d[] result = new Vector2d[ring.size()];
        for (int i = 0; i < result.length; i++) {
            long vertex = ring.getLong(i);
            result[i] = new Vector2d(SectionUtils.getSectionX(vertex) << shift, SectionUtils.getSectionZ(vertex) << shift);
        }
        return result;
    }

    private SectionsToOutlines() {
        throw new UnsupportedOperationException();
    }
}
