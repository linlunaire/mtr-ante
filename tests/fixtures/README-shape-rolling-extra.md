# Shapes, rolling and extension interfaces

The reference is Java ANTE `1.1.1-26.2`, SHA-256
`db3662a772ad746bcdbfe81b2e44fed09ea084d73f11b29022be9b11e9f4d79c`.
The runners verify selected code sources/language metadata; recording current
Kotlin as the Java reference is rejected. Seven migrated production files
retain 82 exported JVM class/field/method contracts, checked in compiler output
and both final loader JARs.

`checkShapeRollingCompatibility` runs 1,117 assertions and compares 650 records:

- shape geometry at seven rotations, empty/trailing separators, ASCII trim,
  malformed coordinates, NaN/infinity and malformed compound tails;
- 256 seeded rotations in both enabled states, reversed and normal quaternion
  paths, exact float bits and pose-matrix digests;
- consumed-once pending rotation, nullable no-ops, mutable position identity,
  deep copying, source-vector ownership and signed-zero/NaN identity checks.

The test redirects only client configuration reads and logging. It executes
the selected production math and actual Minecraft shapes/PoseStack. Parsing
still constructs every box before performing unions, preserving failure
order. Coordinates use primitive doubles instead of boxed arrays.

## Intentional cache correction

The Java validator looked up a bare shape string but stored a shape-plus-
rotation key. Its 1,024 repeated valid checks therefore reparsed geometry
1,024 times and replaced the cached object. `isValid` now delegates to the
same keyed `getShape` path. `checkShapeCacheReuse` requires zero parse calls
after warming and unchanged shape identity. The original Java fails this
gate. An invalid string equal to another shape's cache key no longer bypasses
parsing accidentally. Cache size and thread-safety are unchanged; no bounded
or concurrent cache is claimed.

## Extension behavior and weaving

`checkExtraSupplierCompatibility` compares 838 Java records, including 512
ordered train-axle queries, rail slope/roll interpolation, reversed direction,
integer overflow before conversion to double, nullable map entries and
non-finite values. Public mutable roll maps are still copied/sorted per call;
a persistent cache would be stale when callers mutate them directly.

The migrated interfaces are `RailExtraSupplier`, `TrainExtraSupplier`,
`RailAngleExtra`, `RailActionsModuleExtraSupplier` and
`VehicleRidingClientExtraSupplier`. Actual rail weaving separately executes
`RailAngleExtra` static dispatch and checks 637 extended rail types in both
Java `values()` and Kotlin `entries`. Both finished loader artifacts repeat
that test, alongside existing save/NBT/packet construction checks.

`checkJavaShapeRollingBaseline` and `checkJavaExtraSupplierBaseline` with
`-PjavaBaselineJar=<original-jar>` rerun the historical behavior. All current
gates participate in `build`. These checks do not exercise camera rendering,
GPU reload, full FML initialization or multiplayer capacity. No game JAR or
save was changed.
