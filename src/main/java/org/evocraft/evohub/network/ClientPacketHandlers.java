package org.evocraft.evohub.network;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.evocraft.evohub.client.ClientCratesData;
import org.evocraft.evohub.gui.CrateAnimationScreen;
import org.evocraft.evohub.gui.EvoWarpScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandlers {

    // Funcția care introduce rank-urile venite de la server
    public static void handleRanks(Map<java.util.UUID, String> ranks) {
        org.evocraft.evohub.client.CustomTabListHandler.syncedRanks.putAll(ranks);
    }

    public static void handleCrates(Map<String, Integer> keys) {
        ClientCratesData.playerKeys = keys;
        ClientCratesData.needsRefresh = true;
    }

    public static void playAnimation(String crateName, ItemStack winningItem, List<ItemStack> previewItems) {
        if (previewItems.isEmpty()) return;

        List<ItemStack> rouletteItems = new ArrayList<>();
        java.util.Random rand = new java.util.Random();

        for (int i = 0; i < 50; i++) {
            rouletteItems.add(previewItems.get(rand.nextInt(previewItems.size())));
        }

        int winningIndex = 40;
        rouletteItems.set(winningIndex, winningItem);

        net.minecraft.client.Minecraft.getInstance().setScreen(
                new CrateAnimationScreen(crateName, rouletteItems, winningIndex)
        );
    }
    public static void openPreview(String crateName, List<ItemStack> items, List<Integer> chances) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new org.evocraft.evohub.gui.CratePreviewScreen(crateName, items, chances));
    }

    public static void handleWarps(List<String> warpNames, List<ItemStack> warpIcons) {
        List<EvoWarpScreen.WarpInfo> clientWarps = new ArrayList<>();
        for (int i = 0; i < warpNames.size(); i++) {
            clientWarps.add(new EvoWarpScreen.WarpInfo(warpNames.get(i), warpIcons.get(i)));
        }
        EvoWarpScreen.loadedWarps = clientWarps;
        EvoWarpScreen.isLoading = false;
    }
    public static void handleKits(List<String> names, List<String> perms, List<Integer> cds, List<Integer> colors, List<List<ItemStack>> items) {
        List<org.evocraft.evohub.gui.EvoKitScreen.KitInfo> clientKits = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            ItemStack[] itemArr = items.get(i).toArray(new ItemStack[0]);
            clientKits.add(new org.evocraft.evohub.gui.EvoKitScreen.KitInfo(
                    names.get(i), "Kit " + names.get(i), perms.get(i), cds.get(i) + " Hours", colors.get(i), itemArr
            ));
        }
        org.evocraft.evohub.gui.EvoKitScreen.loadedKits = clientKits;
        org.evocraft.evohub.gui.EvoKitScreen.isLoading = false;
    }

    // Funcția care randează notificarea de Join/Leave pe ecran
    public static void handleJoinLeave(String playerName, java.util.UUID playerUuid, boolean isJoin) {
        org.evocraft.evohub.client.JoinLeaveNotificationHandler.addNotification(playerName, playerUuid, isJoin);
    }
}
