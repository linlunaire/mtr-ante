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
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;

/** Supplies bytecode and isolated properties to Sponge Mixin without launching a game. */
public final class HeadlessMixinHarness {
    public static final class HeadlessService extends MixinServiceAbstract
            implements IClassProvider, IClassBytecodeProvider, IClassTracker {
        @Override public String getName() { return "ANTE headless rail regression"; }
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
        @Override public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("rail-weaving"); }
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
