package cn.zbx1425.mtrsteamloco.compatibility;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;

/** Executes Sponge's actual injector against Camera bytes without loading or starting Minecraft. */
public final class CameraWeavingCheck {
    public static void main(String[] args) throws Exception {
        System.setProperty("mixin.service", HeadlessService.class.getName());
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("camera-weaving.mixins.json");
        HeadlessService service = (HeadlessService) MixinService.getService();
        ClassNode camera = service.getClassNode("net.minecraft.client.Camera");
        boolean neoForgeCamera = camera.methods.stream().anyMatch(method -> method.name.equals("setRotation") && method.desc.equals("(FFF)V"));
        if (args.length > 0 && ((!args[0].equals("neoforge") && !args[0].equals("fabric"))
                || neoForgeCamera != args[0].equals("neoforge"))) {
            throw new AssertionError("Wrong Camera classpath for " + args[0] + ": setRotation(FFF)V exists=" + neoForgeCamera);
        }
        boolean transformed = service.transformerFactory().createTransformer()
                .transformClass(environment, "net.minecraft.client.Camera", camera);
        if (!transformed) throw new AssertionError("Camera was not transformed");
        int rollCalls = 0;
        for (var method : camera.methods) {
            int rotation = -1, hook = -1, firstDirection = Integer.MAX_VALUE, directions = 0;
            for (int i = 0; i < method.instructions.size(); i++) {
                if (!(method.instructions.get(i) instanceof MethodInsnNode call)) continue;
                if (call.owner.equals(camera.name) && call.name.contains("rollRotation")) {
                    rollCalls++;
                    hook = i;
                }
                if (call.owner.equals("org/joml/Quaternionf") && call.name.equals("rotationYXZ")) rotation = i;
                if (call.owner.equals("org/joml/Vector3fc") && call.name.equals("rotate")) {
                    firstDirection = Math.min(firstDirection, i);
                    directions++;
                }
            }
            if (hook >= 0 && (!method.name.equals("setRotation") || rotation < 0 || hook <= rotation
                    || hook >= firstDirection || directions != 3)) {
                throw new AssertionError("Camera roll must follow quaternion initialization and precede all three direction vectors: " + method.name + method.desc);
            }
        }
        if (rollCalls != 1) throw new AssertionError("Expected one woven rollRotation call, got " + rollCalls);
        System.out.println("PASS: actual Sponge Mixin Camera weaving (" + (neoForgeCamera ? "NeoForge FFF" : "vanilla/Fabric FF")
                + "), exactly one rollRotation call before all three direction vectors; no game classes initialized");
    }

    public static final class HeadlessService extends MixinServiceAbstract
            implements IClassProvider, IClassBytecodeProvider, IClassTracker {
        @Override public String getName() { return "ANTE headless Camera regression"; }
        @Override public boolean isValid() { return true; }
        @Override public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
        @Override public IClassProvider getClassProvider() { return this; }
        @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
        @Override public IClassTracker getClassTracker() { return this; }
        @Override public ITransformerProvider getTransformerProvider() { return null; }
        @Override public IMixinAuditTrail getAuditTrail() { return null; }
        @Override public IFeatureValidator getFeatureValidator() { return IFeatureValidator.ALLOW_ALL; }
        @Override public IAdviceProvider getAdviceProvider() { return IAdviceProvider.GENERIC; }
        @Override public Collection<String> getPlatformAgents() { return List.of(); }
        @Override public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("camera-weaving"); }
        @Override public InputStream getResourceAsStream(String name) { return getClass().getClassLoader().getResourceAsStream(name); }
        @Override public URL[] getClassPath() { return new URL[0]; }
        @Override public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
        @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.")) throw new AssertionError("Cannot load game class " + name);
            return Class.forName(name, initialize, getClass().getClassLoader());
        }
        @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
        @Override public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException { return getClassNode(name, false); }
        @Override public ClassNode getClassNode(String name, boolean transform) throws ClassNotFoundException, IOException { return getClassNode(name, transform, 0); }
        @Override public ClassNode getClassNode(String name, boolean transform, int flags) throws ClassNotFoundException, IOException {
            try (var stream = getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (stream == null) throw new ClassNotFoundException(name);
                ClassNode result = new ClassNode();
                new ClassReader(stream).accept(result, flags);
                return result;
            }
        }
        @Override public void registerInvalidClass(String name) { }
        @Override public boolean isClassLoaded(String name) { return false; }
        @Override public String getClassRestrictions(String name) { return ""; }
        IMixinTransformerFactory transformerFactory() { return getInternal(IMixinTransformerFactory.class); }
    }

    public static final class Properties implements IGlobalPropertyService {
        private final Map<IPropertyKey, Object> values = new HashMap<>();
        private record Key(String name) implements IPropertyKey { }
        @Override public IPropertyKey resolveKey(String name) { return new Key(name); }
        @SuppressWarnings("unchecked")
        @Override public <T> T getProperty(IPropertyKey key) { return (T) values.get(key); }
        @Override public void setProperty(IPropertyKey key, Object value) { values.put(key, value); }
        @SuppressWarnings("unchecked")
        @Override public <T> T getProperty(IPropertyKey key, T fallback) { return (T) values.getOrDefault(key, fallback); }
        @Override public String getPropertyString(IPropertyKey key, String fallback) { return getProperty(key, fallback).toString(); }
    }
}
