package org.evocraft.evohub.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evohub.Evohub;

import java.util.*;

@Mod.EventBusSubscriber(modid = Evohub.MODID, value = Dist.CLIENT)
public class HubNotificationHandler {

    public enum NotifType { JOIN, LEAVE, TPA, TPA_HERE, DEATH, EVENT }
    private static final Map<UUID, ResourceLocation> skinCache = new HashMap<>();
    private static final List<Notification> activeNotifications = new ArrayList<>();

    private static class Notification {
        String name; UUID uuid; NotifType type; String extraMsg; int ticks; int maxTicks;

        public Notification(String name, UUID uuid, NotifType type, String extraMsg, int customTicks) {
            this.name = name; this.uuid = uuid; this.type = type; this.extraMsg = extraMsg;
            if (type == NotifType.EVENT) this.maxTicks = customTicks;
            else if (type == NotifType.DEATH) this.maxTicks = 80;
            else if (type == NotifType.TPA || type == NotifType.TPA_HERE) this.maxTicks = 160;
            else this.maxTicks = 120;
            this.ticks = this.maxTicks;
        }
    }

    private static void addTpaNotification(String name, UUID uuid, boolean isHere) {
        NotifType type = isHere ? NotifType.TPA_HERE : NotifType.TPA;
        activeNotifications.add(new Notification(name, uuid, type, "", 0));
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        String raw = event.getMessage().getString();

        // INTERCEPTĂM TPA-UL INVIZIBIL TRIMIS DE SERVER
        if (raw.contains("~EVOTPA~")) {
            event.setCanceled(true); // ÎL ASCUNDEM DIN CHAT
            try {
                String data = raw.substring(raw.indexOf("~EVOTPA~") + 8);
                String[] parts = data.split(":");
                UUID uuid = UUID.fromString(parts[0]);
                String name = parts[1];
                boolean isHere = parts[2].equals("TPA_HERE");
                addTpaNotification(name, uuid, isHere);
            } catch (Exception ignored) {}
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                    skinCache.put(info.getProfile().getId(), info.getSkinLocation());
                }
            }
            Iterator<Notification> it = activeNotifications.iterator();
            while (it.hasNext()) {
                Notification n = it.next(); n.ticks--;
                if (n.ticks <= 0) it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type() || activeNotifications.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance(); GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth(); int yOffset = 20;

        for (Notification n : activeNotifications) {
            int boxHeight = 32; int boxWidth = (n.type == NotifType.TPA || n.type == NotifType.TPA_HERE) ? 220 : 160;
            float animProgress = n.ticks > n.maxTicks - 20 ? (n.maxTicks - n.ticks) / 20f : (n.ticks < 20 ? n.ticks / 20f : 1f);
            float easeProgress = 1f - (float) Math.pow(1f - animProgress, 3);
            int x = (int) (w - boxWidth + ((1f - easeProgress) * boxWidth)); int y = yOffset;
            int alphaHex = (int)(easeProgress * 255) << 24;

            int themeColor = 0xFFFFAA00;
            String subtitleText = n.type == NotifType.TPA ? "§eWants to teleport to you!" : "§eWants you to teleport to them!";

            g.fillGradient(x, y, x + boxWidth, y + boxHeight, 0xAA000000 | (alphaHex & 0xFF000000), 0xEE000000 | (alphaHex & 0xFF000000));
            g.fill(x, y, x + 3, y + boxHeight, themeColor | (alphaHex & 0xFF000000));

            g.drawString(mc.font, "§l" + n.name, x + 35, y + 6, 0xFFFFFF | alphaHex, true);
            g.drawString(mc.font, subtitleText, x + 35, y + 18, 0xAAAAAA | alphaHex, true);

            RenderSystem.enableBlend();
            ResourceLocation skin = skinCache.getOrDefault(n.uuid, net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(n.uuid));
            RenderSystem.setShaderTexture(0, skin);
            g.blit(skin, x + 8, y + 6, 20, 20, 8.0F, 8.0F, 8, 8, 64, 64);
            g.blit(skin, x + 8, y + 6, 20, 20, 40.0F, 8.0F, 8, 8, 64, 64);
            RenderSystem.disableBlend();

            yOffset += boxHeight + 5;
        }
    }
}
