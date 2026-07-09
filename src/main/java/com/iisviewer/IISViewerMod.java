package com.iisviewer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class IISViewerMod implements ClientModInitializer {

    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        // Register keybinding — uses reflection to handle both
        // 1.21.4 (KeyBinding + KeyBindingHelper) and 26.2 (KeyMapping + KeyMappingHelper)
        registerKeyBinding();

        // Toggle HUD on key press
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (toggleKey.wasPressed()) {
                    ModConfig.enabled = !ModConfig.enabled;
                    ModConfig.save();
                    if (client.player != null) {
                        Text msg = ModConfig.enabled
                            ? Text.literal("\u00A7a[IISViewer] HUD \u0432\u043A\u043B\u044E\u0447\u0435\u043D")
                            : Text.literal("\u00A77[IISViewer] HUD \u0432\u044B\u043A\u043B\u044E\u0447\u0435\u043D");
                        client.player.sendMessage(msg, true);
                    }
                }
            } catch (Throwable e) {
                System.err.println("[IISViewer] Tick error: " + e.getMessage());
            }
        });

        // HUD rendering is injected via Mixin (IISViewerMixin) into InGameHud.render()
        // This replaces the removed HudRenderCallback from Fabric API 0.152.2+26.2

        // Load config
        ModConfig.load();
    }

    /**
     * Registers the keybinding in a cross-version compatible way.
     * 1.21.4: KeyBindingHelper.registerKeyBinding(KeyBinding)
     * 26.2:   KeyMappingHelper.registerKeyMapping(KeyMapping)
     * Uses reflection for ALL paths to avoid verifier issues after bytecode remapping.
     */
    private void registerKeyBinding() {
        // Try 26.2 path: KeyMappingHelper.registerKeyMapping()
        try {
            Class<?> kmClass = Class.forName("net.minecraft.client.KeyMapping");
            Class<?> inputConstantsType = Class.forName("com.mojang.blaze3d.platform.InputConstants$Type");
            Class<?> categoryClass = Class.forName("net.minecraft.client.KeyMapping$Category");
            Object catInstance = categoryClass.getField("MISC").get(null);

            Constructor<?> kmCtor = kmClass.getConstructor(
                String.class, inputConstantsType, int.class, categoryClass
            );
            Object typeEnum = inputConstantsType.getMethod("valueOf", String.class)
                .invoke(null, "KEYSYM");
            Object keyMapping = kmCtor.newInstance(
                "key.iisviewer.toggle", typeEnum, GLFW.GLFW_KEY_R, catInstance
            );

            Class<?> helperClass = Class.forName("net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper");
            Method regMethod = helperClass.getMethod("registerKeyMapping", kmClass);
            regMethod.invoke(null, keyMapping);

            // After remapping, (KeyBinding) cast becomes (KeyMapping) — both are field-compatible
            toggleKey = (KeyBinding) keyMapping;
            return;
        } catch (ClassNotFoundException e) {
            // 26.2 classes not found — fall through to 1.21.4 path
        } catch (Exception e) {
            throw new RuntimeException("Failed to register 26.2 keybinding", e);
        }

        // 1.21.4 path: KeyBindingHelper.registerKeyBinding() — via reflection to avoid verifier issues
        try {
            Class<?> kbClass = Class.forName("net.minecraft.client.option.KeyBinding");
            Class<?> inputUtilType = Class.forName("net.minecraft.client.util.InputUtil$Type");
            Constructor<?> kbCtor = kbClass.getConstructor(
                String.class, inputUtilType, int.class, String.class
            );
            Object typeEnum = inputUtilType.getMethod("valueOf", String.class)
                .invoke(null, "KEYSYM");
            Object keyBinding = kbCtor.newInstance(
                "key.iisviewer.toggle", typeEnum, GLFW.GLFW_KEY_R, "category.iisviewer"
            );

            Class<?> helperClass = Class.forName("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper");
            Method regMethod = helperClass.getMethod("registerKeyBinding", kbClass);
            regMethod.invoke(null, keyBinding);

            toggleKey = (KeyBinding) keyBinding;
            return;
        } catch (Exception e) {
            throw new RuntimeException("Failed to register keybinding", e);
        }
    }
}
