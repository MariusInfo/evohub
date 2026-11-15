package org.evocraft.evohub.crates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class CrateManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern VALID_CRATE_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Object FILE_LOCK = new Object();

    // Locația unde se vor salva crate-urile: folderul "config/evocrates" din server
    private static final Path CRATES_DIR = FMLPaths.CONFIGDIR.get().resolve("evocrates");

    public static void init() {
        synchronized (FILE_LOCK) {
            File dir = CRATES_DIR.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
    }

    public static boolean isValidCrateName(String crateName) {
        return crateName != null && VALID_CRATE_NAME.matcher(crateName.trim()).matches();
    }

    public static String normalizeCrateName(String crateName) {
        if (!isValidCrateName(crateName)) return null;
        return crateName.trim().toLowerCase(Locale.ROOT);
    }

    private static File resolveCrateFile(String crateName) {
        String normalized = normalizeCrateName(crateName);
        if (normalized == null) return null;
        Path base = CRATES_DIR.toAbsolutePath().normalize();
        Path resolved = base.resolve(normalized + ".json").normalize();
        if (!resolved.startsWith(base)) {
            return null;
        }
        return resolved.toFile();
    }

    // Metoda pentru /evo crate create <nume>
    public static boolean createCrateFile(String crateName) {
        synchronized (FILE_LOCK) {
            init();
            File file = resolveCrateFile(crateName);
            if (file == null || file.exists()) return false;

            JsonObject crateObj = new JsonObject();
            crateObj.addProperty("name", normalizeCrateName(crateName));
            crateObj.add("rewards", new JsonArray());

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(crateObj, writer);
                return true;
            } catch (Exception e) {
                LOGGER.error("[EvoCrates] Error creating JSON file for " + crateName, e);
                return false;
            }
        }
    }

    // Metoda pentru /evo crate addreward <crate> <sansa>
    public static boolean addRewardToFile(String crateName, ItemStack item, int chance) {
        if (item == null || item.isEmpty() || chance <= 0) return false;
        synchronized (FILE_LOCK) {
            init();
            File file = resolveCrateFile(crateName);
            if (file == null || !file.exists()) return false;

            try (FileReader reader = new FileReader(file)) {
                JsonObject crateObj = GSON.fromJson(reader, JsonObject.class);
                if (crateObj == null) crateObj = new JsonObject();
                JsonArray rewards = crateObj.getAsJsonArray("rewards");
                if (rewards == null) {
                    rewards = new JsonArray();
                    crateObj.add("rewards", rewards);
                }

                JsonObject newReward = new JsonObject();
                newReward.addProperty("chance", chance);

                // Salvăm tot itemul (enchant-uri, nume, tot) într-un string (NBT format)
                CompoundTag nbt = new CompoundTag();
                item.copy().save(nbt);
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
    }
    // --- METODE NOI ADAUGATE PENTRU CITIRE SI PREVIEW ---

    // 1. Ia lista cu numele tuturor cutiilor existente in folder
    public static List<String> getAllCrates() {
        List<String> list = new ArrayList<>();
        synchronized (FILE_LOCK) {
            init();
            File[] files = CRATES_DIR.toFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    String rawName = f.getName();
                    if (rawName.length() > 5) {
                        String stripped = rawName.substring(0, rawName.length() - 5).toLowerCase(Locale.ROOT);
                        if (isValidCrateName(stripped)) {
                            list.add(stripped);
                        }
                    }
                }
            }
        }
        Collections.sort(list);
        return list;
    }

    // 2. Ia toate itemele dintr-un Crate (Pentru Preview)
    public static List<ItemStack> getPreviewItems(String crateName) {
        List<ItemStack> items = new ArrayList<>();
        for (RewardInfo info : getPreviewRewards(crateName)) {
            if (info.item != null && !info.item.isEmpty()) {
                items.add(info.item);
            }
        }
        return items;
    }

    // 3. Extrage un item castigator pe baza de sanse matematice!
    public static ItemStack pickWinningItem(String crateName) {
        List<RewardInfo> rewards = getPreviewRewards(crateName);
        if (rewards.isEmpty()) return ItemStack.EMPTY;

        long totalChance = 0L;
        List<RewardInfo> positiveRewards = new ArrayList<>();
        for (RewardInfo r : rewards) {
            if (r.item == null || r.item.isEmpty()) continue;
            if (r.chance > 0) {
                totalChance += r.chance;
                positiveRewards.add(r);
            }
        }

        if (positiveRewards.isEmpty()) {
            return rewards.get(ThreadLocalRandom.current().nextInt(rewards.size())).item.copy();
        }

        long bound = Math.min(Integer.MAX_VALUE, totalChance);
        long randomValue = ThreadLocalRandom.current().nextLong(bound);
        long currentSum = 0L;

        for (RewardInfo r : positiveRewards) {
            currentSum += r.chance;
            if (randomValue < currentSum) {
                return r.item.copy();
            }
        }
        return positiveRewards.get(positiveRewards.size() - 1).item.copy();
    }

    // NOU: Tine minte si itemul si sansa
    public static class RewardInfo {
        public ItemStack item;
        public int chance;
        public RewardInfo(ItemStack i, int c) { this.item = i; this.chance = c; }
    }

    // NOU: Metoda care ia itemele CU TOT CU SANSE
    public static List<RewardInfo> getPreviewRewards(String crateName) {
        List<RewardInfo> items = new ArrayList<>();
        File file = resolveCrateFile(crateName);
        if (file == null || !file.exists()) return items;

        synchronized (FILE_LOCK) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject crateObj = GSON.fromJson(reader, JsonObject.class);
                if (crateObj == null) return items;
                JsonArray rewards = crateObj.getAsJsonArray("rewards");
                if (rewards == null) return items;

                for (JsonElement elem : rewards) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject rew = elem.getAsJsonObject();
                    if (!rew.has("item_nbt")) continue;
                    int chance = rew.has("chance") ? Math.max(0, rew.get("chance").getAsInt()) : 0;
                    CompoundTag nbt = TagParser.parseTag(rew.get("item_nbt").getAsString());
                    ItemStack parsed = ItemStack.of(nbt);
                    if (!parsed.isEmpty()) {
                        items.add(new RewardInfo(parsed, chance));
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[EvoCrates] Error reading preview for crate " + crateName, e);
            }
        }
        return items;
    }
}
