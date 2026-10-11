package cn.zbx1425.mtrsteamloco.fabric;

import cn.zbx1425.mtrsteamloco.ClientConfig;
import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.MainClient;
import cn.zbx1425.mtrsteamloco.gui.ConfigScreen;
import cn.zbx1425.mtrsteamloco.gui.ScriptDebugOverlay;
import cn.zbx1425.mtrsteamloco.render.RenderUtil;
import cn.zbx1425.mtrsteamloco.render.train.SteamSmokeParticle;
import mtr.mappings.Text;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import cn.zbx1425.mtrsteamloco.scripting.ScriptContextManager;

public class MainFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {

		ParticleProviderRegistry.getInstance().register(Main.PARTICLE_STEAM_SMOKE, SteamSmokeParticle.Provider::new);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(

					ClientCommands.literal("mtrnte")
							.then(ClientCommands.literal("config")
									.executes(context -> {
										Minecraft.getInstance().schedule(() -> {
											Minecraft.getInstance().gui.setScreen(ConfigScreen.createScreen(Minecraft.getInstance().gui.screen()));
										});
										return 1;
									}))
							.then(ClientCommands.literal("hideriding")
                                    .executes(context -> {
                                        ClientConfig.hideRidingTrain = !ClientConfig.hideRidingTrain;
                                        return 1;
                                    }))
							.then(ClientCommands.literal("clearDebugInfo")
                                    .executes(context -> {
                                        ScriptContextManager.clearDebugInfo();
										cn.zbx1425.mtrsteamloco.gui.ScriptDebugOverlay.STATIC.clear();
                                        return 1;
                                    }))
							.then(ClientCommands.literal("stat")
									.executes(context -> {
										Minecraft.getInstance().schedule(() -> {
											String info = RenderUtil.getRenderStatusMessage();
											Minecraft.getInstance().player.sendSystemMessage(Text.literal(info));
										});
										return 1;
									}))
			);

		});

		HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath(Main.MOD_ID, "script_debug"), (guiGraphics, delta) -> {
			ScriptDebugOverlay.render(guiGraphics);
		});

		LevelExtractionEvents.END_EXTRACTION.register(event -> MainClient.incrementGameTick());
		MainClient.init();
	}

}
