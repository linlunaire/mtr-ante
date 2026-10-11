package mtr.mappings;

import com.google.common.base.Supplier;
import dev.architectury.impl.NetworkAggregator;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-only loader registration/packet construction environment. The actual Architectury
 * NetworkAggregator, payload codecs and vanilla packet classes remain unchanged. This does
 * not run Fabric/NeoForge registration events or establish a real network connection.
 */
public final class NetworkTestAdaptor implements NetworkAggregator.Adaptor {

	private final Map<NetworkManager.Side, Map<ResourceLocation, Registration<?>>> registrations = new EnumMap<>(NetworkManager.Side.class);

	private NetworkTestAdaptor() {
		for (NetworkManager.Side side : NetworkManager.Side.values()) {
			registrations.put(side, new LinkedHashMap<>());
		}
	}

	/** Installs before the memoized platform adaptor is resolved, without modifying final fields. */
	public static NetworkTestAdaptor install() throws ReflectiveOperationException {
		final Object supplier = NetworkAggregator.ADAPTOR;
		if (!supplier.getClass().getName().equals("com.google.common.base.Suppliers$NonSerializableMemoizingSupplier")) {
			throw new IllegalStateException("Unexpected Architectury adaptor supplier: " + supplier.getClass().getName());
		}
		final Field delegate = supplier.getClass().getDeclaredField("delegate");
		if (Modifier.isFinal(delegate.getModifiers()) || !delegate.trySetAccessible()) {
			throw new IllegalStateException("Cannot replace the test-only platform adaptor supplier");
		}
		if (delegate.get(supplier) == null) {
			throw new IllegalStateException("Install the test adaptor before resolving the platform adaptor");
		}
		final NetworkTestAdaptor adaptor = new NetworkTestAdaptor();
		delegate.set(supplier, (Supplier<NetworkAggregator.Adaptor>) () -> adaptor);
		if (NetworkAggregator.ADAPTOR.get() != adaptor) {
			throw new IllegalStateException("Test adaptor installation did not take effect");
		}
		return adaptor;
	}

	/** Clears registrations between simulated physical-side runs in this standalone test JVM. */
	public void reset() {
		registrations.values().forEach(Map::clear);
		NetworkAggregator.C2S_RECEIVER.clear();
		NetworkAggregator.S2C_RECEIVER.clear();
		NetworkAggregator.C2S_CODECS.clear();
		NetworkAggregator.S2C_CODECS.clear();
		NetworkAggregator.C2S_TRANSFORMERS.clear();
		NetworkAggregator.S2C_TRANSFORMERS.clear();
	}

	public Map<ResourceLocation, Registration<?>> registrations(NetworkManager.Side side) {
		return Map.copyOf(registrations.get(side));
	}

	public Registration<?> registration(NetworkManager.Side side, ResourceLocation id) {
		return registrations.get(side).get(id);
	}

	@Override
	public <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, NetworkManager.NetworkReceiver<T> receiver) {
		register(NetworkManager.Side.C2S, type, codec, receiver);
	}

	@Override
	public <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, NetworkManager.NetworkReceiver<T> receiver) {
		register(NetworkManager.Side.S2C, type, codec, receiver);
	}

	@Override
	public <T extends CustomPacketPayload> void registerS2CType(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		register(NetworkManager.Side.S2C, type, codec, null);
	}

	@Override
	public <T extends CustomPacketPayload> Packet<?> toC2SPacket(T payload) {
		return new ServerboundCustomPayloadPacket(payload);
	}

	@Override
	public <T extends CustomPacketPayload> Packet<?> toS2CPacket(T payload) {
		return new ClientboundCustomPayloadPacket(payload);
	}

	private <T extends CustomPacketPayload> void register(NetworkManager.Side side, CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, NetworkManager.NetworkReceiver<T> receiver) {
		// NeoForge requires a unique play payload ID even across opposite directions.
		if (registrations.values().stream().anyMatch(values -> values.containsKey(type.id()))) {
			throw new IllegalStateException("Duplicate test loader registration: " + side + " " + type.id());
		}
		registrations.get(side).put(type.id(), new Registration<>(type, codec, receiver));
	}

	public record Registration<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, NetworkManager.NetworkReceiver<T> receiver) {
	}
}
