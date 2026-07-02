package org.evocraft.evohub.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evocraft.evohub.client.ClientCratesData;
import org.evocraft.evohub.network.EvoHubPacketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EvoCratesScreen extends Screen {

    private final Screen parent;
    private final List<CrateButton> buttons = new ArrayList<>();
    private CustomButton backButton;

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
    protected void init() {
        this.clearWidgets();
        buttons.clear();

        float scale = getScale();
        int sw = (int)(this.width / scale);
        int sh = (int)(this.height / scale);

        int w = 550; int h = 350;
        int x = (sw - w) / 2; int y = (sh - h) / 2;

        // Butonul de INAPOI spre Hub
        backButton = new CustomButton("< Înapoi la Hub", x + 15, y + 15, 100, 20, () -> {
            if (this.minecraft != null) this.minecraft.setScreen(parent);
        });

        // Generam butoanele pentru TOATE cutiile (chiar si cele cu 0 chei!)
        int startX = x + 30;
        int startY = y + 60;
        int currentX = startX;
        int currentY = startY;

        for (Map.Entry<String, Integer> entry : ClientCratesData.playerKeys.entrySet()) {
            String crateName = entry.getKey();
            int keysAmount = entry.getValue();

            // AM SCOS ACEL IF (keysAmount > 0)! Acum adaugă mereu Crate-ul pe ecran.
            buttons.add(new CrateButton(crateName, keysAmount, currentX, currentY, () -> {
                // Dacă jucătorul apasă Deschide dar are 0 chei, serverul îi va trimite automat mesaj că n-are chei, e perfect sigur!
                org.evocraft.evohub.network.EvoHubPacketHandler.INSTANCE.sendToServer(new org.evocraft.evohub.network.EvoHubPacketHandler.C2S_OpenCratePacket(crateName));
            }));

            currentX += 160; // Trecem la urmatoarea coloana
            if (currentX > x + w - 150) {
                currentX = startX;
                currentY += 120; // Trecem pe urmatorul rand
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        float scale = getScale();
        g.pose().pushPose(); g.pose().scale(scale, scale, 1.0f);
        int smx = (int)(mouseX / scale); int smy = (int)(mouseY / scale);
        int sw = (int)(this.width / scale); int sh = (int)(this.height / scale);

        int w = 550; int h = 350;
        int x = (sw - w) / 2; int y = (sh - h) / 2;

        // Fundalul Meniului
        fillRounded(g, x, y, w, h, 0xEE0D140D);
        outlineRounded(g, x, y, w, h, 0xFF83B755);

        // Titlul Sus Centrat
        g.drawCenteredString(this.font, "§l🎁 CRATE-URI DISPONIBILE", sw / 2, y + 15, 0xFF83B755);
        g.fill(x, y + 40, x + w, y + 41, 0xFF3A592D); // Linie despartitoare

        // Render buton Back
        if (backButton != null) backButton.render(g, smx, smy, this.font);

        if (buttons.isEmpty()) {
            g.drawCenteredString(this.font, "§cNiciun Crate nu a fost creat încă!", sw / 2, y + 150, 0xFFFFFF);
            g.drawCenteredString(this.font, "§7Administratorul trebuie să configureze!", sw / 2, y + 170, 0xAAAAAA);
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
        int smx = (int)(mouseX / scale);
        int smy = (int)(mouseY / scale);

        if (backButton != null && backButton.checkClick(smx, smy)) return true;

        for (CrateButton b : buttons) {
            // Verificăm dacă mouse-ul este peste butonul de crate
            if (smx >= b.x && smx <= b.x + b.w && smy >= b.y && smy <= b.y + b.h) {
                if (button == 1) { // 1 este CLICK DREAPTA
                    EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestCratePreview(b.crateName));
                    return true;
                }
            }
            if (b.checkClick(smx, smy)) return true; // Click stânga pe butonul "Deschide"
        }
        return super.mouseClicked(mouseX / scale, mouseY / scale, button);
    }

    // --- CLASE PENTRU BUTOANE ---

    class CrateButton {
        String crateName; int keys; int x, y; Runnable action;
        int w = 140, h = 110; // Mărit la 110 pentru a încăpea textul de jos frumos

        public CrateButton(String crateName, int keys, int x, int y, Runnable action) {
            this.crateName = crateName; this.keys = keys; this.x = x; this.y = y; this.action = action;
        }

        public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            boolean hover = (mx >= x + 20 && mx <= x + w - 20 && my >= y + 70 && my <= y + 90);

            // Casuta Crate-ului
            fillRounded(g, x, y, w, h, 0xAA1A241A);
            outlineRounded(g, x, y, w, h, 0xFF3A592D);

            // Iconita (Punem un cufar sau un block generic de la Minecraft)
            g.pose().pushPose();
            g.pose().translate(x + (w/2f) - 8, y + 15, 0);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.ENDER_CHEST), 0, 0);
            g.pose().popPose();

            String displayCrate = crateName.substring(0, 1).toUpperCase() + crateName.substring(1);
            g.drawCenteredString(font, "§6§l" + displayCrate, x + w / 2, y + 50, 0xFFFFFF);
            g.drawCenteredString(font, "§fChei: §a" + keys, x + w / 2, y + 62, 0xFFFFFF);

            // Butonul de Deschidere din interiorul casutei
            fillRounded(g, x + 20, y + 75, w - 40, 18, 0xAA141C14);
            outlineRounded(g, x + 20, y + 75, w - 40, 18, hover ? 0xFF6C9945 : 0xFF3A592D);
            g.drawCenteredString(font, "DESCHIDE", x + w / 2, y + 80, hover ? 0xFFFFFF : 0xFFDDDDDD);

            // Textul pentru Click-Dreapta (Previzualizare) adăugat aici!
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + 98, 0); // Poziționat sub butonul de deschidere
            g.pose().scale(0.7f, 0.7f, 1f); // Micșorat ca să arate a "hint" subtil
            g.drawCenteredString(font, "§7Click-Dreapta pt Previzualizare", 0, 0, 0xAAAAAA);
            g.pose().popPose();
        }

        public boolean checkClick(int mx, int my) {
            // Verifica daca a dat click fix pe butonul de "DESCHIDE"
            if (mx >= x + 20 && mx <= x + w - 20 && my >= y + 70 && my <= y + 90) {
                action.run(); return true;
            }
            return false;
        }
    }

    // Acelasi CustomButton de la Hub pentru butonul de inapoi
    class CustomButton {
        String text; int x, y, w, h; Runnable action;
        public CustomButton(String text, int x, int y, int w, int h, Runnable action) { this.text = text; this.x = x; this.y = y; this.w = w; this.h = h; this.action = action; }
        public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            boolean hover = (mx >= x && mx <= x + w && my >= y && my <= y + h);
            fillRounded(g, x, y, w, h, 0xAA141C14); outlineRounded(g, x, y, w, h, hover ? 0xFF6C9945 : 0xFF3A592D);
            g.pose().pushPose(); g.pose().translate(x + w / 2f, y + (h - 8) / 2f, 0);
            g.drawCenteredString(font, text, 0, 0, hover ? 0xFFFFFF : 0xFFDDDDDD); g.pose().popPose();
        }
        public boolean checkClick(int mx, int my) { if (mx >= x && mx <= x + w && my >= y && my <= y + h) { action.run(); return true; } return false; }
    }
}

