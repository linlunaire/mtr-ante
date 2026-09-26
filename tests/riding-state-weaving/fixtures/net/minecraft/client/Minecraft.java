package net.minecraft.client;

/** Only the nullable-player boundary is substituted; no boarding logic is mocked. */
public final class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public net.minecraft.client.player.LocalPlayer player;
    public static Minecraft getInstance() { return INSTANCE; }
}
