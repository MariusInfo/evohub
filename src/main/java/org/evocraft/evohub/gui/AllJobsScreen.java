package org.evocraft.evohub.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evocraft.evojobs.client.ClientJobsData.JobInfo; // IMPORTUL REPARAT!

import java.util.List;

public class AllJobsScreen extends Screen {

    private static final int BG_COLOR = 0xEE0D140D;
    private static final int BORDER_COLOR = 0xFF83B755;
    private static final int CARD_BG = 0xAA141C14;
    private static final int CARD_BORDER = 0xFF3A592D;
    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private final Screen parent;
    private final String playerName;
    private final List<JobInfo> jobs;

    public AllJobsScreen(Screen parent, String playerName, List<JobInfo> jobs) {
        super(Component.literal("Jobs: " + playerName));
        this.parent = parent;
        this.playerName = playerName;
        this.jobs = jobs;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

        int w = 280;
        int h = 320;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        fillRounded(g, x, y, w, h, BG_COLOR);
        outlineRounded(g, x, y, w, h, BORDER_COLOR);

        g.pose().pushPose();
        g.pose().translate(x + w / 2.0f, y - 15, 0);
        g.pose().scale(1.3f, 1.3f, 1.0f);
        g.drawCenteredString(this.font, "CAREERS: " + playerName.toUpperCase(), 0, 0, BORDER_COLOR);
        g.pose().popPose();

        int listX = x + 15;
        int currentY = y + 25;

        if (jobs == null || jobs.isEmpty()) {
            g.drawCenteredString(this.font, "This player has no jobs.", x + w / 2, y + 150, 0xFFAAAAAA);
        } else {
            for (JobInfo job : jobs) {
                if (currentY + 40 > y + h - 22) break;
                fillRounded(g, listX, currentY, w - 30, 40, CARD_BG);
                outlineRounded(g, listX, currentY, w - 30, 40, CARD_BORDER);

                g.pose().pushPose();
                g.pose().translate(listX + 8, currentY + 10, 0);
                g.pose().scale(1.2f, 1.2f, 1.0f);
                g.renderItem(job.icon, 0, 0);
                g.pose().popPose();

                String status = job.isActive ? "§aActive" : "§cInactive";
                g.drawString(this.font, job.name.toUpperCase(), listX + 40, currentY + 8, TEXT_COLOR, false);
                g.drawString(this.font, "Level: §e" + job.level + " §f| " + status, listX + 40, currentY + 22, TEXT_COLOR, false);

                String xpTxt = "§b" + (int)job.currentXp + " §f/ " + (int)job.requiredXp;
                g.drawString(this.font, xpTxt, listX + w - 35 - this.font.width(xpTxt), currentY + 8, TEXT_COLOR, false);

                fillRounded(g, listX + 130, currentY + 22, 100, 8, 0xFF333333);
                double ratio = job.requiredXp > 0.0 ? (job.currentXp / job.requiredXp) : 0.0;
                int progressWidth = (int) (100 * Math.max(0.0, Math.min(1.0, ratio)));
                fillRounded(g, listX + 130, currentY + 22, progressWidth, 8, BORDER_COLOR);

                currentY += 45;
            }
        }

        g.drawCenteredString(this.font, "Click anywhere to close", x + w / 2, y + h - 15, 0xFF777777);

        super.render(g, mouseX, mouseY, partialTick);
    }
}
