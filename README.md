# MTR-ANTE

Custom train and rail models, JavaScript-driven rendering, decorative objects and rail editing tools for Minecraft Transit Railway.

**Aphrodite's Nemo's Transit Expansion**, ported to **Minecraft 26.2** for **Fabric** and **NeoForge**. This community fork extends the [MTR 3 community port](https://github.com/linlunaire/Minecraft-Transit-Railway); it is not a standalone mod or an MTR 4 add-on.

## Install

This is an incomplete **Kotlin preview**, not a production-validated release. Use Java 25 and install ANTE together with the **26.2 MTR Kotlin preview** and [Kotlin LunaCore 0.2.1+](https://github.com/linlunaire/Kotlin-LunaCore), using the same loader for all JARs.

| Loader | Required mods |
| --- | --- |
| Fabric | MTR, [Fabric API](https://modrinth.com/mod/fabric-api), [Architectury API](https://modrinth.com/mod/architectury-api) |
| NeoForge | MTR, [Architectury API](https://modrinth.com/mod/architectury-api) |

Place the JARs in `mods` on the server and clients. ANTE's scripting runtime and configuration library are bundled; no separate installation is needed. Players using custom models also need the corresponding resource packs.

The Kotlin standard library is provided by Kotlin LunaCore through loader-managed nesting, not bundled again in ANTE. No separate FLK/KFF installation is needed for our mods; keep those dependencies if other mods require them.

This preview requires **MTR 26.2-3.4.0-kotlin.3 or newer**; its Kotlin Mixin targets are not compatible with the Java maintenance build. **Kotlin LunaCore** supplies frame membership tracking and bounded background task scheduling; neither MTR nor ANTE bundles another copy. ANTE remains an MTR addon. Build matching sources in dependency order.

Back up worlds, configuration and resource packs before upgrading. Test your existing routes, custom trains and scripts on a copy of the world first.

## Build from source

Install **JDK 25** and set `JAVA_HOME`. Clone [Kotlin LunaCore](https://github.com/linlunaire/Kotlin-LunaCore), the [MTR community port](https://github.com/linlunaire/Minecraft-Transit-Railway) and ANTE as siblings:

```text
workspace/
├── Kotlin-LunaCore/
├── Minecraft-Transit-Railway-3.x.x/
└── mtr-ante/
```

Build Kotlin LunaCore, then MTR, then ANTE. Run the same command from each repository root:

```sh
./gradlew build
```

On Windows, use `./gradlew.bat` in place of `./gradlew`. The wrapper downloads Gradle **9.5.1**. For other checkout locations, use `-PmtrProjectDir=/path/to/MTR` and `-PtransitCoreProjectDir=/path/to/Transit-Core`.

The build runs the compatibility checks and writes both loader JARs to `build/release/`:

- `MTR-ANTE-fabric-1.2.0-26.2-kotlin.3.jar`
- `MTR-ANTE-neoforge-1.2.0-26.2-kotlin.3.jar`

## Development

Minecraft-independent Kotlin utilities live in the separate Kotlin LunaCore project. `common/` contains gameplay, rendering, scripts and assets; its production code is being migrated to Kotlin, including the Sowcer package and path generation. `fabric/` and `neoforge/` contain loader integrations. Regression and compatibility checks live in `tests/`. Dependency versions are defined in [gradle.properties](gradle.properties).

The [migration target](https://github.com/linlunaire/Minecraft-Transit-Railway/blob/master/docs/kotlin-migration.md) is all or the overwhelming majority of production code, not just a Kotlin utility layer. Frozen Java JVM contracts and behavioral checks guard Java/Mixin/script compatibility. See the [current checkpoint and compatibility choices](docs/kotlin-migration.md). The rewrite is in progress; language conversion alone is not evidence of higher FPS or TPS.

Report fork-specific problems in [this repository's issue tracker](https://github.com/linlunaire/mtr-ante/issues), including the MTR and ANTE versions, loader, logs and a minimal reproduction or resource pack.

## Older versions

This migration lives on `codex/kotlin-26.2-preview`. The `26.2` branch retains Java maintenance; `master` and `1.21.1` retain the 1.21.1 work. Historical tags keep their original source and build instructions.

The last Java-based 26.2 pair is preserved as [ANTE `1.1.1-26.2`](https://github.com/linlunaire/mtr-ante/tree/1.1.1-26.2) and [MTR `26.2-3.3.2`](https://github.com/linlunaire/Minecraft-Transit-Railway/tree/26.2-3.3.2), before the production Kotlin migration and external Kotlin LunaCore requirement.

To rebuild them without the old Minecraft-Mappings repository, first apply [Kotlin LunaCore's legacy preparation script](https://github.com/linlunaire/Kotlin-LunaCore/blob/650800892192395a8755ef41efcfe90b913a3c4a/docs/legacy-mappings.md) to the old MTR checkout. Old game releases do not require the Kotlin LunaCore mod.

## Credits and license

Based on [ANTE](https://github.com/aphrodite281/mtr-ante) by Aphrodite281 and [Nemo's Transit Expansion](https://github.com/zbx1425/mtr-nte) by Zbx1425, with contributions from their communities.

Code is licensed under [MIT](LICENSE). Bundled models, textures, sounds and other third-party content retain their respective licenses and [credits](docs/feature.md).
