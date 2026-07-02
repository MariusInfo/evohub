package org.evocraft.evohub.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.evocraft.evohub.network.EvoHubPacketHandler;

import java.util.ArrayList;
import java.util.List;

public class EvoKitScreen extends Screen {

    private final Screen parentScreen;

    // Lista a devenit statică pentru a putea fi populată de Pachet!
    public static List<KitInfo> loadedKits = new ArrayList<>();
    public static boolean isLoading = true;

    private KitInfo previewingKit = null;

    // Sistem de Pagini
    public static int currentPage = 0;
    private static final int KITS_PER_PAGE = 10; // 5 coloane x 2 rânduri

    public EvoKitScreen(Screen parentScreen) {
        super(Component.literal("EvoCraft Kits"));
        this.parentScreen = parentScreen;
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
        // REPARAT: De fiecare dată când deschizi GUI-ul, forțăm reîncărcarea kiturilor direct de pe server!
        // Astfel, dacă ai dat /evokit create, apare instant aici!
        isLoading = true;
        EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestKits());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        float scale = getScale();
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);

        int smx = (int)(mouseX / scale);
        int smy = (int)(mouseY / scale);
        int sw = (int)(this.width / scale);
        int sh = (int)(this.height / scale);

        int w = 640;
        int h = 380;
        int x = (sw - w) / 2;
        int y = (sh - h) / 2;

        // Fundalul Meniului
        g.fill(x, y, x + w, y + h, 0xEE0D140D);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xAA141C14);

        // Variabilă pentru a desena detaliile itemului la final
        ItemStack hoveredItem = null;

        if (isLoading) {
            g.renderOutline(x, y, w, h, 0xFF83B755);
            g.drawCenteredString(this.font, "§eSe descarcă kiturile de pe server...", sw / 2, y + h / 2, 0xFFFFFF);
        } else if (previewingKit == null) {
            // ==========================================
            // STAREA 1: LISTA DINAMICĂ DE KIT-URI (5x2)
            // ==========================================
            g.renderOutline(x, y, w, h, 0xFF83B755);
            g.drawCenteredString(this.font, "§lSISTEM DE KIT-URI EVOCRAFT", sw / 2, y + 15, 0xFF83B755);
            g.drawCenteredString(this.font, "§7Alege un pachet configurat.", sw / 2, y + 28, 0xAAAAAA);

            int backX = x + 20; int backY = y + h - 30;
            boolean hoverBack = smx >= backX && smx <= backX + 80 && smy >= backY && smy <= backY + 20;
            g.fill(backX, backY, backX + 80, backY + 20, hoverBack ? 0xFF6C9945 : 0xFF3A592D);
            g.drawCenteredString(this.font, "< Înapoi Hub", backX + 40, backY + 6, 0xFFFFFF);

            int totalKits = loadedKits.size();
            int totalPages = (int) Math.ceil((double) totalKits / KITS_PER_PAGE);
            if (currentPage >= totalPages && totalPages > 0) currentPage = totalPages - 1;

            if (totalPages > 1) {
                g.drawCenteredString(this.font, "Pagina " + (currentPage + 1) + " din " + totalPages, sw / 2, y + h - 22, 0xAAAAAA);

                int px = sw / 2 - 70; int py = y + h - 28;
                boolean hoverP = smx >= px && smx <= px + 20 && smy >= py && smy <= py + 20;
                g.fill(px, py, px + 20, py + 20, hoverP ? 0xFF6C9945 : 0xFF3A592D);
                g.drawCenteredString(this.font, "<", px + 10, py + 6, 0xFFFFFF);

                int nx = sw / 2 + 50; int ny = y + h - 28;
                boolean hoverN = smx >= nx && smx <= nx + 20 && smy >= ny && smy <= ny + 20;
                g.fill(nx, ny, nx + 20, ny + 20, hoverN ? 0xFF6C9945 : 0xFF3A592D);
                g.drawCenteredString(this.font, ">", nx + 10, ny + 6, 0xFFFFFF);
            }

            int startIdx = currentPage * KITS_PER_PAGE;
            int endIdx = Math.min(startIdx + KITS_PER_PAGE, totalKits);

            int startX = x + 25; int startY = y + 55;
            int kX = startX; int kY = startY;

            for (int i = startIdx; i < endIdx; i++) {
                KitInfo kit = loadedKits.get(i);
                boolean hoverKit = smx >= kX && smx <= kX + 110 && smy >= kY && smy <= kY + 140;

                g.fill(kX, kY, kX + 110, kY + 140, hoverKit ? 0x66141C14 : 0x44000000);
                g.renderOutline(kX, kY, 110, 140, kit.color);

                g.drawCenteredString(this.font, "§l" + kit.name, kX + 55, kY + 12, kit.color);

                g.pose().pushPose(); g.pose().translate(kX + 55, kY + 26, 0); g.pose().scale(0.8f, 0.8f, 1f);
                g.drawCenteredString(this.font, "§7" + kit.desc, 0, 0, 0xFFFFFF);
                g.pose().popPose();

                if (kit.items.length > 0) {
                    g.pose().pushPose(); g.pose().translate(kX + 47, kY + 55, 0); g.pose().scale(1.5f, 1.5f, 1f);
                    g.renderItem(kit.items[0], 0, 0);
                    g.pose().popPose();
                }

                g.fill(kX + 10, kY + 110, kX + 100, kY + 130, hoverKit ? kit.color : 0xFF333333);
                g.drawCenteredString(this.font, "§lVEZI KIT", kX + 55, kY + 116, hoverKit ? 0xFF000000 : 0xFFFFFFFF);

                kX += 120;
                if ((i - startIdx + 1) % 5 == 0) { kX = startX; kY += 150; }
            }
        } else {
            // ==========================================
            // STAREA 2: PREVIZUALIZARE KIT SPECIFIC
            // ==========================================
            g.renderOutline(x, y, w, h, previewingKit.color);
            g.drawCenteredString(this.font, "§lPachet: " + previewingKit.name, sw / 2, y + 15, previewingKit.color);
            g.drawCenteredString(this.font, "Treci cu mouse-ul peste iteme pentru a le vedea detaliile", sw / 2, y + 28, 0xAAAAAA);

            int startX = x + 35;
            int startY = y + 55;
            int itemX = startX;
            int itemY = startY;
            int slotSize = 36; // Căsuțele de iteme sunt mult mai mari
            float itemScale = 1.5f; // Mărim itemul fizic

            for (ItemStack item : previewingKit.items) {
                // Desenăm fundalul itemului
                g.fill(itemX, itemY, itemX + slotSize, itemY + slotSize, 0x55000000);
                g.renderOutline(itemX, itemY, slotSize, slotSize, previewingKit.color);

                // Mărim Itemul și textul cantității (Render)
                g.pose().pushPose();
                // Centrăm perfect itemul mărit în căsuța de 36x36
                g.pose().translate(itemX + 6, itemY + 6, 0);
                g.pose().scale(itemScale, itemScale, 1.0f);

                g.renderItem(item, 0, 0);
                g.renderItemDecorations(this.font, item, 0, 0);
                g.pose().popPose();

                // Salvăm item-ul pentru Tooltip dacă avem mouse-ul peste el
                if (smx >= itemX && smx <= itemX + slotSize && smy >= itemY && smy <= itemY + slotSize) {
                    hoveredItem = item;
                }

                itemX += slotSize + 8;
                if (itemX > x + w - 45) {
                    itemX = startX;
                    itemY += slotSize + 8;
                }
            }

            g.drawString(this.font, "Timp de așteptare: §e" + previewingKit.cooldown, x + 35, y + h - 55, 0xFFFFFF, false);
            g.drawString(this.font, "Permisiune Necesară: §f" + previewingKit.permission, x + 35, y + h - 40, 0xAAAAAA, false);

            int backBtnX = x + 20; int backBtnY = y + h - 30;
            boolean hoverBack = smx >= backBtnX && smx <= backBtnX + 85 && smy >= backBtnY && smy <= backBtnY + 20;
            g.fill(backBtnX, backBtnY, backBtnX + 85, backBtnY + 20, hoverBack ? 0xFF6C9945 : 0xFF3A592D);
            g.drawCenteredString(this.font, "< Înapoi Kituri", backBtnX + 42, backBtnY + 6, 0xFFFFFF);

            int claimX = x + (w / 2) - 60; int claimY = y + h - 45;
            boolean hoverClaim = smx >= claimX && smx <= claimX + 120 && smy >= claimY && smy <= claimY + 25;
            g.fill(claimX, claimY, claimX + 120, claimY + 25, hoverClaim ? previewingKit.color : 0xFF333333);
            g.drawCenteredString(this.font, "§lREVENDICĂ", claimX + 60, claimY + 8, hoverClaim ? 0xFF000000 : 0xFFFFFFFF);
        }

        // ==========================================
        // DESENĂM TOOLTIP-UL LA FINAL (Peste tot)
        // ==========================================
        if (hoveredItem != null) {
            g.renderTooltip(this.font, hoveredItem, smx, smy);
        }

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float scale = getScale();
        int smx = (int)(mouseX / scale);
        int smy = (int)(mouseY / scale);

        int sw = (int)(this.width / scale);
        int sh = (int)(this.height / scale);
        int w = 640; int h = 380;
        int x = (sw - w) / 2; int y = (sh - h) / 2;

        if (isLoading) return super.mouseClicked(mouseX, mouseY, button);

        if (previewingKit == null) {
            int backX = x + 20; int backY = y + h - 30;
            if (smx >= backX && smx <= backX + 80 && smy >= backY && smy <= backY + 20) {
                if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
                return true;
            }

            int totalKits = loadedKits.size();
            int totalPages = (int) Math.ceil((double) totalKits / KITS_PER_PAGE);

            if (totalPages > 1) {
                int px = sw / 2 - 70; int py = y + h - 28;
                if (smx >= px && smx <= px + 20 && smy >= py && smy <= py + 20) {
                    if (currentPage > 0) currentPage--;
                    return true;
                }
                int nx = sw / 2 + 50; int ny = y + h - 28;
                if (smx >= nx && smx <= nx + 20 && smy >= ny && smy <= ny + 20) {
                    if (currentPage < totalPages - 1) currentPage++;
                    return true;
                }
            }

            int startIdx = currentPage * KITS_PER_PAGE;
            int endIdx = Math.min(startIdx + KITS_PER_PAGE, totalKits);

            int startX = x + 25; int startY = y + 55;
            int kX = startX; int kY = startY;

            for (int i = startIdx; i < endIdx; i++) {
                if (smx >= kX && smx <= kX + 110 && smy >= kY && smy <= kY + 140) {
                    previewingKit = loadedKits.get(i);
                    return true;
                }
                kX += 120;
                if ((i - startIdx + 1) % 5 == 0) { kX = startX; kY += 150; }
            }
        } else {
            int backBtnX = x + 20; int backBtnY = y + h - 30;
            if (smx >= backBtnX && smx <= backBtnX + 85 && smy >= backBtnY && smy <= backBtnY + 20) {
                previewingKit = null;
                return true;
            }

            int claimX = x + (w / 2) - 60; int claimY = y + h - 45;
            if (smx >= claimX && smx <= claimX + 120 && smy >= claimY && smy <= claimY + 25) {
                EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_ClaimKit(previewingKit.name));
                EvoKitScreen.isLoading = true;
                if (this.minecraft != null) this.minecraft.setScreen(null);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public static class KitInfo {
        public String name, desc, permission, cooldown; public int color; public ItemStack[] items;
        public KitInfo(String name, String desc, String permission, String cooldown, int color, ItemStack[] items) {
            this.name = name; this.desc = desc; this.permission = permission;
            this.cooldown = cooldown; this.color = color; this.items = items;
        }
    }
}