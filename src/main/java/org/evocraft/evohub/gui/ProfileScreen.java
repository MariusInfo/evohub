package org.evocraft.evohub.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.evocraft.evocore.util.EvoCurrencyFormatter;
import org.evocraft.evojobs.client.ClientJobsData.JobInfo; // IMPORTUL REPARAT!

import java.util.ArrayList;
import java.util.List;

public class ProfileScreen extends Screen {

    private final String playerName;
    private final String rank;
    private final double balance;
    private final int playtime;
    private final int kills;
    private final int deaths;
    private final List<JobInfo> allJobs;
    private final boolean isSelf;

    public ProfileScreen(String playerName, String rank, double balance, int playtime, int kills, int deaths, List<JobInfo> jobs) {
        super(Component.literal("Profile: " + playerName));
        this.playerName = playerName;
        this.rank = rank;
        this.balance = balance;
        this.playtime = playtime;
        this.kills = kills;
        this.deaths = deaths;

        this.allJobs = new ArrayList<>(jobs);
        this.allJobs.sort((j1, j2) -> Long.compare(j2.level, j1.level));
        this.isSelf = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getGameProfile().getName().equals(playerName);
    }

    @Override
    protected void init() {
        int w = 420; int h = 250;
        int x = (this.width - w) / 2; int y = (this.height - h) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("View All Jobs"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new AllJobsScreen(this, this.playerName, this.allJobs));
            }
        }).bounds(x + w - 150, y + h - 35, 140, 20).build());
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int w = 420; int h = 250;
        int x = (this.width - w) / 2; int y = (this.height - h) / 2;

        fillRounded(g, x, y, w, h, 0xEE0D140D);
        outlineRounded(g, x, y, w, h, 0xFF83B755);
        fillRounded(g, x, y, w, 25, 0xAA141C14);

        g.drawCenteredString(this.font, "§lPROFILE CARD: §a" + this.playerName.toUpperCase(), this.width / 2, y + 8, 0xFFFFFF);

        int leftX = x + 15;
        fillRounded(g, leftX, y + 40, 110, 160, 0xAA141C14);
        outlineRounded(g, leftX, y + 40, 110, 160, 0xFF3A592D);

        if (this.isSelf && this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftX + 55, y + 185, 65, (float)(leftX + 55 - mouseX), (float)(y + 130 - mouseY), this.minecraft.player);
        } else {
            g.drawCenteredString(this.font, "§7[Avatar Unavailable]", leftX + 55, y + 110, 0xFFFFFF);
        }

        String statut = "§7Newcomer";
        if (playtime > 10) statut = "§aTraveler";
        if (playtime > 50) statut = "§eAdventurer";
        if (playtime > 200) statut = "§cVeteran";
        if (playtime > 500) statut = "§6§lLEGEND";

        g.drawCenteredString(this.font, statut, leftX + 55, y + 210, 0xFFFFFF);

        int midX = x + 140; int midY = y + 40;
        g.drawString(this.font, "§a§lGeneral Info", midX, midY, 0xFFFFFF, true); midY += 20;
        g.drawString(this.font, "§7Rank: §f" + this.rank, midX, midY, 0xFFFFFF, true); midY += 15;
        g.drawString(this.font, "§7Balance: §e" + EvoCurrencyFormatter.formatWithCurrency(balance), midX, midY, 0xFFFFFF, true); midY += 15;
        g.drawString(this.font, "§7Playtime: §b" + playtime + " Hours", midX, midY, 0xFFFFFF, true); midY += 25;

        g.drawString(this.font, "§c§lCombat Stats", midX, midY, 0xFFFFFF, true); midY += 20;
        g.drawString(this.font, "§7Kills: §f" + kills, midX, midY, 0xFFFFFF, true); midY += 15;
        g.drawString(this.font, "§7Deaths: §f" + deaths, midX, midY, 0xFFFFFF, true); midY += 15;

        double kd = deaths == 0 ? kills : (double) kills / deaths;
        String kdColor = kd >= 2.0 ? "§a" : (kd >= 1.0 ? "§e" : "§c");
        g.drawString(this.font, "§7K/D Ratio: " + kdColor + String.format("%.2f", kd), midX, midY, 0xFFFFFF, true);

        int rightX = x + 280; int rightY = y + 40;
        g.drawString(this.font, "§a§lTop 3 Careers", rightX, rightY, 0xFFFFFF, true); rightY += 20;

        if (allJobs.isEmpty()) {
            g.drawString(this.font, "§cUnemployed.", rightX, rightY, 0xFFFFFF, true);
        } else {
            int displayCount = Math.min(3, allJobs.size());
            for (int i = 0; i < displayCount; i++) {
                JobInfo job = allJobs.get(i);
                g.renderItem(job.icon, rightX, rightY);
                g.drawString(this.font, "§f" + job.name + " §7[Lv." + job.level + "]", rightX + 22, rightY + 4, 0xFFFFFF, true);

                int barY = rightY + 20;
                fillRounded(g, rightX, barY, 130, 4, 0xFF333333);
                float progress = job.requiredXp > 0.0 ? Math.min(1.0f, Math.max(0.0f, (float) (job.currentXp / job.requiredXp))) : 0.0f;
                int xpColor = i == 0 ? 0xFFFFD700 : (i == 1 ? 0xFFC0C0C0 : 0xFFCD7F32);
                fillRounded(g, rightX, barY, (int) (130 * progress), 4, xpColor);
                rightY += 35;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
