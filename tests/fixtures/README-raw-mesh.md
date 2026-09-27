# Raw mesh construction and packing

`RawMesh` retains 29 exported JVM contracts from Java ANTE `1.1.1-26.2`,
SHA-256 `db3662a772ad746bcdbfe81b2e44fed09ea084d73f11b29022be9b11e9f4d79c`.
Compiler output and both final loader JARs must retain these contracts and
carry Kotlin metadata. Behavior tests verify the selected class's code source
and language; golden recording is allowed only from Java.

## Behavior and ownership

`raw-mesh-java-1.1.1-26.2.tsv` contains 221 records captured from the original
Java JAR. Each current assertion mode passes 551 assertions. Tests cover:

- Nine scale edge cases, all 64 mirror combinations, chained matrix/rotation/
  shear operations, and five normal-generation cases including degenerate,
  NaN, overlapping and short faces. Float subtraction still precedes double
  arithmetic in normal generation.
- Exact packed bytes for all 128 vertex-attribute combinations, actual retained
  CPU buffers, wire bytes, truncated input and legacy omitted color/light data.
- Deep versus shallow copies, append aliases, live public-list identity, clear,
  triangulation, deduplication and overridable hash callback order.
- Eight render-type inputs, attribute copying before errors, per-face draw
  data, profiling and assertion-controlled nontriangle dispatch.
- Sixteen concurrent cold supplier calls sharing one result; frozen geometry
  and material, capture after packing callbacks, synchronized access, and
  retained supplier identity after the returned mesh is closed.
- Nullable Java overrides, invalid vertex indices, empty operations with
  unused null arguments and failed upload into caller-owned buffers.

The suite runs with assertions off, enabled specifically for `RawMesh`, and
globally enabled but disabled for `RawMesh`. Per-class Java assertion behavior
is explicit, rather than inherited from Kotlin's shared assertion flag.
The original Java release also passes the behavior suite.

## Packing allocation

The vertex loop now indexes shared `VertAttrType.entries`, removing both the
old `values()` array clone and any need for a per-vertex iterator.
`checkRawMeshPacking` selects actual compiled `RawMesh` class bytes, including
its nested classes. Only that class family changes in the comparison; current
Kotlin `Vertex`, `Face`, `MaterialProp` and enum dependencies are shared.
Thus the earlier vertex-hash allocation reduction is not credited twice.

An ASM probe redirects only the packing method's enum `values()` call to a
counter that still performs the original clone. It requires exactly one
Java call site and zero Kotlin call sites. It does not adapt geometry or
packing logic. The private packing method executes normally, but its returned
upload consumer is not invoked during measurement: this measures CPU snapshot
construction and packing, not GPU upload or drawing.

Each size uses four warmups and eight measured packs with position and normal
attributes. Setup, counters and printing are outside the measured window.
Thread-allocation counters under `-Xint` produced:

| Unique vertices | Java bytes/pack | Kotlin bytes/pack | Removed enum clones/pack |
| ---: | ---: | ---: | ---: |
| 96 | 37,600 | 32,992 | 96 |
| 768 | 306,688 | 269,824 | 768 |
| 3,072 | 1,234,120 | 1,086,664 | 3,072 |

The isolated difference is 48 bytes per vertex (144 KiB at 3,072 vertices).
The gate checks zero enum clones, not an elapsed-time threshold. Running the
original Java class without `--baseline` fails this gate with 768 copies in
eight 96-vertex packs. This is not a whole-mesh zero-allocation, FPS/TPS or
gameplay-latency result; `-Xint` is a test setting, not a deployment setting.

## Running and limitations

Current behavior, all three assertion modes, ABI and packing gates participate
in `build`. Optional historical tasks take `-PjavaBaselineJar=<original-jar>`:

- `checkJavaRawMeshBaseline` uses the all-Java geometry implementation.
- `checkJavaRawMeshPackingBaseline` selects only Java `RawMesh`, keeping current
  dependencies to isolate the enum-loop change.

Public collections remain mutable and uncached. Normal generation still
replaces the vertex list and skips faces shorter than three vertices; immediate
upload still aliases the source material, while deferred upload copies it.
Deduplication retains legacy hash callback order rather than combining map
lookups across overridable methods. A supplier still returns its cached mesh
after caller closure. These are preserved contracts, not redesigned policies.
Tests do not force out-of-memory errors or claim to cover every cleanup path.
No GPU context, game process, world save or installed game JAR is modified.

## Face batching adapters

`FaceList` and `BufferSourceProxy` retain eight additional exported contracts
from the same original release. `checkFaceCaptureCompatibility` verifies actual
Kotlin production classes and uses MTR's real 26.2 capture/submission bridge.
`checkJavaFaceCaptureBaseline` repeats the suite with the original Java ANTE
classes, still using the current MTR capture bridge. This is not a comparison
of entire historical client distributions.

The original 15-vertex material/attribute test now also checks that the first
sorting request wins and successful proxy commit resets batch identity. Extra
cases retain frozen summed-position sort vectors despite later vertex edits,
live caller arrays until commit, repeated direct-list commits, stable sorting
of equal sums with different vertex counts, null-key caching, and queued data
after failed proxy commit. Captured snapshots remain immutable after source
mutation; closed extraction scopes reject another commit. No adapter-specific
speedup is claimed from the language migration alone.
