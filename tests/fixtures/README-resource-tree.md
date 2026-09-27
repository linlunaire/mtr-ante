# Resource tree and relative position migration

The 164 records in `resource-tree-java-1.1.1-26.2.tsv` were recorded only from
the original Java ANTE release (SHA-256
`db3662a772ad746bcdbfe81b2e44fed09ea084d73f11b29022be9b11e9f4d79c`).
The fixture asserts exact code-source paths and source-language metadata for
both classes and all six nested types; recording Kotlin output is rejected.
The eight types retain 53 exported JVM class/field/method contracts.

`checkResourceTreeCompatibility` executes 42,102 assertions covering:

- 4,096 seeded byte-coordinate triples, all six directions, integer overflow,
  subclass equality, exact Java byte hashes and out-of-range exceptions;
- encoded set iteration order, malformed/trailing/empty components, mandatory
  origin insertion, unmodifiable decoded sets, live constructor sets and suit
  reference identity;
- first-value retention, duplicate branch/leaf names, repeat resolution,
  aliased nodes/components, leaf-over-branch collisions, mutable public fields,
  copied name/map versus retained parent/payload references and null failures;
- slash/empty/Unicode resource paths, callback ordering/exceptions and 128
  seeded hierarchies before/after copy and flattening.

The same tests run against the Java baseline using
`checkJavaResourceTreeBaseline -PjavaBaselineJar=<original-jar>`.

## Measured work and allocation

`checkResourceTreeScaling` executes the selected production tree in a child
loader. Instrumentation substitutes a counting HashMap and counts the elements
visited by actual `Collections.frequency` calls; it does not replace the tree
algorithm. The comparison counts list visits in the old version versus hash
map get/put probes in the new one, **not equivalent CPU cycles or wall time**.

| Nodes in one level | Java duplicate-name list visits | Kotlin hash probes |
| ---: | ---: | ---: |
| 128 | 16,384 | 384 |
| 1,024 | 1,048,576 | 3,072 |
| 4,096 | 16,777,216 | 12,288 |

Flattening adds one result-map insertion per leaf (16,781,312 to 16,384 counted
operations at 4,096 leaves). Frequency lookup now has expected linear work per
level, with a local name-count map; the full recursive flattening is not
claimed to be linear for arbitrary depth. No persistent cache is kept because
public maps and mutable components can change. Live names are re-read during
suffixing so alias-induced name changes retain the original snapshot behavior.

`checkRelativePositionAllocation` calls the actual position hash 32,768 times
under `-Xint`, outside setup/warm-up. Java's `Objects.hash` allocates 1,048,576
bytes (32 per call); the equivalent signed-byte arithmetic allocates zero.
This bypasses JIT escape analysis intentionally, not as a production JVM setting.

The optional `checkJavaResourceTreeScalingBaseline` and
`checkJavaRelativePositionAllocationBaseline` tasks confirm old behavior.
Both old implementations were also run against the new gates without the
baseline flag and failed, validating the negative controls. All new gates and
packaged ABI/metadata checks participate in `build`.

## Preserved limitations

Tree copying intentionally retains original parent pointers and payloads;
flattening mutates original leaf names and colliding short keys overwrite
earlier values. Only the root resolves duplicates when loading. Public maps
remain mutable and nullable. Changing these contracts belongs in a separately
specified API redesign, not a silent language conversion.

Tests use real Minecraft text and coordinate classes without launching a game.
They do not measure resource-pack reload latency, FPS/TPS, GPU correctness or
multiplayer capacity. Game JARs and saves are not modified.
