# Dynamic resources and eye-candy properties

`DynamicResource`, `EyeCandyItemResources`, `EyeCandyProperties` and
`RailModelProperties` are Kotlin. Their 39 exported JVM contracts come from
Java ANTE `1.1.1-26.2`, SHA-256
`db3662a772ad746bcdbfe81b2e44fed09ea084d73f11b29022be9b11e9f4d79c`.
Checks select real compiler output or the original JAR and verify language
metadata. Final Fabric and NeoForge JARs repeat the ABI/metadata checks.

`checkDynamicResourceCompatibility` uses actual Minecraft resource managers:
lookup, priority, overrides, namespace snapshots, directory prefix filtering,
reload isolation and pack metadata are exercised. Suppliers remain lazy;
stream close ownership, thrown exception identity and nullable supplier
forwarding are checked. No client singleton, graphics window or GPU is needed.

`checkEyeCandyItemResourceCompatibility` loads six virtual items through the
real client-item loader, parses generated model JSON, and runs the actual
items-atlas directory source. It verifies original-pack priority, raw-model
exclusion, duplicate preparation, PNG suffix mapping, resource stacks and
reload-local texture suppliers. The existing constructor hook is checked in
compiled bytecode, not executed by a real game resource reload.

`checkEyeCandyPropertiesCompatibility` adds 10 Java golden records and 50
assertions: all constructor fields/flags, nullable strings, retained mutable
references, the default singleton and the precomputed path. Closing still
closes only the main model, repeats on repeated calls and propagates its
failure unchanged; it does not take ownership of the item model. Tests use
a close-counting model and constructor-free script holder, not a GPU upload or
script runtime. The item-render check now examines compiled property fields
instead of expecting a Java source file.

`checkRailModelPropertiesCompatibility` adds 86 Java records and 550 assertions.
The actual constructor, raw geometry, material attributes and model manager
execute. Exactly one client-singleton field read is adapted, and a raw-model
subclass replaces only GPU upload while retaining clear/rotation dispatch.
Tests cover nullable fields, signed zero, NaN/infinities, seeded vertices,
empty geometry, height bits, reference identity, cache hits and failures at
each step. The legacy angle conversion and repeated in-place rotation before
cached uploads are deliberately unchanged. This is not a renderer/GPU test.

Historical tasks are `checkJavaDynamicResourceBaseline`,
`checkJavaEyeCandyPropertiesBaseline`, `checkJavaEyeCandyItemResourcesBaseline`
and `checkJavaRailModelPropertiesBaseline`,
with `-PjavaBaselineJar=<original-jar>`. Current checks participate in `build`.

## Internal seam and nullability

Kotlin cannot express Java package-private visibility. The resource-manager
entry point keeps its JVM name/descriptor as a synthetic internal static
bridge. Kotlin item preparation calls it directly; the Java regression invokes
it reflectively. It is not a new supported public Java API.

The private dynamic-pack implementation now enforces Minecraft's non-null
parameters for `getResource`, `listResources` and `getNamespaces`; passing null
to those annotated game APIs can therefore fail earlier than in Java. The
outer nullable supplier contract remains intact, including forwarding a null
supplier to Java listing callbacks via a private erased-type bridge. Cache
lifetime, single-manager ownership and thread safety are unchanged.

These checks establish resource/field behavior, not in-game baking, shader
reload, scripting, FPS/TPS or deployment readiness.
