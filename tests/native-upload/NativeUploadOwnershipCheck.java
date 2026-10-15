package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.model.Mesh;
import cn.zbx1425.sowcer.model.Model;
import cn.zbx1425.sowcer.model.VertArrays;
import cn.zbx1425.sowcer.object.VertArray;
import com.mojang.blaze3d.pipeline.RenderCall;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import cn.zbx1425.sowcer.vertex.VertAttrSrc;
import cn.zbx1425.sowcer.vertex.VertAttrType;
import cn.zbx1425.sowcerext.model.Face;
import cn.zbx1425.sowcerext.model.RawMesh;
import cn.zbx1425.sowcerext.model.RawModel;
import cn.zbx1425.sowcerext.model.Vertex;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Executes real RawMesh/RawModel methods; only malloc and GL endpoints are instrumented. */
public final class NativeUploadOwnershipCheck {
    private static final String HOOK = "cn/zbx1425/mtrsteamloco/compatibility/NativeUploadOwnershipCheck";
    private static final IdentityHashMap<ByteBuffer, Integer> nativeLive = new IdentityHashMap<>();
    private static final Set<Integer> gpuLive = new HashSet<>();
    private static final Map<Integer, byte[]> uploaded = new HashMap<>();
    private static final Set<Integer> vaoLive = new HashSet<>();
    private static final List<Runnable> deferred = new java.util.ArrayList<>();
    private static int vaoCreates, vaoConfigures, failVaoCreate, failVaoConfigure;
    private static int boundVao, drawCalls;
    private static boolean renderThread = true;
    private static int allocations, creates, uploads, failAllocation, failCreate, failUpload, failures, assertions, peakNativeBytes;
    private static int deletions, failDeletion, renderSubmissions, failRenderSubmission;
    private static boolean deletionThrowsError;
    private static Throwable injectedDeletion;
    private static final List<Integer> deleteAttempts = new java.util.ArrayList<>();

    public static void premain(String unused, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                    ProtectionDomain domain, byte[] bytes) {
                if (!"cn/zbx1425/sowcer/util/OffHeapAllocator".equals(name)
                        && !"cn/zbx1425/sowcer/object/VertBuf".equals(name)
                        && !name.startsWith("cn/zbx1425/sowcer/object/VertArray")
                        && !"cn/zbx1425/sowcer/vertex/VertAttrMapping".equals(name)) return null;
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                for (var method : node.methods) {
                    if (name.startsWith("cn/zbx1425/sowcer/object/VertArray")) {
                        for (var instruction : method.instructions) {
                            if (!(instruction instanceof MethodInsnNode call)) continue;
                            if (call.owner.equals("cn/zbx1425/sowcer/vertex/VertAttrMapping") && call.name.equals("setupAttrsToVao")) {
                                call.setOpcode(Opcodes.INVOKESTATIC); call.owner = HOOK; call.name = "configureVao";
                                call.desc = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V";
                                continue;
                            }
                            String replacement = call.owner.equals("org/lwjgl/opengl/GL33")
                                    ? switch (call.name) {
                                        case "glGenVertexArrays" -> "createVao";
                                        case "glDeleteVertexArrays" -> "deleteVao";
                                        case "glBindVertexArray" -> "bindVao";
                                        case "glDrawElements" -> "drawElements";
                                        case "glDrawElementsInstanced" -> "drawElementsInstanced";
                                        default -> null;
                                    } : call.owner.equals("cn/zbx1425/sowcer/util/GlStateTracker") && call.name.equals("assertProtected") ? "noOp"
                                    : call.owner.equals("com/mojang/blaze3d/systems/RenderSystem") && call.name.equals("isOnRenderThread") ? "isRenderThread"
                                    : call.owner.equals("com/mojang/blaze3d/systems/RenderSystem") && call.name.equals("recordRenderCall") ? "recordRenderCall"
                                    : call.owner.equals("java/util/concurrent/Executors") && call.name.equals("newScheduledThreadPool") ? "scheduler" : null;
                            if (replacement != null) { call.owner = HOOK; call.name = replacement; }
                        }
                        continue;
                    }
                    if (name.endsWith("VertAttrMapping")) {
                        if (method.name.equals("setupAttrsToVao")) {
                            method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "configureVao", "()V", false));
                            method.instructions.add(new InsnNode(Opcodes.RETURN));
                        }
                        continue;
                    }
                    boolean allocator = name.endsWith("OffHeapAllocator");
                    String hookName = allocator && method.name.equals("allocate") ? "allocate"
                            : allocator && method.name.equals("free") ? "free" : null;
                    boolean constructor = !allocator && method.name.equals("<init>") && method.desc.equals("()V");
                    boolean upload = !allocator && method.name.equals("upload") && method.desc.equals("(Ljava/nio/ByteBuffer;I)V");
                    boolean close = !allocator && method.name.equals("close");
                    if (!allocator && method.name.equals("bind")) {
                        method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
                        method.instructions.add(new InsnNode(Opcodes.RETURN));
                    }
                    if (allocator && method.name.equals("<clinit>")) {
                        method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
                        method.instructions.add(new InsnNode(Opcodes.RETURN));
                    } else if (hookName != null || constructor || upload || close) {
                        method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
                        if (hookName != null) {
                            method.instructions.add(new VarInsnNode(hookName.equals("allocate") ? Opcodes.ILOAD : Opcodes.ALOAD, 0));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, hookName, method.desc, false));
                            method.instructions.add(new InsnNode(hookName.equals("allocate") ? Opcodes.ARETURN : Opcodes.RETURN));
                        } else if (constructor) {
                            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
                            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "create", "()I", false));
                            method.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.PUTFIELD, name, "id", "I"));
                            method.instructions.add(new InsnNode(Opcodes.RETURN));
                        } else {
                            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD, name, "id", "I"));
                            if (upload) {
                                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "upload", "(ILjava/nio/ByteBuffer;)V", false));
                            } else {
                                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "delete", "(I)V", false));
                                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                                method.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.PUTFIELD, name, "id", "I"));
                            }
                            method.instructions.add(new InsnNode(Opcodes.RETURN));
                        }
                    }
                }
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                node.accept(writer);
                return writer.toByteArray();
            }
        });
    }

    public static synchronized ByteBuffer allocate(int bytes) {
        if (++allocations == failAllocation) throw new OutOfMemoryError("injected allocation " + allocations);
        ByteBuffer buffer = ByteBuffer.allocate(bytes).order(ByteOrder.nativeOrder());
        nativeLive.put(buffer, bytes);
        peakNativeBytes = Math.max(peakNativeBytes, nativeLive.values().stream().mapToInt(Integer::intValue).sum());
        return buffer;
    }
    public static synchronized void free(ByteBuffer buffer) {
        if (nativeLive.remove(buffer) == null) throw new AssertionError("double free or non-owned native buffer");
    }
    public static synchronized int create() {
        if (++creates == failCreate) throw new IllegalStateException("injected GL allocation " + creates);
        gpuLive.add(creates); return creates;
    }
    public static synchronized void upload(int id, ByteBuffer buffer) {
        if (++uploads == failUpload) throw new IllegalStateException("injected GL upload " + uploads);
        require(nativeLive.containsKey(buffer), "upload used freed buffer");
        var copy = buffer.duplicate(); copy.clear();
        byte[] bytes = new byte[copy.remaining()]; copy.get(bytes); uploaded.put(id, bytes);
    }
    public static synchronized void delete(int id) {
        if (id == 0) return;
        deleteAttempts.add(id);
        if (++deletions == failDeletion) {
            if (deletionThrowsError) {
                AssertionError failure = new AssertionError("injected buffer delete " + id); injectedDeletion = failure; throw failure;
            }
            IllegalStateException failure = new IllegalStateException("injected buffer delete " + id); injectedDeletion = failure; throw failure;
        }
        if (id != 0 && !gpuLive.remove(id)) throw new AssertionError("double GPU delete " + id);
    }
    public static int createVao() {
        if (++vaoCreates == failVaoCreate) throw new IllegalStateException("injected VAO create " + vaoCreates);
        vaoLive.add(vaoCreates); return vaoCreates;
    }
    public static void configureVao() {
        if (++vaoConfigures == failVaoConfigure) throw new IllegalStateException("injected VAO configure " + vaoConfigures);
    }
    public static void configureVao(Object mapping, Object vertex, Object instance) { configureVao(); }
    public static void deleteVao(int id) {
        require(renderThread, "VAO deleted outside render dispatcher");
        require(id == 0 || vaoLive.remove(id), "double VAO delete " + id);
        if (boundVao == id) boundVao = 0;
    }
    public static void bindVao(int id) { boundVao = id; }
    public static void drawElements(int mode, int count, int type, long offset) {
        require(vaoLive.contains(boundVao), "drawing deleted or unbound VAO " + boundVao); drawCalls++;
    }
    public static void drawElementsInstanced(int mode, int count, int type, long offset, int instances) {
        drawElements(mode, count, type, offset);
    }
    public static void noOp() {}
    public static boolean isRenderThread() { return renderThread; }
    public static void recordRenderCall(RenderCall call) {
        if (++renderSubmissions == failRenderSubmission) {
            IllegalStateException failure = new IllegalStateException("injected VAO deletion submission"); injectedDeletion = failure; throw failure;
        }
        deferred.add(call::execute);
    }
    public static ScheduledExecutorService scheduler(int threads) {
        return (ScheduledExecutorService)Proxy.newProxyInstance(NativeUploadOwnershipCheck.class.getClassLoader(),
                new Class<?>[]{ScheduledExecutorService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("schedule")) { deferred.add((Runnable)args[0]); return null; }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
    private static void drainRenderCalls() {
        renderThread = true;
        while (!deferred.isEmpty()) deferred.remove(0).run();
    }

    public static void main(String[] args) throws Exception {
        if (List.of(args).contains("vao")) { checkVertexArrays(); return; }
        if (List.of(args).contains("close")) { checkCompositeClose(); return; }
        run("mesh second allocation fails", () -> { failAllocation = 2; expectFailure(() -> mesh().uploadAsync(mapping())); });
        run("vertex encoding fails", () -> { var map = mapping(); map.sources.remove(VertAttrType.COLOR); expectFailure(() -> mesh().uploadAsync(map)); });
        run("index encoding overflows", () -> { var mesh = mesh(); mesh.faces.set(0, new Face(new int[]{0, 1, 2, 0})); expectFailure(() -> mesh.uploadAsync(mapping())); });
        run("mesh first GL upload fails", () -> { var task = mesh().uploadAsync(mapping()); failUpload = 1; expectFailure(task::get); });
        run("mesh second GL upload fails", () -> { var task = mesh().uploadAsync(mapping()); failUpload = 2; expectFailure(task::get); });
        run("mesh index GPU constructor fails", () -> { var task = mesh().uploadAsync(mapping()); failCreate = 2; expectFailure(task::get); });
        run("model later preparation fails", () -> { failAllocation = 3; expectFailure(() -> model().uploadAsync(mapping())); });
        run("model later vertex GL upload fails", () -> { var task = model().uploadAsync(mapping()); failUpload = 3; expectFailure(task::get); });
        run("model later index GL upload fails", () -> { var task = model().uploadAsync(mapping()); failUpload = 4; expectFailure(task::get); });
        run("model synchronous later upload fails", () -> { failUpload = 3; expectFailure(() -> model().upload(mapping())); });
        run("synchronous mesh failed upload", () -> { failUpload = 1; expectFailure(() -> mesh().upload(mapping())); });
        run("single-use mesh supplier", () -> {
            var task = mesh().uploadAsync(mapping()); Mesh first = task.get();
            require(task.get() == first, "repeated get created a different mesh"); first.close();
        });
        run("single-use model supplier", () -> {
            var task = model().uploadAsync(mapping()); Model first = task.get();
            require(task.get() == first, "repeated get created a different model");
            require(first.meshList.size() == 2, "repeated get duplicated meshes"); first.close();
        });
        run("geometry and material preserved", () -> {
            RawMesh source = mesh(); var task = source.uploadAsync(mapping()); Mesh result = task.get();
            require(result.materialProp == source.materialProp, "material identity changed");
            require(result.indexBuf.faceCount == 1, "face count changed");
            byte[] vertices = uploaded.get(result.vertBuf.id); byte[] indices = uploaded.get(result.indexBuf.id);
            require(vertices.length == 96 && indices.length == 12, "geometry allocation layout changed");
            ByteBuffer vertex = ByteBuffer.wrap(vertices).order(ByteOrder.nativeOrder());
            require(vertex.getFloat() == 1 && vertex.getFloat() == 2 && vertex.getFloat() == 3, "position bytes changed");
            require(vertex.getInt() == 0x11223344 && vertex.getFloat() == .25F && vertex.getFloat() == .5F, "color/UV bytes changed");
            require(vertex.getInt() == 0x00550066 && vertex.get() == 0 && vertex.get() == 127 && vertex.get() == 0 && vertex.get() == 0, "light/normal/padding bytes changed");
            ByteBuffer index = ByteBuffer.wrap(indices).order(ByteOrder.nativeOrder());
            require(index.getInt() == 0 && index.getInt() == 1 && index.getInt() == 2, "triangle index bytes changed");
            result.close();
        });
        if (!List.of(args).contains("baseline")) {
            run("synchronous model packs one mesh at a time", () -> {
                Model result = model().upload(mapping()); require(peakNativeBytes == 108, "synchronous upload staged more than one mesh: " + peakNativeBytes); result.close();
            });
            run("pre-allocation upper bound and empty meshes", () -> {
                RawModel source = model(); RawMesh empty = mesh(); empty.faces.clear();
                source.meshList.put(empty.materialProp, empty);
                require(estimate(source) == 216, "empty mesh counted in model budget");
                require(allocations == 0, "estimate allocated native memory");
                Object task = prepared(source); require(byteSize(task) == 216, "actual prepared size changed"); close(task);
            });
            run("upper bound before vertex deduplication", () -> {
                RawMesh source = mesh(); source.vertices.add(source.vertices.get(0).copy());
                require(estimate(source) == 140, "preflight did not include duplicate vertices");
                Object task = prepared(source); require(byteSize(task) == 108, "actual allocation did not retain original distinct behavior"); close(task);
            });
            run("empty model cancellation", () -> {
                Object task = prepared(new RawModel()); require(byteSize(task) == 0, "empty model reserved bytes");
                close(task); expectCancellation(() -> ((Supplier<?>)task).get());
            });
            run("model second index allocation fails", () -> {
                failAllocation = 4; expectFailure(() -> model().uploadAsync(mapping()));
            });
            run("upload into caller-owned mesh fails", () -> {
                Mesh target = new Mesh(new cn.zbx1425.sowcer.object.VertBuf(),
                        new cn.zbx1425.sowcer.object.IndexBuf(1, 5125), new MaterialProp());
                failUpload = 2; expectFailure(() -> mesh().upload(target, mapping()));
                require(gpuLive.size() == 2, "failed in-place upload closed caller-owned GPU mesh"); target.close();
            });
            run("mesh cancel and double close", () -> {
                Object task = prepared(mesh()); require(byteSize(task) == 108, "mesh byteSize wrong");
                close(task); close(task); expectCancellation(() -> ((Supplier<?>) task).get());
            });
            run("model cancel and double close", () -> {
                Object task = prepared(model()); require(byteSize(task) == 216, "model byteSize wrong");
                close(task); close(task); expectCancellation(() -> ((Supplier<?>) task).get());
            });
            run("close after successful transfer", () -> {
                Object task = prepared(model()); Model result = (Model)((Supplier<?>)task).get();
                close(task); close(task); require(gpuLive.size() == 4, "close reclaimed transferred GPU objects"); result.close();
            });
            run("failed task cannot retry", () -> {
                Object task = prepared(mesh()); failUpload = 1; expectFailure(() -> ((Supplier<?>)task).get());
                int oldCreates = creates; expectCancellation(() -> ((Supplier<?>)task).get());
                require(creates == oldCreates, "failed task retried GPU construction"); close(task);
            });
            run("mesh get races close", () -> {
                for (int iteration = 0; iteration < 100; iteration++) {
                    Object task = prepared(mesh()); Object[] result = new Object[1]; Throwable[] error = new Throwable[1];
                    Thread upload = new Thread(() -> { try { result[0] = ((Supplier<?>)task).get(); } catch (CancellationException ignored) {} catch (Throwable ex) { error[0] = ex; } });
                    Thread cancel = new Thread(() -> { try { close(task); } catch (Throwable ex) { error[0] = ex; } });
                    upload.start(); cancel.start(); upload.join(); cancel.join();
                    if (error[0] != null) throw new AssertionError("get/close race", error[0]);
                    if (result[0] != null) ((Mesh) result[0]).close();
                }
            });
        }
        if (failures != 0) throw new AssertionError(failures + " native upload ownership scenarios failed");
        System.out.println("PASS: native upload ownership, " + assertions + " assertions; real RawMesh/RawModel byte packing and lifecycle");
    }

    private static void run(String name, CheckedRunnable scenario) {
        nativeLive.clear(); gpuLive.clear(); uploaded.clear(); allocations = creates = uploads = failAllocation = failCreate = failUpload = 0;
        peakNativeBytes = 0;
        vaoLive.clear(); deferred.clear(); vaoCreates = vaoConfigures = failVaoCreate = failVaoConfigure = 0; renderThread = true;
        boundVao = drawCalls = 0;
        deletions = failDeletion = renderSubmissions = failRenderSubmission = 0; deletionThrowsError = false;
        injectedDeletion = null; deleteAttempts.clear();
        try {
            scenario.run(); require(nativeLive.isEmpty(), "native live allocations=" + nativeLive.size() + ", bytes=" + nativeLive.values().stream().mapToInt(Integer::intValue).sum());
            require(gpuLive.isEmpty(), "GPU live buffers=" + gpuLive.size());
            require(vaoLive.isEmpty(), "GPU live VAOs=" + vaoLive.size()); System.out.println("PASS: " + name);
        } catch (Throwable ex) {
            failures++; System.out.println("FAIL: " + name + ": " + ex);
            ex.printStackTrace(System.out);
        }
    }
    private static void checkVertexArrays() throws Exception {
        run("VAO close releases captured id", () -> { VertArray array = new VertArray(); array.close(); drainRenderCalls(); });
        run("VAO close is idempotent off render thread", () -> {
            VertArray array = new VertArray(); renderThread = false; array.close(); array.close();
            require(array.id == 0, "closed array retains public id");
            require(deferred.size() == 1 && vaoLive.size() == 1, "close did not queue exactly one render deletion"); drainRenderCalls();
        });
        run("VAO deletion follows prior render queue work", () -> {
            VertArray array = new VertArray(); int oldId = array.id;
            deferred.add(() -> require(vaoLive.contains(oldId), "VAO deletion preceded queued rendering"));
            array.close(); drainRenderCalls();
        });
        for (int failureIndex : new int[]{1, 2}) {
            run("createAll config failure " + failureIndex, () -> {
                Model model = model().upload(mapping()); failVaoConfigure = failureIndex;
                expectFailure(() -> VertArrays.createAll(model, mapping(), null));
                require(boundVao == 0, "VAO configuration failure left its binding active"); drainRenderCalls();
                require(gpuLive.size() == 4, "createAll closed caller-owned model buffers"); model.close();
            });
        }
        run("createAll later VAO constructor fails", () -> {
            Model model = model().upload(mapping()); failVaoCreate = 2;
            expectFailure(() -> VertArrays.createAll(model, mapping(), null)); drainRenderCalls(); model.close();
        });
        run("createAll success closes only VAOs", () -> {
            Model model = model().upload(mapping()); VertArrays arrays = VertArrays.createAll(model, mapping(), null);
            require(arrays.meshList.size() == 2, "VAO count changed"); arrays.close(); arrays.close(); drainRenderCalls();
            require(gpuLive.size() == 4, "VAO close reclaimed caller-owned model buffers"); model.close();
        });
        for (boolean closeSourceFirst : new boolean[]{false, true}) {
            run("material view close sourceFirst=" + closeSourceFirst, () -> {
                Model model = model().upload(mapping()); VertArrays source = VertArrays.createAll(model, mapping(), null);
                VertArrays view = source.copyForMaterialChanges();
                require(source.meshList.get(0).id == view.meshList.get(0).id && vaoLive.size() == 2, "material copy stopped sharing VAO");
                require(source.meshList.get(0).materialProp != view.meshList.get(0).materialProp, "material copy aliases mutable material");
                view.meshList.get(0).materialProp.translucent = true;
                require(!source.meshList.get(0).materialProp.translucent, "material view changed source material");
                VertArrays first = closeSourceFirst ? source : view, survivor = closeSourceFirst ? view : source;
                first.close(); first.close(); drainRenderCalls();
                require(vaoLive.size() == 2, "first view close deleted shared VAO");
                survivor.meshList.get(0).bind(); survivor.meshList.get(0).draw();
                require(drawCalls == 1, "surviving material view stopped drawing");
                survivor.close(); survivor.close(); drainRenderCalls(); model.close();
            });
        }
        run("nested material views retain until last close", () -> {
            Model model = model().upload(mapping()); VertArrays source = VertArrays.createAll(model, mapping(), null);
            VertArrays first = source.copyForMaterialChanges(), second = first.copyForMaterialChanges();
            source.close(); first.close(); drainRenderCalls(); require(vaoLive.size() == 2, "intermediate view close deleted VAO");
            second.meshList.get(1).bind(); second.meshList.get(1).draw(); second.close(); drainRenderCalls(); model.close();
        });
        for (int failureIndex : new int[]{0, 1}) {
            run("partial material copy fails at " + failureIndex, () -> {
                Model model = model().upload(mapping()); model.meshList.get(failureIndex).materialProp = new MaterialProp() {
                    @Override public MaterialProp copy() { throw new IllegalStateException("injected material copy failure"); }
                };
                VertArrays source = VertArrays.createAll(model, mapping(), null);
                expectFailure(source::copyForMaterialChanges); drainRenderCalls();
                require(vaoLive.size() == 2, "failed copy consumed original VAO");
                source.close(); drainRenderCalls(); model.close();
            });
        }
        if (failures != 0) throw new AssertionError(failures + " VAO ownership scenarios failed");
        System.out.println("PASS: VAO creation and close ownership, " + assertions + " assertions; real VertArrays/VertArray lifecycle");
    }
    private static void checkCompositeClose() throws Exception {
        for (boolean asError : new boolean[]{false, true}) {
            run("mesh continues index close after vertex delete failure, Error=" + asError, () -> {
                Mesh mesh = mesh().upload(mapping()); failDeletion = 1; deletionThrowsError = asError;
                Throwable failure = closeFailure(mesh::close);
                require(failure == injectedDeletion, "mesh replaced first close failure");
                require(deleteAttempts.equals(List.of(1, 2)), "index buffer close not attempted after vertex failure: " + deleteAttempts);
                require(gpuLive.equals(Set.of(1)), "injected failed delete was claimed successful or later buffer leaked");
                failDeletion = 0; mesh.close();
            });
        }
        run("model attempts all meshes after first buffer delete failure", () -> {
            Model model = model().upload(mapping()); failDeletion = 1;
            Throwable failure = closeFailure(model::close);
            require(failure == injectedDeletion, "model replaced first close failure");
            require(deleteAttempts.equals(List.of(1, 2, 3, 4)), "later model buffers were not attempted: " + deleteAttempts);
            require(gpuLive.equals(Set.of(1)), "model retained buffers other than the injected failed deletion");
            failDeletion = 0; model.close();
        });
        run("model propagates first and suppresses later close failure", () -> {
            Model model = model().upload(mapping()); IllegalArgumentException second = new IllegalArgumentException("later member close failure");
            Mesh failingMember = new Mesh(null, null, new MaterialProp()) {
                @Override public void close() { throw second; }
            };
            model.meshList.add(1, failingMember); failDeletion = 1;
            Throwable failure = closeFailure(model::close);
            require(failure == injectedDeletion && java.util.Arrays.equals(failure.getSuppressed(), new Throwable[]{second}), "close failure order or suppression lost");
            require(deleteAttempts.equals(List.of(1, 2, 3, 4)), "suppressed member failure stopped later cleanup");
            require(gpuLive.equals(Set.of(1)), "failed delete was claimed successful");
            model.meshList.remove(failingMember); failDeletion = 0; model.close();
        });
        run("VAO array attempts later deletion submission after first submission fails", () -> {
            Model model = model().upload(mapping()); VertArrays arrays = VertArrays.createAll(model, mapping(), null);
            failRenderSubmission = 1; Throwable failure = closeFailure(arrays::close);
            require(failure == injectedDeletion && renderSubmissions == 2, "later VAO close was not attempted");
            drainRenderCalls();
            require(vaoLive.equals(Set.of(1)), "failed deletion submission was claimed successful or later VAO leaked");
            require(gpuLive.size() == 4, "array close consumed caller-owned model");
            // Explicit harness teardown for the failed submission; do not credit close() with this deletion.
            failRenderSubmission = 0; deleteVao(1); model.close();
        });
        if (failures != 0) throw new AssertionError(failures + " composite GPU close ownership scenarios failed");
        System.out.println("PASS: best-effort composite GPU close, " + assertions + " assertions; first failure propagated, later members attempted");
    }
    private static Throwable closeFailure(CheckedRunnable action) throws Exception {
        try { action.run(); } catch (RuntimeException | Error ex) { return ex; }
        throw new AssertionError("injected close failure was not observed");
    }
    private static RawMesh mesh() {
        RawMesh mesh = new RawMesh(new MaterialProp());
        for (int i = 0; i < 3; i++) {
            Vertex vertex = new Vertex(new Vector3f(1 + i, 2, 3), new Vector3f(0, 1, 0));
            vertex.color = 0x11223344; vertex.u = .25F; vertex.v = .5F; vertex.light = 0x00550066; mesh.vertices.add(vertex);
        }
        mesh.faces.add(new Face(new int[]{0, 1, 2})); return mesh;
    }
    private static RawModel model() {
        RawModel model = new RawModel(); model.meshList = new LinkedHashMap<>();
        RawMesh first = mesh(), second = mesh(); first.materialProp.shaderName = "first"; second.materialProp.shaderName = "second";
        model.meshList.put(first.materialProp, first); model.meshList.put(second.materialProp, second); return model;
    }
    private static VertAttrMapping mapping() {
        var builder = new VertAttrMapping.Builder();
        for (var type : VertAttrType.values()) builder.set(type, VertAttrSrc.GLOBAL);
        for (var type : new VertAttrType[]{VertAttrType.POSITION, VertAttrType.COLOR, VertAttrType.UV_TEXTURE, VertAttrType.UV_LIGHTMAP, VertAttrType.NORMAL}) builder.set(type, VertAttrSrc.VERTEX_BUF);
        return builder.build();
    }
    private static Object prepared(Object source) throws Exception {
        try { return source.getClass().getMethod("prepareUpload", VertAttrMapping.class).invoke(source, mapping()); }
        catch (InvocationTargetException ex) { throw new AssertionError(ex.getCause()); }
    }
    private static long byteSize(Object task) throws Exception {
        return ((Number)Class.forName("cn.zbx1425.sowcerext.model.PreparedUpload").getMethod("byteSize").invoke(task)).longValue();
    }
    private static long estimate(Object source) throws Exception {
        return ((Number)source.getClass().getMethod("estimatedUploadBytes", VertAttrMapping.class).invoke(source, mapping())).longValue();
    }
    private static void close(Object task) throws Exception { ((AutoCloseable)task).close(); }
    private static void expectFailure(CheckedRunnable call) throws Exception {
        try { call.run(); } catch (RuntimeException | OutOfMemoryError expected) { assertions++; return; }
        throw new AssertionError("injected failure did not reach the actual method");
    }
    private static void expectCancellation(CheckedRunnable call) throws Exception {
        try { call.run(); } catch (CancellationException expected) { assertions++; return; }
        throw new AssertionError("cancelled task accepted upload");
    }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private interface CheckedRunnable { void run() throws Exception; }
}
