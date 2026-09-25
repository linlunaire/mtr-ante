# ANTE 26.2 port — experimental test build

This independent target uses Java 25 / Gradle 9.5.1. The root default remains
Minecraft 1.21.1 / Java 21 / Gradle 8.14.5. Both loader targets compile and package;
this remains an **experimental release**. The scoped NeoForge world test below
does not establish general save-migration or gameplay compatibility.

## Build

Build the sibling `Minecraft-Transit-Railway-3.x.x` 26.2 target first. ANTE compiles
against its common dev JAR and depends on its Fabric/NeoForge release JARs. Use the
freshly rebuilt MTR JAR for the matching loader: ANTE now uses MTR's item and
see-through-text extraction bridge. An older JAR with the same `26.2-3.3.2` version
string is not sufficient. MTR classes are deliberately not shaded into ANTE.
Select another checkout with `-PmtrProjectDir=<absolute MTR checkout path>`.

From the ANTE repository root:

```powershell
./gradlew.bat :common:checkCompatibility '-Version=26.2' -JavaHome '<JDK 25 directory>'
./gradlew.bat build '-Version=26.2' -JavaHome '<JDK 25 directory>'
```

`build` runs compilation, access-widener checks, focused compatibility checks,
static mixin checks and isolated artifact checks before copying the two test JARs
to `build/release`. Install only the matching ANTE/MTR pair, with Architectury and
(for Fabric) Fabric API; exact dependencies are recorded in the loader metadata.
Cloth Config, JPinyin and GraalJS/Truffle are bundled. A separate GraalJS
installation is not required. Do not install both loaders' JARs.
After each loader's replacement JAR passes checks and is copied successfully,
`releaseJar` moves older ANTE JARs for **that loader and Minecraft 26.2 only** into
recoverable subdirectories of `build/release/archive`. Other loaders, Minecraft
versions and unrelated files are preserved. Failed checks/copies do not archive
the previous release. The `checkReleaseArchive` fixture test covers this scope,
missing replacements, archive failures and repeated builds.
Development runtime resolution is checked too: script/search libraries are on both
the development classpath and the release shadow configuration. Architectury owns
the transformed common development module; loader resource roots also contain the
access widener and mixin configuration without duplicating the common classes.

For the Loom compile-classpath archive regression:

```powershell
./gradlew.bat help '-Version=26.2' -JavaHome '<JDK 25 directory>' -I tests/jar-classpath.init.gradle
```

The wrapper routes relative paths to this target directory. GraalJS uses the
concrete `js-language` artifact, not the aggregate POM that Loom attempted to open
as a ZIP. Its existing version remains 24.2.2. Runtime libraries retain service
descriptors and multi-release resources in the shaded artifacts.

The 26.2-only release version is now `1.1.1-26.2-beta.5`; replace earlier beta JARs
rather than installing multiple versions. Since beta.3 it isolates Word, Collections, Native Image
(including `com.oracle.svm.core.annotate`) and Truffle Compiler SDK packages under
ANTE's vendor namespace. GraalVM JDKs already provide those packages, so leaving
them in ANTE's automatic module caused NeoForge's JPMS resolver to reject the
release before mod initialization. Public Polyglot/Truffle runtime names, mixin
targets and Native Image property strings remain unchanged. This preserves the
existing interpreter selection; it does not enable or validate native/JIT paths.

Beta.4 also fixes Camera mixin injection on NeoForge: its patched two-argument
`setRotation` delegates quaternion construction to a three-argument overload.
The shared hook scans all overloads, captures no target arguments and requires
exactly one injection, preserving Fabric behavior and camera-roll ordering.

Beta.5 fixes saved-rail construction: MTR calculates facing angles before Mixin
field initializers run, so ANTE must initialize its default roll map before those
calls. The normal constructor-tail readers still restore saved rolling, curves
and facings. A real Sponge-woven constructor regression reproduces the old null
map failure and checks nonzero rolling metadata after a MessagePack round trip.
It also fixes holding-item updates in editing screens to encode/decode with the
player's registry access, including empty hands and custom item components.

The beta.5 NeoForge 26.2.0.88 client was tested on an isolated copy of the supplied
test railway world. The old beta.4 reproduced zero loaded rails with a null roll
map; beta.5 loaded 4 stations, 6 platforms, 6 sidings, 1 route, 1 depot and all
66 rail nodes / 138 directed connections. The client synchronized 3 trains and
nearby rails. Screenshots confirmed visible rails and textured train bodies;
exiting and reopening the copied world preserved the complete railway graph. This test used
the already-converted world copy after confirming its external MTR files were
byte-identical to the original 1.21.1 world. No original save was rewritten.
The paired MTR build also fixes duplicate background blur in its 26.2 legacy
screen bridge. The same real client test opened the railway dashboard and
confirmed its map and all four station entries on screen, without the former
`Can only blur once per frame` exception. Both updated JARs are needed.
It does not verify all 1.20.1 saves, missing third-party mods, or Fabric gameplay.

`checkHoldingItemPacketCompatibility` and `checkRailConstructorWeaving` are part
of `check`. The latter executes actual Sponge-woven Map/packet/NBT constructors,
and optionally reads every saved rail in a supplied world (without modifying it):

```powershell
./gradlew.bat :common:checkRailConstructorWeaving '-Version=26.2' -JavaHome '<JDK 25 directory>' '-PrailFixture=<world directory>'
```

## Implementation boundaries

Shared Java is copied into `build/generated/sources/ante`, transformed by the
target-specific scripts, then replaced by matching `src/main/java` overlays.
Do not edit generated sources. The 1.21.1 source tree is not rewritten by this port.

The port includes registration/network/NBT/item APIs, resource reload and virtual
item models, GUI extraction/input, smoke particles, world/train/block-entity hooks,
camera roll and preview projection, and script texture lifetime. Model geometry is
captured into immutable CPU snapshots and submitted through the 26.2 rendering
pipeline. Instance transforms are expanded on the CPU; this is not a claim of
native GPU instancing or a measured performance improvement.

Eye-candy resource preparation precedes asynchronous model/atlas loading. PNG and
ordinary item-model references have virtual client-item definitions; raw items use
special item render states with display transforms and centering compensation.
Dynamic textures allocate/upload/release on the client thread; uploads snapshot
ARGB pixels, including RGB images and subimage strides, before queuing work.

Train/addon drawing runs at MTR's global extraction tail, after vanilla block
entities. Earlier seat-entity passes are cancelled before consuming ANTE's queues.
The rail-bounds lambda replacement is an explicit checked overwrite. Camera roll
is applied before vanilla derives direction vectors; preview HUD hiding does not
change the user's F1 option.

## Verification checkpoint — 2026-09-22

The 15 independent `:common:checkCompatibility` groups exercise registration,
materials, face capture, item definitions, dynamic resources, eye-candy resource
preparation, item render states, NBT-to-JSON, legacy NBT reads, API symbols, SOWCER
CPU upload/lifetime, GUI widgets, item interactions, steam smoke and script textures.
They use actual Minecraft/MTR classes where possible, without opening a window.

Full-source checks additionally cover:

- World/script input: projection coordinates, delayed rail-box geometry, legacy
  callbacks and Unicode fallback; real vanilla cube emission into ANTE's five
  render stages and body/four-door partitions.
- Static linkage of all 50 common mixins against vanilla (514 checks) and
  NeoForge-patched Minecraft (516 checks), plus the Fabric-specific mixin (7 checks),
  including the frame-dispatch guard. This is **not mixin weaving**.
- `:fabric:checkCameraWeaving` and `:neoforge:checkCameraWeaving` execute the real
  Sponge Mixin transformer against each loader's Camera bytecode without loading
  game classes. The old beta.3 reproduces the reported injection error; beta.4
  injects exactly once after quaternion setup and before all three direction
  vectors. The standalone `tests/camera-weaving/run.ps1` also verified the user's
  NeoForge 26.2.0.88 patched JAR. This is targeted Camera weaving, not full startup.
- Finished artifacts: metadata/resources, no duplicate entries or shaded MTR
  classes, relocated Cloth Config/JPinyin and packaged GraalJS. An isolated
  classloader containing only the finished JAR and JDK executes JavaScript,
  Nashorn compatibility/Java interop and Pinyin conversion. This test selects the
  interpreter explicitly, matching the intended runtime mixin; it does not test
  ANTE's complete script context. It also checks service-provider type linkage,
  preserved Native Image property names and a sequential callback handoff to a
  worker thread, not concurrent use of one context.
- JPMS resolution of each finished artifact against GraalVM SDK modules and an
  intersection check against all system-module packages. Ordinary JDKs use small
  SDK descriptor fixtures, so the regression is still enforced there. The saved
  beta.2 artifact fails this check; beta.3 passes. This does not launch Minecraft.
- NeoForge's `checkFmlModuleCompatibility` additionally uses the actual FML
  package/service scanner and builds a named module layer containing the finished
  JAR. Packaged GraalJS executes legacy JS resources and `Java.type` from that
  named module. GraalVM's four SDK modules are added to the parent boot layer when
  available; ordinary JDKs exercise the standard-JDK path. The test still selects
  ANTE's existing interpreter explicitly and does not weave mixins or launch FML.

Missing runtime libraries in the first packaged JAR were reproduced by the
artifact test before correction. The frame queue guard also has a
failing-before/passing-after static regression. Gradle, JOML and GraalJS
deprecation/native-access warnings remain visible.

Development-server smoke checks also reached the Fabric EULA gate and NeoForge
mod registration/world initialization. Neither test used the user's game directory.
NeoForge development unexpectedly bypassed the EULA gate, so that temporary server
was terminated and its game/map listeners were verified closed. Generated test
data remains under `neoforge/build/server-bootstrap`, not in a user save.

The optional `tests/server-bootstrap.init.gradle` now permits only Fabric and
refuses an EULA-accepted test directory; it rejects NeoForge to prevent another
bootstrap-only check from opening a world. Architectury's development watcher may
keep Fabric's process alive after the EULA message, requiring the run to be stopped.
This is development startup evidence, not validation of a packaged client or every
lazily loaded mixin. NeoForge reports legacy `@OnlyIn` annotation warnings from
Architectury-transformed GUI classes; Windows performance-counter warnings and
third-party metadata deprecations also remain.

## Remaining game-session coverage

Beyond the scoped NeoForge client test above, check both loaders with freshly
rebuilt MTR in a disposable test instance and a copy of a save. Fabric client
startup and the remaining gameplay/rendering paths are unverified. Exercise original and ANTE trains,
standing/riding and rolling cameras, optimized/unoptimized rails, eye-candy and
direct nodes, PNG/raw item models, smoke, GUI editing, dynamic script textures,
resource reload, reconnect/dimension changes and server packet round trips.
Use a dense save to assess stutter and memory/resource lifetime; compilation and
CPU assertions do not establish visual correctness or performance.
