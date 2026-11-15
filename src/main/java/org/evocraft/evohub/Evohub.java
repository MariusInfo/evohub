package org.evocraft.evohub;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent; // Adăugat pentru comenzi
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent; // Adăugat pentru event-ul comenzilor
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.evocraft.evohub.network.EvoHubPacketHandler;
import org.slf4j.Logger;

@Mod(Evohub.MODID)
public class Evohub {

    public static final String MODID = "evohub";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Evohub() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Înregistrăm faza de setup comun (pentru pachetele de rețea)
        modEventBus.addListener(this::commonSetup);

        // Înregistrăm clasa în Event Bus-ul Forge pentru a prinde evenimente generale
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[EvoHub] Initializing command center (UI & Packets)...");

            // AICI ÎNREGISTRĂM CANALUL DE REȚEA PENTRU MENIURI
            EvoHubPacketHandler.register();

            // MAGIA: Forțăm EvoCore să își bage pachetele pe țeavă direct din Hub!
            try {
                org.evocraft.evocore.network.PacketHandler.register();
                LOGGER.info("[EvoHub] Sincronizare pachete EvoCore -> SUCCES!");
            } catch (Exception e) {
                LOGGER.error("[EvoHub] Error syncing EvoCore packets!", e);
            }
        });
    }

    // ==============================================================
    // AICI INREGISTRAM COMENZILE (CRATE-URI, GIVEKEY ETC.)
    // ==============================================================
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        org.evocraft.evohub.EvoCratesCommand.register(event.getDispatcher());
        LOGGER.info("[EvoHub] Comenzile /evo crate si /evo givekey au fost inregistrate!");
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        EvoHubPacketHandler.shutdown();
    }
}
