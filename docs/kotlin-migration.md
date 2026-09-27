# Kotlin migration checkpoint

The shared [migration plan](https://github.com/linlunaire/Minecraft-Transit-Railway/blob/codex/kotlin-26.2-preview/docs/kotlin-migration.md)
targets all or the overwhelming majority of production code. ANTE's Java
baseline is the immutable [`1.1.1-26.2`](https://github.com/linlunaire/mtr-ante/tree/1.1.1-26.2)
tag; conversion does not itself establish an FPS/TPS improvement.

ANTE remains an MTR add-on, not a standalone railway mod. This preview requires
the matching MTR Kotlin line (`26.2-3.4.0-kotlin.1` or newer) and Kotlin LunaCore
`0.2.0` on the same loader. The Java `26.2-3.3.x` maintenance line is not compatible
with ANTE's Kotlin-companion Mixin targets. Kotlin LunaCore's public branding and
repository changed; its runtime mod ID remains `transit_core` for compatibility.

All 32 top-level Sowcer production classes, both path-generation classes,
`IRoute`, `RouteMixin`, `DepotMixin`, `Tree`, `RelativePosition`, `Rolling`,
`ShapeSerializer`, five rail/train/vehicle extension interfaces and
`RailwayDataRailActionsModuleMixin`, `DynamicResource`, `EyeCandyItemResources`
and both `EyeCandyProperties` and `RailModelProperties`, plus the raw-geometry
`Vertex`, `Face`, `RawMesh` and `RawModel` aggregate, plus `FaceList` and
`BufferSourceProxy`, `RawMeshBuilder`, `AtlasSprite`, `NmbModelLoader` and
`ObjModelLoader` and `CsvModelLoader`, are now Kotlin. Gameplay, script integration,
rendering and other packages remain migration work.
The production-source inventory is 64 Kotlin and 223 Java files (4,644 Kotlin and
34,566 Java physical lines), excluding the generated `BuildConfig.java` and its template;
this is not completion of the repository-wide language target.

## Architecture alongside conversion

All three raw-model import formats (CSV, OBJ and NMB) now have Kotlin entrypoints
behind the existing Java-compatible static API. CSV's separate pinned-source
oracle retains the historical parser, primitive geometry, locale, per-line
recovery and resource/atlas behavior. Ordinary verification consumes committed
goldens; the historical Git blob is required only for explicitly regenerating
the oracle. See [the CSV loader checks](../tests/fixtures/README-csv-model-loader.md).

`ModelVariantPreparation` is a new Kotlin policy module used by the existing
`EyeCandyRegistry` and `RailModelRegistry`. Its single preparation entrypoint
borrows a template and JSON definition, makes one owned copy, and applies the
existing texture, UV, geometry and identity rules. The geometry policy explicitly
distinguishes decorations from rails; rails still ignore geometric options.
Resource discovery, atlas IO, GPU upload and cache lifetime remain with callers.
The registries are still Java and are not counted as converted classes.

This boundary prevents the two registries from diverging on copying and common
transforms, and allows ownership/failure tests without a running game or GPU.
Mutable geometry/material containers and vectors are isolated; the existing
matrix callback remains shared according to the original copy contract.
No additional cache, coroutine scheduler or dependency-injection framework is
introduced. Future preview/cache lifetime changes require their own identity
and invalidation design rather than being bundled into this extraction.

The 166-record oracle was captured from the pinned Java release before the
extraction. It exercises both actual registry entrypoints; the direct module
tests also compare 128 combinations with Java captures, then check copy failures,
partial-transform failures, retry, template/JSON preservation and mutable-data
isolation. Compiler output and both final loader JARs execute the tests, with
code-source checks for the selected implementation and its dependencies. See
[the variant preparation notes](../tests/fixtures/README-model-variant.md).
This is an architectural improvement, not evidence of higher FPS or successful
in-game resource reload.

`DepotRoutePlan` also separates main-route capture and computation from the
worker lifecycle. It captures and copies cached path metadata before the
callback; single-owner `assemble` consumes mutable preparation state, handling
route connections, same/opposite joins and stop indices. It is not idempotent
or concurrent; new requests and retries capture fresh plans. Metadata belongs
to the captured request and can be shared across repeated assemblies of that
same plan; rail/platform objects are borrowed.
`DepotPathGen` retains callback-before-start and live siding/depot-setting reads.
Managed notifications now use guarded owner-thread publication; unregistered
direct calls retain worker-side packets. No extra metadata copy or worker pool
is introduced.

The twelve original-Java scenarios still run through the helper and actual
`DepotMixin`; direct calculation tests compare their path-result projection and
check capture ownership, read/copy timing and failure stages. A separate
assertion-only check preserves callbacks that call `Thread.run()` before the
generator calls `Thread.start()`. The original fixture is unchanged, and both
final loader checks run the selected plan classes without development-output
fallback. See [the depot path notes](../tests/depot-path/README.md).

MTR's Kotlin `PathGenerationTask` now retains cancellation beyond worker exit,
checks siding binding/request ownership and guards queued state/status updates.
ANTE's search and route assembly cooperate with interruption, while its worker
handles cancellation separately from failure. Additional headless checks run
these contracts through the actual generator, real DepotMixin and final loader
artifacts, without rewriting the Java computation goldens. World-unload cleanup,
immutable input capture and bounded admission remain outstanding; this does not
establish full thread safety or multiplayer capacity.

## Compatibility choices

- NMB/OBJ loaders retain 81 original-Java records, 172 assertions and 12 JVM
  contracts, including static Java entrypoints and checked exceptions. Real AES
  and OBJ/MTL parsing cover binary geometry, materials, exports and failure/stream
  ownership. Both final loader JARs repeat the corpus with 180 assertions,
  including the actual source of geometry dependencies and the relocated parser.
  NMB's early header failures still precede stream closing; OBJ's
  non-owning overloads still leave closure to callers. Neither the file format
  nor the bundled parser is redesigned. See [the loader notes](../tests/fixtures/README-model-loaders.md).

- Mesh builder/atlas sprite conversion retains 228 original-Java records,
  441 assertions and 28 exported contracts. Tests cover mutable list/vector
  aliases, initial versus reset defaults, failure side effects, UV float bits
  and warning order. The original atlas V-coordinate denominator and unused
  rotation flag are preserved, not treated as permission for an algorithm
  rewrite. These are CPU-only checks, not rendering or performance evidence.

- Formerly open Java classes/methods remain overridable. Java fields, static
  entrypoints, checked exceptions and record component methods retain their
  exported JVM contracts. Explicit record equality/hash/string implementations
  retain Java semantics, including NaN and signed zero.
- Kotlin has no Java package-private visibility. `VertBuf.retain`, `release`
  and `snapshotUpload` are now protected, with unchanged virtual JVM names and
  synchronized access. Synthetic internal bridges serve Kotlin callers without
  per-call wrapper allocation. Its `Upload` record is public so this bridge
  needs no unsupported visibility-error suppression. These are deliberate
  visibility expansions of implementation details, not new stable extension APIs.
- Beam factories retain their old nullable-texture behavior despite stricter
  Minecraft annotations. A private erased-type bridge forwards the reference
  unchanged; regression tests cover Java/Kotlin cache identity and both nullable
  factories. Single-argument cutout factories still reject null.
- Existing stream ownership, first-close failure ordering, mutable defaults,
  serialization bytes and callback side effects are preserved. Known resource
  provider stream-lifetime issues are not silently fixed in language conversion.
- MTR's path implementation now lives in `PathFinder.Companion`. ANTE injects
  there once instead of into only the Java static bridge, which Kotlin callers
  would bypass. Both call forms therefore reach the same cancellable handler.
  `PathData` retains its original fields for the four production accessors.
- `BetterPathFinder` retains ANTE's distinct angular comparison and turn-back
  policy, rather than silently adopting MTR's policy. Its approximate 45-degree
  threshold and lack of automatic links between separate runways are preserved.
  A candidate pass now lazily computes path membership once instead of once per
  eligible edge. In the 400-edge/32-branch regression, comparisons fall from
  12,800 in the original Java release to 400, with unchanged search results.
- `DepotPathGen` retains copied cached paths, reference-based platform joins,
  synchronous callback-before-start, worker-side diagnostics and snapshot
  versus live depot settings. Managed result packets move to the owner thread;
  direct unmanaged calls retain legacy packet threading. A worker per generation remains
  the legacy contract; this is not yet a bounded route-generation scheduler.
- `RouteMixin` retains actual constructor-tail initialization, partial load
  failures, appended platform IDs, reverse-platform filtering and retained path
  list references. Its shadow field becomes protected rather than Java
  package-private, with the same JVM name/type and no generated accessors.
  Route entries and destination strings remain nullable where Java allowed it;
  depot generation still fails at the corresponding use rather than earlier.

- `RailTypeMixin` updates Kotlin `$ENTRIES` as well as Java `$VALUES` after
  extending the enum. Actual weaving verifies all 637 types in both views;
  the previous values-only mutation failed the Kotlin entry check.
- Shape validation now uses the same shape-plus-rotation cache key as shape
  retrieval. This deliberately fixes repeated parsing and erroneous bare-key
  cache hits. The cache remains unbounded and non-concurrent, as before.

## Verification

`build` checks frozen exported JVM contracts in compiler output and both final
loader JARs. Final artifacts must carry Kotlin metadata for migrated classes,
and must not bundle duplicate Kotlin LunaCore or Kotlin runtime classes.

The Java maintenance fixes through `origin/26.2` commit `eb77f9bb` are retained:
new and deserialized rails initialize extension defaults at explicit constructor
hooks, and the actual riding-state Mixin is exercised before its nullable-player
guard. `checkRidingStateWeaving` checks independent boarding metadata, neutral
roll and camera state; both loaders repeat it against their final shaded ANTE
and matching MTR artifacts. Packet sources and loader registration retain the
26.2 maintenance implementations; this is not proof of live proxy/network behavior.

Java interoperability fixtures cover math, vertex layouts, model/batch ownership,
record behavior, synchronized buffer dispatch, shader cache/reload, and actual
native allocation/free. They can also run against the original Java JAR with
`-PjavaBaselineJar=<path>` and the `checkJavaMathBaseline`,
`checkJavaVertexBaseline`, `checkJavaModelBatchBaseline`, and
`checkJavaSowcerRemainingBaseline` tasks. Code-source assertions prevent an
accidental comparison of the Kotlin implementation with itself.

`checkAntePathCompatibility` runs the shared graph corpus with the actual ANTE
policy and its separate 170-record Java golden. `checkAntePathScaling` gates
repeated membership work, not elapsed time. `checkDepotPathCompatibility` applies
real Route/PathData/path-finder Mixins, executes the generator and joins its
worker across 12 Java-baseline scenarios. World height, siding dispatch and
packet I/O use headless adapters, not a running server. Optional historical
checks are `checkJavaAntePathBaseline`, `checkJavaAntePathScalingBaseline` and
`checkJavaDepotPathBaseline`; see [the fixture notes](../tests/depot-path/README.md).

Shapes/rolling retain 650 Java geometry/rotation records and 1,117 assertions;
the five extension interfaces add 838 helper records, including 512 ordered
train-axle queries. Their 82 JVM contracts, exact math, nullable failures and
live map ownership are checked. Warm shape validation drops from 1,024 parses
to zero in a 1,024-call operation-count gate, with the original Java rejected.
See [the fixture notes](../tests/fixtures/README-shape-rolling-extra.md).

`checkRailActionsWeaving` applies the real Kotlin rail-action Mixin and executes
MTR's 21-record Java queue corpus plus live getter/update assertions. Both
final loader JAR pairs repeat it with `checkPackagedRailActionsWeaving` and
class-byte provenance checks. The Mixin retains six exported contracts;
queue policies and block-editing behavior are not redesigned. Actual block
edits and packet transmission are test adapters, not an active world.

The dynamic resource/item-preparation/property chain retains 39 JVM contracts,
10 Java golden records and 50 property assertions. Actual resource managers,
the client-item loader and the items-atlas directory source verify priority,
PNG/model mapping, lazy stream ownership and reload isolation. Resource-manager
access stays an internal synthetic seam, while private pack overrides enforce
Minecraft's non-null argument contracts. The nullable supplier path remains
compatible. Rail-model properties add 86 original-Java geometry records and
550 assertions for rotation, packed heights, mutable references and actual
manager caching/failure order, using a GPU-upload adapter. See
[the resource/property notes](../tests/fixtures/README-dynamic-properties.md).

Raw geometry/model aggregation adds 58 JVM contracts and 396 original-Java
records. Tests retain exact floating-point/hash/wire behavior, three assertion
modes, mutable aliases, synchronized cold/warm uploads, failure/retry order and
live material callbacks. Vertex hashing now captures its six inputs without
an array or boxing: 104 to zero bytes per call under `-Xint`; the original Java
fails the new allocation gate. Face-capture and CPU-upload fixtures load actual
production classes with provenance checks. See
[the raw-geometry notes](../tests/fixtures/README-raw-geometry.md) for scope and
preserved ownership limitations.

`RawMesh` adds 29 JVM contracts, 221 Java records and 551 assertions in each
of three assertion modes. All 128 vertex-attribute layouts retain exact packed
bytes, alongside geometry transforms, serialization, mutable aliases and
concurrent deferred snapshots. Shared enum entries eliminate one array clone
per packed vertex: 48 bytes/vertex under `-Xint`, or 144 KiB per 3,072-vertex
fixture pack. Both versions use current geometry dependencies in this isolated
allocation comparison, so the previous vertex-hash optimization is not counted
again. The Java implementation fails the zero-clone gate. See
[the raw-mesh notes](../tests/fixtures/README-raw-mesh.md) for measurement scope.

`FaceList`/`BufferSourceProxy` add eight exported contracts. The face-capture
suite runs both original Java and current Kotlin through the real 26.2 submit
bridge. It retains first-request sorting policy, summed-position stable sorting,
frozen sort keys with live vertex data until commit, direct-list replay and
proxy failure retention. Successful proxy commits still clear their builders;
snapshots remain immutable after capture. These adapters are a language
migration, not a separate measured performance improvement.

The native-allocation fixture explicitly uses LWJGL's system allocator so it
does not probe optional jemalloc binaries. This changes only that test JVM,
does not suppress library errors, and still requires working core native code.

`checkRouteWeaving` executes the production Kotlin route mixin and compares five
exact save/wire/mutation records with the original all-Java MTR/ANTE pair. Both
loaders' `checkPackagedRouteWeaving` repeat this against their finished release
JARs; raw class-byte and Kotlin-metadata checks prevent accidentally testing a
stale classpath. Missing method/field negative controls fail actual Sponge
injection. See [the fixture notes](../tests/route-weaving/README.md).

`checkDepotWeaving` executes the same 12 generation scenarios through the actual
Kotlin Depot/DepotMixin entry point, not just the helper. Both loaders repeat
this against finished artifacts with `checkPackagedDepotWeaving`. Class-byte
provenance is checked for the selected target and mixin. The headless world
adapter uses common Minecraft classes, not NeoForge-patched world bootstrap:
real FML startup still needs an in-game check. Removing the target method is a
negative control and must fail actual injection. MTR's new shared RailAngle
entries also remove the array clone from both path finders' turn-loop bounds;
the 170-record path goldens remain the behavior gate.

The resource-tree/relative-position batch retains 53 JVM contracts, with 42,102
assertions and 164 original-Java records. Sibling duplicate-name work changes
from repeated list scans to a local frequency map (16,777,216 list visits to
12,288 hash probes for 4,096 nodes). Signed-byte position hashing retains the
same result without the temporary varargs array: 32 to zero bytes per call
under `-Xint`. Public mutable data remains uncached, and legacy copy-parent,
short-key collision and live-name suffix behavior is preserved. Both original
Java implementations fail the new optimization gates. See
[the resource-tree fixture notes](../tests/fixtures/README-resource-tree.md)
for measurement scope and preserved limitations.

The actual Sponge transformer applies three item Mixins, all ten train-model
Invokers and both path Mixins to Kotlin MTR targets. `checkPathWeaving` also
executes Java and Companion dispatch and the four woven field accessors in an
isolated class loader. Headless weaving and geometry tests do not prove
full loader initialization, world behavior, resource reload on a GPU, or server
capacity. The installed game JARs and saves have not been replaced by this work.
