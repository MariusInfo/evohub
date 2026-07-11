package org.evocraft.evohub.crates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

public class CrateManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Locația unde se vor salva crate-urile: folderul "config/evocrates" din server
    private static final Path CRATES_DIR = FMLPaths.CONFIGDIR.get().resolve("evocrates");

    public static void init() {
        File dir = CRATES_DIR.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    // Metoda pentru /evo crate create <nume>
    public static boolean createCrateFile(String crateName) {
        init();
        File file = CRATES_DIR.resolve(crateName.toLowerCase() + ".json").toFile();
        if (file.exists()) return false; // Crate-ul exista deja

        JsonObject crateObj = new JsonObject();
        crateObj.addProperty("name", crateName);
        crateObj.add("rewards", new JsonArray());

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(crateObj, writer);
            return true;
        } catch (Exception e) {
            LOGGER.error("[EvoCrates] Error creating JSON file for " + crateName, e);
            return false;
        }
    }

    // Metoda pentru /evo crate addreward <crate> <sansa>
    public static boolean addRewardToFile(String crateName, ItemStack item, int chance) {
        init();
        File file = CRATES_DIR.resolve(crateName.toLowerCase() + ".json").toFile();
        if (!file.exists()) return false; // Crate-ul nu exista

        try (FileReader reader = new FileReader(file)) {
            JsonObject crateObj = GSON.fromJson(reader, JsonObject.class);
            JsonArray rewards = crateObj.getAsJsonArray("rewards");

            JsonObject newReward = new JsonObject();
            newReward.addProperty("chance", chance);

            // Salvăm tot itemul (enchant-uri, nume, tot) într-un string (NBT format)
            CompoundTag nbt = new CompoundTag();
            item.save(nbt);
            newReward.addProperty("item_nbt", nbt.toString());

            rewards.add(newReward);

            // Rescriem fișierul cu noul item adăugat
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(crateObj, writer);
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("[EvoCrates] Error saving item in " + crateName, e);
            return false;
        }
    }
    // --- METODE NOI ADAUGATE PENTRU CITIRE SI PREVIEW ---

    // 1. Ia lista cu numele tuturor cutiilor existente in folder
    public static java.util.List<String> getAllCrates() {
        java.util.List<String> list = new java.util.ArrayList<>();
        init();
        File[] files = CRATES_DIR.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                list.add(f.getName().replace(".json", ""));
            }
        }
        return list;
    }

    // 2. Ia toate itemele dintr-un Crate (Pentru Preview)
    public static java.util.List<ItemStack> getPreviewItems(String crateName) {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        File file = CRATES_DIR.resolve(crateName.toLowerCase() + ".json").toFile();
        if (!file.exists()) return items;

        try (FileReader reader = new FileReader(file)) {
            JsonObject crateObj = GSON.fromJson(reader, JsonObject.class);
            JsonArray rewards = crateObj.getAsJsonArray("rewards");
            for (int i = 0; i < rewards.size(); i++) {
                JsonObject rew = rewards.get(i).getAsJsonObject();
                CompoundTag nbt = net.minecraft.nbt.TagParser.parseTag(rew.get("item_nbt").getAsString());
                items.add(ItemStack.of(nbt));
            }
        } catch (Exception e) {
            LOGGER.error("Error reading preview for " + crateName, e);
        }
        return items;
    }

    // 3. Extrage un item castigator pe baza de sanse matematice!
    public static ItemStack pickWinningItem(String crateName) {
        File file = CRATES_DIR.resolve(crateName.toLowerCase() + ".json").toFile();
        if (!file.exists()) return ItemStack.EMPTY;

        try (FileReader reader = new FileReader(file)) {
            JsonObject crateObj = GSON.fromJson(reader, JsonObject.class);
            JsonArray rewards = crateObj.getAsJsonArray("rewards");

            int totalChance = 0;
            for (int i = 0; i < rewards.size(); i++) {
                totalChance += rewards.get(i).getAsJsonObject().get("chance").getAsInt();
            }

            int randomValue = new java.util.Random().nextInt(totalChance == 0 ? 100 : totalChance);
            int currentSum = 0;

            for (int i = 0; i < rewards.size(); i++) {
                JsonObject rew = rewards.get(i).getAsJsonObject();
                currentSum += rew.get("chance").getAsInt();
                if (randomValue < currentSum) {
                    CompoundTag nbt = net.minecraft.nbt.TagParser.parseTag(rew.get("item_nbt").getAsString());
                    return ItemStack.of(nbt);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error extracting item from " + crateName, e);
        }
        return ItemStack.EMPTY;
    }
    // NOU: Tine minte si itemul si sansa
    public static class RewardInfo {
        public ItemStack item; public int chance;
        public RewardInfo(ItemStack i, int c) { this.item = i; this.chance = c; }
    }

    // NOU: Metoda care ia itemele CU TOT CU SANSE
    public static java.util.List<RewardInfo> getPreviewRewards(String crateName) {
        java.util.List<RewardInfo> items = new java.util.ArrayList<>();
        File file = CRATES_DIR.resolve(crateName.toLowerCase() + ".json").toFile();
        if (!file.exists()) return items;

        try (java.io.FileReader reader = new java.io.FileReader(file)) {
            JsonObject crateObj = GSON.fromJson(reader, JsonObject.class);
            JsonArray rewards = crateObj.getAsJsonArray("rewards");
            for (int i = 0; i < rewards.size(); i++) {
                JsonObject rew = rewards.get(i).getAsJsonObject();
                int chance = rew.get("chance").getAsInt();
                net.minecraft.nbt.CompoundTag nbt = net.minecraft.nbt.TagParser.parseTag(rew.get("item_nbt").getAsString());
                items.add(new RewardInfo(ItemStack.of(nbt), chance));
            }
        } catch (Exception e) {}
        return items;
    }
}
