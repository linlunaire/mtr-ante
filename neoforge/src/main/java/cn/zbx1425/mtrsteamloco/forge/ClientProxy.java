package cn.zbx1425.mtrsteamloco.forge;

import cn.zbx1425.mtrsteamloco.ClientConfig;
import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.MainClient;
import cn.zbx1425.mtrsteamloco.gui.ConfigScreen;
import cn.zbx1425.mtrsteamloco.render.RenderUtil;
import cn.zbx1425.mtrsteamloco.render.train.SteamSmokeParticle;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import cn.zbx1425.mtrsteamloco.gui.ScriptDebugOverlay;
import cn.zbx1425.mtrsteamloco.scripting.ScriptContextManager;

public class ClientProxy {

    public static void initClient() {

    }


    public static class ModEventBusListener {

        @SubscribeEvent
        public static void onClientSetupEvent(FMLClientSetupEvent event) {
            MainClient.init();
        }

        @SubscribeEvent
        public static void onRegistryParticleFactory(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(Main.PARTICLE_STEAM_SMOKE, SteamSmokeParticle.Provider::new);
        }

        @SubscribeEvent
        public static void onRegisterDebugEntries(net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent event) {
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath(Main.MOD_ID, "render_stats");
            event.register(id, (display, level, clientChunk, serverChunk) -> display.addLine(
                    "[NTE] Calls: " + MainClient.drawContext.drawCallCount
                            + ", Batches: " + MainClient.drawContext.batchCount
                            + ", Faces: " + (MainClient.drawContext.singleFaceCount + MainClient.drawContext.instancedFaceCount)));
            for (var profile : net.minecraft.client.gui.components.debug.DebugScreenProfile.values()) {
                event.includeInProfile(id, profile, net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.IN_OVERLAY);
            }
        }
    }

    public static class ForgeEventBusListener {

        @SubscribeEvent
        public static void onOverlayRender(CustomizeGuiOverlayEvent.Chat event) {
        ScriptDebugOverlay.render(event.getGuiGraphics());
        }

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(
                    Commands.literal("mtrnte")
                            .then(Commands.literal("config")
                                    .executes(context -> {
                                        Minecraft.getInstance().schedule(() -> {
                                            Minecraft.getInstance().gui.setScreen(ConfigScreen.createScreen(Minecraft.getInstance().gui.screen()));
                                        });
                                        return 1;
                                    }))
                            .then(Commands.literal("hideriding")
                                    .executes(context -> {
                                        ClientConfig.hideRidingTrain = !ClientConfig.hideRidingTrain;
                                        return 1;
                                    }))
                            .then(Commands.literal("clearDebugInfo")
                                    .executes(context -> {
                                        ScriptContextManager.clearDebugInfo();
                                        return 1;
                                    }))
                            .then(Commands.literal("stat")
                                    .executes(context -> {
                                        Minecraft.getInstance().schedule(() -> {
                                            String info = RenderUtil.getRenderStatusMessage();
                                            Minecraft.getInstance().player.sendSystemMessage(Text.literal(info));
                                        });
                                        return 1;
                                    }))
            );
        }
    }
}
