package com.iisviewer;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration for IISViewer mod.
 * Allows users to customize HUD position, colors, and visibility.
 */
public class ModConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("iisviewer.properties");

    // HUD position (0.0 = left/top, 1.0 = right/bottom)
    public static float anchorX = 1.0f;
    public static float anchorY = 1.0f;

    // Offset from anchor in pixels
    public static int offsetX = 8;
    public static int offsetY = 8;

    // Whether to show the HUD
    public static boolean enabled = true;

    // Whether to show empty slots
    public static boolean showEmptySlots = false;

    // Gradient high color (100% integrity) - #006600
    public static int gradientRedHigh = 0x00;
    public static int gradientGreenHigh = 0x66;
    public static int gradientBlueHigh = 0x00;

    // Gradient low color (0% integrity) - #990000
    public static int gradientRedLow = 0x99;
    public static int gradientGreenLow = 0x00;
    public static int gradientBlueLow = 0x00;

    public static void load() {
        Properties props = new Properties();

        if (Files.exists(CONFIG_PATH)) {
            try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                props.load(reader);
            } catch (IOException e) {
                // Ignore — use defaults
            }
        }

        anchorX = parseFloat(props.getProperty("anchor-x"), 1.0f);
        anchorY = parseFloat(props.getProperty("anchor-y"), 1.0f);
        offsetX = parseInt(props.getProperty("offset-x"), 8);
        offsetY = parseInt(props.getProperty("offset-y"), 8);
        enabled = parseBool(props.getProperty("enabled"), true);
        showEmptySlots = parseBool(props.getProperty("show-empty-slots"), false);
        gradientRedHigh = parseInt(props.getProperty("gradient-high-r"), 0x00);
        gradientGreenHigh = parseInt(props.getProperty("gradient-high-g"), 0x66);
        gradientBlueHigh = parseInt(props.getProperty("gradient-high-b"), 0x00);
        gradientRedLow = parseInt(props.getProperty("gradient-low-r"), 0x99);
        gradientGreenLow = parseInt(props.getProperty("gradient-low-g"), 0x00);
        gradientBlueLow = parseInt(props.getProperty("gradient-low-b"), 0x00);

        save();
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("anchor-x", String.valueOf(anchorX));
        props.setProperty("anchor-y", String.valueOf(anchorY));
        props.setProperty("offset-x", String.valueOf(offsetX));
        props.setProperty("offset-y", String.valueOf(offsetY));
        props.setProperty("enabled", String.valueOf(enabled));
        props.setProperty("show-empty-slots", String.valueOf(showEmptySlots));
        props.setProperty("gradient-high-r", String.valueOf(gradientRedHigh));
        props.setProperty("gradient-high-g", String.valueOf(gradientGreenHigh));
        props.setProperty("gradient-high-b", String.valueOf(gradientBlueHigh));
        props.setProperty("gradient-low-r", String.valueOf(gradientRedLow));
        props.setProperty("gradient-low-g", String.valueOf(gradientGreenLow));
        props.setProperty("gradient-low-b", String.valueOf(gradientBlueLow));

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (var writer = Files.newBufferedWriter(CONFIG_PATH)) {
                props.store(writer, "IISViewer Mod Configuration");
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    private static float parseFloat(String value, float def) {
        if (value == null) return def;
        try { return Float.parseFloat(value); } catch (NumberFormatException e) { return def; }
    }

    private static int parseInt(String value, int def) {
        if (value == null) return def;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return def; }
    }

    private static boolean parseBool(String value, boolean def) {
        if (value == null) return def;
        return Boolean.parseBoolean(value);
    }
}
