package org.evocraft.evohub.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evohub.Evohub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Evohub.MODID, value = Dist.CLIENT)
public class JoinLeaveNotificationHandler {

    // Am adăugat SWITCH pentru schimbarea serverului!
    public enum NotifType { JOIN, LEAVE, TPA, TPA_HERE, DEATH, EVENT, SWITCH }

    private static final Map<UUID, ResourceLocation> skinCache = new HashMap<>();

    private static class Notification {
        String name;
        UUID uuid;
        NotifType type;
        String extraMsg;
        int ticks;
        int maxTicks;

        public Notification(String name, UUID uuid, NotifType type, String extraMsg, int customDurationTicks) {
            this.name = name;
            this.uuid = uuid;
            this.type = type;
            this.extraMsg = extraMsg;

            if (type == NotifType.EVENT) {
                this.maxTicks = customDurationTicks;
            } else if (type == NotifType.DEATH) {
                this.maxTicks = 80;
            } else if (type == NotifType.TPA || type == NotifType.TPA_HERE) {
                this.maxTicks = 160;
            } else {
                this.maxTicks = 120;
            }

            this.ticks = this.maxTicks;
        }
    }

    private static final List<Notification> activeNotifications = new ArrayList<>();

    public static void addNotification(String name, UUID uuid, boolean isJoin) {
        NotifType type = isJoin ? NotifType.JOIN : NotifType.LEAVE;
        if (isJoin) updateSkinCache(uuid);

        for (Notification n : activeNotifications) {
            if (n.name.equals(name) && n.type == type && n.ticks > 100) return;
        }
        activeNotifications.add(new Notification(name, uuid, type, "", 0));
    }

    private static void addTpaNotification(String name, UUID uuid, boolean isHere) {
        NotifType type = isHere ? NotifType.TPA_HERE : NotifType.TPA;
        for (Notification n : activeNotifications) {
            if (n.name.equals(name) && n.type == type && n.ticks > 100) return;
        }
        activeNotifications.add(new Notification(name, uuid, type, "", 0));
    }

    private static void addDeathNotification(String name, String fullMsg) {
        UUID uuid = guessUuid(name);
        activeNotifications.add(new Notification(name, uuid, NotifType.DEATH, fullMsg, 0));
    }

    private static void addEventNotification(String title, String subtitle, int durationTicks) {
        activeNotifications.add(new Notification(title, UUID.randomUUID(), NotifType.EVENT, subtitle, durationTicks));
    }

    // Funcția care creează notificarea când jucătorul schimbă secțiunea
    private static void addSwitchNotification(String name, UUID uuid, String serverName) {
        updateSkinCache(uuid);
        for (Notification n : activeNotifications) {
            if (n.name.equals(name) && n.type == NotifType.SWITCH && n.ticks > 100) return;
        }
        activeNotifications.add(new Notification(name, uuid, NotifType.SWITCH, serverName, 0));
    }

    private static UUID guessUuid(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info.getProfile().getName().equalsIgnoreCase(name)) {
                    return info.getProfile().getId();
                }
            }
        }
        return UUID.randomUUID();
    }

    private static void updateSkinCache(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                skinCache.put(uuid, info.getSkinLocation());
            }
        }
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        Component msg = event.getMessage();
        String raw = msg.getString();

        // DECESE
        if (raw.contains("[☠]")) {
            event.setCanceled(true);
            String clean = raw.replaceAll("§.", "");
            String[] parts = clean.split(" ");
            String playerName = parts.length > 1 ? parts[1] : "A player";
            addDeathNotification(playerName, raw);
            return;
        }

        if (msg.getContents() instanceof TranslatableContents trans) {
            String key = trans.getKey();
            if (key.startsWith("death.")) {
                event.setCanceled(true);
                String fullMsg = msg.getString();
                String playerName = fullMsg.split(" ")[0];
                addDeathNotification(playerName, "§c" + fullMsg);
                return;
            }
            if (key.equals("multiplayer.player.joined") || key.equals("multiplayer.player.left") || key.equals("multiplayer.player.joined.renamed")) {
                event.setCanceled(true);
            }
        }

        // --- INTERCEPTARE SWITCH SERVER (Gamemode) ---
        // Ex: Trimite din proxy mesajul ~EVOSWITCH~:Notch:Creative
        if (raw.contains("~EVOSWITCH~")) {
            event.setCanceled(true);
            try {
                String data = raw.substring(raw.indexOf("~EVOSWITCH~") + 11);
                String[] parts = data.split(":");
                String name = parts[0];
                String server = parts[1];
                addSwitchNotification(name, guessUuid(name), server);
            } catch (Exception ignored) {}
            return;
        }

        // --- INTERCEPTARE TPA ---
        if (raw.contains("~EVOTPA~")) {
            event.setCanceled(true);
            try {
                String data = raw.substring(raw.indexOf("~EVOTPA~") + 8);
                String[] parts = data.split(":");
                UUID uuid = UUID.fromString(parts[0]);
                String name = parts[1];
                boolean isHere = parts[2].equals("TPA_HERE");
                addTpaNotification(name, uuid, isHere);
            } catch (Exception ignored) {}
        }

        // --- INTERCEPTARE EVENIMENTE ASCUNSE ---
        if (raw.contains("~EVOEVENT~")) {
            event.setCanceled(true); // OPRIM INSTANT AFIȘAREA ÎN CHAT!

            if (raw.contains("~STOP")) {
                for (Notification n : activeNotifications) {
                    if (n.type == NotifType.EVENT) {
                        n.ticks = Math.min(n.ticks, 20); // Declanșează animația de dispariție
                    }
                }
                return;
            }

            try {
                String data = raw.substring(raw.indexOf("~EVOEVENT~") + 10);
                String[] parts = data.split(":", 3);
                String type = parts[0];
                int durationMins = Integer.parseInt(parts[1]);
                String text = parts[2];

                int durationTicks = durationMins * 60 * 20;

                if (type.equals("JOB")) {
                    addEventNotification("§d§lEVO BOOST", text, durationTicks);
                } else if (type.equals("CHAT") || type.equals("SCRAMBLE")) {
                    addEventNotification("§lEVO RUSH", text, durationTicks);
                }
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
                Notification n = it.next();
                n.ticks--;
                if (n.ticks <= 0) it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (activeNotifications.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();

        int yOffset = 20;

        for (Notification n : activeNotifications) {
            int boxHeight = (n.type == NotifType.DEATH || n.type == NotifType.EVENT) ? 24 : 34; // Ușor mai înalt pt simetrie
            int boxWidth = 160;

            if (n.type == NotifType.TPA || n.type == NotifType.TPA_HERE) {
                boxWidth = 220;
            } else if (n.type == NotifType.DEATH) {
                boxWidth = mc.font.width(n.extraMsg) + 40;
            } else if (n.type == NotifType.EVENT) {
                boxWidth = Math.max(mc.font.width(n.name), mc.font.width(n.extraMsg)) + 40;
            } else if (n.type == NotifType.SWITCH) {
                boxWidth = 180;
            }

            float animProgress;
            if (n.ticks > n.maxTicks - 20) animProgress = (n.maxTicks - n.ticks) / 20f;
            else if (n.ticks < 20) animProgress = n.ticks / 20f;
            else animProgress = 1f;

            float easeProgress = 1f - (float) Math.pow(1f - animProgress, 3);
            float slideOffset = (1f - easeProgress) * boxWidth;
            int x = (int) (w - boxWidth + slideOffset) - 5; // -5 pentru o distanță mică față de marginea ecranului
            int y = yOffset;

            int alphaHex = (int)(easeProgress * 255) << 24;

            // Culori de fundal stil Glassmorphism
            int bgAlphaStart = (int)(easeProgress * 180) << 24; // 180 pt o transparență elegantă
            int bgAlphaEnd = (int)(easeProgress * 120) << 24;

            int themeColor = 0xFFFFFFFF;
            String subtitleText = "";

            if (n.type == NotifType.JOIN) {
                themeColor = 0xFF83B755; subtitleText = "§aJoined the server"; // Tematica verde evo
            } else if (n.type == NotifType.LEAVE) {
                themeColor = 0xFFFF5555; subtitleText = "§cLeft the server";
            } else if (n.type == NotifType.SWITCH) {
                themeColor = 0xFF55FFFF; subtitleText = "§bJoined " + n.extraMsg;
            } else if (n.type == NotifType.TPA) {
                themeColor = 0xFFFFAA00; subtitleText = "§eWants to teleport to you!";
            } else if (n.type == NotifType.TPA_HERE) {
                themeColor = 0xFFFFAA00; subtitleText = "§eWants you to teleport to them!";
            } else if (n.type == NotifType.DEATH) {
                themeColor = 0xFFFF2222;
            } else if (n.type == NotifType.EVENT) {
                themeColor = 0xFFE055FF; // Mov pentru event
            }

            // Randare fundal elegant
            g.fillGradient(x, y, x + boxWidth, y + boxHeight, bgAlphaStart | 0x050805, bgAlphaEnd | 0x050805);

            // Doar o bară subțire de accent în stânga, fără contur pe toate părțile
            g.fill(x, y, x + 2, y + boxHeight, themeColor | (alphaHex & 0xFF000000));

            if (n.type == NotifType.DEATH) {
                // Aliniere pe centru vertical pentru decese
                g.drawString(mc.font, n.extraMsg, x + 30, y + 8, 0xFFFFFF | alphaHex, true);

                RenderSystem.enableBlend();
                ResourceLocation skin = getSkin(n.uuid);
                RenderSystem.setShaderTexture(0, skin);
                int headSize = 16;
                g.blit(skin, x + 8, y + 4, headSize, headSize, 8.0F, 8.0F, 8, 8, 64, 64);
                g.blit(skin, x + 8, y + 4, headSize, headSize, 40.0F, 8.0F, 8, 8, 64, 64);
                RenderSystem.disableBlend();

            } else if (n.type == NotifType.EVENT) {
                g.drawCenteredString(mc.font, n.name, x + boxWidth/2, y + 4, themeColor | alphaHex);
                g.drawCenteredString(mc.font, n.extraMsg, x + boxWidth/2, y + 14, 0xFFFFFF | alphaHex);
            } else {
                // Pentru Join/Leave/Tpa/Switch: text mai aranjat și iconiță mai frumos poziționată
                g.drawString(mc.font, "§l" + n.name, x + 38, y + 6, 0xFFFFFF | alphaHex, true);
                g.drawString(mc.font, subtitleText, x + 38, y + 19, 0xAAAAAA | alphaHex, true);

                RenderSystem.enableBlend();
                ResourceLocation skin = getSkin(n.uuid);
                RenderSystem.setShaderTexture(0, skin);
                int headSize = 22; // Puțin mai mare
                int headY = y + (boxHeight - headSize) / 2; // Perfect centrat pe Y

                g.blit(skin, x + 10, headY, headSize, headSize, 8.0F, 8.0F, 8, 8, 64, 64);
                g.blit(skin, x + 10, headY, headSize, headSize, 40.0F, 8.0F, 8, 8, 64, 64);
                RenderSystem.disableBlend();
            }

            yOffset += boxHeight + 6; // Puțin mai mult spațiu între ele
        }
    }

    private static ResourceLocation getSkin(UUID uuid) {
        if (skinCache.containsKey(uuid)) {
            return skinCache.get(uuid);
        }
        return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(uuid);
    }
}
