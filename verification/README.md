# Verification records

This directory records verification tasks following the OKOCRAFT
[Formal Verification Guidelines](https://github.com/okocraft/docs/blob/main/dev/guidelines/formal-verification.md).
No result here is a proof of the complete plugin; each record states its own scope.

### FV-001: A region outline covers exactly the sections of the region

- Owner: FoliaRegionVisualizer maintainers
- Target: `src/main/java/net/okocraft/foliaregionvisualizer/visualizer/SectionsToOutlines.java`, `SectionsToOutlines.merge(LongSet, int)`, source commit `dbceb084161797c6d97994c88a4120aacc0ec7e6`
- Property: For an input set of region sections `S` and a shift `k`, the returned vertex array `P` (a closed polygon, last vertex connected to the first) satisfies:
  - P1: every vertex is a section corner, i.e. both coordinates are integer multiples of `2^k`;
  - P2: every edge, including the closing edge, is axis-aligned and has non-zero length;
  - P3: there is an orientation `w ∈ {+1, -1}` such that, for every section `c` in the bounding box of `S` and `P` extended by one section, the winding number of `P` around the centre of `c` is `w` if `c ∈ S` and `0` otherwise.
    Consequently the filled polygon (under both the non-zero and the even-odd fill rule) is exactly the union of the sections in `S`: no section is missing, no outside section is drawn, nothing is covered twice, and holes stay empty. Holes are joined to the outer boundary by zero-width bridges that are traversed once in each direction.
  - P4: `merge(S, k)` equals `merge(S, 0)` with every vertex multiplied by `2^k`.
  - The executable form of P1–P3 is `OutlineSpec.findViolation` in `src/test/java/net/okocraft/foliaregionvisualizer/visualizer/OutlineSpec.java`. It uses only the mathematical definitions and does not reuse production logic. `OutlineSpecTest` checks that the oracle rejects wrong polygons (missing, extra or doubly covered sections, diagonal edges, off-grid vertices).
- Preconditions:
  - `S` is non-empty and 8-connected (sections at Chebyshev distance 1 are adjacent).
    Rationale: `CachingVisualizer` passes the section keys of one Folia `ThreadedRegion`. In Folia `26.2.build.7-beta`, `ServerLevel` creates the regionizer with `emptySectionCreateRadius = max(1, 8 / 2^gridExponent)` and `regionSectionMergeRadius = 1`, and `ThreadedRegionizer` splits regions by Chebyshev distance `max(regionSectionMergeRadius, emptySectionCreateRadius)`. With the default grid exponent 4 that distance is 1, so a region is 8-connected after a split recalculation.
  - `k` and the section coordinates are such that `coordinate << k` does not overflow `int`. In production `k = 4 + gridExponent` (8 by default), and section coordinates are bounded by the world border.
  - Empty input is a precondition violation; the current behaviour is `NoSuchElementException` (tested by `testEmptySectionsAreRejected`).
- Scope: The production method as compiled for Java 25 and executed on the JVM by JUnit (real fastutil, flow-math `Vector2d` and Moonrise `CoordinateUtils` from the Folia dev bundle). No model or rewritten implementation is used.
  - Bounded exhaustive part (`testEveryConnectedSubsetOfSmallGridIsCoveredExactly`): every non-empty 8-connected subset of the 4×4, 3×5, 5×3, 2×8 and 8×2 grids, placed so that the grid straddles the origin (negative coordinates included), with `k = 0`.
  - Randomized part (`testRandomConnectedRegionsAreCoveredExactly`): 4 000 regions with fixed seed `0x5EC71045`, each the 8-connected component of a random pattern in a 12×12 or 24×24 box at a random origin in `[-1000, 1000)`, with `k = 8`.
  - Examples (`SectionsToOutlinesTest`): a single section, a ring with a hole, diagonally touching sections, sections near the world border with `k = 8`, and P4 for `k ∈ {1, 4, 6, 8, 10}`.
- Out of scope:
  - Inputs that are not 8-connected (see Follow-up), and regions in transient states between Folia split recalculations, or with grid exponent ≤ 2 (where the merge distance is larger than 1).
  - Grids larger than those listed above for the exhaustive part; P4 is only checked on one shape.
  - How BlueMap triangulates and renders the `Shape` (including how it draws the zero-width bridges), the marker position, colours and labels in `CachingVisualizer`.
  - Concurrency: the method is pure and sequential; how `RegionInfo` snapshots `sectionByKey` from a running regionizer is not covered.
  - Performance on large regions.
- Method/tool: Bounded exhaustive enumeration and seeded property-based testing with JUnit Jupiter 6.1.3 (`gradle/libs.versions.toml`) on Java 25, Gradle wrapper 9.8.2. A deductive verifier or JBMC was not used: the target depends on fastutil collections, streams and `Vector2d`, and the project targets Java 25 bytecode, which the guideline requires to be checked against tool support before investing in a proof.
- Reproduce: `./gradlew test --tests 'net.okocraft.foliaregionvisualizer.visualizer.*'` (part of the normal `./gradlew build`; runs in about 5 seconds).
- Evidence: `src/test/java/net/okocraft/foliaregionvisualizer/visualizer/{OutlineSpec,OutlineSpecTest,SectionsToOutlinesTest}.java`; JUnit XML reports under `build/test-results/test/` (29 tests, 0 failures at the target commit).
- Status: BOUNDED (exhaustive part, within the grids listed above) / TESTED (randomized part and examples). Not PROVED.
- Reviewed by: not yet reviewed — the specification (especially the 8-connectivity precondition) needs maintainer review.
- Follow-up:
  - **FAIL outside the precondition:** for inputs that are not 8-connected, some components are silently dropped. Counterexample: `S = {(0, 0), (2, 0)}`, `k = 0` returns `[(0, 0), (1, 0), (1, 1), (0, 1)]`; section `(2, 0)` is not drawn. Cause: `withHalls` skips (`continue`) a child loop when no horizontal parent edge lies at or above its first vertex. Some disconnected inputs are still handled correctly (e.g. `{(0, 0), (0, 2)}`). In the exhaustive 4×4 run, 16 293 of 28 339 non-8-connected subsets violated P3. This only matters if a region can be passed in a non-8-connected state (see Out of scope); decide whether to support it before adding a regression test.
  - Consider extending the exhaustive part (e.g. 5×5) in an opt-in task if more confidence is needed; it is too slow for the normal `test` task.
