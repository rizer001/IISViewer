package com.iisviewer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import java.text.DecimalFormat;

/**
 * Renders the Integrity HUD overlay in the bottom-right corner of the screen.
 */
public class IntegrityHudOverlay {

    private static final DecimalFormat PCT_FORMAT = new DecimalFormat("0.0");
    private static final String NO_INTEGRITY = "\u2014";

    private static final int ICON_SIZE = 16;
    private static final int SLOT_SPACING = 20;
    private static final int TEXT_GAP = 1;
    private static final int ROW_GAP = 2;
    private static final int HORIZONTAL_GAP = 30;
    private static final int BOTTOM_PADDING = 30;

    // Gradient color stops: [t, r, g, b] — from high (100%) to low (0%)
    private static final double[][] GRADIENT_STOPS = {
        {1.00, 0xFF, 0xFF, 0xFF},  // White
        {0.75, 0xFF, 0xFF, 0x00},  // Yellow
        {0.50, 0xFF, 0x8C, 0x00},  // Orange
        {0.25, 0xFF, 0x00, 0x00},  // Red
        {0.00, 0x8B, 0x00, 0x00}   // Dark Red
    };

    public static void render(DrawContext context) {
        try {
            if (!ModConfig.enabled) return;

            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if (player == null) return;

            TextRenderer textRenderer = client.textRenderer;

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            int startX = (int) (screenWidth * ModConfig.anchorX) - ModConfig.offsetX;
            int startY = (int) (screenHeight * ModConfig.anchorY) - ModConfig.offsetY;
            startY -= BOTTOM_PADDING;
            startX -= 45; // shift HUD left for more space

            ItemStack[] armorSlots = {
                player.getEquippedStack(EquipmentSlot.FEET),
                player.getEquippedStack(EquipmentSlot.LEGS),
                player.getEquippedStack(EquipmentSlot.CHEST),
                player.getEquippedStack(EquipmentSlot.HEAD)
            };

            Arm mainArm = player.getMainArm();
            ItemStack mainHand = player.getMainHandStack();
            ItemStack offHand = player.getOffHandStack();

            if (mainArm == Arm.LEFT) {
                ItemStack temp = mainHand;
                mainHand = offHand;
                offHand = temp;
            }

            int currentY = startY;

            int handRowWidth = HORIZONTAL_GAP + ICON_SIZE;
            int handRowX = startX - handRowWidth;

            renderSlot(context, textRenderer, handRowX, currentY, mainHand);
            renderSlot(context, textRenderer, handRowX + HORIZONTAL_GAP, currentY, offHand);

            currentY -= SLOT_SPACING + ROW_GAP;

            for (ItemStack armor : armorSlots) {
                renderSlot(context, textRenderer, startX - ICON_SIZE, currentY, armor);
                currentY -= SLOT_SPACING;
            }
        } catch (Throwable e) {
            System.err.println("[IISViewer] HUD render error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void renderSlot(DrawContext context, TextRenderer textRenderer,
                                   int iconX, int iconY, ItemStack stack) {
        IntegrityData data = IntegrityData.from(stack);
        boolean slotEmpty = stack == null || stack.isEmpty();

        if (slotEmpty && !ModConfig.showEmptySlots) {
            return;
        }

        if (!slotEmpty) {
            context.drawItemWithoutEntity(stack, iconX, iconY);
        }

        if (slotEmpty && ModConfig.showEmptySlots) {
            context.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0x15FFFFFF);
            return;
        }

        // Determine text to display
        String displayText;
        int textColor;

        if (!data.hasIntegrity) {
            displayText = NO_INTEGRITY;
            textColor = 0xFF888888;
        } else if (data.unbreakable) {
            displayText = "\u25C6\u221E";
            textColor = 0xFF55FFFF;
        } else {
            displayText = PCT_FORMAT.format(data.getPercent()) + "%";
            textColor = getGradientColor(data.getPercent());
        }

        // Center text horizontally under the icon
        int textWidth = textRenderer.getWidth(displayText);
        int textX = iconX + (ICON_SIZE - textWidth) / 2;
        int textY = iconY + ICON_SIZE + TEXT_GAP;

        context.drawText(textRenderer, displayText, textX, textY, textColor, false);
    }

    /**
     * Multi-stop gradient: White → Yellow → Orange → Red → Dark Red
     * t=1.0: #FFFFFF (white)
     * t=0.75: #FFFF00 (yellow)
     * t=0.50: #FF8C00 (orange)
     * t=0.25: #FF0000 (red)
     * t=0.0:  #8B0000 (dark red)
     */
    private static int getGradientColor(double pct) {
        double t = Math.max(0.0, Math.min(1.0, pct / 100.0));

        // Default to lowest stop
        int r = (int) GRADIENT_STOPS[GRADIENT_STOPS.length - 1][1];
        int g = (int) GRADIENT_STOPS[GRADIENT_STOPS.length - 1][2];
        int b = (int) GRADIENT_STOPS[GRADIENT_STOPS.length - 1][3];

        for (int i = 0; i < GRADIENT_STOPS.length - 1; i++) {
            double tHigh = GRADIENT_STOPS[i][0];
            double tLow = GRADIENT_STOPS[i + 1][0];
            if (t <= tHigh && t >= tLow) {
                double seg = (t - tLow) / (tHigh - tLow);
                r = (int) Math.round(GRADIENT_STOPS[i + 1][1] + (GRADIENT_STOPS[i][1] - GRADIENT_STOPS[i + 1][1]) * seg);
                g = (int) Math.round(GRADIENT_STOPS[i + 1][2] + (GRADIENT_STOPS[i][2] - GRADIENT_STOPS[i + 1][2]) * seg);
                b = (int) Math.round(GRADIENT_STOPS[i + 1][3] + (GRADIENT_STOPS[i][3] - GRADIENT_STOPS[i + 1][3]) * seg);
                break;
            }
        }

        return 0xFF000000 | (Math.max(0, Math.min(0xFF, r)) << 16)
                       | (Math.max(0, Math.min(0xFF, g)) << 8)
                       | Math.max(0, Math.min(0xFF, b));
    }
}
