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
    private static final int PANEL_W = 640;
    private static final int PANEL_H = 380;
    private static final int CARD_W = 112;
    private static final int CARD_H = 124;

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

        int smx = (int) (mouseX / scale);
        int smy = (int) (mouseY / scale);
        int sw = (int) (this.width / scale);
        int sh = (int) (this.height / scale);
        int x = (sw - PANEL_W) / 2;
        int y = (sh - PANEL_H) / 2;

        EvoHubTheme.drawPanel(g, x, y, PANEL_W, PANEL_H);
        EvoHubTheme.drawTitle(g, this.font, "EVOCRAFT WARPS", isLoading ? "Downloading locations from the server..." : "Choose a destination", sw / 2, y + 14);

        if (isLoading) {
            g.drawCenteredString(this.font, "§eLoading...", sw / 2, y + PANEL_H / 2, 0xFFFFFF);
        } else {
            renderWarpList(g, smx, smy, x, y, sw);
        }

        g.pose().popPose();
    }

    private void renderWarpList(GuiGraphics g, int smx, int smy, int x, int y, int sw) {
        int backX = x + 18;
        int backY = y + PANEL_H - 30;
        EvoHubTheme.drawButton(g, this.font, "< Back to Hub", backX, backY, 95, 20, isHover(smx, smy, backX, backY, 95, 20));

        int totalWarps = loadedWarps.size();
        int totalPages = (int) Math.ceil((double) totalWarps / WARPS_PER_PAGE);
        if (currentPage >= totalPages && totalPages > 0) currentPage = totalPages - 1;

        if (totalPages > 1) {
            g.drawCenteredString(this.font, "Page " + (currentPage + 1) + " of " + totalPages, sw / 2, y + PANEL_H - 22, EvoHubTheme.TEXT_MUTED);
            int px = sw / 2 - 72;
            int nx = sw / 2 + 52;
            int py = y + PANEL_H - 30;
            EvoHubTheme.drawButton(g, this.font, "<", px, py, 22, 20, isHover(smx, smy, px, py, 22, 20));
            EvoHubTheme.drawButton(g, this.font, ">", nx, py, 22, 20, isHover(smx, smy, nx, py, 22, 20));
        }

        if (loadedWarps.isEmpty()) {
            g.drawCenteredString(this.font, "§cNo warps are configured.", sw / 2, y + 165, 0xFFFFFF);
            return;
        }

        int startIdx = currentPage * WARPS_PER_PAGE;
        int endIdx = Math.min(startIdx + WARPS_PER_PAGE, totalWarps);
        int startX = x + 20;
        int startY = y + 60;
        int kX = startX;
        int kY = startY;

        for (int i = startIdx; i < endIdx; i++) {
            WarpInfo warp = loadedWarps.get(i);
            boolean hoverWarp = isHover(smx, smy, kX, kY, CARD_W, CARD_H);
            renderWarpCard(g, warp, kX, kY, hoverWarp);

            kX += CARD_W + 10;
            if ((i - startIdx + 1) % 5 == 0) {
                kX = startX;
                kY += CARD_H + 18;
            }
        }
    }

    private void renderWarpCard(GuiGraphics g, WarpInfo warp, int x, int y, boolean hover) {
        EvoHubTheme.fillRounded(g, x, y, CARD_W, CARD_H, hover ? EvoHubTheme.CARD_HOVER : EvoHubTheme.CARD_BG_DARK);
        EvoHubTheme.outlineRounded(g, x, y, CARD_W, CARD_H, hover ? EvoHubTheme.ACCENT : EvoHubTheme.BORDER);
        g.fill(x + 8, y + 8, x + CARD_W - 8, y + 9, hover ? EvoHubTheme.ACCENT : EvoHubTheme.ACCENT_DARK);

        g.drawCenteredString(this.font, "§l" + EvoHubTheme.shorten(warp.name, 14), x + CARD_W / 2, y + 15, hover ? 0xFFFFFFFF : 0xFFE8F2E2);

        g.pose().pushPose();
        g.pose().translate(x + CARD_W / 2f - 18, y + 40, 0);
        g.pose().scale(2.2f, 2.2f, 1.0f);
        g.renderItem(warp.icon, 0, 0);
        g.pose().popPose();

        EvoHubTheme.drawButton(g, this.font, "TELEPORT", x + 10, y + 96, CARD_W - 20, 20, hover);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float scale = getScale();
        int smx = (int) (mouseX / scale);
        int smy = (int) (mouseY / scale);

        int sw = (int) (this.width / scale);
        int sh = (int) (this.height / scale);
        int x = (sw - PANEL_W) / 2;
        int y = (sh - PANEL_H) / 2;

        if (isLoading) return super.mouseClicked(mouseX, mouseY, button);

        int backX = x + 18;
        int backY = y + PANEL_H - 30;
        if (isHover(smx, smy, backX, backY, 95, 20)) {
            if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
            return true;
        }

        int totalWarps = loadedWarps.size();
        int totalPages = (int) Math.ceil((double) totalWarps / WARPS_PER_PAGE);
        if (totalPages > 1) {
            int px = sw / 2 - 72;
            int nx = sw / 2 + 52;
            int py = y + PANEL_H - 30;
            if (isHover(smx, smy, px, py, 22, 20)) {
                if (currentPage > 0) currentPage--;
                return true;
            }
            if (isHover(smx, smy, nx, py, 22, 20)) {
                if (currentPage < totalPages - 1) currentPage++;
                return true;
            }
        }

        int startIdx = currentPage * WARPS_PER_PAGE;
        int endIdx = Math.min(startIdx + WARPS_PER_PAGE, totalWarps);
        int kX = x + 20;
        int kY = y + 60;

        for (int i = startIdx; i < endIdx; i++) {
            if (isHover(smx, smy, kX, kY, CARD_W, CARD_H)) {
                WarpInfo warp = loadedWarps.get(i);
                EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_TeleportWarp(warp.name));
                if (this.minecraft != null) this.minecraft.setScreen(null);
                return true;
            }
            kX += CARD_W + 10;
            if ((i - startIdx + 1) % 5 == 0) {
                kX = x + 20;
                kY += CARD_H + 18;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isHover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static class WarpInfo {
        public String name;
        public ItemStack icon;

        public WarpInfo(String name, ItemStack icon) {
            this.name = name;
            this.icon = icon;
        }
    }
}
