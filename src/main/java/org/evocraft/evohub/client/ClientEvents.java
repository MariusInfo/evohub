package org.evocraft.evohub.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evohub.gui.EvoHubScreen;
import org.evocraft.evohub.gui.EvoKitScreen;
import org.evocraft.evohub.gui.EvoWarpScreen;
import org.evocraft.evokits.network.EvoKitsPacketHandler;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = "evohub", value = Dist.CLIENT)
public class ClientEvents {

    public static final KeyMapping HUB_KEY = new KeyMapping("Open Hub", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "EvoCraft");
    public static final KeyMapping HOME_KEY = new KeyMapping("My Homes", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_HOME, "EvoCraft");
    public static final KeyMapping TPA_KEY = new KeyMapping("Teleport Menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "EvoCraft");
    public static final KeyMapping KIT_KEY = new KeyMapping("Kits Menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "EvoCraft");

    public static void openKitsScreen(Minecraft mc, Screen fallbackParent) {
        if (mc == null) return;
        if (ModList.get().isLoaded("evokits") && mc.getConnection() != null && mc.player != null) {
            EvoKitsPacketHandler.INSTANCE.sendToServer(new EvoKitsPacketHandler.C2S_RequestKitsPacket(true));
            return;
        }
        mc.setScreen(new EvoKitScreen(fallbackParent != null ? fallbackParent : new EvoHubScreen()));
    }

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(HUB_KEY);
        event.register(HOME_KEY);
        event.register(TPA_KEY);
        event.register(KIT_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                if (HUB_KEY.consumeClick()) mc.setScreen(new EvoHubScreen());
                if (HOME_KEY.consumeClick()) mc.setScreen(new EvoHubScreen(6));
                if (TPA_KEY.consumeClick()) mc.setScreen(new EvoHubScreen(5));
                if (KIT_KEY.consumeClick()) openKitsScreen(mc, new EvoHubScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        CustomTabListHandler.syncedRanks.clear();
        CustomTabListHandler.lastRankSync = 0;
        ClientCratesData.playerKeys.clear();
        ClientCratesData.needsRefresh = false;
        EvoKitScreen.loadedKits = new ArrayList<>();
        EvoKitScreen.isLoading = true;
        EvoKitScreen.currentPage = 0;
        EvoWarpScreen.loadedWarps = new ArrayList<>();
        EvoWarpScreen.isLoading = true;
        EvoWarpScreen.currentPage = 0;
        EvoHubScreen.currentTopList.clear();
        EvoHubScreen.currentServerName = "Identifying...";
        EvoHubScreen.isLoadingTop = false;
        JoinLeaveNotificationHandler.reset();
    }
}
