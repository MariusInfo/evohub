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

    private static final int PANEL_W = 540;
    private static final int PANEL_H = 250;
    private static final int ROULETTE_W = 430;
    private static final int ROULETTE_H = 82;
    private static final int ITEM_SPACING = 48;
    private static final long SPIN_DURATION_MS = 6200L;
    private static final long CLOSE_AFTER_MS = 3600L;

    private long startTime;
    private long finishTime;
    private double targetScroll;
    private boolean isFinished;
    private boolean finishSoundPlayed;
    private int lastTickIndex = -1;

    public CrateAnimationScreen(String crateName, List<ItemStack> items, int winningIndex) {
        super(Component.literal("Deschidere Crate: " + crateName));
        this.crateName = crateName;
        this.items = items;
        this.winningIndex = winningIndex;
    }

    @Override
    protected void init() {
        super.init();
        this.startTime = System.currentTimeMillis();
        this.finishTime = 0L;
        this.isFinished = false;
        this.finishSoundPlayed = false;
        this.lastTickIndex = -1;

        int winner = getSafeWinningIndex();
        this.targetScroll = (winner * ITEM_SPACING) - (ROULETTE_W / 2.0) + (ITEM_SPACING / 2.0);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g);
        if (items.isEmpty()) {
            g.drawCenteredString(this.font, "§cCrate-ul nu are iteme pentru animație.", this.width / 2, this.height / 2, 0xFFFFFF);
            super.render(g, mouseX, mouseY, partialTicks);
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double progress = Math.min(1.0, elapsed / (double) SPIN_DURATION_MS);
        double eased = 1.0 - Math.pow(1.0 - progress, 3.35);
        double currentScroll = targetScroll * eased;

        if (!isFinished && progress >= 1.0) {
            isFinished = true;
            finishTime = System.currentTimeMillis();
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelX = centerX - PANEL_W / 2;
        int panelY = centerY - PANEL_H / 2;
        int rouletteX = centerX - ROULETTE_W / 2;
        int rouletteY = centerY - 36;

        drawAmbientGlow(g, centerX, centerY, elapsed, isFinished);
        EvoHubTheme.drawPanel(g, panelX, panelY, PANEL_W, PANEL_H);
        EvoHubTheme.drawTitle(g, this.font, "DESCHIDERE CRATE", crateName.toUpperCase(), centerX, panelY + 14);

        String status = isFinished ? "§a§lRECOMPENSA TA" : "§7Se alege recompensa...";
        g.drawCenteredString(this.font, status, centerX, panelY + 48, 0xFFFFFF);

        drawRoulette(g, rouletteX, rouletteY, currentScroll);
        drawSelector(g, centerX, rouletteY);
        playTickSound(currentScroll);

        if (isFinished) {
            drawRewardReveal(g, centerX, rouletteY + ROULETTE_H + 22, elapsed);
            if (!finishSoundPlayed) {
                finishSoundPlayed = true;
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }
            if (System.currentTimeMillis() - finishTime > CLOSE_AFTER_MS && this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
        } else {
            int barW = 220;
            int barX = centerX - barW / 2;
            int barY = panelY + PANEL_H - 32;
            EvoHubTheme.fillRounded(g, barX, barY, barW, 6, 0xAA111611);
            g.fill(barX, barY, barX + (int) (barW * progress), barY + 6, EvoHubTheme.ACCENT);
        }

        super.render(g, mouseX, mouseY, partialTicks);
    }

    private void drawRoulette(GuiGraphics g, int x, int y, double currentScroll) {
        EvoHubTheme.fillRounded(g, x, y, ROULETTE_W, ROULETTE_H, 0xCC050805);
        EvoHubTheme.outlineRounded(g, x, y, ROULETTE_W, ROULETTE_H, EvoHubTheme.BORDER);
        g.fill(x + 8, y + 8, x + ROULETTE_W - 8, y + 9, EvoHubTheme.ACCENT_DARK);
        g.fill(x + 8, y + ROULETTE_H - 9, x + ROULETTE_W - 8, y + ROULETTE_H - 8, EvoHubTheme.ACCENT_DARK);

        g.enableScissor(x + 3, y + 3, x + ROULETTE_W - 3, y + ROULETTE_H - 3);
        for (int i = 0; i < items.size(); i++) {
            int slotX = x + (i * ITEM_SPACING) - (int) currentScroll;
            int slotCenter = slotX + ITEM_SPACING / 2;
            if (slotX > x - ITEM_SPACING && slotX < x + ROULETTE_W + ITEM_SPACING) {
                boolean nearCenter = Math.abs(slotCenter - (x + ROULETTE_W / 2)) < 25;
                int slotY = y + 17;
                EvoHubTheme.fillRounded(g, slotX + 4, slotY, 40, 48, nearCenter ? 0x884A6B38 : 0x4424361D);
                EvoHubTheme.outlineRounded(g, slotX + 4, slotY, 40, 48, nearCenter ? EvoHubTheme.ACCENT : EvoHubTheme.BORDER);

                g.renderItem(items.get(i), slotX + 16, slotY + 15);
                g.renderItemDecorations(this.font, items.get(i), slotX + 16, slotY + 15);
            }
        }
        g.disableScissor();
    }

    private void drawSelector(GuiGraphics g, int centerX, int rouletteY) {
        g.fill(centerX - 2, rouletteY - 9, centerX + 2, rouletteY + ROULETTE_H + 9, 0xFFFFD36B);
        g.fill(centerX - 9, rouletteY - 9, centerX + 10, rouletteY - 6, 0xFFFFD36B);
        g.fill(centerX - 9, rouletteY + ROULETTE_H + 6, centerX + 10, rouletteY + ROULETTE_H + 9, 0xFFFFD36B);
    }

    private void drawRewardReveal(GuiGraphics g, int centerX, int y, long elapsed) {
        ItemStack wonItem = items.get(getSafeWinningIndex());
        double pulse = 1.0 + (Math.sin(elapsed / 150.0) * 0.04);

        int cardW = 240;
        int cardH = 54;
        int cardX = centerX - cardW / 2;
        EvoHubTheme.fillRounded(g, cardX, y, cardW, cardH, 0xDD141C14);
        EvoHubTheme.outlineRounded(g, cardX, y, cardW, cardH, EvoHubTheme.ACCENT);

        g.pose().pushPose();
        g.pose().translate(cardX + 24, y + 11, 0);
        g.pose().scale((float) (1.8 * pulse), (float) (1.8 * pulse), 1.0f);
        g.renderItem(wonItem, 0, 0);
        g.pose().popPose();

        g.drawString(this.font, "§a§lAi câștigat", cardX + 70, y + 12, 0xFFFFFF, false);
        g.drawString(this.font, "§f" + wonItem.getHoverName().getString(), cardX + 70, y + 28, 0xFFFFFF, false);
    }

    private void drawAmbientGlow(GuiGraphics g, int centerX, int centerY, long elapsed, boolean strong) {
        int count = strong ? 34 : 20;
        int alphaBase = strong ? 120 : 65;
        for (int i = 0; i < count; i++) {
            double angle = i * 2.399 + elapsed * 0.0014;
            double radius = 74 + (i % 7) * 18 + Math.sin(elapsed * 0.002 + i) * 10;
            int sx = centerX + (int) (Math.cos(angle) * radius);
            int sy = centerY + (int) (Math.sin(angle) * radius * 0.45);
            int alpha = Math.max(30, Math.min(170, alphaBase + (int) (Math.sin(elapsed * 0.006 + i) * 45)));
            int color = (alpha << 24) | 0x83B755;
            g.fill(sx, sy, sx + 2, sy + 2, color);
        }
    }

    private void playTickSound(double currentScroll) {
        int currentIndex = (int) Math.round(currentScroll / ITEM_SPACING);
        if (!isFinished && currentIndex != lastTickIndex) {
            lastTickIndex = currentIndex;
            if (this.minecraft != null && this.minecraft.player != null) {
                float pitch = 1.0f + Math.min(0.6f, currentIndex * 0.015f);
                this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.25f, pitch);
            }
        }
    }

    private int getSafeWinningIndex() {
        return Math.max(0, Math.min(winningIndex, items.size() - 1));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return isFinished;
    }
}
