package org.evocraft.evohub.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.evocraft.evohub.network.EvoHubPacketHandler;

import java.util.ArrayList;
import java.util.List;

public class EvoWarpScreen extends Screen {

    private final Screen parentScreen;

    public static List<WarpInfo> loadedWarps = new ArrayList<>();
    public static boolean isLoading = true;

    public static int currentPage = 0;
    private static final int WARPS_PER_PAGE = 10;

    public EvoWarpScreen(Screen parentScreen) {
        super(Component.literal("EvoCraft Warps"));
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
        isLoading = true;
        EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestWarps());
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

        g.fill(x, y, x + w, y + h, 0xEE0D140D);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xAA141C14);
        g.renderOutline(x, y, w, h, 0xFF83B755);

        if (isLoading) {
            g.drawCenteredString(this.font, "§eSe descarcă locațiile de pe server...", sw / 2, y + h / 2, 0xFFFFFF);
        } else {
            g.drawCenteredString(this.font, "§lSISTEM DE WARP-URI EVOCRAFT", sw / 2, y + 15, 0xFF83B755);
            g.drawCenteredString(this.font, "§7Apasă pe un Warp pentru a te teleporta", sw / 2, y + 28, 0xAAAAAA);

            int backX = x + 20; int backY = y + h - 30;
            boolean hoverBack = smx >= backX && smx <= backX + 80 && smy >= backY && smy <= backY + 20;
            g.fill(backX, backY, backX + 80, backY + 20, hoverBack ? 0xFF6C9945 : 0xFF3A592D);
            g.drawCenteredString(this.font, "< Înapoi Hub", backX + 40, backY + 6, 0xFFFFFF);

            int totalWarps = loadedWarps.size();
            int totalPages = (int) Math.ceil((double) totalWarps / WARPS_PER_PAGE);
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

            int startIdx = currentPage * WARPS_PER_PAGE;
            int endIdx = Math.min(startIdx + WARPS_PER_PAGE, totalWarps);

            int startX = x + 25; int startY = y + 55;
            int kX = startX; int kY = startY;

            for (int i = startIdx; i < endIdx; i++) {
                WarpInfo warp = loadedWarps.get(i);
                boolean hoverWarp = smx >= kX && smx <= kX + 110 && smy >= kY && smy <= kY + 120;

                g.fill(kX, kY, kX + 110, kY + 120, hoverWarp ? 0x66141C14 : 0x44000000);
                g.renderOutline(kX, kY, 110, 120, 0xFF55FFFF); // Culoare Cyan pentru Warps

                g.drawCenteredString(this.font, "§l" + warp.name, kX + 55, kY + 12, 0xFF55FFFF);

                // Mărim Itemul de 2 ORI ca să arate beton
                g.pose().pushPose();
                g.pose().translate(kX + 39, kY + 35, 0);
                g.pose().scale(2.0f, 2.0f, 1f);
                g.renderItem(warp.icon, 0, 0);
                g.pose().popPose();

                g.fill(kX + 10, kY + 95, kX + 100, kY + 115, hoverWarp ? 0xFF55FFFF : 0xFF333333);
                g.drawCenteredString(this.font, "§lTELEPORT!", kX + 55, kY + 101, hoverWarp ? 0xFF000000 : 0xFFFFFFFF);

                kX += 120;
                if ((i - startIdx + 1) % 5 == 0) { kX = startX; kY += 140; }
            }
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

        int backX = x + 20; int backY = y + h - 30;
        if (smx >= backX && smx <= backX + 80 && smy >= backY && smy <= backY + 20) {
            if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
            return true;
        }

        int totalWarps = loadedWarps.size();
        int totalPages = (int) Math.ceil((double) totalWarps / WARPS_PER_PAGE);

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

        int startIdx = currentPage * WARPS_PER_PAGE;
        int endIdx = Math.min(startIdx + WARPS_PER_PAGE, totalWarps);

        int startX = x + 25; int startY = y + 55;
        int kX = startX; int kY = startY;

        for (int i = startIdx; i < endIdx; i++) {
            if (smx >= kX && smx <= kX + 110 && smy >= kY && smy <= kY + 120) {
                WarpInfo warp = loadedWarps.get(i);

                // Trimite comanda de teleport la server și închide HUB-ul
                EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_TeleportWarp(warp.name));
                if (this.minecraft != null) this.minecraft.setScreen(null);
                return true;
            }
            kX += 120;
            if ((i - startIdx + 1) % 5 == 0) { kX = startX; kY += 140; }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public static class WarpInfo {
        public String name; public ItemStack icon;
        public WarpInfo(String name, ItemStack icon) { this.name = name; this.icon = icon; }
    }
}