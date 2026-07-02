package org.evocraft.evohub.client;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evohub.gui.EvoHubScreen;

@Mod.EventBusSubscriber(modid = "evohub", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientTpaCommand {

    @SubscribeEvent
    public static void onClientCommandRegister(RegisterClientCommandsEvent event) {
        // Înregistrăm o comandă strict pe client
        event.getDispatcher().register(
                Commands.literal("tpa")
                        .executes(context -> {
                            // Când jucătorul scrie DOAR /tpa, deschidem meniul EvoHub la tab-ul 5 (Teleportare)
                            Minecraft.getInstance().tell(() -> {
                                Minecraft.getInstance().setScreen(new EvoHubScreen(5));
                            });
                            return 1;
                        })
        );
    }
}