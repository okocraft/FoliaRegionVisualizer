# Verification records

This directory records verification tasks following the OKOCRAFT
[Formal Verification Guidelines](https://github.com/okocraft/docs/blob/main/dev/guidelines/formal-verification.md).
No result here is a proof of the complete plugin; each record states its own scope.

### FV-001: Region outlines cover exactly the sections of the region

- Owner: FoliaRegionVisualizer maintainers
- Target: `src/main/java/net/okocraft/foliaregionvisualizer/visualizer/SectionsToOutlines.java`, `SectionsToOutlines.merge(LongSet, int)`, at the commit that introduced this revision of the record (the commit that adds per-component outlines on top of `dbceb084161797c6d97994c88a4120aacc0ec7e6`)
- Property: For an input set of region sections `S` and a shift `k`, the returned list of closed polygons (the last vertex of each is connected to its first) satisfies:
  - P0: the list is empty if and only if `S` is empty;
  - P1: every vertex is a section corner, i.e. both coordinates are integer multiples of `2^k`;
  - P2: every edge, including the closing edge, is axis-aligned and has non-zero length;
  - P3: for each polygon there is an orientation `w ∈ {+1, -1}` such that its winding number around the centre of every section is `0` or `w`, and it is `w` for at least one section;
  - P4: every section in `S` is covered (non-zero winding number) by exactly one polygon, and every other section by none.
    P3 and P4 are checked for every section in the bounding box of `S` and all vertices, extended by one section. Consequently the filled polygons (under both the non-zero and the even-odd fill rule) are exactly the union of the sections in `S`: no section is missing, no outside section is drawn, nothing is covered twice, and holes stay empty. Holes are joined to the outer boundary by zero-width bridges that are traversed once in each direction;
  - P5: each polygon covers exactly one 8-connected component of `S` (sections at Chebyshev distance 1 are adjacent): the sections it covers are 8-connected and there are as many polygons as components. Disconnected parts are therefore never joined by a bridge line;
  - P6: `merge(S, k)` equals `merge(S, 0)` with every vertex multiplied by `2^k`;
  - P7: the polygons are ordered by the smallest section (X, then Z) of their component, so the first polygon, which gets the region's marker id in `CachingVisualizer`, does not depend on hash-set iteration order.
  - The executable form of P0–P5 is `OutlineSpec.findViolation` in `src/test/java/net/okocraft/foliaregionvisualizer/visualizer/OutlineSpec.java`. It uses only the mathematical definitions and does not reuse production logic. `OutlineSpecTest` checks that the oracle rejects wrong results (missing, extra or doubly covered sections, two components joined by a bridge, diagonal edges, off-grid vertices, missing polygons).
- Preconditions:
  - `k` and the section coordinates are such that `coordinate << k` does not overflow `int`. In production `k = 4 + gridExponent` (8 by default), and section coordinates are bounded by the world border.
  - `S` may be empty or not 8-connected. In Folia `26.2.build.7-beta`, `ServerLevel` creates the regionizer with `emptySectionCreateRadius = max(1, 8 / 2^gridExponent)` and `regionSectionMergeRadius = 1`, and `ThreadedRegionizer` splits regions by Chebyshev distance `max(regionSectionMergeRadius, emptySectionCreateRadius)`. With grid exponent 3 or more (default 4) a region is 8-connected; with grid exponent 0–2 (accepted by the configuration) a region can contain sections at Chebyshev distance 2–8 without sections in between. This analysis is from reading the Folia source and was not reproduced on a server.
- Scope: The production method as compiled for Java 25 and executed on the JVM by JUnit (real fastutil, flow-math `Vector2d` and Moonrise `CoordinateUtils` from the Folia dev bundle). No model or rewritten implementation is used.
  - Bounded exhaustive part (`testEverySubsetOfSmallGridIsCoveredExactly`): every subset, including the empty set and disconnected subsets, of the 4×4, 3×5, 5×3, 2×8 and 8×2 grids, placed so that the grid straddles the origin (negative coordinates included), with `k = 0`.
  - Randomized part (`testRandomRegionsAreCoveredExactly`): 6 000 inputs with fixed seed `0x5EC71045`; half are random patterns (usually disconnected) and half the 8-connected component of such a pattern, in a 12×12 or 24×24 box at a random origin in `[-1000, 1000)`, with `k = 8`.
  - Examples (`SectionsToOutlinesTest`): a single section, a ring with a hole, an island inside a hole, diagonally touching sections, separated sections (regression), component order (P7), sections near the world border with `k = 8`, the empty set, and P6 for `k ∈ {1, 4, 6, 8, 10}`.
- Out of scope:
  - Grids larger than those listed above for the exhaustive part; P6 is only checked on one shape and P7 on one input.
  - How BlueMap triangulates and renders each `Shape` (including how it draws the zero-width bridges to holes), and the rest of `CachingVisualizer` (marker ids `region-<id>` / `region-<id>_<n>`, removal of stale markers, colours and labels).
  - Concurrency: the method is pure and sequential; how `RegionInfo` snapshots `sectionByKey` from a running regionizer is not covered.
  - Performance on large regions.
- Method/tool: Bounded exhaustive enumeration and seeded property-based testing with JUnit Jupiter 6.1.3 (`gradle/libs.versions.toml`) on Java 25, Gradle wrapper 9.8.2. A deductive verifier or JBMC was not used: the target depends on fastutil collections, streams and `Vector2d`, and the project targets Java 25 bytecode, which the guideline requires to be checked against tool support before investing in a proof.
- Reproduce: `./gradlew test --tests 'net.okocraft.foliaregionvisualizer.visualizer.*'` (part of the normal `./gradlew build`; runs in about 10 seconds).
- Evidence: `src/test/java/net/okocraft/foliaregionvisualizer/visualizer/{OutlineSpec,OutlineSpecTest,SectionsToOutlinesTest}.java`; JUnit XML reports under `build/test-results/test/` (37 tests, 0 failures).
- Status: BOUNDED (exhaustive part, within the grids listed above) / TESTED (randomized part and examples). Not PROVED.
- Reviewed by: not yet reviewed — the specification needs maintainer review.
- History:
  - At `dbceb084161797c6d97994c88a4120aacc0ec7e6`, `merge` returned a single polygon and **FAILED** P4 for inputs that are not 8-connected: `withHalls` skipped (`continue`) a child loop when no horizontal parent edge lay at or above its first vertex. Counterexample: `S = {(0, 0), (2, 0)}`, `k = 0` returned `[(0, 0), (1, 0), (1, 1), (0, 1)]`, so section `(2, 0)` was not drawn. In the exhaustive 4×4 run, 16 293 of 28 339 non-8-connected subsets were affected. Fixed by outlining each 8-connected component separately; the counterexample is kept as `testSeparatedSectionsAreAllCovered`.
- Follow-up:
  - Consider extending the exhaustive part (e.g. 5×5) in an opt-in task if more confidence is needed; it is too slow for the normal `test` task.
