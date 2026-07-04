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

    public static List<KitInfo> loadedKits = new ArrayList<>();
    public static boolean isLoading = true;

    private KitInfo previewingKit = null;

    public static int currentPage = 0;
    private static final int KITS_PER_PAGE = 10;
    private static final int PANEL_W = 640;
    private static final int PANEL_H = 380;
    private static final int CARD_W = 112;
    private static final int CARD_H = 130;

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
        isLoading = true;
        EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_RequestKits());
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
        ItemStack hoveredItem = null;

        EvoHubTheme.drawPanel(g, x, y, PANEL_W, PANEL_H);

        if (isLoading) {
            EvoHubTheme.drawTitle(g, this.font, "EVOCRAFT KITS", "Downloading kits from the server...", sw / 2, y + 14);
            g.drawCenteredString(this.font, "§eLoading...", sw / 2, y + PANEL_H / 2, 0xFFFFFF);
        } else if (previewingKit == null) {
            EvoHubTheme.drawTitle(g, this.font, "EVOCRAFT KITS", "Choose a configured kit", sw / 2, y + 14);
            renderKitList(g, smx, smy, x, y, sw);
        } else {
            hoveredItem = renderKitPreview(g, smx, smy, x, y, sw);
        }

        if (hoveredItem != null) {
            g.renderTooltip(this.font, hoveredItem, smx, smy);
        }

        g.pose().popPose();
    }

    private void renderKitList(GuiGraphics g, int smx, int smy, int x, int y, int sw) {
        int backX = x + 18;
        int backY = y + PANEL_H - 30;
        EvoHubTheme.drawButton(g, this.font, "< Back to Hub", backX, backY, 95, 20, isHover(smx, smy, backX, backY, 95, 20));

        int totalKits = loadedKits.size();
        int totalPages = (int) Math.ceil((double) totalKits / KITS_PER_PAGE);
        if (currentPage >= totalPages && totalPages > 0) currentPage = totalPages - 1;

        if (totalPages > 1) {
            g.drawCenteredString(this.font, "Page " + (currentPage + 1) + " of " + totalPages, sw / 2, y + PANEL_H - 22, EvoHubTheme.TEXT_MUTED);

            int px = sw / 2 - 72;
            int nx = sw / 2 + 52;
            int py = y + PANEL_H - 30;
            EvoHubTheme.drawButton(g, this.font, "<", px, py, 22, 20, isHover(smx, smy, px, py, 22, 20));
            EvoHubTheme.drawButton(g, this.font, ">", nx, py, 22, 20, isHover(smx, smy, nx, py, 22, 20));
        }

        if (loadedKits.isEmpty()) {
            g.drawCenteredString(this.font, "§cNo kits are configured.", sw / 2, y + 165, 0xFFFFFF);
            return;
        }

        int startIdx = currentPage * KITS_PER_PAGE;
        int endIdx = Math.min(startIdx + KITS_PER_PAGE, totalKits);
        int startX = x + 20;
        int startY = y + 58;
        int kX = startX;
        int kY = startY;

        for (int i = startIdx; i < endIdx; i++) {
            KitInfo kit = loadedKits.get(i);
            boolean hoverKit = isHover(smx, smy, kX, kY, CARD_W, CARD_H);
            renderKitCard(g, kit, kX, kY, hoverKit);

            kX += CARD_W + 10;
            if ((i - startIdx + 1) % 5 == 0) {
                kX = startX;
                kY += CARD_H + 14;
            }
        }
    }

    private void renderKitCard(GuiGraphics g, KitInfo kit, int x, int y, boolean hover) {
        EvoHubTheme.fillRounded(g, x, y, CARD_W, CARD_H, hover ? EvoHubTheme.CARD_HOVER : EvoHubTheme.CARD_BG_DARK);
        EvoHubTheme.outlineRounded(g, x, y, CARD_W, CARD_H, hover ? EvoHubTheme.ACCENT : EvoHubTheme.BORDER);
        g.fill(x + 8, y + 8, x + CARD_W - 8, y + 9, hover ? EvoHubTheme.ACCENT : EvoHubTheme.ACCENT_DARK);

        g.drawCenteredString(this.font, "§l" + EvoHubTheme.shorten(kit.name, 14), x + CARD_W / 2, y + 14, kit.color);

        g.pose().pushPose();
        g.pose().translate(x + CARD_W / 2f, y + 30, 0);
        g.pose().scale(0.72f, 0.72f, 1.0f);
        g.drawCenteredString(this.font, "§7" + EvoHubTheme.shorten(kit.desc, 22), 0, 0, EvoHubTheme.TEXT_MUTED);
        g.pose().popPose();

        if (kit.items.length > 0) {
            g.pose().pushPose();
            g.pose().translate(x + CARD_W / 2f - 16, y + 48, 0);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            g.renderItem(kit.items[0], 0, 0);
            g.pose().popPose();
        }

        EvoHubTheme.drawButton(g, this.font, "VEZI KIT", x + 10, y + 101, CARD_W - 20, 20, hover);
    }

    private ItemStack renderKitPreview(GuiGraphics g, int smx, int smy, int x, int y, int sw) {
        EvoHubTheme.drawTitle(g, this.font, "KIT: " + previewingKit.name, "Hover items to view details", sw / 2, y + 14);

        int startX = x + 34;
        int startY = y + 58;
        int itemX = startX;
        int itemY = startY;
        int slotSize = 36;
        ItemStack hoveredItem = null;

        for (ItemStack item : previewingKit.items) {
            boolean hoverSlot = isHover(smx, smy, itemX, itemY, slotSize, slotSize);
            EvoHubTheme.fillRounded(g, itemX, itemY, slotSize, slotSize, hoverSlot ? 0x884A6B38 : 0x55141C14);
            EvoHubTheme.outlineRounded(g, itemX, itemY, slotSize, slotSize, hoverSlot ? EvoHubTheme.ACCENT : EvoHubTheme.BORDER);

            g.pose().pushPose();
            g.pose().translate(itemX + 6, itemY + 6, 0);
            g.pose().scale(1.5f, 1.5f, 1.0f);
            g.renderItem(item, 0, 0);
            g.renderItemDecorations(this.font, item, 0, 0);
            g.pose().popPose();

            if (hoverSlot) hoveredItem = item;

            itemX += slotSize + 8;
            if (itemX > x + PANEL_W - 45) {
                itemX = startX;
                itemY += slotSize + 8;
            }
        }

        g.drawString(this.font, "Cooldown: §e" + previewingKit.cooldown, x + 35, y + PANEL_H - 55, 0xFFFFFF, false);
        g.drawString(this.font, "Required permission: §f" + previewingKit.permission, x + 35, y + PANEL_H - 40, EvoHubTheme.TEXT_MUTED, false);

        int backBtnX = x + 20;
        int backBtnY = y + PANEL_H - 30;
        EvoHubTheme.drawButton(g, this.font, "< Back to Kits", backBtnX, backBtnY, 100, 20, isHover(smx, smy, backBtnX, backBtnY, 100, 20));

        int claimX = x + (PANEL_W / 2) - 62;
        int claimY = y + PANEL_H - 45;
        EvoHubTheme.drawButton(g, this.font, "CLAIM", claimX, claimY, 124, 25, isHover(smx, smy, claimX, claimY, 124, 25));

        return hoveredItem;
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

        if (previewingKit == null) {
            int backX = x + 18;
            int backY = y + PANEL_H - 30;
            if (isHover(smx, smy, backX, backY, 95, 20)) {
                if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
                return true;
            }

            int totalKits = loadedKits.size();
            int totalPages = (int) Math.ceil((double) totalKits / KITS_PER_PAGE);
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

            int startIdx = currentPage * KITS_PER_PAGE;
            int endIdx = Math.min(startIdx + KITS_PER_PAGE, totalKits);
            int kX = x + 20;
            int kY = y + 58;

            for (int i = startIdx; i < endIdx; i++) {
                if (isHover(smx, smy, kX, kY, CARD_W, CARD_H)) {
                    previewingKit = loadedKits.get(i);
                    return true;
                }
                kX += CARD_W + 10;
                if ((i - startIdx + 1) % 5 == 0) {
                    kX = x + 20;
                    kY += CARD_H + 14;
                }
            }
        } else {
            int backBtnX = x + 20;
            int backBtnY = y + PANEL_H - 30;
            if (isHover(smx, smy, backBtnX, backBtnY, 100, 20)) {
                previewingKit = null;
                return true;
            }

            int claimX = x + (PANEL_W / 2) - 62;
            int claimY = y + PANEL_H - 45;
            if (isHover(smx, smy, claimX, claimY, 124, 25)) {
                EvoHubPacketHandler.INSTANCE.sendToServer(new EvoHubPacketHandler.C2S_ClaimKit(previewingKit.name));
                EvoKitScreen.isLoading = true;
                if (this.minecraft != null) this.minecraft.setScreen(null);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isHover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static class KitInfo {
        public String name, desc, permission, cooldown;
        public int color;
        public ItemStack[] items;

        public KitInfo(String name, String desc, String permission, String cooldown, int color, ItemStack[] items) {
            this.name = name;
            this.desc = desc;
            this.permission = permission;
            this.cooldown = cooldown;
            this.color = color;
            this.items = items;
        }
    }
}
