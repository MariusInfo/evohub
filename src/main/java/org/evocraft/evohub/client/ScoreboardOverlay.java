package org.evocraft.evohub.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.client.ClientBalanceData;
import org.evocraft.evocore.client.ClientStatsData;
import org.evocraft.evohub.gui.EvoHubScreen;
import org.evocraft.evojobs.client.ClientJobsData;
import org.evocraft.evojobs.client.ClientJobsData.JobInfo;

@Mod.EventBusSubscriber(modid = "evohub", value = Dist.CLIENT)
public class ScoreboardOverlay {

    public static int onlinePlayers = 1;

    // Calculăm înălțimea totală pe baza noilor "cutii" (module)
    private static int calculateTotalHeight() {
        int totalHeight = 22 + 4; // Header-ul (PLAY.EVOCRAFT.RO) + spațiul

        boolean isCreative = EvoHubScreen.isCreative();
        int gridItems = 0;

        // Numărăm câte elemente din grila de mijloc sunt active
        if (EvoHubScreen.sbShowTu || EvoHubScreen.sbShowRank) gridItems++;
        if (EvoHubScreen.sbShowMoney) gridItems++;
        if (EvoHubScreen.sbShowPlaytime) gridItems++;
        if (EvoHubScreen.sbShowCombat && !isCreative) gridItems++;

        // Adăugăm înălțimea pentru grila pe două coloane (am redus înălțimea blocurilor la 22)
        int rows = (int) Math.ceil(gridItems / 2.0);
        if (rows > 0) {
            totalHeight += rows * (22 + 4);
        }

        // Adăugăm modulul de job-uri
        if (EvoHubScreen.sbShowJobs && !isCreative && !ClientJobsData.activeJobs.isEmpty()) {
            int activeCount = 0;
            for (JobInfo j : ClientJobsData.activeJobs) if (j.isActive) activeCount++;
            if (activeCount > 0) totalHeight += 20 + (activeCount * 28) + 4;
        }

        // Adăugăm info și vote (fără textul suplimentar, doar cutii subțiri)
        if (EvoHubScreen.sbShowInfo) totalHeight += 22 + 4;
        totalHeight += 22; // Modulul de Vote

        return totalHeight;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!EvoHubScreen.settingShowScoreboard) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.renderDebug) return;

        // ASCUNDE SCOREBOARD-UL CÂND TAB ESTE DESCHIS
        if (mc.options.keyPlayerList.isDown()) return;

        if (mc.getConnection() != null) {
            onlinePlayers = mc.getConnection().getOnlinePlayers().size();
        }

        GuiGraphics g = event.getGuiGraphics();
        double guiScale = mc.getWindow().getGuiScale();
        float autoScale = 1.0f;
        if (guiScale >= 3) autoScale = 0.85f;
        if (guiScale >= 4) autoScale = 0.70f;
        float finalScale = autoScale * EvoHubScreen.getScoreboardUserScale();

        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        String playerName = mc.player.getGameProfile().getName();

        int boxWidth = EvoHubScreen.getScoreboardWidth();
        int totalHeight = calculateTotalHeight();

        g.pose().pushPose();
        int margin = 5;
        float scaledScreenWidth = screenWidth / finalScale;
        float scaledScreenHeight = screenHeight / finalScale;
        float scaledX;
        float scaledY;
        switch (EvoHubScreen.sbPosition) {
            case EvoHubScreen.SB_TOP_LEFT -> {
                scaledX = margin;
                scaledY = margin;
            }
            case EvoHubScreen.SB_TOP_RIGHT -> {
                scaledX = scaledScreenWidth - boxWidth - margin;
                scaledY = margin;
            }
            case EvoHubScreen.SB_BOTTOM_LEFT -> {
                scaledX = margin;
                scaledY = scaledScreenHeight - totalHeight - margin;
            }
            case EvoHubScreen.SB_BOTTOM_RIGHT -> {
                scaledX = scaledScreenWidth - boxWidth - margin;
                scaledY = scaledScreenHeight - totalHeight - margin;
            }
            default -> {
                scaledX = scaledScreenWidth - boxWidth - margin;
                scaledY = (scaledScreenHeight - totalHeight) / 2.0f;
            }
        }
        scaledX = Math.max(margin, scaledX);
        scaledY = Math.max(margin, scaledY);
        g.pose().scale(finalScale, finalScale, 1.0f);

        int x = (int) scaledX;
        int y = (int) scaledY;

        // Culorile pe tematica ta verde
        int colorTop = 0xCC0D140D;
        int colorBot = 0xCC3A592D;
        int accentColor = 0xFF83B755;

        int currentY = y;
        boolean isCreative = EvoHubScreen.isCreative();

        // 1. MODULUL HEADER (IP și Online)
        g.fillGradient(x, currentY, x + boxWidth, currentY + 22, colorTop, colorBot);
        g.drawString(mc.font, "§lPLAY.EVOCRAFT.RO", x + 5, currentY + 7, accentColor, true);
        String onlineText = "👥 ONLINE: " + onlinePlayers;
        g.drawString(mc.font, onlineText, x + boxWidth - mc.font.width(onlineText) - 5, currentY + 7, 0xFFFFFF, true);
        currentY += 26;

        // 2. GRILA DE STATISTICI (fără text descriptiv, doar iconițe)
        int col = 0;
        int gridY = currentY;
        int blockWidth = (boxWidth - 4) / 2;
        int blockHeight = 22; // Cutii mai slim pentru că am scos textul

        if (EvoHubScreen.sbShowTu || EvoHubScreen.sbShowRank) {
            int cx = x + (col * (blockWidth + 4));
            g.fillGradient(cx, gridY, cx + blockWidth, gridY + blockHeight, colorTop, colorBot);

            g.drawString(mc.font, "👤 " + playerName, cx + 4, gridY + 2, 0xFFFFFF, true);

            String myRank = "§7Membru";
            if (CustomTabListHandler.syncedRanks.containsKey(mc.player.getUUID())) {
                myRank = CustomTabListHandler.applyRankColor(CustomTabListHandler.syncedRanks.get(mc.player.getUUID()));
            } else if (ClientStatsData.rank != null && !ClientStatsData.rank.isEmpty()) {
                myRank = CustomTabListHandler.applyRankColor(ClientStatsData.rank);
            }
            g.drawString(mc.font, "⭐ " + myRank, cx + 4, gridY + 12, 0xFFFFFF, true);

            col++; if(col > 1) { col = 0; gridY += blockHeight + 4; }
        }

        if (EvoHubScreen.sbShowMoney) {
            int cx = x + (col * (blockWidth + 4));
            g.fillGradient(cx, gridY, cx + blockWidth, gridY + blockHeight, colorTop, colorBot);
            String balanceStr = String.format("%.0f Lei", ClientBalanceData.getBalance());
            // Centrat pe verticală
            g.drawString(mc.font, "💵 §e" + balanceStr, cx + 4, gridY + 7, 0xFFFFFF, true);
            col++; if(col > 1) { col = 0; gridY += blockHeight + 4; }
        }

        if (EvoHubScreen.sbShowPlaytime) {
            int cx = x + (col * (blockWidth + 4));
            g.fillGradient(cx, gridY, cx + blockWidth, gridY + blockHeight, colorTop, colorBot);
            int totalSecs = ClientStatsData.playtimeSeconds;
            String timeStr = (totalSecs < 3600) ? (totalSecs / 60) + " min" : (totalSecs / 3600) + " ore";
            // Centrat pe verticală
            g.drawString(mc.font, "⌚ §b" + timeStr, cx + 4, gridY + 7, 0xFFFFFF, true);
            col++; if(col > 1) { col = 0; gridY += blockHeight + 4; }
        }

        if (EvoHubScreen.sbShowCombat && !isCreative) {
            int cx = x + (col * (blockWidth + 4));
            g.fillGradient(cx, gridY, cx + blockWidth, gridY + blockHeight, colorTop, colorBot);
            g.drawString(mc.font, "⚔ §a" + ClientStatsData.kills, cx + 4, gridY + 2, 0xFFFFFF, true);
            g.drawString(mc.font, "☠ §c" + ClientStatsData.deaths, cx + 4, gridY + 12, 0xFFFFFF, true);
            col++; if(col > 1) { col = 0; gridY += blockHeight + 4; }
        }

        // Actualizăm poziția Y după grilă
        if (col > 0) gridY += blockHeight + 4;
        currentY = gridY;

        // 3. MODULUL DE JOB-URI
        boolean hasActiveJobs = false;
        for (JobInfo j : ClientJobsData.activeJobs) if (j.isActive) hasActiveJobs = true;

        if (EvoHubScreen.sbShowJobs && !isCreative && hasActiveJobs) {
            int activeCount = 0;
            for (JobInfo j : ClientJobsData.activeJobs) if (j.isActive) activeCount++;
            int jobsBoxHeight = 20 + (activeCount * 28);

            g.fillGradient(x, currentY, x + boxWidth, currentY + jobsBoxHeight, colorTop, colorBot);
            g.drawString(mc.font, "💼 §a§lJob-uri", x + 5, currentY + 5, 0xFFFFFF, true);

            int jobY = currentY + 18;
            for (JobInfo job : ClientJobsData.activeJobs) {
                if (!job.isActive) continue;
                g.renderItem(job.icon, x + 5, jobY);
                g.drawString(mc.font, "§f" + job.name + " §7[Niv " + job.level + "]", x + 26, jobY, 0xFFFFFF, true);

                int barWidth = boxWidth - 35;
                int barX = x + 26;
                int barY = jobY + 12;

                g.fill(barX, barY, barX + barWidth, barY + 4, 0xFF111111);
                float progress = (float) (job.currentXp / job.requiredXp);
                g.fill(barX, barY, barX + (int) (barWidth * Math.min(1.0f, progress)), barY + 4, accentColor);

                g.pose().pushPose();
                g.pose().translate(barX + barWidth, barY + 5, 0);
                g.pose().scale(0.55f, 0.55f, 0.55f);
                String xpText = (int)job.currentXp + "/" + (int)job.requiredXp + " XP";
                g.drawString(mc.font, xpText, -mc.font.width(xpText), 0, 0xAAAAAA, false);
                g.pose().popPose();

                jobY += 28;
            }
            currentY += jobsBoxHeight + 4;
        }

        // 4. MODULUL SERVER INFO (Centrat și Clean)
        if (EvoHubScreen.sbShowInfo) {
            g.fillGradient(x, currentY, x + boxWidth, currentY + 22, colorTop, colorBot);
            String infoText = "🌐 §fevocraft.ro";
            g.drawString(mc.font, infoText, x + (boxWidth - mc.font.width(infoText)) / 2, currentY + 7, 0xFFFFFF, true);
            currentY += 26;
        }

        // 5. MODULUL VOTE (Centrat și Clean)
        g.fillGradient(x, currentY, x + boxWidth, currentY + 22, colorTop, colorBot);
        String voteText = "🎁 §a/vote";
        g.drawString(mc.font, voteText, x + (boxWidth - mc.font.width(voteText)) / 2, currentY + 7, 0xFFFFFF, true);

        g.pose().popPose();
    }
}
