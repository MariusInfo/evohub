package org.evocraft.evohub.client;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evoessentials.client.ClientHomeData;
import org.evocraft.evohub.Evohub;
import org.evocraft.evohub.gui.EvoCratesScreen;
import org.evocraft.evohub.gui.EvoHubScreen;
import org.evocraft.evohub.gui.EvoWarpScreen;
import org.evocraft.evohub.gui.HomeScreen;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Evohub.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientMenuCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {

        // Comanda /hub
        event.getDispatcher().register(Commands.literal("hub")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new EvoHubScreen()));
                    return 1;
                }));

        // Comenzi /warps și /warp
        event.getDispatcher().register(Commands.literal("warps")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new EvoWarpScreen(new EvoHubScreen())));
                    return 1;
                }));
        event.getDispatcher().register(Commands.literal("warp")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new EvoWarpScreen(new EvoHubScreen())));
                    return 1;
                }));

        // Comenzi /crates și /crate
        event.getDispatcher().register(Commands.literal("crates")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new EvoCratesScreen(new EvoHubScreen())));
                    return 1;
                }));
        event.getDispatcher().register(Commands.literal("crate")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new EvoCratesScreen(new EvoHubScreen())));
                    return 1;
                }));

        // Comenzi /homes și /home
        event.getDispatcher().register(Commands.literal("homes")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> {
                        List<String> homes = ClientHomeData.playerHomes;
                        mc.setScreen(new HomeScreen(homes != null ? homes : new ArrayList<>()));
                    });
                    return 1;
                }));
        event.getDispatcher().register(Commands.literal("home")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> {
                        List<String> homes = ClientHomeData.playerHomes;
                        mc.setScreen(new HomeScreen(homes != null ? homes : new ArrayList<>()));
                    });
                    return 1;
                }));
    }
}