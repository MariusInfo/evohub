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
import org.evocraft.evohub.gui.ScoreboardLayoutScreen;
import org.evocraft.evojobs.client.ClientJobsData;
import org.evocraft.evojobs.client.ClientJobsData.JobInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

@Mod.EventBusSubscriber(modid = "evohub", value = Dist.CLIENT)
public class ScoreboardOverlay {

    private static final int MODULE_HEIGHT = 22;
    private static final int MODULE_GAP = 4;
    private static final int COLOR_TOP = 0xCC0D140D;
    private static final int COLOR_BOT = 0xCC3A592D;
    private static final int ACCENT_COLOR = 0xFF83B755;

    public static int onlinePlayers = 1;

    public enum HudElement {
        HEADER("header", "Server"),
        NAME("name", "Name"),
        RANK("rank", "Rank"),
        MONEY("money", "Money"),
        PLAYTIME("playtime", "Playtime"),
        COMBAT("combat", "Combat"),
        JOBS("jobs", "Jobs"),
        INFO("info", "Info"),
        VOTE("vote", "Vote");

        public final String id;
        public final String label;

        HudElement(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    public static class HudElementBounds {
        public final HudElement element;
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final int drawWidth;
        public final int drawHeight;
        public final float scale;

        private HudElementBounds(HudElement element, int x, int y, int width, int height, int drawWidth, int drawHeight, float scale) {
            this.element = element;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.drawWidth = drawWidth;
            this.drawHeight = drawHeight;
            this.scale = scale;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private static class DefaultPlacement {
        final int x;
        final int y;

        DefaultPlacement(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!EvoHubScreen.settingShowScoreboard) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.renderDebug) return;
        if (mc.options.keyPlayerList.isDown()) return;
        if (mc.screen instanceof ScoreboardLayoutScreen) return;

        updateOnlinePlayers(mc);
        renderElements(event.getGuiGraphics(), mc, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), false, null);
    }

    public static void updateOnlinePlayers(Minecraft mc) {
        if (mc != null && mc.getConnection() != null) {
            onlinePlayers = mc.getConnection().getOnlinePlayers().size();
        }
    }

    public static void renderElements(GuiGraphics g, Minecraft mc, int screenWidth, int screenHeight, boolean editMode, HudElement selected) {
        for (HudElementBounds bounds : getVisibleElementBounds(mc, screenWidth, screenHeight)) {
            renderElement(g, mc, bounds);
            if (editMode) {
                int outline = bounds.element == selected ? 0xFFE6D26A : 0xAA83B755;
                drawOutline(g, bounds.x - 1, bounds.y - 1, bounds.width + 2, bounds.height + 2, outline);
                g.drawString(mc.font, bounds.element.label, bounds.x + 3, bounds.y - 10, outline, true);
            }
        }
    }

    public static List<HudElementBounds> getVisibleElementBounds(Minecraft mc, int screenWidth, int screenHeight) {
        List<HudElementBounds> bounds = new ArrayList<>();
        if (mc == null || mc.player == null) return bounds;

        float baseScale = getBaseHudScale(mc);
        EnumMap<HudElement, DefaultPlacement> defaults = getDefaultPlacements(mc, screenWidth, screenHeight, baseScale);

        for (HudElement element : HudElement.values()) {
            if (!isElementVisible(element)) continue;

            EvoHubClientSettings.HudElementSettings settings = EvoHubClientSettings.getHudElement(element.id);
            int drawWidth = getElementDrawWidth(element, settings);
            int drawHeight = getElementDrawHeight(element);
            float elementScale = baseScale * (settings.scalePercent / 100.0f);
            int actualWidth = Math.max(1, Math.round(drawWidth * elementScale));
            int actualHeight = Math.max(1, Math.round(drawHeight * elementScale));

            DefaultPlacement placement = defaults.get(element);
            int x = placement != null ? placement.x : 5;
            int y = placement != null ? placement.y : 5;
            if (settings.hasCustomPosition()) {
                x = (int) Math.round(settings.x * screenWidth);
                y = (int) Math.round(settings.y * screenHeight);
            }

            x = clamp(x, 0, Math.max(0, screenWidth - actualWidth));
            y = clamp(y, 0, Math.max(0, screenHeight - actualHeight));
            bounds.add(new HudElementBounds(element, x, y, actualWidth, actualHeight, drawWidth, drawHeight, elementScale));
        }

        return bounds;
    }

    public static HudElementBounds findBounds(Minecraft mc, int screenWidth, int screenHeight, HudElement element) {
        for (HudElementBounds bounds : getVisibleElementBounds(mc, screenWidth, screenHeight)) {
            if (bounds.element == element) return bounds;
        }
        return null;
    }

    private static float getBaseHudScale(Minecraft mc) {
        double guiScale = mc.getWindow().getGuiScale();
        float autoScale = 1.0f;
        if (guiScale >= 3) autoScale = 0.85f;
        if (guiScale >= 4) autoScale = 0.70f;
        return autoScale * EvoHubScreen.getScoreboardUserScale();
    }

    private static EnumMap<HudElement, DefaultPlacement> getDefaultPlacements(Minecraft mc, int screenWidth, int screenHeight, float baseScale) {
        EnumMap<HudElement, DefaultPlacement> placements = new EnumMap<>(HudElement.class);
        int boxWidth = EvoHubScreen.getScoreboardWidth();
        int totalHeight = calculateStackedHeight();
        int margin = 5;

        float scaledScreenWidth = screenWidth / baseScale;
        float scaledScreenHeight = screenHeight / baseScale;
        float baseX;
        float baseY;
        switch (EvoHubScreen.sbPosition) {
            case EvoHubScreen.SB_TOP_LEFT -> {
                baseX = margin;
                baseY = margin;
            }
            case EvoHubScreen.SB_TOP_RIGHT -> {
                baseX = scaledScreenWidth - boxWidth - margin;
                baseY = margin;
            }
            case EvoHubScreen.SB_BOTTOM_LEFT -> {
                baseX = margin;
                baseY = scaledScreenHeight - totalHeight - margin;
            }
            case EvoHubScreen.SB_BOTTOM_RIGHT -> {
                baseX = scaledScreenWidth - boxWidth - margin;
                baseY = scaledScreenHeight - totalHeight - margin;
            }
            default -> {
                baseX = scaledScreenWidth - boxWidth - margin;
                baseY = (scaledScreenHeight - totalHeight) / 2.0f;
            }
        }

        baseX = Math.max(margin, baseX);
        baseY = Math.max(margin, baseY);

        int y = 0;
        if (isElementVisible(HudElement.HEADER)) {
            putPlacement(placements, HudElement.HEADER, baseX, baseY, 0, y, baseScale);
            y += MODULE_HEIGHT + MODULE_GAP;
        }

        List<HudElement> stats = getVisibleStats();
        int statWidth = getStatBaseWidth();
        for (int i = 0; i < stats.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            putPlacement(placements, stats.get(i), baseX, baseY, col * (statWidth + MODULE_GAP), y + row * (MODULE_HEIGHT + MODULE_GAP), baseScale);
        }
        if (!stats.isEmpty()) {
            int rows = (int) Math.ceil(stats.size() / 2.0);
            y += rows * (MODULE_HEIGHT + MODULE_GAP);
        }

        if (isElementVisible(HudElement.JOBS)) {
            putPlacement(placements, HudElement.JOBS, baseX, baseY, 0, y, baseScale);
            y += getElementDrawHeight(HudElement.JOBS) + MODULE_GAP;
        }

        if (isElementVisible(HudElement.INFO)) {
            putPlacement(placements, HudElement.INFO, baseX, baseY, 0, y, baseScale);
            y += MODULE_HEIGHT + MODULE_GAP;
        }

        if (isElementVisible(HudElement.VOTE)) {
            putPlacement(placements, HudElement.VOTE, baseX, baseY, 0, y, baseScale);
        }
        return placements;
    }

    private static void putPlacement(EnumMap<HudElement, DefaultPlacement> placements, HudElement element, float baseX, float baseY, int offsetX, int offsetY, float baseScale) {
        placements.put(element, new DefaultPlacement(Math.round((baseX + offsetX) * baseScale), Math.round((baseY + offsetY) * baseScale)));
    }

    private static int calculateStackedHeight() {
        int totalHeight = 0;
        if (isElementVisible(HudElement.HEADER)) totalHeight += MODULE_HEIGHT + MODULE_GAP;
        List<HudElement> stats = getVisibleStats();
        if (!stats.isEmpty()) {
            totalHeight += (int) Math.ceil(stats.size() / 2.0) * (MODULE_HEIGHT + MODULE_GAP);
        }
        if (isElementVisible(HudElement.JOBS)) totalHeight += getElementDrawHeight(HudElement.JOBS) + MODULE_GAP;
        if (isElementVisible(HudElement.INFO)) totalHeight += MODULE_HEIGHT + MODULE_GAP;
        if (isElementVisible(HudElement.VOTE)) totalHeight += MODULE_HEIGHT;
        return totalHeight;
    }

    private static List<HudElement> getVisibleStats() {
        List<HudElement> stats = new ArrayList<>();
        if (isElementVisible(HudElement.NAME)) stats.add(HudElement.NAME);
        if (isElementVisible(HudElement.RANK)) stats.add(HudElement.RANK);
        if (isElementVisible(HudElement.MONEY)) stats.add(HudElement.MONEY);
        if (isElementVisible(HudElement.PLAYTIME)) stats.add(HudElement.PLAYTIME);
        if (isElementVisible(HudElement.COMBAT)) stats.add(HudElement.COMBAT);
        return stats;
    }

    private static boolean isElementVisible(HudElement element) {
        boolean isCreative = EvoHubScreen.isCreative();
        return switch (element) {
            case HEADER -> EvoHubScreen.sbShowHeader;
            case VOTE -> EvoHubScreen.sbShowVote;
            case NAME -> EvoHubScreen.sbShowTu;
            case RANK -> EvoHubScreen.sbShowRank;
            case MONEY -> EvoHubScreen.sbShowMoney;
            case PLAYTIME -> EvoHubScreen.sbShowPlaytime;
            case COMBAT -> EvoHubScreen.sbShowCombat && !isCreative;
            case JOBS -> EvoHubScreen.sbShowJobs && !isCreative && getActiveJobsCount() > 0;
            case INFO -> EvoHubScreen.sbShowInfo;
        };
    }

    private static int getElementDrawWidth(HudElement element, EvoHubClientSettings.HudElementSettings settings) {
        int baseWidth = switch (element) {
            case NAME, RANK, MONEY, PLAYTIME, COMBAT -> getStatBaseWidth();
            default -> EvoHubScreen.getScoreboardWidth();
        };
        return Math.max(58, Math.round(baseWidth * (settings.widthPercent / 100.0f)));
    }

    private static int getStatBaseWidth() {
        return Math.max(70, (EvoHubScreen.getScoreboardWidth() - MODULE_GAP) / 2);
    }

    private static int getElementDrawHeight(HudElement element) {
        if (element == HudElement.JOBS) return 20 + (getActiveJobsCount() * 28);
        return MODULE_HEIGHT;
    }

    private static int getActiveJobsCount() {
        int activeCount = 0;
        for (JobInfo job : ClientJobsData.activeJobs) {
            if (job.isActive) activeCount++;
        }
        return activeCount;
    }

    private static void renderElement(GuiGraphics g, Minecraft mc, HudElementBounds bounds) {
        g.pose().pushPose();
        g.pose().translate(bounds.x, bounds.y, 0);
        g.pose().scale(bounds.scale, bounds.scale, 1.0f);
        drawElementContent(g, mc, bounds.element, bounds.drawWidth, bounds.drawHeight);
        g.pose().popPose();
    }

    private static void drawElementContent(GuiGraphics g, Minecraft mc, HudElement element, int width, int height) {
        g.fillGradient(0, 0, width, height, COLOR_TOP, COLOR_BOT);
        switch (element) {
            case HEADER -> drawHeader(g, mc, width);
            case NAME -> g.drawString(mc.font, "👤 " + mc.player.getGameProfile().getName(), 4, 7, 0xFFFFFF, true);
            case RANK -> g.drawString(mc.font, "⭐ " + getRankText(mc), 4, 7, 0xFFFFFF, true);
            case MONEY -> {
                String balanceStr = String.format("%.0f Lei", ClientBalanceData.getBalance());
                g.drawString(mc.font, "💵 §e" + balanceStr, 4, 7, 0xFFFFFF, true);
            }
            case PLAYTIME -> {
                int totalSecs = ClientStatsData.playtimeSeconds;
                String timeStr = (totalSecs < 3600) ? (totalSecs / 60) + " min" : (totalSecs / 3600) + " hours";
                g.drawString(mc.font, "⌚ §b" + timeStr, 4, 7, 0xFFFFFF, true);
            }
            case COMBAT -> {
                g.drawString(mc.font, "⚔ §a" + ClientStatsData.kills, 4, 2, 0xFFFFFF, true);
                g.drawString(mc.font, "☠ §c" + ClientStatsData.deaths, 4, 12, 0xFFFFFF, true);
            }
            case JOBS -> drawJobs(g, mc, width);
            case INFO -> {
                String infoText = "🌐 §fevocraft.ro";
                g.drawString(mc.font, infoText, Math.max(4, (width - mc.font.width(infoText)) / 2), 7, 0xFFFFFF, true);
            }
            case VOTE -> {
                String voteText = "🎁 §a/vote";
                g.drawString(mc.font, voteText, Math.max(4, (width - mc.font.width(voteText)) / 2), 7, 0xFFFFFF, true);
            }
        }
    }

    private static void drawHeader(GuiGraphics g, Minecraft mc, int width) {
        String title = "§lPLAY.EVOCRAFT.RO";
        g.drawString(mc.font, title, 5, 7, ACCENT_COLOR, true);
        String onlineText = width < 170 ? "ON: " + onlinePlayers : "👥 ONLINE: " + onlinePlayers;
        int onlineX = width - mc.font.width(onlineText) - 5;
        if (onlineX > 78) {
            g.drawString(mc.font, onlineText, onlineX, 7, 0xFFFFFF, true);
        }
    }

    private static void drawJobs(GuiGraphics g, Minecraft mc, int width) {
        g.drawString(mc.font, "💼 §a§lJobs", 5, 5, 0xFFFFFF, true);
        int jobY = 18;
        for (JobInfo job : ClientJobsData.activeJobs) {
            if (!job.isActive) continue;
            g.renderItem(job.icon, 5, jobY);
            g.drawString(mc.font, "§f" + job.name + " §7[Lv. " + job.level + "]", 26, jobY, 0xFFFFFF, true);

            int barWidth = Math.max(20, width - 35);
            int barX = 26;
            int barY = jobY + 12;
            g.fill(barX, barY, barX + barWidth, barY + 4, 0xFF111111);
            float progress = job.requiredXp > 0.0 ? (float) (job.currentXp / job.requiredXp) : 0.0f;
            g.fill(barX, barY, barX + (int) (barWidth * Math.min(1.0f, Math.max(0.0f, progress))), barY + 4, ACCENT_COLOR);

            g.pose().pushPose();
            g.pose().translate(barX + barWidth, barY + 5, 0);
            g.pose().scale(0.55f, 0.55f, 0.55f);
            String xpText = (int) job.currentXp + "/" + (int) job.requiredXp + " XP";
            g.drawString(mc.font, xpText, -mc.font.width(xpText), 0, 0xAAAAAA, false);
            g.pose().popPose();

            jobY += 28;
        }
    }

    private static String getRankText(Minecraft mc) {
        String myRank = "§7Member";
        if (CustomTabListHandler.syncedRanks.containsKey(mc.player.getUUID())) {
            myRank = CustomTabListHandler.applyRankColor(CustomTabListHandler.syncedRanks.get(mc.player.getUUID()));
        } else if (ClientStatsData.rank != null && !ClientStatsData.rank.isEmpty()) {
            myRank = CustomTabListHandler.applyRankColor(ClientStatsData.rank);
        }
        return myRank;
    }

    private static void drawOutline(GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + 1, color);
        g.fill(x, y + height - 1, x + width, y + height, color);
        g.fill(x, y, x + 1, y + height, color);
        g.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
