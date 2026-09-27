# CSV model-loader conversion

`CsvModelLoaderCompatibilityCheck` runs the real CSV parser, geometry classes,
resource decoder and atlas callbacks without starting Minecraft or a GPU.
The public constructor, non-final class/static method and declared `IOException`
are frozen in `tests/kotlin-abi/ante-csv-loader-java-1.1.1-26.2.tsv`.

The reference is the unmodified Java `CsvModelLoader.java` from tag
`1.1.1-26.2` (commit `04c8765125f2f8a6c3f5473f38bc38bc1c1ffef5`), Git blob
`86ef037dfb87ea6bf57e8cc5bd17f926db264d0b`. The optional baseline task
extracts that exact blob into the ignored build directory and validates its
Git object hash before compilation. It cannot use a modified working-tree source.
The remaining model/resource dependencies are the current compatibility-tested
implementations; this is a differential test of the CSV conversion, not a replay
of an entire historical game distribution.

## Verification

- `:common:checkCsvModelLoaderCompatibility` compares Kotlin production output
  with the committed Java golden and verifies code source and Kotlin metadata.
- `:common:checkKotlinCsvModelLoaderAbi` checks the compiled JVM contracts.
- `:fabric:checkPackagedCsvModelLoaderCompatibility` and
  `:neoforge:checkPackagedCsvModelLoaderCompatibility` run the same corpus against
  their finished shaded JARs, checking that the loader and model dependencies
  actually came from that artifact rather than a development fallback.
- `:common:checkJavaCsvModelLoaderBaseline` repeats the corpus against the pinned
  Java source. Only adding `-PrecordCsvModelLoaderBaseline` regenerates the
  committed golden. Fetch the `1.1.1-26.2` tag first in a shallow checkout.

Ordinary `check`/`build` requires no historical Git objects: it executes the
committed golden. The corpus has 97 frozen records and 194 common-output
assertions (198 when checking a packaged JAR). Inputs include every command, partial/malformed
commands followed by recovery, resource precedence, BOM decoding, empty or
missing resources, nullable locations, caller-locale case conversion, Java
ASCII trim and split semantics, default/explicit parameters, mesh-builder
boundaries, primitive topology and atlas mutation/failure order. Mesh bytes are
hashed independently and sorted because the material grouping is an unordered
map; vertex/face/attribute order inside each mesh is preserved exactly. Atlas
callback order is asserted separately.

This deliberately retains existing ownership behavior: `ResourceUtil.readResource`
does not close its input, and CSV continues after per-line `Exception`s while
failures before parsing and atlas failures propagate. Correcting resource
ownership or changing malformed-input policy would be separate changes with
their own compatibility decisions. No FPS/TPS or in-game rendering claim follows
from this CPU corpus.
