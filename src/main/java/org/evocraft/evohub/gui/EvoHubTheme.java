package org.evocraft.evohub.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class EvoHubTheme {
    public static final int PANEL_BG = 0xEE050805;
    public static final int CARD_BG = 0xAA141C14;
    public static final int CARD_BG_DARK = 0xAA050805;
    public static final int CARD_HOVER = 0xDD141C14;
    public static final int BORDER = 0xFF3A592D;
    public static final int ACCENT = 0xFF83B755;
    public static final int ACCENT_DARK = 0xFF3A592D;
    public static final int ACCENT_HOVER = 0xFF6C9945;
    public static final int TEXT_MUTED = 0xFFAAAAAA;
    public static final int TEXT = 0xFFFFFFFF;

    private EvoHubTheme() {}

    public static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    public static void outlineRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        fillRounded(g, x, y, w, h, PANEL_BG);
        outlineRounded(g, x, y, w, h, BORDER);
        g.fill(x + 14, y + 42, x + w - 14, y + 43, ACCENT_DARK);
    }

    public static void drawTitle(GuiGraphics g, Font font, String title, String subtitle, int centerX, int y) {
        g.drawCenteredString(font, "§l" + title, centerX, y, ACCENT);
        if (subtitle != null && !subtitle.isEmpty()) {
            g.drawCenteredString(font, "§7" + subtitle, centerX, y + 13, TEXT_MUTED);
        }
    }

    public static void drawButton(GuiGraphics g, Font font, String text, int x, int y, int w, int h, boolean hover) {
        fillRounded(g, x, y, w, h, hover ? 0xCC1F331B : CARD_BG);
        outlineRounded(g, x, y, w, h, hover ? ACCENT : BORDER);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, hover ? TEXT : 0xFFDDDDDD);
    }

    public static void drawIconCard(GuiGraphics g, Font font, String title, String subtitle, ItemStack icon, int x, int y, int w, int h, boolean hover) {
        fillRounded(g, x, y, w, h, hover ? CARD_HOVER : CARD_BG_DARK);
        outlineRounded(g, x, y, w, h, hover ? ACCENT : BORDER);
        g.fill(x + 8, y + 8, x + w - 8, y + 9, hover ? ACCENT : ACCENT_DARK);

        if (icon != null && !icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + w / 2f - 16, y + 22, 0);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }

        g.drawCenteredString(font, "§l" + title, x + w / 2, y + h - 30, hover ? TEXT : 0xFFE8F2E2);
        if (subtitle != null && !subtitle.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + h - 16, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            g.drawCenteredString(font, "§7" + subtitle, 0, 0, TEXT_MUTED);
            g.pose().popPose();
        }
    }

    public static String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 2)) + "..";
    }
}
