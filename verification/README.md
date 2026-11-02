# Verification records

This directory records verification tasks following the OKOCRAFT
[Formal Verification Guidelines](https://github.com/okocraft/docs/blob/main/dev/guidelines/formal-verification.md).
No result here is a proof of the complete plugin; each record states its own scope.

### FV-001: Region outlines cover exactly the sections of the region

- Owner: FoliaRegionVisualizer maintainers
- Target: `src/main/java/net/okocraft/foliaregionvisualizer/visualizer/SectionsToOutlines.java`, `SectionsToOutlines.merge(LongSet, int)`, at the commit that introduced this revision of the record (outer ring and holes per 4-connected component)
- Property: For an input set of region sections `S` and a shift `k`, the returned list of outlines satisfies the following. An outline is a closed shape ring and a list of closed hole rings, passed to BlueMap's `ShapeMarker` as its shape and holes; the last vertex of a ring is connected to its first. A section is *filled* by an outline when the shape's winding number around its centre is non-zero and no hole's is.
  - P0: the list is empty if and only if `S` is empty;
  - P1: every vertex of every ring is a section corner, i.e. both coordinates are integer multiples of `2^k`;
  - P2: every edge of every ring, including the closing edge, is axis-aligned and has non-zero length;
  - P3: every winding number (of a shape or a hole, around any section) is `-1`, `0` or `+1`; a hole lies inside its shape; the holes of an outline do not overlap; and each outline fills at least one section;
  - P4: every section in `S` is filled by exactly one outline, and every other section by none. P3 and P4 are checked for every section in the bounding box of `S` and all vertices, extended by one section. Consequently the filled area is exactly the union of the sections in `S`: no section is missing, no outside section is filled, nothing is filled twice, and holes stay empty;
  - P5: each outline fills exactly one 4-connected component of `S` (sections sharing an edge): the sections it fills are 4-connected and there are as many outlines as components. Sections touching only at a corner are separate outlines (see FV-002 for why);
  - P6: `merge(S, k)` equals `merge(S, 0)` with every vertex multiplied by `2^k`;
  - P7: the outlines are ordered by the smallest section (X, then Z) of their component, so the first outline, which gets the region's marker id in `CachingVisualizer`, does not depend on hash-set iteration order;
  - P8: every edge of every ring lies on the boundary of `S`: splitting the edges into unit edges, each unit edge between a section in `S` and a section not in `S` appears exactly once over all rings, and no other unit edge appears. BlueMap draws every edge of a shape and its holes as a border line, so no line is drawn inside or outside the region.
  - The executable form of P0–P5 and P8 is `OutlineSpec.findViolation` in `src/test/java/net/okocraft/foliaregionvisualizer/visualizer/OutlineSpec.java`. It uses only the mathematical definitions and does not reuse production logic. `OutlineSpecTest` checks that the oracle rejects wrong results (missing, extra or doubly filled sections, a ring drawn with a bridge, a hole outside its shape, one outline for two components or for sections touching at a corner, diagonal edges, off-grid vertices, a missing outline list).
- Preconditions:
  - `k` and the section coordinates are such that `coordinate << k` does not overflow `int`. In production `k = 4 + gridExponent` (8 by default), and section coordinates are bounded by the world border.
  - `S` may be empty or not connected. In Folia `26.2.build.7-beta`, `ServerLevel` creates the regionizer with `emptySectionCreateRadius = max(1, 8 / 2^gridExponent)` and `regionSectionMergeRadius = 1`, and `ThreadedRegionizer` splits regions by Chebyshev distance `max(regionSectionMergeRadius, emptySectionCreateRadius)`. With grid exponent 3 or more (default 4) a region is 8-connected; with grid exponent 0–2 (accepted by the configuration) a region can contain sections at Chebyshev distance 2–8 without sections in between. This analysis is from reading the Folia source and was not reproduced on a server.
- Scope: The production method as compiled for Java 25 and executed on the JVM by JUnit (real fastutil, flow-math `Vector2d` and Moonrise `CoordinateUtils` from the Folia dev bundle). No model or rewritten implementation is used.
  - Bounded exhaustive part (`testEverySubsetOfSmallGridIsCoveredExactly`): every subset, including the empty set and disconnected subsets, of the 4×4, 3×5, 5×3, 2×8 and 8×2 grids, placed so that the grid straddles the origin (negative coordinates included), with `k = 0`.
  - Randomized part (`testRandomRegionsAreCoveredExactly`): 6 000 inputs with fixed seed `0x5EC71045`; half are random patterns (usually disconnected) and half the 8-connected component of such a pattern, in a 12×12 or 24×24 box at a random origin in `[-1000, 1000)`, with `k = 8`.
  - Examples (`SectionsToOutlinesTest`): a single section, a ring with a hole, an island inside a hole, sections touching only at corners, a hole touching the outer boundary at a corner, separated sections, outline order (P7), sections near the world border with `k = 8`, the empty set, and P6 for `k ∈ {1, 4, 6, 8, 10}`.
- Out of scope:
  - Grids larger than those listed above for the exhaustive part; P6 is only checked on one shape and P7 on one input.
  - How BlueMap renders the outlines: see FV-002.
  - The rest of `CachingVisualizer` (marker ids `region-<id>` / `region-<id>_<n>`, removal of stale markers, colours and labels).
  - Concurrency: the method is pure and sequential; how `RegionInfo` snapshots `sectionByKey` from a running regionizer is not covered.
  - Performance on large regions.
- Method/tool: Bounded exhaustive enumeration and seeded property-based testing with JUnit Jupiter 6.1.3 (`gradle/libs.versions.toml`) on Java 25, Gradle wrapper 9.8.2. A deductive verifier or JBMC was not used: the target depends on fastutil collections and `Vector2d`, and the project targets Java 25 bytecode, which the guideline requires to be checked against tool support before investing in a proof.
- Reproduce: `./gradlew test --tests 'net.okocraft.foliaregionvisualizer.visualizer.*'` (part of the normal `./gradlew build`; runs in about 10 seconds).
- Evidence: `src/test/java/net/okocraft/foliaregionvisualizer/visualizer/{OutlineSpec,OutlineSpecTest,SectionsToOutlinesTest}.java`; JUnit XML reports under `build/test-results/test/` (43 tests, 0 failures).
- Status: BOUNDED (exhaustive part, within the grids listed above) / TESTED (randomized part and examples). Not PROVED.
- Reviewed by: not yet reviewed — the specification needs maintainer review.
- History:
  - At `dbceb084161797c6d97994c88a4120aacc0ec7e6`, `merge` returned a single polygon and **FAILED** P4 for inputs that are not 8-connected: `withHalls` skipped (`continue`) a child loop when no horizontal parent edge lay at or above its first vertex. Counterexample: `S = {(0, 0), (2, 0)}`, `k = 0` returned `[(0, 0), (1, 0), (1, 1), (0, 1)]`, so section `(2, 0)` was not drawn. In the exhaustive 4×4 run, 16 293 of 28 339 non-8-connected subsets were affected. Fixed in `1ce2095` by outlining each 8-connected component separately; the counterexample is kept as `testSeparatedSectionsAreAllCovered`.
  - Up to `1ce2095`, each polygon joined its holes to the outer boundary with zero-width bridges. That satisfied P0–P7 but not P8, and FV-002 showed that BlueMap draws the bridges and sometimes fills such polygons incorrectly. Replaced by tracing the outer ring and the hole rings of each 4-connected component and passing the holes to `ShapeMarker.Builder#holes` (available since BlueMapAPI 2.4.0, December 2022).
- Follow-up:
  - Consider extending the exhaustive part (e.g. 5×5) in an opt-in task if more confidence is needed; it is too slow for the normal `test` task.

### FV-002: BlueMap draws the outlines as the sections of the region

- Owner: FoliaRegionVisualizer maintainers
- Target: the outlines of `SectionsToOutlines.merge(LongSet, int)` (FV-001), rendered by BlueMap's web app `ShapeMarker` (`common/webapp/src/js/markers/ShapeMarker.js` at BlueMap tag `v5.29`, commit `a61e143fe1e069c536159e0bc79410d5b6da3885`) with three.js `0.186.1` (earcut `3.0.2`), configured as `CachingVisualizer` does (shape, holes, `depthTest` off, line width 2).
- Property: for every exported input `S`,
  - Fill: every sample point in a section of `S` is covered by exactly one triangle of the markers' fill geometry, every sample point in other sections by none, and the total triangle area equals `|S|` (in section units). 16 sample points per section, at offsets chosen so that no sample lies on a line through two integer points;
  - Border lines: every line segment of the markers' border geometry lies on the boundary of `S` (no unit edge with sections of `S` on both sides, or on neither side).
- Preconditions: as FV-001. The outlines are produced by the production Java code and exported as JSON Lines.
- Scope: BlueMap's actual `ShapeMarker`, `ShapeGeometry` (three.js triangulation) and `Line2` border geometry, bundled with esbuild and executed in headless Chromium through Playwright `1.63.0`. The geometry is checked numerically; no pixels are compared. Inputs:
  - `grid4x4`: every non-empty subset of a 4×4 grid (65 535 inputs, `k = 0`);
  - `folia`: 3 000 regions shaped like Folia's with the default grid exponent, i.e. unions of 3×3 blocks of sections (`emptySectionCreateRadius = 1`), seed `0xF011A`, `k = 8`;
  - `random`: 3 000 random patterns with many holes and corner contacts near the world border, seed `0x5EC71045`, `k = 8`.
- Out of scope: GPU rasterization, BlueMap's map tiles, marker popups and the BlueMap server side (JSON serialization of the API objects); other BlueMap or three.js versions; inputs other than those listed.
- Method/tool: exhaustive and randomized testing in a browser, with three `0.186.1`, vue `3.5.43`, esbuild `0.24.0` and Playwright `1.63.0` (Chromium). The harness (a JUnit class exporting the inputs and outputs of `merge` as JSON Lines, and a script that bundles BlueMap's `ShapeMarker.js` and checks every entry) was run once for this record and is **not** kept in the repository.
- Reproduce: not reproducible from the repository alone. To repeat it, export `SectionsToOutlines.merge` results for the inputs above, build markers with BlueMap's `ShapeMarker#updateFromData` (`shape`, `holes`, `position` = first vertex of the shape), and check `fill.geometry` and `border.geometry` as described under Property.
- Evidence (results of the harness run on 2026-11-02):

  | Outlines | Input | Fill failures | Lines inside the region | Lines outside the region |
  | --- | --- | --- | --- | --- |
  | Bridged polygon per 8-connected component (`1ce2095`) | grid4x4 | 15 | 4 616 | 0 |
  | | folia | 0 | 1 243 | 7 |
  | | random | 224 | 2 681 | 708 |
  | Shape and holes per 8-connected component (diagonal sections in one ring) | grid4x4 | 4 | 0 | 0 |
  | | folia | 1 | 0 | 0 |
  | | random | 222 | 0 | 0 |
  | **Shape and holes per 4-connected component (current)** | grid4x4 | **0** | **0** | **0** |
  | | folia | **0** | **0** | **0** |
  | | random | **0** | **0** | **0** |

  The counts are numbers of inputs. The first two rows were measured with the same harness against BlueMap `main` (`96ae320`), whose `ShapeMarker.js` and dependencies are identical to `v5.29`.
- Status: TESTED (one-off run, not part of CI). Not PROVED: earcut has no documented guarantee for rings that touch at a vertex, which still occurs (a hole touching the outer boundary at a corner becomes part of the outer ring).
- Reviewed by: not yet reviewed.
- History:
  - With bridged polygons, BlueMap drew every bridge as a 2-pixel border line inside the region (in 41% of the Folia-shaped inputs), sometimes across another hole, and earcut left parts unfilled when a hole touched the outer boundary at corners (e.g. the 4×4 pattern `..#./.#.#/#.#./.#..`, where half of a section stayed empty).
  - Passing holes removed every extra line. Keeping diagonally touching sections in one ring still produced fill failures when a hole touched the outer ring at all four corners (`.#../..#./.#.#/#.#.`). Outlining each 4-connected component separately removed them in every tested input; two outlines that touch at a corner are drawn exactly as one.
