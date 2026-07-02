package org.evocraft.evohub.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evohub.Evohub;
import org.evocraft.evohub.gui.*;

@Mod.EventBusSubscriber(modid = Evohub.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientMenuCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {

        // Comanda /hub
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("hub")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new EvoHubScreen()));
                    return 1;
                }));

        // Comenzi /kits și /kit
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("kits")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new EvoKitScreen(new EvoHubScreen())));
                    return 1;
                }));
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("kit")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new EvoKitScreen(new EvoHubScreen())));
                    return 1;
                }));

        // Comenzi /warps și /warp
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("warps")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new EvoWarpScreen(new EvoHubScreen())));
                    return 1;
                }));
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("warp")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new EvoWarpScreen(new EvoHubScreen())));
                    return 1;
                }));

        // Comenzi /crates și /crate
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("crates")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new EvoCratesScreen(new EvoHubScreen())));
                    return 1;
                }));
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("crate")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new EvoCratesScreen(new EvoHubScreen())));
                    return 1;
                }));

        // Comenzi /homes și /home
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("homes")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> {
                        java.util.List<String> homes = org.evocraft.evocore.client.ClientHomeData.playerHomes;
                        if (homes == null) homes = new java.util.ArrayList<>();
                        Minecraft.getInstance().setScreen(new HomeScreen(homes));
                    });
                    return 1;
                }));
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("home")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> {
                        java.util.List<String> homes = org.evocraft.evocore.client.ClientHomeData.playerHomes;
                        if (homes == null) homes = new java.util.ArrayList<>();
                        Minecraft.getInstance().setScreen(new HomeScreen(homes));
                    });
                    return 1;
                }));
    }
}