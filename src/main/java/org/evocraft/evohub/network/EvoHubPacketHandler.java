package org.evocraft.evohub.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "evohub", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EvoHubPacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("evohub", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, C2S_RequestCratePreview.class, C2S_RequestCratePreview::toBytes, C2S_RequestCratePreview::new, C2S_RequestCratePreview::handle);
        INSTANCE.registerMessage(id++, S2C_SendCratePreview.class, S2C_SendCratePreview::toBytes, S2C_SendCratePreview::new, S2C_SendCratePreview::handle);
        INSTANCE.registerMessage(id++, C2S_RequestTop.class, C2S_RequestTop::toBytes, C2S_RequestTop::new, C2S_RequestTop::handle);
        INSTANCE.registerMessage(id++, S2C_SendTopList.class, S2C_SendTopList::toBytes, S2C_SendTopList::new, S2C_SendTopList::handle);
        INSTANCE.registerMessage(id++, C2S_RequestMenu.class, C2S_RequestMenu::toBytes, C2S_RequestMenu::new, C2S_RequestMenu::handle);
        INSTANCE.registerMessage(id++, C2S_SaveSettings.class, C2S_SaveSettings::toBytes, C2S_SaveSettings::new, C2S_SaveSettings::handle);
        INSTANCE.registerMessage(id++, C2S_BlockPlayer.class, C2S_BlockPlayer::toBytes, C2S_BlockPlayer::new, C2S_BlockPlayer::handle);
        INSTANCE.registerMessage(id++, C2S_TeleportHome.class, C2S_TeleportHome::toBytes, C2S_TeleportHome::new, C2S_TeleportHome::handle);

        INSTANCE.registerMessage(id++, C2S_ClaimKit.class, C2S_ClaimKit::toBytes, C2S_ClaimKit::new, C2S_ClaimKit::handle);
        INSTANCE.registerMessage(id++, C2S_RequestKits.class, C2S_RequestKits::toBytes, C2S_RequestKits::new, C2S_RequestKits::handle);
        INSTANCE.registerMessage(id++, S2C_SendKits.class, S2C_SendKits::toBytes, S2C_SendKits::new, S2C_SendKits::handle);

        INSTANCE.registerMessage(id++, C2S_RequestWarps.class, C2S_RequestWarps::toBytes, C2S_RequestWarps::new, C2S_RequestWarps::handle);
        INSTANCE.registerMessage(id++, S2C_SendWarps.class, S2C_SendWarps::toBytes, S2C_SendWarps::new, S2C_SendWarps::handle);
        INSTANCE.registerMessage(id++, C2S_TeleportWarp.class, C2S_TeleportWarp::toBytes, C2S_TeleportWarp::new, C2S_TeleportWarp::handle);

        INSTANCE.registerMessage(id++, C2S_RequestCrates.class, C2S_RequestCrates::toBytes, C2S_RequestCrates::new, C2S_RequestCrates::handle);
        INSTANCE.registerMessage(id++, S2C_SendCrates.class, S2C_SendCrates::toBytes, S2C_SendCrates::new, S2C_SendCrates::handle);
        INSTANCE.registerMessage(id++, C2S_OpenCratePacket.class, C2S_OpenCratePacket::toBytes, C2S_OpenCratePacket::new, C2S_OpenCratePacket::handle);
        INSTANCE.registerMessage(id++, S2C_PlayCrateAnimation.class, S2C_PlayCrateAnimation::toBytes, S2C_PlayCrateAnimation::new, S2C_PlayCrateAnimation::handle);

        INSTANCE.registerMessage(id++, S2C_SendRanks.class, S2C_SendRanks::toBytes, S2C_SendRanks::new, S2C_SendRanks::handle);
        INSTANCE.registerMessage(id++, C2S_RequestRanks.class, C2S_RequestRanks::toBytes, C2S_RequestRanks::new, C2S_RequestRanks::handle);
        INSTANCE.registerMessage(id++, S2C_PlayerJoinLeave.class, S2C_PlayerJoinLeave::toBytes, S2C_PlayerJoinLeave::new, S2C_PlayerJoinLeave::handle);

        INSTANCE.registerMessage(id++, S2C_CurrentServerName.class, S2C_CurrentServerName::toBytes, S2C_CurrentServerName::new, S2C_CurrentServerName::handle);
    }

    public static final Map<UUID, String> serverRankCache = new HashMap<>();
    private static int tickCounter = 0;
    private static boolean ranksTableCreated = false;

    // --- UTILA PENTRU CULORILE IN CHAT ---
    public static String applyRankColorServer(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) return "§7Member";
        prefix = prefix.replace('&', '§');
        if (prefix.contains("§")) return prefix;
        String lower = prefix.toLowerCase();
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
    @SubscribeEvent
    public static void onServerChat(net.minecraftforge.event.ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String rawMessage = event.getRawText();

        String rankPrefix = serverRankCache.getOrDefault(player.getUUID(), "§7Member");
        rankPrefix = applyRankColorServer(rankPrefix);

        Component chatComponent = Component.literal(rankPrefix + " §f" + player.getGameProfile().getName() + " §8» §7" + rawMessage);

        event.setCanceled(true); // Anulăm mesajul standard
        player.getServer().getPlayerList().broadcastSystemMessage(chatComponent, false); // Trimitem noua formă colorată la toți!
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter >= 200) {
            tickCounter = 0;
            updateAndBroadcastRanks();
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        updateAndBroadcastRanks();

        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            INSTANCE.send(PacketDistributor.ALL.noArg(), new S2C_PlayerJoinLeave(player.getGameProfile().getName(), player.getUUID(), true));
            int port = player.getServer().getPort();
            String sName = "Survival";
            if (port == 5002) sName = "Creative";
            else if (port == 5004) sName = "Parkour";
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2C_CurrentServerName(sName));
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            INSTANCE.send(PacketDistributor.ALL.noArg(), new S2C_PlayerJoinLeave(player.getGameProfile().getName(), player.getUUID(), false));
        }
    }

    private static void updateAndBroadcastRanks() {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
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
                        prefix = prefix.replace('&', '§');

                        if (!prefix.equals(serverRankCache.get(p.getUUID()))) {
                            changesMade = true;
                            serverRankCache.put(p.getUUID(), prefix);
                            updateRankInDB(p.getUUID().toString(), p.getGameProfile().getName(), prefix);
                        }
                    }
                }
            }

            if (changesMade || Math.random() < 0.2) {
                INSTANCE.send(PacketDistributor.ALL.noArg(), new S2C_SendRanks(serverRankCache));
            }
        } catch (Exception e) {}
    }

    private static void updateRankInDB(String uuid, String name, String rank) {
        new Thread(() -> {
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

                    boolean exists = false;
                    try (PreparedStatement check = conn.prepareStatement("SELECT uuid FROM player_ranks WHERE uuid=?")) {
                        check.setString(1, uuid);
                        if (check.executeQuery().next()) exists = true;
                    }

                    if (exists) {
                        try (PreparedStatement update = conn.prepareStatement("UPDATE player_ranks SET rank_name=?, name=? WHERE uuid=?")) {
                            update.setString(1, rank); update.setString(2, name); update.setString(3, uuid); update.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO player_ranks (uuid, name, rank_name) VALUES (?, ?, ?)")) {
                            insert.setString(1, uuid); insert.setString(2, name); insert.setString(3, rank); insert.executeUpdate();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static class C2S_RequestRanks {
        public C2S_RequestRanks() {}
        public C2S_RequestRanks(FriendlyByteBuf buf) {}
        public void toBytes(FriendlyByteBuf buf) {}

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

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
                                ranks.put(p.getUUID(), prefix);
                            }
                        }
                    }
                } catch (Exception e) {}

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
            int size = buf.readInt();
            for (int i = 0; i < size; i++) {
                ranks.put(buf.readUUID(), buf.readUtf());
            }
        }
        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(ranks.size());
            for (Map.Entry<UUID, String> entry : ranks.entrySet()) {
                buf.writeUUID(entry.getKey());
                buf.writeUtf(entry.getValue());
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
                if (player == null) return;

                String username = player.getGameProfile().getName();

                new Thread(() -> {
                    Map<String, Integer> playerKeys = new HashMap<>();
                    List<String> allCrates = org.evocraft.evohub.crates.CrateManager.getAllCrates();
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
                                            if (playerKeys.containsKey(cName)) playerKeys.put(cName, rs.getInt("amount"));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {}
                    }

                    INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2C_SendCrates(playerKeys));
                }).start();
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class S2C_SendCrates {
        public Map<String, Integer> keysToSend;
        public S2C_SendCrates(Map<String, Integer> keys) { this.keysToSend = keys; }
        public S2C_SendCrates(FriendlyByteBuf buf) {
            keysToSend = new HashMap<>();
            int size = buf.readInt();
            for (int i = 0; i < size; i++) keysToSend.put(buf.readUtf(), buf.readInt());
        }
        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(keysToSend.size());
            for (Map.Entry<String, Integer> entry : keysToSend.entrySet()) {
                buf.writeUtf(entry.getKey()); buf.writeInt(entry.getValue());
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
        public C2S_OpenCratePacket(FriendlyByteBuf buf) { this.crateName = buf.readUtf(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(crateName); }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (player.getInventory().getFreeSlot() == -1) {
                    player.sendSystemMessage(Component.literal("§c✖ Inventar plin!")); return;
                }

                String username = player.getGameProfile().getName();
                new Thread(() -> {
                    boolean hasKey = false;

                    synchronized (DatabaseManager.get()) {
                        try {
                            Connection conn = DatabaseManager.get().getConnection();
                            if (conn != null && !conn.isClosed()) {
                                try (PreparedStatement checkStmt = conn.prepareStatement("SELECT amount FROM player_keys WHERE username=? AND crate_name=? AND amount>0")) {
                                    checkStmt.setString(1, username); checkStmt.setString(2, crateName.toLowerCase());
                                    if (checkStmt.executeQuery().next()) hasKey = true;
                                }
                                if (hasKey) {
                                    try (PreparedStatement updateStmt = conn.prepareStatement("UPDATE player_keys SET amount = amount - 1 WHERE username=? AND crate_name=?")) {
                                        updateStmt.setString(1, username); updateStmt.setString(2, crateName.toLowerCase()); updateStmt.executeUpdate();
                                    }
                                }
                            }
                        } catch (Exception e) {}
                    }

                    if (!hasKey) {
                        player.sendSystemMessage(Component.literal("§cNu ai chei!"));
                        return;
                    }

                    ItemStack wonItem = org.evocraft.evohub.crates.CrateManager.pickWinningItem(crateName);
                    List<ItemStack> previewItems = org.evocraft.evohub.crates.CrateManager.getPreviewItems(crateName);

                    player.getServer().execute(() -> {
                        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                                new S2C_PlayCrateAnimation(crateName, wonItem, previewItems));
                        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new C2S_RequestCrates());
                    });

                    // LOGICĂ NOUĂ AICI PENTRU A PROCESA CHEILE CAȘTIGATE!
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            if (player.getServer() != null) {
                                player.getServer().execute(() -> {
                                    if (wonItem.hasTag() && wonItem.getTag().contains("EvoMoney")) {
                                        int amount = wonItem.getTag().getInt("EvoMoney");
                                        player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(), "eco give " + username + " " + amount);
                                        player.sendSystemMessage(Component.literal("§8[§a🎁§8] §fYou received: §a" + amount + " Lei§f!"));
                                    } else if (wonItem.hasTag() && wonItem.getTag().contains("EvoKey")) {
                                        String kType = wonItem.getTag().getString("EvoKey");
                                        int kAmt = wonItem.getTag().getInt("EvoKeyAmount");
                                        player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(), "evo givekey " + username + " " + kType + " " + kAmt);
                                        player.sendSystemMessage(Component.literal("§8[§a🎁§8] §fYou received: §d" + kAmt + "x " + kType.toUpperCase() + " Keys§f!"));
                                    } else {
                                        player.getInventory().add(wonItem.copy());
                                        player.sendSystemMessage(Component.literal("§8[§a🎁§8] §fYou received: §b" + wonItem.getHoverName().getString() + "§f!"));
                                    }
                                });
                            }
                        }
                    }, 10000);

                }).start();
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_RequestCratePreview {
        public String crateName;
        public C2S_RequestCratePreview(String cn) { this.crateName = cn; }
        public C2S_RequestCratePreview(FriendlyByteBuf buf) { this.crateName = buf.readUtf(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(crateName); }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                List<org.evocraft.evohub.crates.CrateManager.RewardInfo> items = org.evocraft.evohub.crates.CrateManager.getPreviewRewards(crateName);
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2C_SendCratePreview(crateName, items));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class S2C_SendCratePreview {
        public String crateName; public List<ItemStack> items; public List<Integer> chances;
        public S2C_SendCratePreview(String cn, List<org.evocraft.evohub.crates.CrateManager.RewardInfo> list) {
            this.crateName = cn; items = new ArrayList<>(); chances = new ArrayList<>();
            for(org.evocraft.evohub.crates.CrateManager.RewardInfo r : list) { items.add(r.item); chances.add(r.chance); }
        }
        public S2C_SendCratePreview(FriendlyByteBuf buf) {
            crateName = buf.readUtf(); int size = buf.readInt();
            items = new ArrayList<>(); chances = new ArrayList<>();
            for(int i=0; i<size; i++) { items.add(buf.readItem()); chances.add(buf.readInt()); }
        }
        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(crateName); buf.writeInt(items.size());
            for(int i=0; i<items.size(); i++) { buf.writeItem(items.get(i)); buf.writeInt(chances.get(i)); }
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
            crateName = buf.readUtf(); winningItem = buf.readItem(); int size = buf.readInt();
            previewItems = new ArrayList<>(); for (int i = 0; i < size; i++) previewItems.add(buf.readItem());
        }
        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(crateName); buf.writeItem(winningItem); buf.writeInt(previewItems.size());
            for (ItemStack item : previewItems) buf.writeItem(item);
        }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.playAnimation(crateName, winningItem, previewItems));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static final Map<UUID, Long> warpCooldowns = new HashMap<>();

    public static class C2S_RequestWarps {
        public C2S_RequestWarps() {}
        public C2S_RequestWarps(FriendlyByteBuf buf) {}
        public void toBytes(FriendlyByteBuf buf) {}
        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2C_SendWarps());
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class S2C_SendWarps {
        public List<String> warpNames = new ArrayList<>();
        public List<ItemStack> warpIcons = new ArrayList<>();
        public S2C_SendWarps() {
            for (org.evocraft.evocore.data.WarpManager.Warp w : org.evocraft.evocore.data.WarpManager.get().warps.values()) {
                warpNames.add(w.name); warpIcons.add(w.icon);
            }
        }
        public S2C_SendWarps(FriendlyByteBuf buf) {
            int size = buf.readInt();
            for (int i = 0; i < size; i++) { warpNames.add(buf.readUtf()); warpIcons.add(buf.readItem()); }
        }
        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(warpNames.size());
            for (int i = 0; i < warpNames.size(); i++) { buf.writeUtf(warpNames.get(i)); buf.writeItem(warpIcons.get(i)); }
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
        public C2S_TeleportWarp(FriendlyByteBuf buf) { this.warpName = buf.readUtf(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(warpName); }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                if (org.evocraft.evocore.combat.CombatLogManager.isInCombat(player)) {
                    player.sendSystemMessage(Component.literal("§c✖ You cannot teleport to a warp while in combat!"));
                    return;
                }

                org.evocraft.evocore.data.WarpManager.Warp w = org.evocraft.evocore.data.WarpManager.get().warps.get(warpName.toLowerCase());
                if (w == null) {
                    player.sendSystemMessage(Component.literal("§cThis warp no longer exists!"));
                    return;
                }

                UUID uuid = player.getUUID();
                long currentTime = System.currentTimeMillis();

                if (warpCooldowns.containsKey(uuid)) {
                    if (currentTime - warpCooldowns.get(uuid) < 5500) {
                        player.sendSystemMessage(Component.literal("§cA teleport is already being processed... Please wait!"));
                        return;
                    }
                }
                warpCooldowns.put(uuid, currentTime);

                net.minecraft.world.phys.Vec3 startPos = player.position();
                player.sendSystemMessage(Component.literal("§eTeleporting in §l5 seconds§e... Do not move!"));

                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        if (player.getServer() != null) {
                            player.getServer().execute(() -> {
                                if (player.position().distanceTo(startPos) > 0.5) {
                                    player.sendSystemMessage(Component.literal("§cTeleport canceled because you moved!"));
                                } else {
                                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(w.dimension));
                                    net.minecraft.server.level.ServerLevel level = player.getServer().getLevel(dim);
                                    if (level != null) {
                                        player.teleportTo(level, w.x, w.y, w.z, w.yaw, w.pitch);
                                        player.sendSystemMessage(Component.literal("§aYou were teleported to §l" + w.name + "§a!"));
                                    }
                                }
                            });
                        }
                    }
                }, 5000);
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    private static final Map<UUID, Map<String, Long>> kitCooldowns = new HashMap<>();

    public static class C2S_RequestKits {
        public C2S_RequestKits() {}
        public C2S_RequestKits(FriendlyByteBuf buf) {}
        public void toBytes(FriendlyByteBuf buf) {}

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                List<String> names = new ArrayList<>();
                List<String> perms = new ArrayList<>();
                List<Integer> cooldowns = new ArrayList<>();
                List<Integer> colors = new ArrayList<>();
                List<List<ItemStack>> allItems = new ArrayList<>();

                int[] btnColors = {0xFF55FF55, 0xFF55FFFF, 0xFFFFAA00, 0xFFFFFF55, 0xFFFF5555};
                int idx = 0;

                for (org.evocraft.evocore.data.KitManager.Kit k : org.evocraft.evocore.data.KitManager.get().kits.values()) {
                    names.add(k.name);
                    perms.add(k.permission);
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
            int size = buf.readInt();
            names = new ArrayList<>(); perms = new ArrayList<>(); cds = new ArrayList<>(); colors = new ArrayList<>(); items = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                names.add(buf.readUtf()); perms.add(buf.readUtf()); cds.add(buf.readInt()); colors.add(buf.readInt());
                int itemSize = buf.readInt();
                List<ItemStack> kitItems = new ArrayList<>();
                for (int j = 0; j < itemSize; j++) kitItems.add(buf.readItem());
                items.add(kitItems);
            }
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(names.size());
            for (int i = 0; i < names.size(); i++) {
                buf.writeUtf(names.get(i)); buf.writeUtf(perms.get(i)); buf.writeInt(cds.get(i)); buf.writeInt(colors.get(i));
                buf.writeInt(items.get(i).size());
                for (ItemStack is : items.get(i)) buf.writeItem(is);
            }
        }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> ClientPacketHandlers.handleKits(names, perms, cds, colors, items));
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_ClaimKit {
        public String kitName;
        public C2S_ClaimKit(String kitName) { this.kitName = kitName; }
        public C2S_ClaimKit(FriendlyByteBuf buf) { this.kitName = buf.readUtf(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(kitName); }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                org.evocraft.evocore.data.KitManager.Kit kit = org.evocraft.evocore.data.KitManager.get().kits.get(kitName.toLowerCase());
                if (kit == null) return;

                boolean hasPerm = false;
                try {
                    hasPerm = net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(player.getUUID())
                            .getCachedData().getPermissionData().checkPermission(kit.permission).asBoolean();
                } catch(Exception e) {}

                if (!hasPerm) {
                    player.sendSystemMessage(Component.literal("§c✖ Nu ai rank-ul necesar!")); return;
                }

                long cooldownMs = kit.cooldownHours * 3600000L;
                long now = System.currentTimeMillis();
                Map<String, Long> pCds = kitCooldowns.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

                if (pCds.containsKey(kit.name.toLowerCase())) {
                    long passed = now - pCds.get(kit.name.toLowerCase());
                    if (passed < cooldownMs) {
                        player.sendSystemMessage(Component.literal("§c⏳ Mai ai de așteptat!")); return;
                    }
                }

                for (ItemStack item : kit.items) {
                    if (item.hasTag() && item.getTag().contains("EvoMoney")) {
                        int amount = item.getTag().getInt("EvoMoney");
                        player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(), "eco give " + player.getGameProfile().getName() + " " + amount);
                    } else {
                        player.getInventory().add(item.copy());
                    }
                }
                org.evocraft.evocore.data.KitManager.get().grantCrateKeys(player, kit);

                pCds.put(kit.name.toLowerCase(), now);
                player.sendSystemMessage(Component.literal("§a✔ You received the §l" + kit.name + "§a kit!"));
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
        public C2S_RequestTop(FriendlyByteBuf buf) { category = buf.readUtf(); page = buf.readInt(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(category); buf.writeInt(page); }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                new Thread(() -> {
                    List<EntryDTO> topList = new ArrayList<>();
                    int limit = 7;
                    int offset = (page - 1) * 7;
                    String query = "";
                    boolean isJob = category.startsWith("job_");

                    if (category.equals("bani")) {
                        query = "SELECT name, balance AS val FROM player_stats WHERE balance > 0 ORDER BY balance DESC LIMIT ? OFFSET ?";
                    } else if (category.equals("kills")) {
                        query = "SELECT name, kills AS val FROM player_stats WHERE kills > 0 ORDER BY kills DESC LIMIT ? OFFSET ?";
                    } else if (category.equals("decese")) {
                        query = "SELECT name, deaths AS val FROM player_stats WHERE deaths > 0 ORDER BY deaths DESC LIMIT ? OFFSET ?";
                    } else if (category.equals("ore")) {
                        query = "SELECT name, (playtime_sec / 3600.0) AS val FROM player_stats WHERE playtime_sec > 0 ORDER BY playtime_sec DESC LIMIT ? OFFSET ?";
                    } else if (category.equals("claimuri")) {
                        query = "SELECT name, claims AS val FROM player_stats WHERE claims > 0 ORDER BY claims DESC LIMIT ? OFFSET ?";
                    } else if (isJob) {
                        query = "SELECT player_name AS name, level AS val FROM player_jobs WHERE job_id = ? AND level > 0 ORDER BY level DESC, xp DESC LIMIT ? OFFSET ?";
                    }

                    if (!query.isEmpty()) {
                        synchronized (org.evocraft.evocore.database.DatabaseManager.get()) {
                            try {
                                Connection conn = org.evocraft.evocore.database.DatabaseManager.get().getConnection();

                                if (conn != null && !conn.isClosed()) {
                                    try (PreparedStatement stmt = conn.prepareStatement(query)) {

                                        if (isJob) {
                                            stmt.setString(1, category.replace("job_", ""));
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
                                                if (name != null && !name.equals("Necunoscut")) {
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

                    String json = new com.google.gson.Gson().toJson(topList);
                    INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2C_SendTopList(category, page, json));
                }).start();
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
            buf.writeUtf(this.category);
            buf.writeInt(this.page);
            buf.writeUtf(this.json);
        }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    List<EntryDTO> list = new com.google.gson.Gson().fromJson(json, new com.google.gson.reflect.TypeToken<List<EntryDTO>>(){}.getType());

                    org.evocraft.evohub.gui.EvoHubScreen.currentTopList.clear();
                    if (list != null) {
                        for(EntryDTO dto : list) {
                            org.evocraft.evohub.gui.EvoHubScreen.currentTopList.add(new org.evocraft.evohub.gui.EvoHubScreen.TopEntry(dto.name, dto.value));
                        }
                    }
                    org.evocraft.evohub.gui.EvoHubScreen.currentTopCategory = category;
                    org.evocraft.evohub.gui.EvoHubScreen.currentTopPage = page;
                    org.evocraft.evohub.gui.EvoHubScreen.isLoadingTop = false;
                });
            });
            ctx.get().setPacketHandled(true); return true;
        }
    }

    public static class C2S_RequestMenu {
        public String menuName;
        public C2S_RequestMenu(String menuName) { this.menuName = menuName; }
        public C2S_RequestMenu(FriendlyByteBuf buf) { this.menuName = buf.readUtf(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(menuName); }
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
        public C2S_BlockPlayer(FriendlyByteBuf buf) { target = buf.readUtf(); block = buf.readBoolean(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(target); buf.writeBoolean(block); }
        public boolean handle(Supplier<NetworkEvent.Context> ctx) { ctx.get().setPacketHandled(true); return true; }
    }

    public static class C2S_TeleportHome {
        public String homeName;
        public C2S_TeleportHome(String homeName) { this.homeName = homeName; }
        public C2S_TeleportHome(FriendlyByteBuf buf) { this.homeName = buf.readUtf(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(homeName); }
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
            this.playerName = buf.readUtf();
            this.playerUuid = buf.readUUID();
            this.isJoin = buf.readBoolean();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(this.playerName);
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
            this.serverName = buf.readUtf();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(this.serverName);
        }

        public boolean handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    org.evocraft.evohub.gui.EvoHubScreen.currentServerName = serverName;
                });
            });
            ctx.get().setPacketHandled(true);
            return true;
        }
    }
}
