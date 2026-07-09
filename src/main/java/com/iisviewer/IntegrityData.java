package com.iisviewer;

import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Item Integrity System (IIS) data from item lore/description.
 * <p>
 * The server plugin stores integrity in the item's lore as:
 * {@code Integrity: 100.000%} or {@code Integrity: 45,500%}
 * (with dot or comma as decimal separator, 3 decimal places).
 */
public class IntegrityData {

    // Pattern to match "Integrity: <number with 3 decimals>%"
    // Handles both dot (100.000) and comma (45,500) as decimal separator
    private static final Pattern INTEGRITY_PATTERN =
            Pattern.compile("[Ii]ntegrity[:\\s]+([0-9]+[.,][0-9]{3})%");

    // Cached reflection handles for lore component access
    private static Object LORE_COMPONENT_TYPE; // ComponentType for LORE
    private static boolean REFLECTION_INITIALIZED = false;
    private static boolean REFLECTION_FAILED = false;

    public final double current;
    public final double max;
    public final boolean hasIntegrity;
    public final boolean unbreakable;

    private IntegrityData(double current, double max, boolean hasIntegrity, boolean unbreakable) {
        this.current = current;
        this.max = max;
        this.hasIntegrity = hasIntegrity;
        this.unbreakable = unbreakable;
    }

    public double getPercent() {
        if (max <= 0) return 0;
        return Math.max(0, Math.min(100.0, (current / max) * 100.0));
    }

    /**
     * Lazily initialises reflection handles for lore component access.
     */
    private static void initReflection() {
        if (REFLECTION_INITIALIZED) return;
        REFLECTION_INITIALIZED = true;

        try {
            // Try 26.2 path: net.minecraft.core.component.DataComponents
            Class<?> dataComponentsClass;
            try {
                dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            } catch (ClassNotFoundException e) {
                // Fall back to Yarn: net.minecraft.component.DataComponentTypes
                dataComponentsClass = Class.forName("net.minecraft.component.DataComponentTypes");
            }

            // Try to find LORE field - try various names
            String[] fieldNames = {"LORE", "LORE_COMPONENT", "ITEM_LORE"};
            Field loreField = null;
            for (String name : fieldNames) {
                try {
                    loreField = dataComponentsClass.getField(name);
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }

            if (loreField != null) {
                LORE_COMPONENT_TYPE = loreField.get(null);
            } else {
                // Fallback: find any field whose type name contains "Lore" or "lore"
                for (Field f : dataComponentsClass.getFields()) {
                    String typeName = f.getType().getName();
                    if (typeName.contains("Lore") || typeName.contains("ItemLore")) {
                        loreField = f;
                        break;
                    }
                }
                if (loreField != null) {
                    LORE_COMPONENT_TYPE = loreField.get(null);
                } else {
                    System.err.println("[IISViewer] Could not find LORE field in " + dataComponentsClass.getName());
                    REFLECTION_FAILED = true;
                }
            }
        } catch (Throwable e) {
            System.err.println("[IISViewer] Reflection init error: " + e.getMessage());
            REFLECTION_FAILED = true;
        }
    }

    /**
     * Reads integrity data from an ItemStack by parsing the item's lore.
     *
     * @param stack the item to read data from
     * @return IntegrityData — never null; {@code hasIntegrity} indicates validity
     */
    public static IntegrityData from(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) {
                return empty();
            }

            if (!REFLECTION_INITIALIZED) {
                initReflection();
            }

            if (REFLECTION_FAILED || LORE_COMPONENT_TYPE == null) {
                return empty();
            }

            // Get the lore component from the item stack via reflection
            Object loreComponent = invokeStackGet(stack, LORE_COMPONENT_TYPE);
            if (loreComponent == null) {
                return empty();
            }

            // Get lines() method result
            List<?> lines = getLoreLines(loreComponent);
            if (lines == null || lines.isEmpty()) {
                return empty();
            }

            // Search for "Integrity:" in each line
            for (Object line : lines) {
                String text = getTextString(line);
                if (text == null) continue;

                Matcher matcher = INTEGRITY_PATTERN.matcher(text);
                if (matcher.find()) {
                    String numStr = matcher.group(1);
                    // Replace comma with dot for parsing
                    numStr = numStr.replace(',', '.');
                    double value = Double.parseDouble(numStr);
                    // Value is between 0.0 and 100.0 (the percentage)
                    return new IntegrityData(value, 100.0, true, false);
                }
            }

            return empty();
        } catch (Throwable e) {
            System.err.println("[IISViewer] IntegrityData.from() error: " + e.getMessage());
            return empty();
        }
    }

    /**
     * Calls stack.get(componentType) via reflection.
     */
    private static Object invokeStackGet(ItemStack stack, Object componentType) {
        try {
            Method getMethod = stack.getClass().getMethod("get", componentType.getClass());
            return getMethod.invoke(stack, componentType);
        } catch (NoSuchMethodException e) {
            try {
                for (Method m : stack.getClass().getMethods()) {
                    if (m.getName().equals("get") && m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        if (paramType.isInstance(componentType)) {
                            return m.invoke(stack, componentType);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Gets the lines list from a lore component via reflection.
     * Tries lines() (both Yarn and 26.2).
     */
    private static List<?> getLoreLines(Object loreComponent) {
        try {
            Method m = loreComponent.getClass().getMethod("lines");
            Object result = m.invoke(loreComponent);
            if (result instanceof List) {
                return (List<?>) result;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Gets the plain string from a Text/Component via reflection.
     * Tries getString() method.
     */
    private static String getTextString(Object text) {
        try {
            Method m = text.getClass().getMethod("getString");
            return (String) m.invoke(text);
        } catch (Throwable e) {
            return null;
        }
    }

    public static IntegrityData empty() {
        return new IntegrityData(0, 0, false, false);
    }

    @Override
    public String toString() {
        if (!hasIntegrity) return "\u2014";
        if (unbreakable) return "\u25C6\u221E";
        return String.format("%.3f%%", getPercent());
    }
}
