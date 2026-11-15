package org.evocraft.evohub;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.evocraft.evocore.data.CrateKeyManager;
import org.evocraft.evocore.database.DatabaseManager;
import org.evocraft.evocore.util.EvoCurrencyFormatter;
import org.evocraft.evohub.crates.CrateManager;
import org.evocraft.evohub.network.EvoHubPacketHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EvoCratesCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("evo")
                .requires(source -> source.hasPermission(2))

                // COMANDA VECHE PENTRU UN SINGUR JUCĂTOR
                .then(Commands.literal("givekey")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("crate", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000000))
                                                .executes(context -> giveKey(context.getSource(), StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "crate"), IntegerArgumentType.getInteger(context, "amount")))
                                        ))))

                // COMANDA NOUĂ: DĂ CHEI LA TOȚI CEI ONLINE!
                .then(Commands.literal("givekeyall")
                        .then(Commands.argument("crate", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000000))
                                        .executes(context -> giveKeyAll(context.getSource(), StringArgumentType.getString(context, "crate"), IntegerArgumentType.getInteger(context, "amount")))
                                )))

                .then(Commands.literal("crate")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> createCrate(context.getSource(), StringArgumentType.getString(context, "name")))
                                ))
                        .then(Commands.literal("addreward")
                                .then(Commands.argument("crate", StringArgumentType.word())
                                        .then(Commands.argument("chance", IntegerArgumentType.integer(1, 100))
                                                .executes(context -> addReward(context.getSource(), StringArgumentType.getString(context, "crate"), IntegerArgumentType.getInteger(context, "chance")))
                                        )))
                        .then(Commands.literal("addmoney")
                                .then(Commands.argument("crate", StringArgumentType.word())
                                        .then(Commands.argument("chance", IntegerArgumentType.integer(1, 100))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> addMoneyReward(context.getSource(), StringArgumentType.getString(context, "crate"), IntegerArgumentType.getInteger(context, "chance"), IntegerArgumentType.getInteger(context, "amount")))
                                                ))))
                        // COMANDA NOUA: ADAUGA CHEI CA RECOMPENSA IN CRATE!
                        .then(Commands.literal("addkey")
                                .then(Commands.argument("crate", StringArgumentType.word())
                                        .then(Commands.argument("chance", IntegerArgumentType.integer(1, 100))
                                                .then(Commands.argument("keyType", StringArgumentType.word())
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000000))
                                                                .executes(context -> addKeyReward(context.getSource(), StringArgumentType.getString(context, "crate"), IntegerArgumentType.getInteger(context, "chance"), StringArgumentType.getString(context, "keyType"), IntegerArgumentType.getInteger(context, "amount")))
                                                        )))))
                )
        );
    }

    private static int giveKey(CommandSourceStack source, String targetPlayer, String crateName, int amount) {
        String normalizedCrate = CrateManager.normalizeCrateName(crateName);
        if (normalizedCrate == null || targetPlayer == null || targetPlayer.isBlank()) {
            source.sendFailure(Component.literal("§c[EvoHub] Invalid player or crate name."));
            return 0;
        }
        String cleanPlayer = targetPlayer.trim();
        MinecraftServer server = source.getServer();

        EvoHubPacketHandler.runDbTask(() -> {
            CrateKeyManager.get().addKeys(cleanPlayer, normalizedCrate, amount);
            if (server != null) {
                server.execute(() -> {
                    ServerPlayer onlineTarget = server.getPlayerList().getPlayerByName(cleanPlayer);
                    if (onlineTarget != null) {
                        onlineTarget.sendSystemMessage(Component.literal("§8[§a🎁§8] §aYou received §e" + amount + "x " + normalizedCrate + " Keys§a!"));
                        EvoHubPacketHandler.sendCratesToPlayer(onlineTarget);
                    }
                    source.sendSuccess(() -> Component.literal("§a[EvoHub] Gave §e" + amount + "x " + normalizedCrate + " Keys §ato §f" + cleanPlayer), false);
                });
            }
        });
        return 1;
    }

    // LOGICA NOUĂ PENTRU EVENT-URI (TOȚI ONLINE)
    private static int giveKeyAll(CommandSourceStack source, String crateName, int amount) {
        String normalizedCrate = CrateManager.normalizeCrateName(crateName);
        if (normalizedCrate == null) {
            source.sendFailure(Component.literal("§c[EvoHub] Invalid crate name."));
            return 0;
        }
        MinecraftServer server = source.getServer();
        if (server == null) return 0;

        List<String> targetNames = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            targetNames.add(player.getGameProfile().getName());
        }
        if (targetNames.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§e[EvoHub] No players online to receive keys."), false);
            return 1;
        }

        EvoHubPacketHandler.runDbTask(() -> {
            boolean success = false;
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn != null && !conn.isClosed()) {
                        boolean mySql = DatabaseManager.get().isMySql();
                        String query = mySql
                                ? "INSERT INTO player_keys (username, crate_name, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + ?"
                                : "INSERT INTO player_keys (username, crate_name, amount) VALUES (?, ?, ?) ON CONFLICT(username, crate_name) DO UPDATE SET amount = amount + excluded.amount";
                        try (PreparedStatement stmt = conn.prepareStatement(query)) {
                            for (String name : targetNames) {
                                stmt.setString(1, name);
                                stmt.setString(2, normalizedCrate);
                                stmt.setInt(3, amount);
                                if (mySql) {
                                    stmt.setInt(4, amount);
                                }
                                stmt.addBatch();
                            }
                            stmt.executeBatch();
                            success = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            final boolean dbOk = success;
            server.execute(() -> {
                if (!dbOk) {
                    source.sendFailure(Component.literal("§c[EvoHub] Database error!"));
                    return;
                }
                int notified = 0;
                for (String name : targetNames) {
                    ServerPlayer player = server.getPlayerList().getPlayerByName(name);
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("§8[§a🎁§8] §aYou received §e" + amount + "x " + normalizedCrate + " Keys §afrom an Administrator!"));
                        EvoHubPacketHandler.sendCratesToPlayer(player);
                        notified++;
                    }
                }
                final int finalCount = notified;
                source.sendSuccess(() -> Component.literal("§a[EvoHub] Successfully gave §e" + amount + "x " + normalizedCrate + " Keys §ato §b" + finalCount + " §aonline players!"), true);
            });
        });
        return 1;
    }

    private static int createCrate(CommandSourceStack source, String crateName) {
        if (!CrateManager.isValidCrateName(crateName)) {
            source.sendFailure(Component.literal("§c[EvoHub] Invalid crate name! Use 1-64 letters, numbers, '_' or '-'."));
            return 0;
        }
        if (CrateManager.createCrateFile(crateName)) source.sendSuccess(() -> Component.literal("§a[EvoHub] Crate §e" + crateName.toLowerCase(Locale.ROOT) + " §awas created!"), false);
        else source.sendFailure(Component.literal("§c[EvoHub] Crate " + crateName + " already exists!"));
        return 1;
    }

    private static int addReward(CommandSourceStack source, String crateName, int chance) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c[EvoHub] Only a player can run this command!"));
            return 0;
        }
        ItemStack itemInHand = player.getMainHandItem();
        if (itemInHand.isEmpty()) { source.sendFailure(Component.literal("§c[EvoHub] Hold an item in your hand!")); return 0; }

        if (CrateManager.addRewardToFile(crateName, itemInHand.copy(), chance)) source.sendSuccess(() -> Component.literal("§aAdded the item to crate §6" + crateName + " §awith chance §b" + chance + "%"), false);
        else source.sendFailure(Component.literal("§c[EvoHub] That crate does not exist or name is invalid!"));
        return 1;
    }

    private static int addMoneyReward(CommandSourceStack source, String crateName, int chance, int amount) {
        ItemStack moneyItem = new ItemStack(Items.PAPER);
        moneyItem.setHoverName(Component.literal("§e§lEVO: §a" + EvoCurrencyFormatter.formatWithCurrency(amount)));
        moneyItem.getOrCreateTag().putInt("EvoMoney", amount);

        if (CrateManager.addRewardToFile(crateName, moneyItem, chance)) {
            source.sendSuccess(() -> Component.literal("§aAdded the §e" + EvoCurrencyFormatter.formatWithCurrency(amount) + " §areward to crate §6" + crateName + " §awith chance §b" + chance + "%"), false);
        } else {
            source.sendFailure(Component.literal("§c[EvoHub] That crate does not exist or name is invalid!"));
        }
        return 1;
    }

    // COMANDA NOUA: ADAUGA CHEIE IN CRATE
    private static int addKeyReward(CommandSourceStack source, String crateName, int chance, String keyType, int amount) {
        String normalizedKey = CrateManager.normalizeCrateName(keyType);
        if (normalizedKey == null) {
            source.sendFailure(Component.literal("§c[EvoHub] Invalid key type name!"));
            return 0;
        }
        ItemStack keyItem = new ItemStack(Items.TRIPWIRE_HOOK);
        keyItem.setHoverName(Component.literal("§d§lKEY: §f" + amount + "x " + normalizedKey.toUpperCase(Locale.ROOT)));
        keyItem.getOrCreateTag().putString("EvoKey", normalizedKey);
        keyItem.getOrCreateTag().putInt("EvoKeyAmount", amount);

        if (CrateManager.addRewardToFile(crateName, keyItem, chance)) {
            source.sendSuccess(() -> Component.literal("§aAdded the §d" + amount + "x " + normalizedKey + " Keys §areward to crate §6" + crateName + " §awith chance §b" + chance + "%"), false);
        } else {
            source.sendFailure(Component.literal("§c[EvoHub] That crate does not exist or name is invalid!"));
        }
        return 1;
    }
}
