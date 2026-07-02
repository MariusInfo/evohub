package org.evocraft.evohub.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class HomeScreen extends Screen {

    private static final int BG_COLOR = 0xEE0D140D;
    private static final int BORDER_COLOR = 0xFF83B755;
    private static final int CARD_BG = 0xAA141C14;
    private static final int CARD_BORDER = 0xFF3A592D;
    private static final int HOVER_COLOR = 0xFF6C9945;
    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private final int imageWidth = 240;
    private final int imageHeight = 200;

    private final List<String> homeNames;
    private final List<CustomButton> buttons = new ArrayList<>();

    public HomeScreen(List<String> homes) {
        super(Component.literal("Casele Tale"));
        this.homeNames = homes;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();

        int x = (this.width - imageWidth) / 2;
        int y = (this.height - imageHeight) / 2;

        int buttonY = y + 40;

        for (String home : homeNames) {
            buttons.add(new CustomButton("🏠 " + home + " [Teleport]", x + 20, buttonY, 200, 20, () -> {
                // REPARAT: Am luat sistemul exact din EvoHubScreen!
                org.evocraft.evocore.network.PacketHandler.INSTANCE.sendToServer(new org.evocraft.evocore.network.C2S_HomeActionPacket("tp", home));

                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.sendSystemMessage(Component.literal("§aTe teleportezi la casa: §e" + home));
                }
                this.onClose();
            }));
            buttonY += 25;
        }

        buttons.add(new CustomButton("Închide Meniul", x + 70, y + imageHeight - 30, 100, 20, this::onClose));
    }

    private void fillRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private void outlineRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g);

        int x = (this.width - imageWidth) / 2;
        int y = (this.height - imageHeight) / 2;

        fillRounded(g, x, y, imageWidth, imageHeight, BG_COLOR);
        outlineRounded(g, x, y, imageWidth, imageHeight, BORDER_COLOR);

        g.pose().pushPose();
        g.pose().translate(x + imageWidth / 2.0f, y + 15, 0);
        g.pose().scale(1.3f, 1.3f, 1.3f);
        g.drawCenteredString(this.font, "§lCASELE TALE", 0, 0, BORDER_COLOR);
        g.pose().popPose();

        for (CustomButton b : buttons) b.render(g, mouseX, mouseY, this.font);

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (CustomButton b : buttons) {
            if (b.checkClick((int)mouseX, (int)mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean isPauseScreen() { return false; }

    class CustomButton {
        String text; int x, y, w, h; Runnable action;
        public CustomButton(String text, int x, int y, int w, int h, Runnable action) {
            this.text = text; this.x = x; this.y = y; this.w = w; this.h = h; this.action = action;
        }
        public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
            fillRounded(g, x, y, w, h, CARD_BG);
            outlineRounded(g, x, y, w, h, hover ? HOVER_COLOR : CARD_BORDER);
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + (h - 8) / 2f, 0);
            g.drawCenteredString(font, text, 0, 0, hover ? 0xFFFFFF : TEXT_COLOR);
            g.pose().popPose();
        }
        public boolean checkClick(int mx, int my) {
            if (mx >= x && mx <= x + w && my >= y && my <= y + h) { action.run(); return true; }
            return false;
        }
    }
}