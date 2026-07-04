package org.evocraft.evohub.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evohub.gui.EvoHubScreen;
import org.evocraft.evohub.gui.EvoKitScreen;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "evohub", value = Dist.CLIENT)
public class ClientEvents {

    public static final KeyMapping HUB_KEY = new KeyMapping("Open Hub", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "EvoCraft");
    public static final KeyMapping HOME_KEY = new KeyMapping("My Homes", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_HOME, "EvoCraft");
    public static final KeyMapping TPA_KEY = new KeyMapping("Teleport Menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "EvoCraft");
    public static final KeyMapping KIT_KEY = new KeyMapping("Kits Menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "EvoCraft");

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
                if (TPA_KEY.consumeClick()) mc.setScreen(new EvoHubScreen(5));
                if (HOME_KEY.consumeClick()) mc.setScreen(new EvoHubScreen(6));
                if (KIT_KEY.consumeClick()) mc.setScreen(new EvoKitScreen(new EvoHubScreen()));
            }
        }
    }
}
