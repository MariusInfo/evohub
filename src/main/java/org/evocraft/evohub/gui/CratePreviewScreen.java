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
    private final int ITEMS_PER_PAGE = 18; // 6 coloane * 3 rânduri

    public CratePreviewScreen(String crateName, List<ItemStack> items, List<Integer> chances) {
        super(Component.literal("Preview: " + crateName));
        this.crateName = crateName;
        this.items = items;
        this.chances = chances;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int w = 350; int h = 220;
        int x = (this.width - w) / 2; int y = (this.height - h) / 2;

        g.fill(x, y, x + w, y + h, 0xEE0D140D);
        g.renderOutline(x, y, w, h, 0xFF83B755);

        int maxPages = (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
        int displayPage = maxPages == 0 ? 1 : currentPage + 1;
        int displayMax = maxPages == 0 ? 1 : maxPages;

        g.drawCenteredString(this.font, "§6Conținut: §l" + crateName.toUpperCase() + " §e(Pagina " + displayPage + "/" + displayMax + ")", this.width / 2, y + 10, 0xFFFFFF);

        // Am modificat startX de la x + 25 la x + 42 pentru a centra perfect cele 6 coloane
        int startX = x + 42;
        int startY = y + 40;

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            int indexOnPage = i - startIndex;
            int row = indexOnPage / 6; int col = indexOnPage % 6;
            int ix = startX + (col * 50); int iy = startY + (row * 50);

            g.fill(ix - 5, iy - 5, ix + 21, iy + 21, 0x5543B581);
            g.renderItem(items.get(i), ix, iy);
            g.renderItemDecorations(this.font, items.get(i), ix, iy);

            g.pose().pushPose(); g.pose().translate(ix + 8, iy + 22, 0); g.pose().scale(0.7f, 0.7f, 1f);
            g.drawCenteredString(this.font, "§b" + chances.get(i) + "%", 0, 0, 0xFFFFFF);
            g.pose().popPose();

            if (mouseX >= ix && mouseX <= ix + 16 && mouseY >= iy && mouseY <= iy + 16) g.renderTooltip(this.font, items.get(i), mouseX, mouseY);
        }

        // --- Buton Custom: Pagina Anterioară ---
        if (currentPage > 0) {
            int prevX = x + 25; int prevY = y + 175; int btnW = 130; int btnH = 20;
            boolean hovered = mouseX >= prevX && mouseX <= prevX + btnW && mouseY >= prevY && mouseY <= prevY + btnH;

            // Schimbăm culoarea dacă mouse-ul este deasupra butonului
            g.fill(prevX, prevY, prevX + btnW, prevY + btnH, hovered ? 0xFF3A8A46 : 0xFF2A6332);
            g.renderOutline(prevX, prevY, btnW, btnH, 0xFF83B755);
            g.drawCenteredString(this.font, "< Pagina Anterioară", prevX + btnW / 2, prevY + 6, hovered ? 0xFFFFFF : 0xDDDDDD);
        }

        // --- Buton Custom: Pagina Următoare ---
        if ((currentPage + 1) * ITEMS_PER_PAGE < items.size()) {
            int nextX = x + w - 155; int nextY = y + 175; int btnW = 130; int btnH = 20;
            boolean hovered = mouseX >= nextX && mouseX <= nextX + btnW && mouseY >= nextY && mouseY <= nextY + btnH;

            g.fill(nextX, nextY, nextX + btnW, nextY + btnH, hovered ? 0xFF3A8A46 : 0xFF2A6332);
            g.renderOutline(nextX, nextY, btnW, btnH, 0xFF83B755);
            g.drawCenteredString(this.font, "Pagina Următoare >", nextX + btnW / 2, nextY + 6, hovered ? 0xFFFFFF : 0xDDDDDD);
        }

        g.drawCenteredString(this.font, "§7Apasă ESC pentru a te întoarce", this.width / 2, y + h - 15, 0xAAAAAA);
        super.render(g, mouseX, mouseY, partialTick);
    }

    // --- Metodă nouă pentru a detecta click-urile pe butoanele custom ---
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Click stânga
            int w = 350; int h = 220;
            int x = (this.width - w) / 2; int y = (this.height - h) / 2;

            // Verificare click Pagina Anterioară
            if (currentPage > 0) {
                int prevX = x + 25; int prevY = y + 175; int btnW = 130; int btnH = 20;
                if (mouseX >= prevX && mouseX <= prevX + btnW && mouseY >= prevY && mouseY <= prevY + btnH) {
                    currentPage--;
                    return true;
                }
            }

            // Verificare click Pagina Următoare
            if ((currentPage + 1) * ITEMS_PER_PAGE < items.size()) {
                int nextX = x + w - 155; int nextY = y + 175; int btnW = 130; int btnH = 20;
                if (mouseX >= nextX && mouseX <= nextX + btnW && mouseY >= nextY && mouseY <= nextY + btnH) {
                    currentPage++;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}