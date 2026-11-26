package dev.mapmaker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class MapMakerClient implements ClientModInitializer {
    private boolean wasDown;

    @Override
    public void onInitializeClient() {
        MapMakerConfig.load();

        // /mapmaker always works, even before you bind a key
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("mapmaker").executes(ctx -> {
                    MinecraftClient mc = ctx.getSource().getClient();
                    mc.send(() -> mc.setScreen(new MapMakerScreen(null)));
                    return 1;
                })));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            int key = MapMakerConfig.get().key;
            if (key <= 0 || client.getWindow() == null) return;
            boolean down = GLFW.glfwGetKey(client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
            if (down && !wasDown && client.currentScreen == null && client.player != null) {
                client.setScreen(new MapMakerScreen(null));
            }
            wasDown = down;
        });
    }
}
