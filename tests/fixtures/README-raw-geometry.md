# Raw geometry and model aggregation

`Vertex`, `Face` and `RawModel` retain 58 exported JVM contracts from Java ANTE
`1.1.1-26.2`, SHA-256
`db3662a772ad746bcdbfe81b2e44fed09ea084d73f11b29022be9b11e9f4d79c`.
Checks verify the selected class source and Java/Kotlin metadata. Final loader
JARs repeat the ABI and Kotlin-metadata checks.

## Behavior

The 342 records in `raw-geometry-java-1.1.1-26.2.tsv` cover vertex hashing,
NaN/signed-zero equality, deep copies, exact binary bytes, face fan order,
two-sided input-array reversal, integer-range edge cases and real `RawMesh`
deduplication up to 4,096 input vertices. Additional assertions cover nullable
fields, partial I/O, exception identity and virtual callbacks that mutate the
object being hashed. All six hash inputs must be captured before the first
overridable vector hash runs, as they were by Java's `Objects.hash` call.

`checkRawGeometryCompatibility` runs 8,113 assertions. The same corpus runs
with only `Face` assertions enabled (8,114 assertions), then with assertions
globally enabled but disabled specifically for `Face` (8,113). Java's per-class
switch is retained explicitly; Kotlin's shared assertion flag would differ.
Malformed face writes retain partial-byte/failure order when assertions are
off. A null output receiver still fails after argument evaluation.

The 54 records in `raw-model-java-1.1.1-26.2.tsv` cover actual geometry
transformations, material reset, texture replacement, wire bytes, append/copy
ownership, deferred retry and draw dispatch. Tests also verify cold and warm
concurrent supplier access, synchronization, lazy capture, nullable results,
live mesh replacement from material callbacks, and nullable Java overrides.
Upload probes replace GPU work; real CPU packing, retained buffers and delayed
submission are additionally exercised by `checkSowcerCompatibility`.

The face-capture and Sowcer fixtures now compile only their test sources and
load production class output directly. Explicit code-source checks prevent
stale copied Java classes from masking a Kotlin regression.

## Allocation

`checkVertexHashAllocation` measures 32,768 actual hash calls after 4,096 warmup
calls using thread allocation counters under `-Xint`. Vertices use ordinary
JOML-backed vectors and integer fields outside the boxing cache. The original
Java release allocates 3,407,872 bytes (104 bytes/call); Kotlin allocates zero.
The original Java implementation fails the zero-allocation gate when run
without its baseline flag. Input setup, counters and printing are not measured.

The change removes the temporary six-element array and four boxed primitives,
not the mesh's own maps, lists, geometry or material allocations. It preserves
the exact hash polynomial and overflow behavior. This is not an FPS/TPS,
end-to-end deduplication latency or zero-allocation rendering claim; `-Xint`
is a fixture setting, not a recommended game/server flag.

## Running

Current behavior, allocation and ABI checks participate in `build`. Historical
tasks accept `-PjavaBaselineJar=<original-jar>`:

- `checkJavaRawGeometryBaseline`
- `checkJavaRawGeometryAssertionsBaseline`
- `checkJavaVertexHashAllocationBaseline`
- `checkJavaRawModelBaseline`

The baseline files can only be recorded from Java, not from converted classes.
Raw-model aggregation still uses its public mutable map. Material changes can
mutate map keys, newly added materials can fail legacy `reset`, and a failed
deferred attempt does not close earlier mesh results. Those ownership/policy
limitations are retained, not silently redesigned during conversion. No GPU,
Minecraft window, running world or installed game JAR is changed by these tests.
