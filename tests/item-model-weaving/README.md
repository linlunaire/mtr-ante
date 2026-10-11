# Item and model Mixin migration gate

This check runs the real Sponge Mixin transformer on current production class bytes. The fixture configuration references the actual ANTE `ItemWithCreativeTabBaseMixin`, `ItemNodeModifierBaseMixin`, `ItemRailModifierMixin`, and `ModelSimpleTrainBaseAccessor`; it does not substitute test mixins or reconstruct their logic.

It checks these pre-agreed integration seams:

- The brush override is merged onto `ItemWithCreativeTabBase`, retaining server screen, client brush, and superclass fallback paths.
- The direct-node handler is actually injected once at `onEndClick` HEAD, binds its original `isConnector` field, and has a cancellation return before the original connection path.
- Rail modifier overrides are supplied by the actual mixin, both rail directions retain ANTE path-mode/straightness operations, and rail synchronization remains reachable.
- The woven model implements the production accessor interface and has all 10 concrete public Invoker bridges, each delegating once to its matching original method descriptor.

The normal run requires Kotlin metadata on all three item targets and `ModelSimpleTrainBase`, preventing a stale all-Java artifact from silently satisfying the migration gate.

## Build integration

Main class: `cn.zbx1425.mtrsteamloco.compatibility.ItemModelWeavingCheck`.

`compileItemModelWeaving` compiles this directory's `ItemModelWeavingCheck.java` together with the existing `tests/camera-weaving/CameraWeavingCheck.java`, using `configurations.compileClasspath`, UTF-8 and `-proc:none`. `checkItemModelWeaving` runs it and is required by `check` and `build`.

Runtime classpath order:

1. Compiled fixture classes.
2. `tests/item-model-weaving/resources`.
3. `tests/camera-weaving/resources` (the existing global property service).
4. Current ANTE main output classes.
5. `configurations.compileClasspath`, including current MTR common artifacts and Sponge/ASM.

No arguments are needed for the Kotlin migration gate. `--allow-java-model-baseline` is only for characterizing the known-good Java model before its migration; the normal verification task must not use that option.

## Negative controls

The following options mutate only an in-memory test `ClassNode`, never a source file or artifact:

- `--missing-shadow`: removes `ItemNodeModifierBase.isConnector`. Expected nonzero exit with Sponge `InvalidMixinException` stating that the Shadow field cannot be located.
- `--missing-invoker`: removes `ModelSimpleTrainBase.getEndPositions()[I`. Expected nonzero exit with Sponge `InvalidAccessorException` stating that no matching Invoker target exists.

Both controls were run against real production targets and failed for their intended Sponge reason. The default positive case passed all three item transformations and all 10 model bridges against the consumed Kotlin MTR artifact, without the Java-model baseline option.

## Scope

The headless service reads game/MTR bytes and uses actual Mixin application; it does not initialize a Minecraft target class, start a world, construct a game screen, send packets, initialize a GPU, or prove rendered output. Packet/control-flow checks describe the woven result, not execution of a player's interaction. Full loader startup and in-game behavior remain separate checks.
