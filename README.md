# MTR-ANTE

Custom train and rail models, JavaScript-driven rendering, decorative objects and rail editing tools for Minecraft Transit Railway.

**Aphrodite's Nemo's Transit Expansion**, ported to **Minecraft 26.2** for **Fabric** and **NeoForge**. This community fork extends the [MTR 3 community port](https://github.com/linlunaire/Minecraft-Transit-Railway); it is not a standalone mod or an MTR 4 add-on.

## Install

Use Java 25 and install ANTE together with the **26.2 MTR community port**, using the same loader for both JARs.

| Loader | Required mods |
| --- | --- |
| Fabric | MTR, [Fabric API](https://modrinth.com/mod/fabric-api), [Architectury API](https://modrinth.com/mod/architectury-api) |
| NeoForge | MTR, [Architectury API](https://modrinth.com/mod/architectury-api) |

Place the JARs in `mods` on the server and clients. ANTE's scripting runtime and configuration library are bundled; no separate installation is needed. Players using custom models also need the corresponding resource packs.

Back up worlds, configuration and resource packs before upgrading. Test your existing routes, custom trains and scripts on a copy of the world first.

## Build from source

Install **JDK 25** and set `JAVA_HOME`. Clone the [MTR community port](https://github.com/linlunaire/Minecraft-Transit-Railway) and ANTE as siblings:

```text
workspace/
├── Minecraft-Transit-Railway-3.x.x/
└── mtr-ante/
```

Build MTR first, following its [build instructions](https://github.com/linlunaire/Minecraft-Transit-Railway#build-from-source). Then run from the ANTE repository root:

```sh
./gradlew build
```

On Windows, use `./gradlew.bat` in place of `./gradlew`. The wrapper downloads Gradle **9.5.1**. For a different MTR checkout location, append `-PmtrProjectDir=/path/to/MTR` to the command.

The build runs the compatibility checks and writes both loader JARs to `build/release/`:

- `MTR-ANTE-fabric-1.1.1-26.2.jar`
- `MTR-ANTE-neoforge-1.1.1-26.2.jar`

## Development

`common/` contains shared code and assets; `fabric/` and `neoforge/` contain loader integrations. Regression and compatibility checks live in `tests/`. Dependency versions are defined in [gradle.properties](gradle.properties).

Report fork-specific problems in [this repository's issue tracker](https://github.com/linlunaire/mtr-ante/issues), including the MTR and ANTE versions, loader, logs and a minimal reproduction or resource pack.

## Older versions

`master` targets **26.2 only**. For Minecraft 1.21.1, use [ANTE tag `1.1.1-1.21.1-beta.2`](https://github.com/linlunaire/mtr-ante/tree/1.1.1-1.21.1-beta.2) with [MTR tag `1.21.1-3.3.2`](https://github.com/linlunaire/Minecraft-Transit-Railway/tree/1.21.1-3.3.2). Those tags retain the original source and build instructions.

## Credits and license

Based on [ANTE](https://github.com/aphrodite281/mtr-ante) by Aphrodite281 and [Nemo's Transit Expansion](https://github.com/zbx1425/mtr-nte) by Zbx1425, with contributions from their communities.

Code is licensed under [MIT](LICENSE). Bundled models, textures, sounds and other third-party content retain their respective licenses and [credits](docs/feature.md).
