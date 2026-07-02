package org.evocraft.evohub.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.client.ClientVanishData;
import org.evocraft.evocore.client.ClientStatsData;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "evohub", value = Dist.CLIENT)
public class CustomTabListHandler {

    private static final int COLUMNS = 4;
    private static final int ROWS = 5;
    private static final int PLAYERS_PER_PAGE = COLUMNS * ROWS;
    private static final int PAGE_DURATION_MS = 5000;

    public static final Map<UUID, String> syncedRanks = new HashMap<>();
    public static long lastRankSync = 0;

    // Păstrăm funcțiile de bază, dar le vom folosi mai modern jos
    private static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private static void outlineRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (System.currentTimeMillis() - lastRankSync > 30000) {
            lastRankSync = System.currentTimeMillis();
            org.evocraft.evohub.network.EvoHubPacketHandler.INSTANCE.sendToServer(new org.evocraft.evohub.network.EvoHubPacketHandler.C2S_RequestRanks());
        }
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        event.setResult(Event.Result.DENY);

        double distanceSqr = mc.getEntityRenderDispatcher().distanceToSqr(player);
        if (distanceSqr > 4096.0D) return;

        PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
        String coloredRank = "§7Membru";

        if (syncedRanks.containsKey(player.getUUID())) {
            coloredRank = applyRankColor(syncedRanks.get(player.getUUID()));
        } else if (info != null) {
            coloredRank = getColoredPrefix(info);
        }

        String colorCode = "§7";
        if (coloredRank.length() >= 2 && coloredRank.charAt(0) == '§') {
            colorCode = coloredRank.substring(0, 2);
        }

        Component nameComp = Component.literal(colorCode + player.getGameProfile().getName());
        Component rankComp = Component.literal(coloredRank);

        boolean isSneaking = player.isCrouching();
        float heightOffset = player.getNameTagOffsetY();
        if (isSneaking) heightOffset -= 0.25f;

        com.mojang.blaze3d.vertex.PoseStack poseStack = event.getPoseStack();
        net.minecraft.client.renderer.MultiBufferSource buffer = event.getMultiBufferSource();
        net.minecraft.client.gui.Font font = mc.font;

        poseStack.pushPose();
        poseStack.translate(0.0F, heightOffset + 0.3F, 0.0F);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        org.joml.Matrix4f matrix4f = poseStack.last().pose();
        float bgOpacity = mc.options.getBackgroundOpacity(0.25F);
        int bgColor = (int) (bgOpacity * 255.0F) << 24;

        net.minecraft.client.gui.Font.DisplayMode displayMode = isSneaking
                ? net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH
                : net.minecraft.client.gui.Font.DisplayMode.NORMAL;

        float nameWidth = font.width(nameComp);
        float rankWidth = font.width(rankComp);
        float nameX = -nameWidth / 2;
        float rankX = -rankWidth / 2;

        font.drawInBatch(nameComp, nameX, 0f, 0x20FFFFFF, false, matrix4f, buffer, displayMode, bgColor, event.getPackedLight());
        font.drawInBatch(nameComp, nameX, 0f, isSneaking ? 0xFFFFAA : -1, false, matrix4f, buffer, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, event.getPackedLight());

        font.drawInBatch(rankComp, rankX, 10f, 0x20FFFFFF, false, matrix4f, buffer, displayMode, bgColor, event.getPackedLight());
        font.drawInBatch(rankComp, rankX, 10f, isSneaking ? 0xFFFFAA : -1, false, matrix4f, buffer, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, event.getPackedLight());

        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.PLAYER_LIST.type()) {
            event.setCanceled(true);
            if (Minecraft.getInstance().options.keyPlayerList.isDown()) {
                renderCustomTab(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
            }
        }
    }

    private static void renderCustomTab(GuiGraphics g, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        Collection<PlayerInfo> players = mc.getConnection().getOnlinePlayers();
        if (players.isEmpty()) return;

        List<PlayerInfo> sortedPlayers = players.stream()
                .filter(CustomTabListHandler::isValidPlayer)
                .sorted(Comparator.<PlayerInfo, Integer>comparing(p -> getRankWeight(getCleanRank(p))).reversed()
                        .thenComparing(p -> p.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (sortedPlayers.isEmpty()) return;

        double guiScale = mc.getWindow().getGuiScale();
        float currentScale = 1.0f;
        if (guiScale >= 3) currentScale = 0.85f;
        if (guiScale >= 4) currentScale = 0.65f;

        int unscaledSw = (int) (sw / currentScale);
        int unscaledSh = (int) (sh / currentScale);

        int totalPlayers = sortedPlayers.size();
        int totalPages = (int) Math.ceil((double) totalPlayers / PLAYERS_PER_PAGE);
        int currentPage = 0;
        if (totalPages > 1) {
            currentPage = (int) ((System.currentTimeMillis() / PAGE_DURATION_MS) % totalPages);
        }

        int startIndex = currentPage * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, totalPlayers);
        List<PlayerInfo> pagePlayers = sortedPlayers.subList(startIndex, endIndex);

        int cellWidth = 150;
        int cellHeight = 32;
        int gap = 4;

        int totalWidth = COLUMNS * cellWidth + (COLUMNS - 1) * gap;
        int totalHeight = ROWS * cellHeight + (ROWS - 1) * gap;

        int startX = (unscaledSw - totalWidth) / 2;
        int startY = (unscaledSh - totalHeight) / 2;

        g.pose().pushPose();
        g.pose().scale(currentScale, currentScale, 1.0f);

        // HEADER MODERNISED - Separat de corpul principal
        fillRounded(g, startX - 10, startY - 38, totalWidth + 20, 26, 0xEE0D140D);
        g.fill(startX - 10, startY - 12, startX + totalWidth + 10, startY - 10, 0xFF83B755); // O linie subțire accent verde

        // BACKGROUND PRINCIPAL LISTĂ JUCĂTORI (mai curat, fără contur gros, sticlă fumurie)
        fillRounded(g, startX - 10, startY - 5, totalWidth + 20, totalHeight + 35, 0xCC050805);

        String headerText = "EVO CRAFT";
        int headerY = startY - 29;
        int textWidth = mc.font.width(headerText);
        int currentTextX = startX + (totalWidth - textWidth) / 2;

        long time = System.currentTimeMillis();
        boolean isGlitchMoment = (time % 2500) < 200;
        boolean isMiniGlitch = (time % 800) < 50;

        for (int i = 0; i < headerText.length(); i++) {
            char originalChar = headerText.charAt(i);
            char displayChar = originalChar;
            int color = 0xFF83B755;
            int offsetX = 0;
            int offsetY = 0;

            if ((isGlitchMoment || isMiniGlitch) && Math.random() > 0.4) {
                double rand = Math.random();
                if (rand < 0.3) color = 0xFFFFFFFF;
                else if (rand < 0.6) color = 0xFF6C9945;
                else color = 0xFF3A592D;
                offsetX = (int) (Math.random() * 3) - 1;
                offsetY = (int) (Math.random() * 3) - 1;
                if (isGlitchMoment && Math.random() > 0.7 && originalChar != ' ') {
                    String symbols = "@#$%&*<>?!";
                    displayChar = symbols.charAt((int)(Math.random() * symbols.length()));
                }
            }

            g.drawString(mc.font, String.valueOf(displayChar), currentTextX + offsetX, headerY + offsetY, color, true);
            currentTextX += mc.font.width(String.valueOf(originalChar));
        }

        // Randare celule jucători
        for (int i = 0; i < pagePlayers.size(); i++) {
            PlayerInfo info = pagePlayers.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = startX + col * (cellWidth + gap);
            int y = startY + row * (cellHeight + gap);
            drawPlayerCell(g, mc, x, y, cellWidth, cellHeight, info);
        }

        // FOOTER
        if (totalPages > 1) {
            String footerText = "Pagina: " + (currentPage + 1) + " / " + totalPages + " (Auto-schimbare 5s)";
            int pulseAlpha = (int) (155 + 100 * Math.sin(time / 300.0));
            int footerColor = (pulseAlpha << 24) | 0x83B755;
            g.drawCenteredString(mc.font, footerText, unscaledSw / 2, startY + totalHeight + 12, footerColor);
        } else {
            g.drawCenteredString(mc.font, "§7Jucători Online: §a" + totalPlayers, unscaledSw / 2, startY + totalHeight + 12, 0xFFFFFF);
        }

        g.pose().popPose();
    }

    private static boolean isValidPlayer(PlayerInfo info) {
        return info != null
                && info.getProfile().getName() != null
                && info.getProfile().getId().version() != 2
                && !ClientVanishData.vanishedPlayers.contains(info.getProfile().getId());
    }

    public static String applyRankColor(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) return "§7Membru";
        prefix = prefix.replace('&', '§');

        if (prefix.contains("§")) return prefix;

        String lower = prefix.toLowerCase();
        if (lower.contains("fondator")) return "§4" + prefix;
        if (lower.contains("supervizor")) return "§c" + prefix;
        if (lower.contains("admin")) return "§c" + prefix;
        if (lower.contains("mod")) return "§5" + prefix;
        if (lower.contains("builder")) return "§e" + prefix;
        if (lower.contains("helper")) return "§9" + prefix;
        if (lower.contains("youtuber") || lower.contains("yt")) return "§c" + prefix;
        if (lower.contains("tiktoker") || lower.contains("tk")) return "§d" + prefix;
        if (lower.contains("legend")) return "§6" + prefix;
        if (lower.contains("ultra")) return "§b" + prefix;
        if (lower.contains("titan")) return "§1" + prefix;
        if (lower.contains("mvp")) return "§3" + prefix;
        if (lower.contains("vip+") || lower.contains("vipplus")) return "§a" + prefix;
        if (lower.contains("vip")) return "§a" + prefix;

        return "§7" + prefix;
    }

    private static String getColoredPrefix(PlayerInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (info.getProfile() == null || info.getProfile().getName() == null) return "§7Membru";

        String rawName = info.getProfile().getName();
        UUID uuid = info.getProfile().getId();

        if (syncedRanks.containsKey(uuid)) {
            return applyRankColor(syncedRanks.get(uuid));
        }

        if (mc.player != null && uuid.equals(mc.player.getUUID())) {
            if (ClientStatsData.rank != null && !ClientStatsData.rank.isEmpty()) {
                return applyRankColor(ClientStatsData.rank);
            }
        }

        String foundPrefix = "";

        if (mc.level != null) {
            net.minecraft.world.scores.PlayerTeam team = mc.level.getScoreboard().getPlayersTeam(rawName);
            if (team != null && team.getPlayerPrefix() != null) {
                foundPrefix = team.getPlayerPrefix().getString().replaceAll("\\[|\\]", "").trim();
            }
        }

        if (foundPrefix.isEmpty() && info.getTabListDisplayName() != null) {
            String displayName = info.getTabListDisplayName().getString();
            if (displayName.contains(rawName)) {
                int idx = displayName.indexOf(rawName);
                if (idx > 0) {
                    foundPrefix = displayName.substring(0, idx).replaceAll("\\[|\\]", "").trim();
                }
            }
        }

        if (foundPrefix.isEmpty() && info.getTeam() != null && info.getTeam().getPlayerPrefix() != null) {
            foundPrefix = info.getTeam().getPlayerPrefix().getString().replaceAll("\\[|\\]", "").trim();
        }

        if (foundPrefix.isEmpty()) {
            foundPrefix = "Membru";
        }

        return applyRankColor(foundPrefix);
    }

    private static String getCleanRank(PlayerInfo info) {
        String raw = getColoredPrefix(info).replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("\\[|\\]", "").trim();
        if (raw.isEmpty()) return "membru";
        return raw;
    }

    private static void drawPlayerCell(GuiGraphics g, Minecraft mc, int x, int y, int w, int h, PlayerInfo info) {
        // DESIGN MODERN PENTRU CELULE (Card Style / Glass)
        g.fillGradient(x, y, x + w, y + h, 0x88000000, 0x55000000); // Fundal semi-transparent elegant
        g.fill(x, y, x + 2, y + h, 0xFF83B755); // Bară de accent verde pe laterala stângă

        RenderSystem.enableBlend();
        ResourceLocation skin = info.getSkinLocation();
        RenderSystem.setShaderTexture(0, skin);
        int headSize = 24;
        int headX = x + 6;
        int headY = y + 4;

        // Randăm capul jucătorului
        g.blit(skin, headX, headY, headSize, headSize, 8.0F, 8.0F, 8, 8, 64, 64);
        g.blit(skin, headX, headY, headSize, headSize, 40.0F, 8.0F, 8, 8, 64, 64);
        RenderSystem.disableBlend();

        String playerName = info.getProfile().getName();
        String coloredRank = getColoredPrefix(info);

        int textX = headX + headSize + 6;
        int maxNameWidth = w - 38 - 30; // Spațiu redus pentru a nu se suprapune cu ping-ul

        String renderName = playerName;
        if (mc.font.width(renderName) > maxNameWidth) {
            renderName = mc.font.plainSubstrByWidth(renderName, maxNameWidth - 5) + "...";
        }

        // Numele jucătorului (ușor mai jos pentru a fi centrat cu iconița)
        g.drawString(mc.font, renderName, textX, y + 6, 0xFFFFFF, true);

        // Rank-ul sub nume (cu font mai mic)
        g.pose().pushPose();
        g.pose().translate(textX, y + 18, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.drawString(mc.font, coloredRank, 0, 0, 0xFFFFFF, true);
        g.pose().popPose();

        // PING text curat
        int ping = info.getLatency();
        String pingText = ping + "ms";
        int pingColor = 0xFF83B755; // Verde
        if (ping >= 150 && ping < 300) pingColor = 0xFFFFAA00; // Galben
        else if (ping >= 300) pingColor = 0xFFFF5555; // Roșu

        int pingWidth = mc.font.width(pingText);
        g.drawString(mc.font, pingText, x + w - pingWidth - 5, y + 12, pingColor, true); // Centrat vertical pe axa dreaptă
    }

    private static int getRankWeight(String rawRankName) {
        if (rawRankName == null || rawRankName.isEmpty()) return 1;
        String lower = rawRankName.toLowerCase();

        if (lower.contains("fondator")) return 100;
        if (lower.contains("supervizor")) return 90;
        if (lower.contains("admin")) return 80;
        if (lower.contains("mod")) return 70;
        if (lower.contains("builder")) return 65;
        if (lower.contains("helper")) return 60;
        if (lower.contains("youtuber") || lower.contains("yt")) return 55;
        if (lower.contains("tiktoker") || lower.contains("tk")) return 54;
        if (lower.contains("legend")) return 37;
        if (lower.contains("ultra")) return 36;
        if (lower.contains("titan")) return 35;
        if (lower.contains("mvp")) return 30;
        if (lower.contains("vip+") || lower.contains("vipplus")) return 20;
        if (lower.contains("vip")) return 10;

        return 1;
    }
}