package org.evocraft.evohub.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.evocraft.evocombat.combat.CombatLogManager;
import org.evocraft.evocore.data.CrateKeyManager;
import org.evocraft.evocore.data.EconomyManager;
import org.evocraft.evocore.database.DatabaseManager;
import org.evocraft.evocore.util.EvoCurrencyFormatter;
import org.evocraft.evoessentials.data.WarpManager;
import org.evocraft.evoessentials.teleport.TeleportManager;
import org.evocraft.evoessentials.vanish.VanishManager;
import org.evocraft.evohub.crates.CrateManager;
import org.evocraft.evohub.gui.EvoHubScreen;
import org.evocraft.evokits.data.KitManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "evohub", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EvoHubPacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final int TOP_ENTRIES_PER_PAGE = 12;
    private static final int CRATE_REWARD_DELAY_TICKS = 140;

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("evohub", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean isRegistered = false;
    private static ExecutorService dbExecutor = createExecutor();
    private static volatile boolean shuttingDown = false;

    private static final List<PendingCrateReward> pendingCrateRewards = new ArrayList<>();

    private static class PendingCrateReward {
        final UUID playerUuid;
        final String username;
        final ItemStack wonItem;
        int ticksRemaining;

        PendingCrateReward(UUID playerUuid, String username, ItemStack wonItem, int ticksRemaining) {
            this.playerUuid = playerUuid;
            this.username = username;
            this.wonItem = wonItem;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EvoHub-DB");
            t.setDaemon(true);
            return t;
        });
    }

    private static synchronized ExecutorService executor() {
        if (dbExecutor == null || dbExecutor.isShutdown()) {
            dbExecutor = createExecutor();
        }
        return dbExecutor;
    }

    public static void runDbTask(Runnable task) {
        if (task == null) return;
        if (shuttingDown) {
            task.run();
            return;
        }
        try {
            executor().execute(task);
        } catch (Exception e) {
            task.run();
        }
    }

    public static void shutdown() {
        shuttingDown = true;
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            List<PendingCrateReward> remaining;
            synchronized (pendingCrateRewards) {
                remaining = new ArrayList<>(pendingCrateRewards);
                pendingCrateRewards.clear();
            }
            if (server != null) {
                for (PendingCrateReward reward : remaining) {
                    ServerPlayer player = server.getPlayerList().getPlayer(reward.playerUuid);
                    grantCrateReward(server, player, reward.playerUuid, reward.username, reward.wonItem);
                }
            }

            ExecutorService exec;
            synchronized (EvoHubPacketHandler.class) {
                exec = dbExecutor;
            }
            if (exec != null && !exec.isShutdown()) {
                exec.shutdown();
                try {
                    if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
                        exec.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    exec.shutdownNow();
                }
            }
            serverRankCache.clear();
        } finally {
            shuttingDown = false;
        }
    }

    public static void register() {
        if (isRegistered) return;
        isRegistered = true;

        int id = 0;
        INSTANCE.registerMessage(id++, C2S_RequestCratePreview.class, C2S_RequestCratePreview::toBytes, C2S_RequestCratePreview::new, C2S_RequestCratePreview::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, S2C_SendCratePreview.class, S2C_SendCratePreview::toBytes, S2C_SendCratePreview::new, S2C_SendCratePreview::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, C2S_RequestTop.class, C2S_RequestTop::toBytes, C2S_RequestTop::new, C2S_RequestTop::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, S2C_SendTopList.class, S2C_SendTopList::toBytes, S2C_SendTopList::new, S2C_SendTopList::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, C2S_RequestMenu.class, C2S_RequestMenu::toBytes, C2S_RequestMenu::new, C2S_RequestMenu::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, C2S_SaveSettings.class, C2S_SaveSettings::toBytes, C2S_SaveSettings::new, C2S_SaveSettings::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, C2S_BlockPlayer.class, C2S_BlockPlayer::toBytes, C2S_BlockPlayer::new, C2S_BlockPlayer::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, C2S_TeleportHome.class, C2S_TeleportHome::toBytes, C2S_TeleportHome::new, C2S_TeleportHome::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, C2S_ClaimKit.class, C2S_ClaimKit::toBytes, C2S_ClaimKit::new, C2S_ClaimKit::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, C2S_RequestKits.class, C2S_RequestKits::toBytes, C2S_RequestKits::new, C2S_RequestKits::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, S2C_SendKits.class, S2C_SendKits::toBytes, S2C_SendKits::new, S2C_SendKits::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, C2S_RequestWarps.class, C2S_RequestWarps::toBytes, C2S_RequestWarps::new, C2S_RequestWarps::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, S2C_SendWarps.class, S2C_SendWarps::toBytes, S2C_SendWarps::new, S2C_SendWarps::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, C2S_TeleportWarp.class, C2S_TeleportWarp::toBytes, C2S_TeleportWarp::new, C2S_TeleportWarp::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, C2S_RequestCrates.class, C2S_RequestCrates::toBytes, C2S_RequestCrates::new, C2S_RequestCrates::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, S2C_SendCrates.class, S2C_SendCrates::toBytes, S2C_SendCrates::new, S2C_SendCrates::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, C2S_OpenCratePacket.class, C2S_OpenCratePacket::toBytes, C2S_OpenCratePacket::new, C2S_OpenCratePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, S2C_PlayCrateAnimation.class, S2C_PlayCrateAnimation::toBytes, S2C_PlayCrateAnimation::new, S2C_PlayCrateAnimation::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, S2C_SendRanks.class, S2C_SendRanks::toBytes, S2C_SendRanks::new, S2C_SendRanks::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, C2S_RequestRanks.class, C2S_RequestRanks::toBytes, C2S_RequestRanks::new, C2S_RequestRanks::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id++, S2C_PlayerJoinLeave.class, S2C_PlayerJoinLeave::toBytes, S2C_PlayerJoinLeave::new, S2C_PlayerJoinLeave::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, S2C_CurrentServerName.class, S2C_CurrentServerName::toBytes, S2C_CurrentServerName::new, S2C_CurrentServerName::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static final Map<UUID, String> serverRankCache = new ConcurrentHashMap<>();
    private static int tickCounter = 0;
    private static volatile boolean ranksTableCreated = false;

    private static String safeTruncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    // --- UTILA PENTRU CULORILE IN CHAT ---
    public static String applyRankColorServer(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) return "§7Member";
        prefix = prefix.replace('&', '§');
        if (prefix.contains("§")) return prefix;
        String lower = prefix.toLowerCase(Locale.ROOT);
        if (lower.contains("fondator")) return "§4" + prefix;
        if (lower.contains("supervizor")) return "§c" + prefix;
        if (lower.contains("admin")) return "§c" + prefix;
        if (lower.contains("mod")) return "§5" + prefix;
        if (lower.contains("builder")) return "§e" + prefix;
        if (lower.contains("helper")) return "§9" + prefix;
        if (lower.contains("youtuber") || lower.contains("yt")) return "§c" + prefix;
        if (lower.contains("tiktoker") || lower.contains("tk")) return "§d" + prefix;
        if (lower.contains("legend")) return "§6" + prefix;
        if (lower.contains("ultra")) return "§b" + prefix;
        if (lower.contains("titan")) return "§1" + prefix;
        if (lower.contains("mvp")) return "§3" + prefix;
        if (lower.contains("vip+") || lower.contains("vipplus")) return "§a" + prefix;
        if (lower.contains("vip")) return "§a" + prefix;
        return "§7" + prefix;
    }

    // --- INTERCEPTARE CHAT PENTRU A ADĂUGA RANK-UL ---
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerChat(ServerChatEvent event) {
        if (event.isCanceled()) return;
        ServerPlayer player = event.getPlayer();
        if (player == null || player.getServer() == null) return;
        String rawMessage = event.getRawText();
        String safeMessage = player.hasPermissions(2) ? rawMessage : rawMessage.replace("§", "");

        String rankPrefix = serverRankCache.getOrDefault(player.getUUID(), "§7Member");
        rankPrefix = applyRankColorServer(rankPrefix);

        Component chatComponent = Component.literal(rankPrefix + " §f" + player.getGameProfile().getName() + " §8» §7")
                .append(Component.literal(safeMessage));

        event.setCanceled(true); // Anulăm mesajul standard
        player.getServer().getPlayerList().broadcastSystemMessage(chatComponent, false); // Trimitem noua formă colorată la toți!
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        List<PendingCrateReward> dueRewards = null;
        synchronized (pendingCrateRewards) {
            if (!pendingCrateRewards.isEmpty()) {
                Iterator<PendingCrateReward> it = pendingCrateRewards.iterator();
                while (it.hasNext()) {
                    PendingCrateReward reward = it.next();
                    reward.ticksRemaining--;
                    if (reward.ticksRemaining <= 0) {
                        it.remove();
                        if (dueRewards == null) dueRewards = new ArrayList<>();
                        dueRewards.add(reward);
                    }
                }
            }
        }
        if (dueRewards != null && server != null) {
            for (PendingCrateReward reward : dueRewards) {
                ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(reward.playerUuid);
                grantCrateReward(server, onlinePlayer, reward.playerUuid, reward.username, reward.wonItem);
            }
        }

        tickCounter++;
        if (tickCounter >= 200) {
            tickCounter = 0;
            updateAndBroadcastRanks();
        }
    }

    private static void grantCrateReward(MinecraftServer server, ServerPlayer player, UUID playerUuid, String username, ItemStack wonItem) {
        if (wonItem == null || wonItem.isEmpty()) return;

        if (wonItem.hasTag() && wonItem.getTag().contains("EvoMoney")) {
            int amount = wonItem.getTag().getInt("EvoMoney");
            if (amount > 0) {
                EconomyManager.get().addBalance(playerUuid, amount);
                if (player != null && !player.hasDisconnected()) {
                    player.sendSystemMessage(Component.literal("§8[§a🎁§8] §fYou received: §a" + EvoCurrencyFormatter.formatWithCurrency(amount) + "§f!"));
                }
            }
        } else if (wonItem.hasTag() && wonItem.getTag().contains("EvoKey")) {
            String kType = wonItem.getTag().getString("EvoKey");
            int kAmt = Math.max(1, wonItem.getTag().getInt("EvoKeyAmount"));
            runDbTask(() -> {
                CrateKeyManager.get().addKeys(username, kType, kAmt);
                if (server != null) {
                    server.execute(() -> {
                        ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
                        if (online != null && !online.hasDisconnected()) {
                            online.sendSystemMessage(Component.literal("§8[§a🎁§8] §fYou received: §d" + kAmt + "x " + kType.toUpperCase(Locale.ROOT) + " Keys§f!"));
                            sendCratesToPlayer(online);
                        }
                    });
                }
            });
        } else if (player != null) {
            ItemStack copy = wonItem.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
            if (!player.hasDisconnected()) {
                player.sendSystemMessage(Component.literal("§8[§a🎁§8] §fYou received: §b" + wonItem.getHoverName().getString() + "§f!"));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        updateAndBroadcastRanks();

        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            if (!VanishManager.isVanished(player)) {
                INSTANCE.send(PacketDistributor.ALL.noArg(), new S2C_PlayerJoinLeave(player.getGameProfile().getName(), player.getUUID(), true));
            }
            MinecraftServer server = player.getServer();
            if (server != null) {
                int port = server.getPort();
                String sName = "Survival";
                if (port == 5002) sName = "Creative";
                else if (port == 5004) sName = "Parkour";
                if (!player.hasDisconnected()) {
                    INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2C_CurrentServerName(sName));
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            if (!VanishManager.isVanished(player)) {
                INSTANCE.send(PacketDistributor.ALL.noArg(), new S2C_PlayerJoinLeave(player.getGameProfile().getName(), uuid, false));
            }
            serverRankCache.remove(uuid);

            List<PendingCrateReward> playerRewards = null;
            synchronized (pendingCrateRewards) {
                Iterator<PendingCrateReward> it = pendingCrateRewards.iterator();
                while (it.hasNext()) {
                    PendingCrateReward r = it.next();
                    if (r.playerUuid.equals(uuid)) {
                        it.remove();
                        if (playerRewards == null) playerRewards = new ArrayList<>();
                        playerRewards.add(r);
                    }
                }
            }
            if (playerRewards != null) {
                MinecraftServer server = player.getServer();
                for (PendingCrateReward r : playerRewards) {
                    grantCrateReward(server, player, r.playerUuid, r.username, r.wonItem);
                }
            }
        }
    }

    private static void updateAndBroadcastRanks() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        boolean changesMade = false;

        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                net.luckperms.api.model.user.User user = lp.getUserManager().getUser(p.getUUID());
                if (user != null) {
                    String prefix = user.getCachedData().getMetaData().getPrefix();
                    if (prefix == null || prefix.isEmpty()) {
                        prefix = user.getPrimaryGroup();
                    }
                    if (prefix != null) {
                        prefix = safeTruncate(prefix.replace('&', '§'), 64);

                        if (!prefix.equals(serverRankCache.get(p.getUUID()))) {
                            changesMade = true;
                            serverRankCache.put(p.getUUID(), prefix);
                            updateRankInDB(p.getUUID().toString(), p.getGameProfile().getName(), prefix);
                        }
                    }
                }
            }

            if (changesMade || Math.random() < 0.2) {
                INSTANCE.send(PacketDistributor.ALL.noArg(), new S2C_SendRanks(new HashMap<>(serverRankCache)));
            }
        } catch (Throwable ignored) {}
    }

    private static void updateRankInDB(String uuid, String name, String rank) {
        final String safeName = safeTruncate(name, 64);
        final String safeRank = safeTruncate(rank, 64);
        runDbTask(() -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn == null || conn.isClosed()) return;

                    if (!ranksTableCreated) {
                        try (PreparedStatement create = conn.prepareStatement(
                                "CREATE TABLE IF NOT EXISTS player_ranks (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(64), rank_name VARCHAR(64))")) {
                            create.executeUpdate();
                        }
                        ranksTableCreated = true;
                    }

                    boolean mySql = DatabaseManager.get().isMySql();
                    String sql = mySql
                            ? "INSERT INTO player_ranks (uuid, name, rank_name) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), rank_name = VALUES(rank_name)"
                            : "INSERT INTO player_ranks (uuid, name, rank_name) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, rank_name = excluded.rank_name";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, uuid);
                        stmt.setString(2, safeName);
                        stmt.setString(3, safeRank);
                        stmt.executeUpdate();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void sendCratesToPlayer(ServerPlayer player) {
        if (player == null || player.hasDisconnected()) return;
        UUID playerUuid = player.getUUID();
        String username = player.getGameProfile().getName();
        MinecraftServer server = player.getServer();

        runDbTask(() -> {
            Map<String, Integer> playerKeys = new HashMap<>();
            List<String> allCrates = CrateManager.getAllCrates();
            for (String c : allCrates) playerKeys.put(c, 0);

            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn != null && !conn.isClosed()) {
                        String query = "SELECT crate_name, amount FROM player_keys WHERE username = ?";
                        try (PreparedStatement stmt = conn.prepareStatement(query)) {
                            stmt.setString(1, username);
                            try (ResultSet rs = stmt.executeQuery()) {
                                while (rs.next()) {
                                    String cName = rs.getString("crate_name");
                                    if (cName != null) {
                                        String normalized = cName.toLowerCase(Locale.ROOT);
                                        if (playerKeys.containsKey(normalized)) {
                                            playerKeys.put(normalized, Math.max(0, rs.getInt("amount")));
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (server != null) {
                server.execute(() -> {
                    ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
                    if (online != null && !online.hasDisconnected()) {
                        INSTANCE.send(PacketDistributor.PLAYER.with(() -> online), new S2C_SendCrates(playerKeys));
                    }
                });
            }
        });
    }

    public static class C2S_RequestRanks {
        public C2S_RequestRanks() {}
        public C2S_RequestRanks(FriendlyByteBuf buf) {}
        public void toBytes(FriendlyByteBuf buf) {}

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || player.getServer() == null || player.hasDisconnected()) return;

                Map<UUID, String> ranks = new HashMap<>();
                try {
                    net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
                    for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                        net.luckperms.api.model.user.User user = lp.getUserManager().getUser(p.getUUID());
                        if (user != null) {
                            String prefix = user.getCachedData().getMetaData().getPrefix();
                            if (prefix == null || prefix.isEmpty()) {
                                prefix = user.getPrimaryGroup();
                            }
                            if (prefix != null) {
                                ranks.put(p.getUUID(), safeTruncate(prefix, 64));
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2C_SendRanks(ranks));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class S2C_SendRanks {
        public Map<UUID, String> ranks;
        public S2C_SendRanks(Map<UUID, String> ranks) { this.ranks = ranks; }
        public S2C_SendRanks(FriendlyByteBuf buf) {
            ranks = new HashMap<>();
            int size = Math.max(0, Math.min(1024, buf.readInt()));
            for (int i = 0; i < size; i++) {
                ranks.put(buf.readUUID(), buf.readUtf(128));
            }
        }
        public void toBytes(FriendlyByteBuf buf) {
            int count = Math.min(1024, ranks.size());
            buf.writeInt(count);
            int written = 0;
            for (Map.Entry<UUID, String> entry : ranks.entrySet()) {
                if (written >= count) break;
                buf.writeUUID(entry.getKey());
                buf.writeUtf(entry.getValue() != null ? entry.getValue() : "", 128);
                written++;
            }
        }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleRanks(ranks));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_RequestCrates {
        public C2S_RequestCrates() {}
        public C2S_RequestCrates(FriendlyByteBuf buf) {}
        public void toBytes(FriendlyByteBuf buf) {}

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    sendCratesToPlayer(player);
                }
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class S2C_SendCrates {
        public Map<String, Integer> keysToSend;
        public S2C_SendCrates(Map<String, Integer> keys) { this.keysToSend = keys; }
        public S2C_SendCrates(FriendlyByteBuf buf) {
            keysToSend = new HashMap<>();
            int size = Math.max(0, Math.min(256, buf.readInt()));
            for (int i = 0; i < size; i++) keysToSend.put(buf.readUtf(64), buf.readInt());
        }
        public void toBytes(FriendlyByteBuf buf) {
            int count = Math.min(256, keysToSend.size());
            buf.writeInt(count);
            int written = 0;
            for (Map.Entry<String, Integer> entry : keysToSend.entrySet()) {
                if (written >= count) break;
                buf.writeUtf(entry.getKey() != null ? entry.getKey() : "", 64);
                buf.writeInt(entry.getValue() != null ? entry.getValue() : 0);
                written++;
            }
        }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleCrates(keysToSend));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_OpenCratePacket {
        public String crateName;
        public C2S_OpenCratePacket(String crateName) { this.crateName = crateName; }
        public C2S_OpenCratePacket(FriendlyByteBuf buf) { this.crateName = buf.readUtf(64); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(crateName != null ? crateName : "", 64); }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || player.hasDisconnected()) return;

                String normalizedCrate = CrateManager.normalizeCrateName(crateName);
                if (normalizedCrate == null) {
                    player.sendSystemMessage(Component.literal("§c✖ Invalid crate name!"));
                    return;
                }

                List<ItemStack> previewItems = CrateManager.getPreviewItems(normalizedCrate);
                ItemStack wonItem = CrateManager.pickWinningItem(normalizedCrate);
                if (wonItem.isEmpty() || previewItems.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§c✖ This crate has no configured rewards right now!"));
                    return;
                }

                boolean virtualReward = wonItem.hasTag() && (wonItem.getTag().contains("EvoMoney") || wonItem.getTag().contains("EvoKey"));
                if (!virtualReward && player.getInventory().getFreeSlot() == -1) {
                    player.sendSystemMessage(Component.literal("§c✖ Full inventory! Free at least one slot before opening this crate."));
                    return;
                }

                UUID playerUuid = player.getUUID();
                String username = player.getGameProfile().getName();
                MinecraftServer server = player.getServer();
                if (server == null) return;

                runDbTask(() -> {
                    boolean consumedKey = false;
                    boolean dbError = false;

                    synchronized (DatabaseManager.get()) {
                        try {
                            Connection conn = DatabaseManager.get().getConnection();
                            if (conn != null && !conn.isClosed()) {
                                try (PreparedStatement updateStmt = conn.prepareStatement(
                                        "UPDATE player_keys SET amount = amount - 1 WHERE username = ? AND crate_name = ? AND amount > 0")) {
                                    updateStmt.setString(1, username);
                                    updateStmt.setString(2, normalizedCrate);
                                    consumedKey = updateStmt.executeUpdate() > 0;
                                }
                            } else {
                                dbError = true;
                            }
                        } catch (Exception e) {
                            dbError = true;
                            e.printStackTrace();
                        }
                    }

                    final boolean didConsume = consumedKey;
                    final boolean hadError = dbError;

                    server.execute(() -> {
                        if (hadError) {
                            ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
                            if (online != null && !online.hasDisconnected()) {
                                online.sendSystemMessage(Component.literal("§c✖ Could not verify your keys right now. Please try again."));
                            }
                            return;
                        }

                        if (!didConsume) {
                            ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
                            if (online != null && !online.hasDisconnected()) {
                                online.sendSystemMessage(Component.literal("§cYou do not have any keys!"));
                            }
                            return;
                        }

                        ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
                        if (online == null || online.hasDisconnected()) {
                            runDbTask(() -> CrateKeyManager.get().addKeys(username, normalizedCrate, 1));
                            return;
                        }

                        INSTANCE.send(PacketDistributor.PLAYER.with(() -> online),
                                new S2C_PlayCrateAnimation(normalizedCrate, wonItem.copy(), previewItems));
                        sendCratesToPlayer(online);

                        synchronized (pendingCrateRewards) {
                            pendingCrateRewards.add(new PendingCrateReward(playerUuid, username, wonItem.copy(), CRATE_REWARD_DELAY_TICKS));
                        }
                    });
                });
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_RequestCratePreview {
        public String crateName;
        public C2S_RequestCratePreview(String cn) { this.crateName = cn; }
        public C2S_RequestCratePreview(FriendlyByteBuf buf) { this.crateName = buf.readUtf(64); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(crateName != null ? crateName : "", 64); }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || player.hasDisconnected()) return;
                String normalized = CrateManager.normalizeCrateName(crateName);
                if (normalized == null) return;
                List<CrateManager.RewardInfo> items = CrateManager.getPreviewRewards(normalized);
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2C_SendCratePreview(normalized, items));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class S2C_SendCratePreview {
        public String crateName; public List<ItemStack> items; public List<Integer> chances;
        public S2C_SendCratePreview(String cn, List<CrateManager.RewardInfo> list) {
            this.crateName = cn; items = new ArrayList<>(); chances = new ArrayList<>();
            for(CrateManager.RewardInfo r : list) { items.add(r.item); chances.add(r.chance); }
        }
        public S2C_SendCratePreview(FriendlyByteBuf buf) {
            crateName = buf.readUtf(64);
            int size = Math.max(0, Math.min(512, buf.readInt()));
            items = new ArrayList<>(); chances = new ArrayList<>();
            for(int i = 0; i < size; i++) { items.add(buf.readItem()); chances.add(buf.readInt()); }
        }
        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(crateName != null ? crateName : "", 64);
            int count = Math.min(512, items.size());
            buf.writeInt(count);
            for(int i = 0; i < count; i++) { buf.writeItem(items.get(i)); buf.writeInt(chances.get(i)); }
        }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.openPreview(crateName, items, chances));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class S2C_PlayCrateAnimation {
        public String crateName; public ItemStack winningItem; public List<ItemStack> previewItems;
        public S2C_PlayCrateAnimation(String cn, ItemStack wi, List<ItemStack> pi) { crateName = cn; winningItem = wi; previewItems = pi; }
        public S2C_PlayCrateAnimation(FriendlyByteBuf buf) {
            crateName = buf.readUtf(64); winningItem = buf.readItem();
            int size = Math.max(0, Math.min(512, buf.readInt()));
            previewItems = new ArrayList<>(); for (int i = 0; i < size; i++) previewItems.add(buf.readItem());
        }
        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(crateName != null ? crateName : "", 64);
            buf.writeItem(winningItem != null ? winningItem : ItemStack.EMPTY);
            int count = Math.min(512, previewItems.size());
            buf.writeInt(count);
            for (int i = 0; i < count; i++) buf.writeItem(previewItems.get(i));
        }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.playAnimation(crateName, winningItem, previewItems));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_RequestWarps {
        public C2S_RequestWarps() {}
        public C2S_RequestWarps(FriendlyByteBuf buf) {}
        public void toBytes(FriendlyByteBuf buf) {}
        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && !player.hasDisconnected()) INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2C_SendWarps());
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class S2C_SendWarps {
        public List<String> warpNames = new ArrayList<>();
        public List<ItemStack> warpIcons = new ArrayList<>();
        public S2C_SendWarps() {
            for (WarpManager.Warp w : WarpManager.get().getWarpsSnapshot().values()) {
                warpNames.add(w.name);
                warpIcons.add(w.icon != null ? w.icon : ItemStack.EMPTY);
            }
        }
        public S2C_SendWarps(FriendlyByteBuf buf) {
            int size = Math.max(0, Math.min(512, buf.readInt()));
            for (int i = 0; i < size; i++) { warpNames.add(buf.readUtf(64)); warpIcons.add(buf.readItem()); }
        }
        public void toBytes(FriendlyByteBuf buf) {
            int count = Math.min(512, warpNames.size());
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeUtf(warpNames.get(i) != null ? warpNames.get(i) : "", 64);
                buf.writeItem(warpIcons.get(i) != null ? warpIcons.get(i) : ItemStack.EMPTY);
            }
        }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleWarps(warpNames, warpIcons));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_TeleportWarp {
        public String warpName;
        public C2S_TeleportWarp(String warpName) { this.warpName = warpName; }
        public C2S_TeleportWarp(FriendlyByteBuf buf) { this.warpName = buf.readUtf(64); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(warpName != null ? warpName : "", 64); }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || player.hasDisconnected()) return;

                if (CombatLogManager.isInCombat(player)) {
                    player.sendSystemMessage(Component.literal("§c✖ You cannot teleport to a warp while in combat!"));
                    return;
                }

                WarpManager.Warp w = WarpManager.get().getWarp(warpName);
                if (w == null) {
                    player.sendSystemMessage(Component.literal("§cThis warp no longer exists!"));
                    return;
                }

                ResourceLocation dimLoc = ResourceLocation.tryParse(w.dimension != null ? w.dimension : "minecraft:overworld");
                if (dimLoc == null) {
                    player.sendSystemMessage(Component.literal("§cInvalid destination dimension for this warp!"));
                    return;
                }
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimLoc);

                TeleportManager.queueTeleport(player, "§l" + w.name + "§e", () -> {
                    MinecraftServer server = player.getServer();
                    if (server == null) return;
                    ServerLevel level = server.getLevel(dim);
                    if (level != null) {
                        player.teleportTo(level, w.x, w.y, w.z, w.yaw, w.pitch);
                        player.sendSystemMessage(Component.literal("§aYou were teleported to §l" + w.name + "§a!"));
                    }
                });
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_RequestKits {
        public C2S_RequestKits() {}
        public C2S_RequestKits(FriendlyByteBuf buf) {}
        public void toBytes(FriendlyByteBuf buf) {}

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || player.hasDisconnected()) return;

                List<String> names = new ArrayList<>();
                List<String> perms = new ArrayList<>();
                List<Integer> cooldowns = new ArrayList<>();
                List<Integer> colors = new ArrayList<>();
                List<List<ItemStack>> allItems = new ArrayList<>();

                int[] btnColors = {0xFF55FF55, 0xFF55FFFF, 0xFFFFAA00, 0xFFFFFF55, 0xFFFF5555};
                int idx = 0;

                for (KitManager.Kit k : KitManager.get().getKitsSnapshot()) {
                    names.add(k.name);
                    perms.add(k.permission != null ? k.permission : "");
                    cooldowns.add(k.cooldownHours);
                    colors.add(btnColors[idx % btnColors.length]);
                    allItems.add(new ArrayList<>(k.items));
                    idx++;
                }

                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2C_SendKits(names, perms, cooldowns, colors, allItems));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class S2C_SendKits {
        private List<String> names; private List<String> perms; private List<Integer> cds;
        private List<Integer> colors; private List<List<ItemStack>> items;

        public S2C_SendKits(List<String> n, List<String> p, List<Integer> c, List<Integer> col, List<List<ItemStack>> i) {
            this.names = n; this.perms = p; this.cds = c; this.colors = col; this.items = i;
        }

        public S2C_SendKits(FriendlyByteBuf buf) {
            int size = Math.max(0, Math.min(256, buf.readInt()));
            names = new ArrayList<>(); perms = new ArrayList<>(); cds = new ArrayList<>(); colors = new ArrayList<>(); items = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                names.add(buf.readUtf(64)); perms.add(buf.readUtf(128)); cds.add(buf.readInt()); colors.add(buf.readInt());
                int itemSize = Math.max(0, Math.min(128, buf.readInt()));
                List<ItemStack> kitItems = new ArrayList<>();
                for (int j = 0; j < itemSize; j++) kitItems.add(buf.readItem());
                items.add(kitItems);
            }
        }

        public void toBytes(FriendlyByteBuf buf) {
            int count = Math.min(256, names.size());
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeUtf(names.get(i) != null ? names.get(i) : "", 64);
                buf.writeUtf(perms.get(i) != null ? perms.get(i) : "", 128);
                buf.writeInt(cds.get(i));
                buf.writeInt(colors.get(i));
                List<ItemStack> kitItems = items.get(i);
                int itemCount = kitItems != null ? Math.min(128, kitItems.size()) : 0;
                buf.writeInt(itemCount);
                for (int j = 0; j < itemCount; j++) {
                    ItemStack is = kitItems.get(j);
                    buf.writeItem(is != null ? is : ItemStack.EMPTY);
                }
            }
        }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ClientPacketHandlers.handleKits(names, perms, cds, colors, items));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_ClaimKit {
        public String kitName;
        public C2S_ClaimKit(String kitName) { this.kitName = kitName; }
        public C2S_ClaimKit(FriendlyByteBuf buf) { this.kitName = buf.readUtf(64); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(kitName != null ? kitName : "", 64); }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || player.hasDisconnected()) return;
                KitManager.get().claimKit(player, kitName);
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class EntryDTO {
        public String name;
        public double value;
        public EntryDTO(String name, double value) {
            this.name = name;
            this.value = value;
        }
    }

    public static class C2S_RequestTop {
        public String category; public int page;
        public C2S_RequestTop(String c, int p) { category = c; page = p; }
        public C2S_RequestTop(FriendlyByteBuf buf) { category = buf.readUtf(64); page = buf.readInt(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(category != null ? category : "", 64); buf.writeInt(page); }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || player.hasDisconnected()) return;

                UUID playerUuid = player.getUUID();
                MinecraftServer server = player.getServer();
                String reqCategory = category != null ? category.trim() : "bani";
                int reqPage = page;

                runDbTask(() -> {
                    List<EntryDTO> topList = new ArrayList<>();
                    int safePage = Math.max(1, Math.min(1000, reqPage));
                    int limit = TOP_ENTRIES_PER_PAGE;
                    int offset = (safePage - 1) * TOP_ENTRIES_PER_PAGE;
                    String query = "";
                    boolean isJob = reqCategory.startsWith("job_") && reqCategory.length() <= 32;

                    if (reqCategory.equals("bani")) {
                        query = "SELECT name, balance AS val FROM player_stats WHERE balance > 0 ORDER BY balance DESC LIMIT ? OFFSET ?";
                    } else if (reqCategory.equals("kills")) {
                        query = "SELECT name, kills AS val FROM player_stats WHERE kills > 0 ORDER BY kills DESC LIMIT ? OFFSET ?";
                    } else if (reqCategory.equals("decese")) {
                        query = "SELECT name, deaths AS val FROM player_stats WHERE deaths > 0 ORDER BY deaths DESC LIMIT ? OFFSET ?";
                    } else if (reqCategory.equals("ore")) {
                        query = "SELECT name, (playtime_sec / 3600.0) AS val FROM player_stats WHERE playtime_sec > 0 ORDER BY playtime_sec DESC LIMIT ? OFFSET ?";
                    } else if (reqCategory.equals("claimuri")) {
                        query = "SELECT name, claims AS val FROM player_stats WHERE claims > 0 ORDER BY claims DESC LIMIT ? OFFSET ?";
                    } else if (isJob) {
                        query = "SELECT player_name AS name, level AS val FROM player_jobs WHERE job_id = ? AND level > 0 ORDER BY level DESC, xp DESC LIMIT ? OFFSET ?";
                    }

                    if (!query.isEmpty()) {
                        synchronized (DatabaseManager.get()) {
                            try {
                                Connection conn = DatabaseManager.get().getConnection();

                                if (conn != null && !conn.isClosed()) {
                                    try (PreparedStatement stmt = conn.prepareStatement(query)) {

                                        if (isJob) {
                                            stmt.setString(1, reqCategory.substring(4));
                                            stmt.setInt(2, limit);
                                            stmt.setInt(3, offset);
                                        } else {
                                            stmt.setInt(1, limit);
                                            stmt.setInt(2, offset);
                                        }

                                        try (ResultSet rs = stmt.executeQuery()) {
                                            while (rs.next()) {
                                                String name = rs.getString("name");
                                                double val = rs.getDouble("val");
                                                if (name != null && !name.isBlank()
                                                        && !name.equalsIgnoreCase("Necunoscut")
                                                        && !name.equalsIgnoreCase("Unknown")) {
                                                    topList.add(new EntryDTO(name, val));
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    String json = new Gson().toJson(topList);
                    if (server != null) {
                        server.execute(() -> {
                            ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
                            if (online != null && !online.hasDisconnected()) {
                                INSTANCE.send(PacketDistributor.PLAYER.with(() -> online), new S2C_SendTopList(reqCategory, safePage, json));
                            }
                        });
                    }
                });
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class S2C_SendTopList {
        public String category;
        public int page;
        public String json;

        public S2C_SendTopList(String category, int page, String json) {
            this.category = category;
            this.page = page;
            this.json = json;
        }

        public S2C_SendTopList(FriendlyByteBuf buf) {
            this.category = buf.readUtf(256);
            this.page = buf.readInt();
            this.json = buf.readUtf(32767);
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(this.category != null ? this.category : "", 256);
            buf.writeInt(this.page);
            buf.writeUtf(this.json != null ? this.json : "[]", 32767);
        }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    List<EntryDTO> list = new Gson().fromJson(json, new TypeToken<List<EntryDTO>>(){}.getType());

                    EvoHubScreen.currentTopList.clear();
                    if (list != null) {
                        for(EntryDTO dto : list) {
                            EvoHubScreen.currentTopList.add(new EvoHubScreen.TopEntry(dto.name, dto.value));
                        }
                    }
                    EvoHubScreen.currentTopCategory = category;
                    EvoHubScreen.currentTopPage = page;
                    EvoHubScreen.isLoadingTop = false;
                });
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_RequestMenu {
        public String menuName;
        public C2S_RequestMenu(String menuName) { this.menuName = menuName; }
        public C2S_RequestMenu(FriendlyByteBuf buf) { this.menuName = buf.readUtf(64); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(menuName != null ? menuName : "", 64); }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) { ctx.get().setPacketHandled(true); return true; }
    }

    public static class C2S_SaveSettings {
        public boolean tpa, trade;
        public C2S_SaveSettings(boolean t, boolean tr) { tpa = t; trade = tr; }
        public C2S_SaveSettings(FriendlyByteBuf buf) { tpa = buf.readBoolean(); trade = buf.readBoolean(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeBoolean(tpa); buf.writeBoolean(trade); }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) { ctx.get().setPacketHandled(true); return true; }
    }

    public static class C2S_BlockPlayer {
        public String target; public boolean block;
        public C2S_BlockPlayer(String t, boolean b) { target = t; block = b; }
        public C2S_BlockPlayer(FriendlyByteBuf buf) { target = buf.readUtf(64); block = buf.readBoolean(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(target != null ? target : "", 64); buf.writeBoolean(block); }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) { ctx.get().setPacketHandled(true); return true; }
    }

    public static class C2S_TeleportHome {
        public String homeName;
        public C2S_TeleportHome(String homeName) { this.homeName = homeName; }
        public C2S_TeleportHome(FriendlyByteBuf buf) { this.homeName = buf.readUtf(64); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(homeName != null ? homeName : "", 64); }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) { ctx.get().setPacketHandled(true); return true; }
    }

    public static class S2C_PlayerJoinLeave {
        public String playerName;
        public UUID playerUuid;
        public boolean isJoin;

        public S2C_PlayerJoinLeave(String playerName, UUID playerUuid, boolean isJoin) {
            this.playerName = playerName;
            this.playerUuid = playerUuid;
            this.isJoin = isJoin;
        }

        public S2C_PlayerJoinLeave(FriendlyByteBuf buf) {
            this.playerName = buf.readUtf(64);
            this.playerUuid = buf.readUUID();
            this.isJoin = buf.readBoolean();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(this.playerName != null ? this.playerName : "", 64);
            buf.writeUUID(this.playerUuid);
            buf.writeBoolean(this.isJoin);
        }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    ClientPacketHandlers.handleJoinLeave(playerName, playerUuid, isJoin);
                });
            });
            ctx.get().setPacketHandled(true);
            return true;
        }
    }

    public static class S2C_CurrentServerName {
        public String serverName;

        public S2C_CurrentServerName(String serverName) {
            this.serverName = serverName;
        }

        public S2C_CurrentServerName(FriendlyByteBuf buf) {
            this.serverName = buf.readUtf(64);
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(this.serverName != null ? this.serverName : "", 64);
        }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    EvoHubScreen.currentServerName = serverName != null && !serverName.isBlank() ? serverName : "Identifying...";
                });
            });
            ctx.get().setPacketHandled(true);
            return true;
        }
    }
}
