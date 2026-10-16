<div align="center">
  <img src="common/src/main/resources/icon.png" alt="YLTE icon" width="128">

  <h1>YLTE</h1>
  <p><strong>Yanling Transit Expansion</strong></p>
  <p>Custom models, scripted rendering and rail tools for your Minecraft railway.</p>

  <p>
    <a href="#whats-new"><img src="https://img.shields.io/badge/YLTE-1.1.3-A77AE5?style=flat-square" alt="YLTE 1.1.3"></a>
    <img src="https://img.shields.io/badge/Minecraft-1.21.1-62A854?style=flat-square" alt="Minecraft 1.21.1">
    <img src="https://img.shields.io/badge/Loaders-Fabric%20%2B%20NeoForge-546E7A?style=flat-square" alt="Fabric and NeoForge">
    <img src="https://img.shields.io/badge/Java-21-E58A2B?style=flat-square" alt="Java 21">
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-327E88?style=flat-square" alt="MIT license"></a>
  </p>
  <p>
    <a href="https://github.com/linlunaire/mtr-ante/actions/workflows/build.yml?query=branch%3A1.21.1"><img src="https://github.com/linlunaire/mtr-ante/actions/workflows/build.yml/badge.svg?branch=1.21.1" alt="1.21.1 build status"></a>
  </p>
  <p>
    <a href="#download--install">Download &amp; install</a> &nbsp;·&nbsp;
    <a href="#whats-new">What's new</a> &nbsp;·&nbsp;
    <a href="#build-from-source">Build</a> &nbsp;·&nbsp;
    <a href="https://github.com/linlunaire/mtr-ante/issues">Report an issue</a> &nbsp;·&nbsp;
    <a href="https://github.com/linlunaire/YanlingMTR/tree/1.21.1">YanlingMTR</a>
  </p>
</div>

---

YLTE continues [Aphrodite's Nemo's Transit Expansion (ANTE)](https://github.com/aphrodite281/mtr-ante) for **Minecraft 1.21.1**. It adds custom models, JavaScript rendering, decorative objects and track-building tools to YanlingMTR / MTR.

## 🛠️ Make the railway your own

| Models & scripting | Building & track tools |
| :--- | :--- |
| Load custom train, rail and decoration models. | Shape track connections with direct nodes and rail tools. |
| Add JavaScript-driven rendering and behavior. | Place and transform decorative objects around stations. |
| Use existing ANTE resource packs and script interfaces. | Edit track geometry, cant and model placement. |

## Download & install

Download **`YLTE-1.21.1`** from the Artifacts section of a successful [1.21.1 build](https://github.com/linlunaire/mtr-ante/actions/workflows/build.yml?query=branch%3A1.21.1). GitHub requires sign-in to download workflow artifacts.

Use **Java 21** with [YanlingMTR 1.0.4](https://github.com/linlunaire/YanlingMTR/tree/1.21.1), and place the matching JARs in `mods/`:

| Loader | Required dependencies | Mod file |
| :--- | :--- | :--- |
| Fabric | YanlingMTR · [Fabric API](https://modrinth.com/mod/fabric-api) · [Architectury API](https://modrinth.com/mod/architectury-api) | `YLTE-fabric-1.1.3-1.21.1.jar` |
| NeoForge | YanlingMTR · [Architectury API](https://modrinth.com/mod/architectury-api) | `YLTE-neoforge-1.1.3-1.21.1.jar` |

Replace the old ANTE JAR and update clients and servers together. The loader accepts MTR compatibility versions from `1.21.1-3.3.4`; YanlingMTR is the maintained companion build used here.

> [!IMPORTANT]
> Back up worlds, configuration and resource packs before upgrading. YLTE retains the `mtrsteamloco` ID, resource namespaces, Java/script interfaces, `ANTE_FLAG`, `ANTE-Data` save key and protocol 11. Resource-pack groups and dynamic pack IDs are also retained.

## What's new

**1.1.3 · October 16, 2026**

- Fixed rails becoming invisible with Iris shaders while their shadows remained visible.
- Fixed More Culling hiding block faces beneath direct nodes.
- Fixed client slider limits and step sizes resetting after a restart.
- Fixed custom raw-model draw calls being skipped or replayed across frames.

<details>
<summary>Previous updates</summary>

- **1.1.2** — Bounded asynchronous rail builds and pending uploads; fixed native-buffer cleanup, chunk-unload cancellation and VAO disposal. See the [rail memory notes](docs/rail-memory-2026-10-14.md).
- **1.1.1** — Fixed pinyin search, string configuration encoding, direct-node editing, train configuration IDs, route-tool and held-decoration persistence, and server-side edit validation.

String values written with `YLTE-SM1:` require the newer decoder. Previously lost or corrupted data still requires a backup. Earlier changes and targeted checks are in the [maintenance history](docs/maintenance-history.md).

</details>

## Build from source

Use **JDK 21** and the included Gradle 8.14.5 wrapper. Place the [YanlingMTR `1.21.1` checkout](https://github.com/linlunaire/YanlingMTR/tree/1.21.1) next to this repository:

```text
workspace/
├── Minecraft-Transit-Railway-1.21.1/
└── mtr-ante/
```

Build YanlingMTR first, then YLTE:

```powershell
cd ../Minecraft-Transit-Railway-1.21.1
.\gradlew.bat build
cd ../mtr-ante
.\gradlew.bat build
```

On Linux or macOS, use `./gradlew` instead. For a different MTR location, pass `-PmtrProjectDir=<path>` as a single argument.

The build checks the Minecraft version and development JARs, runs compatibility checks and writes both loader JARs to **`build/release/`**. CI uses a pinned YanlingMTR commit. Older local builds are retained under `build/release/archive/`, which is removed by `clean`.

## 📚 Documentation & support

Read the [feature documentation](docs/README.md), [maintenance history](docs/maintenance-history.md) and [rendering regression instructions](tests/render-runtime/README.md). These technical notes are currently in Chinese. Report problems in [Issues](https://github.com/linlunaire/mtr-ante/issues), including mod versions, logs and reproduction steps; include rendering mods and shader packs for visual issues.

This is the standalone Java **1.21.1** line. Current **26.2** development lives in [YanlingMTR's `ante/` module](https://github.com/linlunaire/YanlingMTR/tree/master/ante); the earlier [Java 26.2](https://github.com/linlunaire/mtr-ante/tree/26.2) and [standalone Kotlin](https://github.com/linlunaire/mtr-ante/tree/master) branches remain separate. Their builds are not interchangeable with 1.21.1.

## Credits & license

Based on **Aphrodite281's [ANTE](https://github.com/aphrodite281/mtr-ante)** and **Zbx1425's [Nemo's Transit Expansion](https://github.com/zbx1425/mtr-nte)**. Maintained by **linlunaire** as an independent community continuation.

The [MIT License](LICENSE) and original author credits are retained. Bundled font, pinyin and scripting runtime notices are included in the source tree and JAR: [Noto OFL](common/src/main/resources/licenses/Noto-OFL-1.1.txt), [pinyin libraries and data](common/src/main/resources/licenses/Pinyin-notices.txt), and [Graal runtime](common/src/main/resources/licenses/Graal-runtime-notices.txt).
