package org.evocraft.evohub.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CrateAnimationScreen extends Screen {

    private final List<ItemStack> items;
    private final int winningIndex;
    private final String crateName;

    private double currentScroll = 0;
    private double targetScroll = 0;

    private final int ITEM_SIZE = 40;
    private final int BOX_WIDTH = 300;
    private final int BOX_HEIGHT = 70;

    private boolean isFinished = false;
    private boolean soundPlayed = false;
    private long finishTime = 0;
    private int lastTickIndex = -1; // Pentru sunetul de ticait

    public CrateAnimationScreen(String crateName, List<ItemStack> items, int winningIndex) {
        super(Component.literal("Deschidere Crate: " + crateName));
        this.crateName = crateName; this.items = items; this.winningIndex = winningIndex;
    }

    @Override
    protected void init() {
        super.init();
        double offsetRandom = (Math.random() * 20) - 10;
        this.targetScroll = (this.winningIndex * ITEM_SIZE) - (BOX_WIDTH / 2.0) + (ITEM_SIZE / 2.0) + offsetRandom;
        this.currentScroll = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int boxLeft = centerX - (BOX_WIDTH / 2); int boxTop = centerY - (BOX_HEIGHT / 2);
        int boxRight = centerX + (BOX_WIDTH / 2); int boxBottom = centerY + (BOX_HEIGHT / 2);

        // Fundal Ruletă
        graphics.fill(boxLeft, boxTop, boxRight, boxBottom, 0xCC000000);
        graphics.fill(boxLeft, boxTop - 2, boxRight, boxTop, 0xFF43B581);
        graphics.fill(boxLeft, boxBottom, boxRight, boxBottom + 2, 0xFF43B581);

        // LOGICA DE SCROLL (factor 0.014 pentru 10 secunde)
        if (!isFinished) {
            double distance = targetScroll - currentScroll;
            currentScroll += distance * 0.014;

            int currentIndex = (int) (currentScroll / ITEM_SIZE);
            if (currentIndex != lastTickIndex) {
                lastTickIndex = currentIndex;
                if (this.minecraft.player != null) this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.3f, 1.5f);
            }
            if (distance < 0.3) { isFinished = true; finishTime = System.currentTimeMillis(); }
        }

        graphics.enableScissor(boxLeft, boxTop, boxRight, boxBottom);

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            int itemX = boxLeft + (i * ITEM_SIZE) - (int) currentScroll + (ITEM_SIZE / 2) - 8;
            int itemY = centerY - 8;

            if (itemX > boxLeft - 20 && itemX < boxRight + 20) {
                // --- DESENĂM CASETA PENTRU ITEM ---
                int slotX = boxLeft + (i * ITEM_SIZE) - (int) currentScroll;
                // Fundalul casetei
                graphics.fill(slotX + 2, boxTop + 2, slotX + ITEM_SIZE - 2, boxBottom - 2, 0x33FFFFFF);
                // Bordura casetei (separatoarele)
                graphics.renderOutline(slotX + 1, boxTop + 1, ITEM_SIZE - 2, BOX_HEIGHT - 2, 0x4443B581);

                // Desenăm item-ul
                graphics.renderItem(stack, itemX, itemY);
                graphics.renderItemDecorations(this.font, stack, itemX, itemY);
            }
        }
        graphics.disableScissor();

        // SELECTORUL CENTRAL (Linia de aur)
        graphics.fill(centerX - 1, boxTop - 10, centerX + 1, boxBottom + 10, 0xFFFFD700);

        if (isFinished) {
            if (!soundPlayed) {
                soundPlayed = true;
                if (this.minecraft.player != null) this.minecraft.player.playSound(SoundEvents.PLAYER_LEVELUP, 1f, 1f);
            }
            ItemStack wonItem = items.get(winningIndex);
            graphics.drawCenteredString(this.font, "§a§lAI CÂȘTIGAT: §f" + wonItem.getHoverName().getString(), centerX, boxBottom + 20, 0xFFFFFF);

            // Închidem după 2 secunde de la oprire (ajungem la ~10s total)
            if (System.currentTimeMillis() - finishTime > 2000) {
                this.minecraft.setScreen(null);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTicks);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return isFinished; }
}