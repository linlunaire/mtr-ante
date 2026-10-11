package mtr.mappings;

import dev.architectury.impl.NetworkAggregator;
import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Exercises ANTE startup declarations, the real MTR payload factory and Architectury encoding without a game launch. */
public final class NetworkCompatibilityCheck {
	public static void main(String[] args) throws Exception {
		final Path root = Path.of(args[0]);
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		final NetworkTestAdaptor adaptor = NetworkTestAdaptor.install();
		final Map<String, ResourceLocation> ids = packetIds(root);
		final Set<ResourceLocation> s2c = declaredIds(root.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/MainClient.java"), "RegistryClient", ids);
		final Set<ResourceLocation> c2s = declaredIds(root.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/Main.java"), "Registry", ids);
		require(s2c.size() == 3 && c2s.size() == 6, "Review changed packet inventory");
		initializeFromEntryPoint(root, Env.SERVER, ids);
		// Exercise the original red path before asserting inventory, so removing the hook reproduces codec-null.
		encode(ResourceLocation.parse("mtrsteamloco:version_check"), new byte[]{5, '3', '.', '3', '.', '2'});
		registerC2S(c2s);
		require(adaptor.registrations(NetworkManager.Side.S2C).keySet().equals(s2c.stream().map(NetworkCompatibilityCheck::s2cId).collect(Collectors.toSet())), "Dedicated-server S2C types differ from client declarations");
		require(NetworkAggregator.S2C_RECEIVER.isEmpty(), "Dedicated-server initialization registered client handlers");
		final Map<ResourceLocation, byte[]> original = new LinkedHashMap<>();
		final Map<ResourceLocation, CustomPacketPayload> packets = new LinkedHashMap<>();
		for (ResourceLocation id : s2c) {
			final byte[] bytes = sampleBytes(id);
			original.put(id, bytes);
			final CustomPacketPayload packet = encode(id, bytes);
			packets.put(id, wireRoundTrip(adaptor.registration(NetworkManager.Side.S2C, s2cId(id)), packet));
		}
		final Map<ResourceLocation, byte[]> delivered = new LinkedHashMap<>();
		adaptor.reset();
		NetworkUtilities.PAYLOAD_TYPES.clear();
		initializeFromEntryPoint(root, Env.CLIENT, ids);
		require(NetworkAggregator.S2C_CODECS.isEmpty() && adaptor.registrations(NetworkManager.Side.S2C).isEmpty(), "Physical client must not pre-register S2C types");
		registerC2S(c2s);
		for (ResourceLocation id : s2c) {
			NetworkUtilities.registerReceiverS2C(id, (buffer, context) -> {
				final FriendlyByteBuf received = (FriendlyByteBuf) buffer;
				try {
					final byte[] bytes = new byte[received.readableBytes()];
					received.readBytes(bytes);
					delivered.put(id, bytes);
				} finally { received.release(); }
			});
		}
		require(adaptor.registrations(NetworkManager.Side.C2S).keySet().equals(c2s), "C2S IDs changed");
		require(adaptor.registrations(NetworkManager.Side.S2C).size() == 3, "Missing/duplicate client S2C registration");
		for (var packet : packets.entrySet()) {
			deliver(adaptor.registration(NetworkManager.Side.S2C, s2cId(packet.getKey())), packet.getValue());
			require(Arrays.equals(original.get(packet.getKey()), delivered.get(packet.getKey())), "Payload bytes changed for " + packet.getKey());
		}
		// A negative control keeps this harness sensitive to the real missing-codec bug.
		adaptor.reset();
		try {
			encode(ResourceLocation.parse("mtrsteamloco:version_check"), new byte[0]);
			throw new AssertionError("Missing S2C codec unexpectedly encoded");
		} catch (NullPointerException expected) {
			require(expected.getMessage().contains("codec"), "Unexpected failure in missing-codec control");
		}
		System.out.println("PASS: ANTE server/client startup hook, 3 S2C / 6 C2S IDs, no duplicate client registration, actual Architectury encoding/wire codecs/receiver byte round-trips and missing-codec negative control (test loader adaptor; no game connection)");
	}

	private static CustomPacketPayload encode(ResourceLocation id, byte[] bytes) throws Exception {
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes.clone()));
		try {
			if (buffer.isReadable()) buffer.readByte(); // The existing S2C factory resets the read position.
			final var factory = NetworkUtilities.class.getDeclaredMethod("createPayload", ResourceLocation.class, FriendlyByteBuf.class);
			factory.setAccessible(true);
			final NetworkUtilities.RawPayload payload = (NetworkUtilities.RawPayload) factory.invoke(null, id, buffer);
			require(payload.type().id().equals(s2cId(id)) && Arrays.equals(payload.bytes(), bytes), "MTR payload ID/bytes changed");
			final var packet = NetworkManager.toPacket(NetworkManager.Side.S2C, payload, RegistryAccess.EMPTY);
			require(packet instanceof ClientboundCustomPayloadPacket, "Wrong vanilla packet direction");
			return ((ClientboundCustomPayloadPacket) packet).payload();
		} finally { buffer.release(); }
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static CustomPacketPayload wireRoundTrip(NetworkTestAdaptor.Registration<?> registration, CustomPacketPayload payload) {
		final RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			final StreamCodec codec = registration.codec();
			codec.encode(wire, payload);
			final CustomPacketPayload decoded = (CustomPacketPayload) codec.decode(wire);
			require(!wire.isReadable() && decoded.type().id().equals(payload.type().id()), "Wire codec changed ID or left bytes");
			return decoded;
		} finally { wire.release(); }
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void deliver(NetworkTestAdaptor.Registration<?> registration, CustomPacketPayload payload) {
		final NetworkManager.NetworkReceiver receiver = registration.receiver();
		receiver.receive(payload, new NetworkManager.PacketContext() {
			@Override public Player getPlayer() { return null; }
			@Override public void queue(Runnable action) { action.run(); }
			@Override public Env getEnvironment() { return Env.CLIENT; }
			@Override public RegistryAccess registryAccess() { return RegistryAccess.EMPTY; }
		});
	}

	private static void registerC2S(Set<ResourceLocation> ids) {
		for (ResourceLocation id : ids) NetworkUtilities.registerReceiverC2S(id, (server, player, buffer) -> { });
	}

	private static byte[] sampleBytes(ResourceLocation id) {
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			buffer.writeUtf(id + " / 列车 🚆");
			final byte[] body = new byte[16385];
			for (int i = 0; i < body.length; i++) body[i] = (byte) (i * 31);
			buffer.writeByteArray(body);
			final byte[] bytes = new byte[buffer.readableBytes()];
			buffer.readBytes(bytes);
			return bytes;
		} finally { buffer.release(); }
	}

	private static Set<ResourceLocation> declaredIds(Path source, String registry, Map<String, ResourceLocation> ids) throws Exception {
		return Pattern.compile(registry + "\\.registerNetworkReceiver\\((\\w+\\.\\w+),").matcher(Files.readString(source)).results().map(match -> ids.get(match.group(1))).collect(Collectors.toSet());
	}

	private static Map<String, ResourceLocation> packetIds(Path root) throws Exception {
		final Map<String, ResourceLocation> ids = new LinkedHashMap<>();
		try (var sources = Files.list(root.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/network"))) {
			for (Path file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
				final String className = file.getFileName().toString().replace(".java", "");
				Pattern.compile("ResourceLocation (\\w+) = ResourceLocation.fromNamespaceAndPath\\(Main.MOD_ID, \"([^\"]+)\"\\)").matcher(Files.readString(file)).results()
						.forEach(match -> ids.put(className + "." + match.group(1), ResourceLocation.fromNamespaceAndPath("mtrsteamloco", match.group(2))));
			}
		}
		return ids;
	}

	private static void initializeFromEntryPoint(Path root, Env environment, Map<String, ResourceLocation> ids) throws Exception {
		final String entryPoint = Files.readString(root.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/Main.java"));
		final var init = Pattern.compile("public static void init\\(RegistriesWrapper registries\\) \\{([\\s\\S]*?)LOGGER\\.info\\(").matcher(entryPoint);
		if (!init.find()) throw new AssertionError("Review changed ANTE initialization boundary");
		final Binding binding = new Binding();
		binding.setVariable("environment", environment);
		final Map<String, Map<String, ResourceLocation>> constants = new LinkedHashMap<>();
		ids.forEach((name, id) -> {
			final String[] parts = name.split("\\.");
			constants.computeIfAbsent(parts[0], key -> new LinkedHashMap<>()).put(parts[1], id);
		});
		constants.forEach(binding::setVariable);
		// Execute the actual early hook without loading client screens or registering game blocks.
		new GroovyShell(binding).evaluate(init.group(1).replace("dev.architectury.platform.Platform.getEnvironment()", "environment"));
	}

	private static ResourceLocation s2cId(ResourceLocation id) { return ResourceLocation.parse(id + "_s2c"); }
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
