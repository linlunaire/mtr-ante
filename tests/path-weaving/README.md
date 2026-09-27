# Path Mixin migration gate

This fixture applies the real Sponge transformer to current production path classes using the actual ANTE `PathFinderMixin` and `PathDataAccessor`. It then executes the woven result in an isolated class loader. It does not replace either path finder with a test implementation.

## Integration contract

Kotlin's `@JvmStatic` methods leave Java-compatible static bridges on `PathFinder`, but Kotlin callers use `PathFinder.Companion` directly. ANTE therefore intercepts the **single Companion implementation**, not both classes. This preserves Java calls, direct Kotlin calls and the implementation's internal append call without double interception.

The fixture checks:

- Kotlin metadata on `PathFinder`, its Companion and `PathData`; stale Java artifacts cannot satisfy the migration gate.
- Original non-final static Java bridges delegate exactly once to the intercepted Companion methods. The original Kotlin `findPath` body also calls that same instance `appendPath` method.
- Sponge applies exactly one instance HEAD handler per public method, and cancellation returns before the original MTR body. The outer class receives no duplicate injection.
- Both Java static and direct Companion calls execute the real production `BetterPathFinder` exactly once, while counters confirm that the cancelled MTR body never executes.
- The ANTE finder and its Companion are Kotlin too; their bytecode is loaded in the same isolated loader as the woven MTR path data so the fixture does not accidentally execute a stale Java helper.
- Executed cases include clearing an empty route, appending a nullable entry, deduplicating an adjoining same-direction rail while retaining the original object, retaining a reverse rail, and clearing an empty partial path.
- All four actual PathData accessors are generated against the correct fields. The three `@Mutable` setters remove `FINAL` and update the original public fields; the ending-position getter preserves both object identity and null.

Counters are inserted into in-memory class bytes solely to observe real method entry. Sources, build artifacts and production method bodies are not replaced or written by the test.

## Running

The `:common:checkPathWeaving` verification task compiles and runs the fixture. Main class: `cn.zbx1425.mtrsteamloco.compatibility.PathWeavingCheck`.

For standalone compilation, compile `PathWeavingCheck.java` together with the shared `tests/camera-weaving/CameraWeavingCheck.java`, using the current compile classpath, UTF-8 and `-proc:none`.

Runtime classpath order:

1. Compiled fixture classes.
2. `tests/path-weaving/resources`.
3. `tests/camera-weaving/resources` (the shared global property service).
4. Current ANTE main output classes.
5. The compile classpath, including current Kotlin MTR artifacts, Kotlin stdlib, Sponge and ASM.

The normal invocation takes no arguments and must not use the historical Java MTR artifact.

## Negative controls

Each option mutates only an in-memory test class and must exit nonzero:

- `--missing-method`: removes the public Companion `findPath` implementation. Actual Sponge application fails with `InvalidInjectionException` because its injection target is missing.
- `--missing-field`: removes `PathData.savedRailBaseId`. Actual Sponge application fails with `InvalidAccessorException` because the mutable setter has no matching field.
- `--broken-java-bridge`: redirects the Java bridge away from the intercepted Companion. The explicit dispatch assertion fails before weaving.

The positive case and all three controls have been executed against the migrated Kotlin targets. The standalone invocation is `java -cp <runtime-classpath> cn.zbx1425.mtrsteamloco.compatibility.PathWeavingCheck`, optionally followed by one negative-control argument.

## Scope

This check initializes and executes the path classes and `BlockPos`, but does not start Minecraft, create a world, initialize a GPU, send packets or touch saves. It establishes real Mixin binding, dispatch, cancellation and accessor behavior, not full loader startup or route-scale performance. Complete path-search and serialization equivalence is covered separately by MTR's Java-baseline path fixture.
