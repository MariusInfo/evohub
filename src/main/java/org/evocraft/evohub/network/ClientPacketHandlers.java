package org.evocraft.evohub.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.evocraft.evohub.client.ClientCratesData;
import org.evocraft.evohub.client.CustomTabListHandler;
import org.evocraft.evohub.client.JoinLeaveNotificationHandler;
import org.evocraft.evohub.gui.CrateAnimationScreen;
import org.evocraft.evohub.gui.CratePreviewScreen;
import org.evocraft.evohub.gui.EvoKitScreen;
import org.evocraft.evohub.gui.EvoWarpScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandlers {

    // Funcția care introduce rank-urile venite de la server
    public static void handleRanks(Map<UUID, String> ranks) {
        if (ranks == null) return;
        CustomTabListHandler.syncedRanks.clear();
        CustomTabListHandler.syncedRanks.putAll(ranks);
    }

    public static void handleCrates(Map<String, Integer> keys) {
        if (keys == null) return;
        ClientCratesData.playerKeys = keys;
        ClientCratesData.needsRefresh = true;
    }

    public static void playAnimation(String crateName, ItemStack winningItem, List<ItemStack> previewItems) {
        if (previewItems == null || previewItems.isEmpty() || winningItem == null || winningItem.isEmpty()) return;

        List<ItemStack> rouletteItems = new ArrayList<>();
        Random rand = new Random();

        for (int i = 0; i < 50; i++) {
            rouletteItems.add(previewItems.get(rand.nextInt(previewItems.size())));
        }

        int winningIndex = 40;
        rouletteItems.set(winningIndex, winningItem);

        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new CrateAnimationScreen(crateName != null ? crateName : "", rouletteItems, winningIndex));
    }

    public static void openPreview(String crateName, List<ItemStack> items, List<Integer> chances) {
        if (items == null || chances == null) return;
        Minecraft.getInstance().setScreen(new CratePreviewScreen(crateName != null ? crateName : "", items, chances));
    }

    public static void handleWarps(List<String> warpNames, List<ItemStack> warpIcons) {
        List<EvoWarpScreen.WarpInfo> clientWarps = new ArrayList<>();
        if (warpNames != null && warpIcons != null) {
            int count = Math.min(warpNames.size(), warpIcons.size());
            for (int i = 0; i < count; i++) {
                clientWarps.add(new EvoWarpScreen.WarpInfo(warpNames.get(i), warpIcons.get(i)));
            }
        }
        EvoWarpScreen.loadedWarps = clientWarps;
        EvoWarpScreen.isLoading = false;
    }

    public static void handleKits(List<String> names, List<String> perms, List<Integer> cds, List<Integer> colors, List<List<ItemStack>> items) {
        List<EvoKitScreen.KitInfo> clientKits = new ArrayList<>();
        if (names != null && perms != null && cds != null && colors != null && items != null) {
            int count = Math.min(names.size(), Math.min(perms.size(), Math.min(cds.size(), Math.min(colors.size(), items.size()))));
            for (int i = 0; i < count; i++) {
                List<ItemStack> kitItems = items.get(i);
                ItemStack[] itemArr = kitItems != null ? kitItems.toArray(new ItemStack[0]) : new ItemStack[0];
                String kName = names.get(i) != null ? names.get(i) : "";
                clientKits.add(new EvoKitScreen.KitInfo(
                        kName, "Kit " + kName, perms.get(i), cds.get(i) + " Hours", colors.get(i), itemArr
                ));
            }
        }
        EvoKitScreen.loadedKits = clientKits;
        EvoKitScreen.isLoading = false;
    }

    // Funcția care randează notificarea de Join/Leave pe ecran
    public static void handleJoinLeave(String playerName, UUID playerUuid, boolean isJoin) {
        JoinLeaveNotificationHandler.addNotification(playerName, playerUuid, isJoin);
    }
}
