# Route extension migration checks

`RouteWeavingCheck` applies the actual Sponge transformer and executes the real
`RouteMixin` on the real MTR `Route`. Its five golden records were captured
using both original Java artifacts:

- `MTR-neoforge-26.2-3.3.2.jar`: SHA-256
  `15f96a804948735ab69ef717a3f09bafaa9c58a842f25aaf91f0706a4901e8ec`.
- `MTR-ANTE-neoforge-1.1.1-26.2.jar`: SHA-256
  `db3662a772ad746bcdbfe81b2e44fed09ea084d73f11b29022be9b11e9f4d79c`.

Before weaving, raw class bytes from the classpath must match the selected
artifacts, and Kotlin metadata must match the selected baseline/current mode.
An isolated class loader owns the transformed Route and all its nested classes.
No Route, path-data, Mixin or network encoding implementation is replaced with
a test double. Only the real encoded buffers are used; no connection or world
is opened.

Scenarios cover all constructors, per-route path-list initialization, absence
of shadow-field initializer corruption, list identity, opposite-platform
filtering, repeated setter appends, empty replacement, null/partial setter
failure, exact MessagePack/packet tails, negative path counts, truncated packet
rejection and partial retention/diagnostics after malformed saved path data.
The golden contains complete encoded bytes, not rounded geometry or counts only.

`checkRouteWeaving` and `checkKotlinRouteAbi` run during the common `build`.
Each loader's `checkPackagedRouteWeaving` uses its actual finished ANTE JAR and
matching MTR release JAR, including the real loader compile classpath.
`checkJavaRouteWeavingBaseline` is enabled with both
`-PjavaBaselineJar=<original-ante-jar>` and
`-PmtrJavaBaselineJar=<original-mtr-jar>`.

The standalone runner accepts `--missing-method` or `--missing-field` as negative
controls. Removing `writePacket` fails real injection with
`InvalidInjectionException`; removing `platformIds` fails shadow resolution
with `InvalidMixinException`. Record mode additionally requires
`--java-baseline`, and never runs in ordinary verification tasks.

The Kotlin shadow field is protected instead of Java package-private; it remains
an implementation detail with the same JVM name/type and no generated accessors.
Public interface descriptors and overridability are checked independently.
Existing 12-scenario depot generation and path-weaving checks exercise the new
route extension in their integrated call paths. Game startup, world behavior,
GPU rendering and server capacity still require separate runtime validation.
