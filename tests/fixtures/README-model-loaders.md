# NMB and OBJ loader compatibility

`ModelLoadersCompatibilityCheck` exercises the actual production loaders and the
bundled, unchanged `de.javagl.obj` parser. Its Java oracle is
`MTR-ANTE-neoforge-1.1.1-26.2.jar`, SHA-256
`DB3662A772AD746BCDBFE81B2E44FED09EA084D73F11B29022BE9B11E9F4D79C`.
Both selected classes have code-source and Kotlin-metadata provenance checks;
their language is read from the actual loaded classes, not inferred from the
file extension. Classes-directory checks require Kotlin. Golden recording is
rejected unless the loaded Java JAR matches the exact SHA-256 above. Twelve exported JVM
contracts are frozen separately, including generic maps, public constructors,
non-final static methods and `IOException` declarations. Java subclasses also
compile static hiding methods against the converted API.

## Checks

- `:common:checkModelLoadersCompatibility` runs the frozen Java corpus against
  current Kotlin production classes.
- `:common:checkKotlinModelLoadersAbi` checks compiled exported contracts. The
  existing release checks also inspect this snapshot in both final loader JARs.
- `:fabric:checkPackagedModelLoadersCompatibility` and
  `:neoforge:checkPackagedModelLoadersCompatibility` rerun the same behavior corpus
  against each final `shadowJar`, with finished ANTE and matching MTR artifacts
  ahead of development classpaths. Provenance checks require both loaders, their
  geometry/material dependencies and the relocated OBJ parser to come from the
  specified final ANTE JAR. Both tasks are part of their loader's `check`.
- `:common:checkJavaModelLoadersBaseline -PjavaBaselineJar=<original-jar>` reruns
  the same corpus against original Java. Adding `-PrecordModelLoadersBaseline`
  deliberately regenerates the golden from that Java artifact only.

The first two checks are part of `check` and therefore `build`. No Minecraft
instance or GPU context is started. Resource lookup is a headless
`ResourceManager` adapter returning real Minecraft `Resource` instances and
tracked streams; actual parsers and cryptographic providers are not stubbed.

## Covered contracts

NMB uses real AES/CBC/PKCS5Padding. The test independently decrypts each generated
random-key envelope, checks its signature/version/key length/ciphertext length,
and compares the exact decrypted model bytes with the Java golden. With the raw
appendix enabled, all sixteen zero integers and the exact repeated plaintext
are checked. A fixed-key independently encoded envelope also exercises loading.
Random key/ciphertext bytes are not compared across runs. This preserves the
existing container format; it is not a cryptographic redesign or security audit.

Tests include resource-stack priority, location identity/nullability, atlas
callbacks after input close, ignored magic/version values, truncated headers,
key/ciphertext truncation, negative ciphertext length, model decoding failures,
output failures and non-closing/non-flushing output ownership. In particular,
header parsing remains outside the closing `finally`, and a close failure
replaces (rather than suppresses beneath) a decrypt failure.

OBJ coverage includes actual triangulation, generated normals, missing normals
and UVs, invalid faces, material parsing and replacement, texture resolution,
alpha/color conversion, render-type and `flipv` options, Java split limits and
trailing delimiters, group-name normalization, mutable returned group maps,
nullable locations, atlas mutation, stream read failures and resource overloads.
The bundled parser intentionally treats each `mtllib` declaration as one name
and replaces the previous declaration; the fixture preserves that policy.

OBJ exports cover all six render types, group vertex offsets, material
deduplication, optional normals, nullable keys/textures, caller locale, partial
files on failure, and second-file-open failures. Only release-version heading
text and platform newline spelling are normalized. External-model source paths
normalize the temporary directory while retaining the namespace, filename and
group suffix. Temporary files belong solely to the test and are cleaned up;
renaming after an external load also checks file-handle closure on Windows.

InputStream/resource OBJ overloads continue to leave input ownership to callers;
only `loadExternalModels` owns its opened file. Early NMB header failures still
leave the opened stream unclosed. Those pre-existing behaviors are deliberately
not repaired as part of language conversion. Passing these CPU checks does not
establish FPS/TPS gains or successful in-game resource reload.
