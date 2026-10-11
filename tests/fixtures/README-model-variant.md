# Model variant preparation

`ModelVariantPreparation` owns the synchronous CPU preparation shared by
`EyeCandyRegistry` and `RailModelRegistry`: one deep copy, default-texture
replacement, UV flip, optional ordered geometry transforms, and the original
`sourceLocation/key` identity. It borrows the template read-only and does not
retain or mutate the JSON definition. Returned mesh, material, vertex and face
storage, including material attribute position/normal vectors, belongs
exclusively to that result. As in the existing `RawModel.copy` contract,
`matrixModel` transform callbacks retain their shared identity; callback
closures are not cloned. `Geometry.IGNORE` preserves the
rail registry's existing behavior of ignoring geometry options, including
malformed values. Loading, atlas IO, GPU upload and cache policy remain outside
the module.

## Original-Java oracle

`model-variant-java-1.1.1-26.2.tsv` contains 166 records captured **before**
introducing the module, by invoking the actual original Java registries from
`MTR-ANTE-neoforge-1.1.1-26.2.jar`, SHA-256
`DB3662A772AD746BCDBFE81B2E44FED09EA084D73F11B29022BE9B11E9F4D79C`.
Both baseline verification and recording reject any artifact without this exact hash.

`ModelVariantPreparationCheck` invokes the actual private registry entrypoints,
not copies of their transform algorithms. A test-only class loader changes
only external `MainClient` model/atlas fields and the resource-manager field
to headless adapters. The real `RawModel.copy`, geometry/material operations,
JSON parsing, source identifiers, and rail-property construction all execute.
Only resource acquisition and GPU upload are substituted; upload captures the
prepared model and stops at the GPU boundary. Each record includes exceptions,
IO/copy/upload order, copy count, source identifier and exact serialized mesh
bytes, including partial transformations before invalid inputs fail.

For each registry the corpus covers all 64 combinations of texture, UV,
translation, rotation, scale and mirror options; atlas ordering; false flips;
negative/zero/signed-zero scale; non-finite coordinates; invalid texture IDs;
missing vector components; wrong/null JSON types; failures after successful
rotation axes; and null/empty/nested/invalid variant keys. Mesh order alone is
normalized, because its backing map is not an ordered serialization contract.

The new module is also tested directly through its single public `prepare`
interface, comparing exact model bytes with the corresponding original-Java
eye-candy capture for both geometry modes. These tests cover exactly one deep
copy, two independent variants, repeated preparation
without cumulative transforms, untouched definitions/templates, nested mutable
storage non-aliasing, copy failure identity, partial-transform failure followed
by a successful preparation, nullable source/key identity, and ignored rail
geometry. The math, model and material dependencies are real production classes,
never test implementations.

Additional interface-only cases use non-null material attribute position and
normal vectors to check both reference non-aliasing and modification isolation
across the template and two variants. They also verify that transform callbacks
retain identity and are not invoked during preparation. These cases do not
rewrite or extend the original-Java fixture.

## Verification tasks

- `:common:checkModelVariantPreparation` compares the integrated current
  registries with the frozen oracle, then exercises the new Kotlin interface.
- `:common:checkJavaModelVariantBaseline -PjavaBaselineJar=<original-jar>`
  verifies that the same corpus still matches original Java. Adding
  `-PrecordModelVariantBaseline` deliberately records only from the verified
  original artifact; this flag is never needed for normal builds.
- `:fabric:checkPackagedModelVariantPreparation` and
  `:neoforge:checkPackagedModelVariantPreparation` rerun the current registry
  and interface checks from the final loader `shadowJar`. The selected ANTE
  JAR and matching MTR release JAR precede development dependencies. Actual
  code-source checks require the module and geometry/resource dependencies to
  come from that selected JAR; Kotlin metadata is checked on the new module.

Current-class and final-artifact checks are connected to their projects'
`check` tasks. These are CPU contract checks, not an FPS/TPS benchmark or a
claim that in-game resource reload was tested. No game, GPU context or network
is required.
