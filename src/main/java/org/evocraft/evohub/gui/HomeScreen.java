package org.evocraft.evohub.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.evocraft.evocore.client.ClientHomeData;
import org.evocraft.evocore.network.C2S_HomeActionPacket;
import org.evocraft.evocore.network.PacketHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HomeScreen extends Screen {
    private static final int MAX_HOMES = 16;
    private static final int MAX_COLUMNS = 4;
    private static final int SIDE_PADDING = 18;
    private static final int TOP_PADDING = 66;
    private static final int BOTTOM_PADDING = 44;
    private static final int CARD_W = 122;
    private static final int CARD_H = 44;
    private static final int GAP = 10;

    private final List<String> homeNames;
    private final List<CustomButton> buttons = new ArrayList<>();

    public HomeScreen(List<String> homes) {
        super(Component.literal("Your Homes"));
        this.homeNames = sanitizeHomes(homes);
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();

        float scale = getScale();
        int sw = (int) (this.width / scale);
        int sh = (int) (this.height / scale);
        Layout layout = createLayout(sw, sh);

        for (int i = 0; i < homeNames.size(); i++) {
            String home = homeNames.get(i);
            int col = i % layout.columns;
            int row = i / layout.columns;
            int bx = layout.gridX + col * (CARD_W + GAP);
            int by = layout.gridY + row * (CARD_H + GAP);

            buttons.add(new HomeButton(home, bx, by, CARD_W, CARD_H, () -> teleportHome(home)));
        }

        buttons.add(new CustomButton("Close", layout.x + layout.w / 2 - 48, layout.y + layout.h - 32, 96, 22, this::onClose));
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g);

        float scale = getScale();
        int smx = (int) (mouseX / scale);
        int smy = (int) (mouseY / scale);
        int sw = (int) (this.width / scale);
        int sh = (int) (this.height / scale);
        Layout layout = createLayout(sw, sh);

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);

        EvoHubTheme.drawPanel(g, layout.x, layout.y, layout.w, layout.h);
        EvoHubTheme.drawTitle(
                g,
                this.font,
                "YOUR HOMES",
                homeNames.size() + "/" + Math.max(ClientHomeData.maxHomesCount, homeNames.size()) + " saved homes",
                layout.x + layout.w / 2,
                layout.y + 14
        );

        g.drawCenteredString(this.font, "Select a home to teleport.", layout.x + layout.w / 2, layout.y + 47, EvoHubTheme.TEXT_MUTED);

        if (homeNames.isEmpty()) {
            drawEmptyState(g, layout);
        }

        for (CustomButton button : buttons) {
            button.render(g, smx, smy, this.font);
        }

        g.pose().popPose();
        super.render(g, smx, smy, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float scale = getScale();
        int smx = (int) (mouseX / scale);
        int smy = (int) (mouseY / scale);
        for (CustomButton customButton : buttons) {
            if (customButton.checkClick(smx, smy)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void teleportHome(String home) {
        PacketHandler.INSTANCE.sendToServer(new C2S_HomeActionPacket("tp", home));
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("§aTeleporting to home: §e" + home));
        }
        this.onClose();
    }

    private void drawEmptyState(GuiGraphics g, Layout layout) {
        int boxW = Math.min(300, layout.w - SIDE_PADDING * 2);
        int boxH = 56;
        int x = layout.x + (layout.w - boxW) / 2;
        int y = layout.gridY;
        EvoHubTheme.fillRounded(g, x, y, boxW, boxH, EvoHubTheme.CARD_BG_DARK);
        EvoHubTheme.outlineRounded(g, x, y, boxW, boxH, EvoHubTheme.BORDER);
        g.drawCenteredString(this.font, "No homes saved yet.", x + boxW / 2, y + 14, EvoHubTheme.TEXT);
        g.drawCenteredString(this.font, "Use the EvoHub homes tab to create one.", x + boxW / 2, y + 30, EvoHubTheme.TEXT_MUTED);
    }

    private Layout createLayout(int screenW, int screenH) {
        int count = Math.max(1, homeNames.size());
        int columns = getColumnCount(count);
        int rows = Math.max(1, (homeNames.size() + columns - 1) / columns);
        int gridW = columns * CARD_W + (columns - 1) * GAP;
        int gridH = rows * CARD_H + (rows - 1) * GAP;
        int panelW = Math.max(300, gridW + SIDE_PADDING * 2);
        int panelH = TOP_PADDING + gridH + BOTTOM_PADDING;
        int x = (screenW - panelW) / 2;
        int y = (screenH - panelH) / 2;
        return new Layout(x, y, panelW, panelH, columns, x + SIDE_PADDING, y + TOP_PADDING);
    }

    private int getColumnCount(int count) {
        if (count <= 2) return count;
        if (count <= 6) return 2;
        if (count <= 12) return 3;
        return MAX_COLUMNS;
    }

    private float getScale() {
        double guiScale = this.minecraft != null ? this.minecraft.getWindow().getGuiScale() : 1.0D;
        if (guiScale >= 4.0D) return 0.72F;
        if (guiScale >= 3.0D) return 0.86F;
        return 1.0F;
    }

    private static List<String> sanitizeHomes(List<String> homes) {
        List<String> cleaned = new ArrayList<>();
        if (homes != null) {
            for (String home : homes) {
                if (home != null && !home.isBlank()) {
                    cleaned.add(home.trim());
                }
                if (cleaned.size() >= MAX_HOMES) break;
            }
        }
        cleaned.sort(Comparator.comparing(String::toLowerCase));
        return cleaned;
    }

    private String fitToWidth(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String value = text;
        String suffix = "...";
        while (!value.isEmpty() && font.width(value + suffix) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private record Layout(int x, int y, int w, int h, int columns, int gridX, int gridY) {
    }

    class CustomButton {
        final String text;
        final int x;
        final int y;
        final int w;
        final int h;
        final Runnable action;

        CustomButton(String text, int x, int y, int w, int h, Runnable action) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        void render(GuiGraphics g, int mx, int my, Font font) {
            boolean hover = contains(mx, my);
            EvoHubTheme.drawButton(g, font, text, x, y, w, h, hover);
        }

        boolean checkClick(int mx, int my) {
            if (!contains(mx, my)) return false;
            action.run();
            return true;
        }

        boolean contains(int mx, int my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    class HomeButton extends CustomButton {
        private final ItemStack icon = new ItemStack(Items.RED_BED);

        HomeButton(String text, int x, int y, int w, int h, Runnable action) {
            super(text, x, y, w, h, action);
        }

        @Override
        void render(GuiGraphics g, int mx, int my, Font font) {
            boolean hover = contains(mx, my);
            EvoHubTheme.fillRounded(g, x, y, w, h, hover ? EvoHubTheme.CARD_HOVER : EvoHubTheme.CARD_BG_DARK);
            EvoHubTheme.outlineRounded(g, x, y, w, h, hover ? EvoHubTheme.ACCENT : EvoHubTheme.BORDER);
            g.fill(x + 8, y + 7, x + w - 8, y + 8, hover ? EvoHubTheme.ACCENT : EvoHubTheme.ACCENT_DARK);
            g.renderItem(icon, x + 10, y + 17);
            g.drawString(font, fitToWidth(font, text, w - 40), x + 33, y + 13, hover ? EvoHubTheme.TEXT : 0xFFE8F2E2, false);
            g.drawString(font, "Teleport", x + 33, y + 26, EvoHubTheme.TEXT_MUTED, false);
        }
    }
}
