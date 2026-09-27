# ANTE path migration checks

Both ANTE path classes now use Kotlin. The checked-in ABI and behavioral
baselines come from `MTR-ANTE-neoforge-1.1.1-26.2.jar`, SHA-256:

`db3662a772ad746bcdbfe81b2e44fed09ea084d73f11b29022be9b11e9f4d79c`

## Search and repeated work

`checkAntePathCompatibility` compiles MTR's shared graph fixture and supplies
the actual `BetterPathFinder` through a test-only interface. There is no copied
search implementation. Its separate 170-record golden covers ANTE's existing
policy: in particular, separated runways fail instead of acquiring MTR's
automatic flight connection. The fixture has 201 assertions, 128 branching
graphs and a 2,000-edge line. The selected implementation's code source is
verified before execution.

`checkAntePathScaling` constructs a 400-edge approach and 32 dead-end branches
with an unreachable destination. A `BlockPos` subclass counts real equality
calls at the branching node; it delegates equality unchanged. The original Java
and unoptimized Kotlin versions each used 12,800 comparisons. Computing the
unchanged path-membership result once per candidate pass reduces this to 400.
The regression limit is 500, and the unreachable result must stay unchanged.

This removes repeated membership scans and per-edge streams, not the DFS
algorithm. Worst-case path exploration is not now linear, and no timing, FPS,
TPS or multiplayer-capacity claim follows from the count.

## Depot generation

`DepotRoutePlan` is the Kotlin computation module behind the generator. Its
`capture(routeIds, dataCache)` interface runs on the caller, resolving all
route/platform lists before copying cached `PathData` metadata. The worker
then calls `assemble(rails, altitude, fast)` to join cached/search-generated
routes, normalize same/opposite rails, and assign stop indices. It returns the
main path, merged platform count and borrowed first/last platform references.
Thread creation, callback registration, live siding/depot settings, siding
dispatch and completion packets remain in `DepotPathGen`.

A plan is a single-owner, request-owned mutable computation, not a complete
immutable world snapshot: rail/platform objects remain borrowed, and the graph
is read at assembly time. Metadata is isolated between independently captured
requests. Assembly modifies its captured state; repeated calls can share
metadata with earlier results and are not idempotent, concurrent-safe or a safe
retry interface. Fresh requests/retries must capture a new plan, including
after failure. Repeated calls are nevertheless not rejected or re-copied,
preserving the legacy public callback's ability to call `Thread.run()` before
the generator calls `Thread.start()`.

`checkDepotRoutePlan` uses the same scenario builder and actual woven path
finder as the worker checks, but calls this computation interface directly,
without starting a worker or invoking siding/packet adapters. Its twelve
outputs are projections of the unchanged original-Java captures, not goldens
generated from the new module. The filtered-siding case uses the same main
path as the identical route input in the normal case. Empty input and the
missing-platform failure are checked separately. Additional direct contracts
cover metadata/list ownership, retained rail/platform identity, capture before
source mutation, all-route resolution before metadata copying, live graph
lookup, equal-ID/different-reference platform joins, capture-versus-assembly
failure timing, and fresh capture after failure.

`checkDepotPathCompatibility` reads the selected real generator class bytes,
then executes them in an isolated class loader with the actual Sponge-woven
Route, PathData and path finder. Original Java and new Kotlin use the same
current MTR/ANTE integration environment; this isolates generator equivalence,
not a complete historical game installation.

Only external effects use test adapters:

- A constructor-free concrete `Level` subtype supplies a fixed maximum height;
  Minecraft registries are bootstrapped, but no world is opened.
- A real `Siding` subclass captures generation arguments and returns a selected
  result or throws, without generating an actual siding route.
- An in-memory packet class records destination, depot ID, result and worker
  thread; it does not serialize or send a multiplayer packet.

Twelve Java golden scenarios cover same/opposite-direction joins, short cached
paths, connections between routes, uncached search, missing platforms, siding
failure, no routes, filtered sidings, cruise-height threshold and callback-time
mutation. Additional assertions verify cached-object isolation, original input
metadata, one completion notification, callback-before-start, callback exception
identity and an unstarted worker after callback failure. Threads are joined
with a bounded timeout. Errors are captured only inside this test process and
must be present in expected failure cases.

An extra assertion-only callback-reentry case runs on both original Java and
the new generator, without changing the twelve-record fixture: calling
`Thread.run()` in the callback completes inline before `Thread.start()` runs
it again. It checks caller/worker packet ordering, two siding dispatches and
the legacy consumed-path result on the second execution.

The generator still creates one platform thread per request. Calls registered
by MTR's railway Module now share `PathGenerationTask` cancellation, and queue
guarded success/failure notifications on the owner executor. Direct unmanaged
calls retain legacy worker-side notifications (including callback reentry).

Current Kotlin checks additionally exercise actual worker cancellation before
start and during siding dispatch, interrupt retention, failure versus
cancellation diagnostics, and notification invalidation after worker exit.
Only the executor supplied to production publication is substituted with a
controllable queue; its request guard and notification action execute unchanged.
The same checks run through real DepotMixin injection and both final artifacts.
The shared search fixture also interrupts during graph lookup and requires an
early cancellation exception, not an ordinary empty-path result.

These checks do not prove immutable world inputs, unload cleanup, bounded
concurrency, or server capacity. Those remain separate scheduling/runtime work.

`checkDepotWeaving` repeats the same scenarios through actual Sponge-injected
`Depot.generateMainRoute`, exercising Mixin cancellation of the original method
body, the Kotlin receiver cast
and the helper call. It verifies selected Depot/DepotMixin class bytes and
requires a Kotlin target. Both loaders' `checkPackagedDepotWeaving` use their
finished MTR/ANTE JARs first on the classpath, with common Minecraft libraries
for headless world bootstrap. This intentionally does not initialize patched
NeoForge world classes, which require a running FML loader. The ordinary
loader/bootstrap checks remain separate.

The same final-artifact task also runs the direct plan contracts. The runner
loads all generator/plan classes, including nested classes, from the explicitly
selected ANTE artifact; missing plan classes cannot fall back to development
classes. It verifies Kotlin metadata and the loaded plan's code source. Java
baseline verification/recording checks the pinned release SHA-256 above.

Runner option `--through-depot --target=<MTR-artifact>` selects the injected
entry point; add `--packaged` for a Kotlin ANTE JAR. Adding
`--missing-depot-method` removes the target method before transformation and
must fail with an actual Sponge injection error. It must never reach a passing
golden comparison.

`--direct-plan` selects only the computation interface; `--check-plan` appends
the direct checks to the existing worker/injected-entrypoint run, reusing the
same isolated weaving loader.

## Running

All of these gates run in `build`:

```text
:common:checkKotlinPathAbi
:common:checkAntePathCompatibility
:common:checkAntePathScaling
:common:checkDepotPathCompatibility
:common:checkDepotRoutePlan
:common:checkDepotWeaving
:common:checkKotlinDepotAbi
:fabric:checkPackagedDepotWeaving
:neoforge:checkPackagedDepotWeaving
```

With `-PjavaBaselineJar=<original-jar>`, the optional tasks
`checkJavaAntePathBaseline`, `checkJavaAntePathScalingBaseline` and
`checkJavaDepotPathBaseline` run historical implementations directly. Ordinary
CI uses the committed goldens and needs no historical local JAR. The private
recording workflow refuses a Kotlin depot implementation; do not regenerate
goldens to conceal a regression.
