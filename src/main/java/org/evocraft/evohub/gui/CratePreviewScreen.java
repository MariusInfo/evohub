package org.evocraft.evohub.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class CratePreviewScreen extends Screen {
    private final String crateName;
    private final List<ItemStack> items;
    private final List<Integer> chances;

    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 18;
    private static final int PANEL_W = 380;
    private static final int PANEL_H = 240;

    public CratePreviewScreen(String crateName, List<ItemStack> items, List<Integer> chances) {
        super(Component.literal("Preview: " + crateName));
        this.crateName = crateName;
        this.items = items;
        this.chances = chances;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        EvoHubTheme.drawPanel(g, x, y, PANEL_W, PANEL_H);

        int maxPages = (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
        int displayPage = maxPages == 0 ? 1 : currentPage + 1;
        int displayMax = maxPages == 0 ? 1 : maxPages;

        EvoHubTheme.drawTitle(g, this.font, "CONTENTS: " + crateName.toUpperCase(), "Page " + displayPage + "/" + displayMax, this.width / 2, y + 12);

        int startX = x + 50;
        int startY = y + 52;
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            int indexOnPage = i - startIndex;
            int row = indexOnPage / 6;
            int col = indexOnPage % 6;
            int ix = startX + (col * 47);
            int iy = startY + (row * 43);
            boolean hover = mouseX >= ix && mouseX <= ix + 28 && mouseY >= iy && mouseY <= iy + 32;

            EvoHubTheme.fillRounded(g, ix - 5, iy - 5, 32, 34, hover ? 0x884A6B38 : 0x55141C14);
            EvoHubTheme.outlineRounded(g, ix - 5, iy - 5, 32, 34, hover ? EvoHubTheme.ACCENT : EvoHubTheme.BORDER);
            g.renderItem(items.get(i), ix, iy);
            g.renderItemDecorations(this.font, items.get(i), ix, iy);

            g.pose().pushPose();
            g.pose().translate(ix + 8, iy + 23, 0);
            g.pose().scale(0.68f, 0.68f, 1.0f);
            g.drawCenteredString(this.font, "§a" + chances.get(i) + "%", 0, 0, 0xFFFFFF);
            g.pose().popPose();

            if (mouseX >= ix && mouseX <= ix + 16 && mouseY >= iy && mouseY <= iy + 16) {
                g.renderTooltip(this.font, items.get(i), mouseX, mouseY);
            }
        }

        if (currentPage > 0) {
            int prevX = x + 25;
            int prevY = y + PANEL_H - 38;
            EvoHubTheme.drawButton(g, this.font, "< Previous Page", prevX, prevY, 135, 20, isHover(mouseX, mouseY, prevX, prevY, 135, 20));
        }

        if ((currentPage + 1) * ITEMS_PER_PAGE < items.size()) {
            int nextX = x + PANEL_W - 160;
            int nextY = y + PANEL_H - 38;
            EvoHubTheme.drawButton(g, this.font, "Next Page >", nextX, nextY, 135, 20, isHover(mouseX, mouseY, nextX, nextY, 135, 20));
        }

        g.drawCenteredString(this.font, "§7Press ESC to go back", this.width / 2, y + PANEL_H - 15, EvoHubTheme.TEXT_MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = (this.width - PANEL_W) / 2;
            int y = (this.height - PANEL_H) / 2;

            if (currentPage > 0) {
                int prevX = x + 25;
                int prevY = y + PANEL_H - 38;
                if (isHover(mouseX, mouseY, prevX, prevY, 135, 20)) {
                    currentPage--;
                    return true;
                }
            }

            if ((currentPage + 1) * ITEMS_PER_PAGE < items.size()) {
                int nextX = x + PANEL_W - 160;
                int nextY = y + PANEL_H - 38;
                if (isHover(mouseX, mouseY, nextX, nextY, 135, 20)) {
                    currentPage++;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isHover(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
