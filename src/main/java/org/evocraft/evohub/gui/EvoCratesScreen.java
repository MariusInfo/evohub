package org.evocraft.evohub.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.evocraft.evohub.client.ClientCratesData;
import org.evocraft.evohub.network.EvoHubPacketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EvoCratesScreen extends Screen {

    private final Screen parent;
    private final List<CrateButton> buttons = new ArrayList<>();
    private CustomButton backButton;

    private static final int PANEL_W = 550;
    private static final int PANEL_H = 350;

    public EvoCratesScreen(Screen parent) {
        super(Component.literal("EvoCraft Crates"));
        this.parent = parent;
    }

    private float getScale() {
        double guiScale = this.minecraft != null ? this.minecraft.getWindow().getGuiScale() : 1.0;
        if (guiScale >= 4) return 0.65f;
        if (guiScale >= 3) return 0.85f;
        return 1.0f;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        buttons.clear();

        float scale = getScale();
        int sw = (int) (this.width / scale);
        int sh = (int) (this.height / scale);
        int x = (sw - PANEL_W) / 2;
        int y = (sh - PANEL_H) / 2;

        backButton = new CustomButton("< Înapoi la Hub", x + 15, y + 15, 105, 20, () -> {
            if (this.minecraft != null) this.minecraft.setScreen(parent);
        });

        int currentX = x + 28;
        int currentY = y + 62;
        for (Map.Entry<String, Integer> entry : ClientCratesData.playerKeys.entrySet()) {
            String crateName = entry.getKey();
            int keysAmount = entry.getValue();
            buttons.add(new CrateButton(crateName, keysAmount, currentX, currentY, () ->
                    EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_OpenCratePacket(crateName))));

            currentX += 164;
            if (currentX > x + PANEL_W - 150) {
                currentX = x + 28;
                currentY += 122;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (ClientCratesData.needsRefresh) {
            ClientCratesData.needsRefresh = false;
            this.init();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        float scale = getScale();
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);

        int smx = (int) (mouseX / scale);
        int smy = (int) (mouseY / scale);
        int sw = (int) (this.width / scale);
        int sh = (int) (this.height / scale);
        int x = (sw - PANEL_W) / 2;
        int y = (sh - PANEL_H) / 2;

        EvoHubTheme.drawPanel(g, x, y, PANEL_W, PANEL_H);
        EvoHubTheme.drawTitle(g, this.font, "CRATE-URI EVOCRAFT", "Click stânga deschide, click dreapta arată reward-urile", sw / 2, y + 14);

        if (backButton != null) backButton.render(g, smx, smy, this.font);

        if (buttons.isEmpty()) {
            g.drawCenteredString(this.font, "§cNiciun crate nu a fost creat încă.", sw / 2, y + 150, 0xFFFFFF);
            g.drawCenteredString(this.font, "§7Administratorul trebuie să configureze crate-urile.", sw / 2, y + 170, EvoHubTheme.TEXT_MUTED);
        } else {
            for (CrateButton b : buttons) {
                b.render(g, smx, smy, this.font);
            }
        }

        super.render(g, smx, smy, partialTick);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float scale = getScale();
        int smx = (int) (mouseX / scale);
        int smy = (int) (mouseY / scale);

        if (backButton != null && backButton.checkClick(smx, smy)) return true;

        for (CrateButton b : buttons) {
            if (b.contains(smx, smy) && button == 1) {
                EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestCratePreview(b.crateName));
                return true;
            }
            if (b.checkClick(smx, smy)) return true;
        }
        return super.mouseClicked(mouseX / scale, mouseY / scale, button);
    }

    class CrateButton {
        String crateName;
        int keys;
        int x, y;
        Runnable action;
        int w = 142;
        int h = 112;

        public CrateButton(String crateName, int keys, int x, int y, Runnable action) {
            this.crateName = crateName;
            this.keys = keys;
            this.x = x;
            this.y = y;
            this.action = action;
        }

        public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            boolean hover = contains(mx, my);
            EvoHubTheme.fillRounded(g, x, y, w, h, hover ? EvoHubTheme.CARD_HOVER : EvoHubTheme.CARD_BG_DARK);
            EvoHubTheme.outlineRounded(g, x, y, w, h, hover ? EvoHubTheme.ACCENT : EvoHubTheme.BORDER);
            g.fill(x + 8, y + 8, x + w - 8, y + 9, hover ? EvoHubTheme.ACCENT : EvoHubTheme.ACCENT_DARK);

            g.pose().pushPose();
            g.pose().translate(x + w / 2f - 18, y + 18, 0);
            g.pose().scale(2.25f, 2.25f, 1.0f);
            g.renderItem(new ItemStack(Items.ENDER_CHEST), 0, 0);
            g.pose().popPose();

            String displayCrate = crateName.isEmpty() ? "Crate" : crateName.substring(0, 1).toUpperCase() + crateName.substring(1);
            g.drawCenteredString(font, "§6§l" + EvoHubTheme.shorten(displayCrate, 16), x + w / 2, y + 58, 0xFFFFFF);

            int badgeW = 58;
            int badgeX = x + (w - badgeW) / 2;
            EvoHubTheme.fillRounded(g, badgeX, y + 72, badgeW, 15, keys > 0 ? 0xAA243D1C : 0xAA2A2A2A);
            EvoHubTheme.outlineRounded(g, badgeX, y + 72, badgeW, 15, keys > 0 ? EvoHubTheme.ACCENT : 0xFF555555);
            g.drawCenteredString(font, "Chei: §a" + keys, x + w / 2, y + 76, 0xFFFFFF);

            EvoHubTheme.drawButton(g, font, "DESCHIDE", x + 18, y + 91, w - 36, 18, hover);
        }

        public boolean contains(int mx, int my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        public boolean checkClick(int mx, int my) {
            if (mx >= x + 18 && mx <= x + w - 18 && my >= y + 88 && my <= y + 111) {
                action.run();
                return true;
            }
            return false;
        }
    }

    class CustomButton {
        String text;
        int x, y, w, h;
        Runnable action;

        public CustomButton(String text, int x, int y, int w, int h, Runnable action) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            EvoHubTheme.drawButton(g, font, text, x, y, w, h, mx >= x && mx <= x + w && my >= y && my <= y + h);
        }

        public boolean checkClick(int mx, int my) {
            if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
                action.run();
                return true;
            }
            return false;
        }
    }
}
