package org.evocraft.evohub;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.evocraft.evocore.util.EvoCurrencyFormatter;
import org.evocraft.evohub.crates.CrateManager;
import org.evocraft.evocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

public class EvoCratesCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("evo")
                .requires(source -> source.hasPermission(2))

                // COMANDA VECHE PENTRU UN SINGUR JUCĂTOR
                .then(Commands.literal("givekey")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("crate", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(context -> giveKey(context.getSource(), StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "crate"), IntegerArgumentType.getInteger(context, "amount")))
                                        ))))

                // COMANDA NOUĂ: DĂ CHEI LA TOȚI CEI ONLINE!
                .then(Commands.literal("givekeyall")
                        .then(Commands.argument("crate", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
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
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                .executes(context -> addKeyReward(context.getSource(), StringArgumentType.getString(context, "crate"), IntegerArgumentType.getInteger(context, "chance"), StringArgumentType.getString(context, "keyType"), IntegerArgumentType.getInteger(context, "amount")))
                                                        )))))
                )
        );
    }

    private static int giveKey(CommandSourceStack source, String targetPlayer, String crateName, int amount) {
        new Thread(() -> {
            String query = "INSERT INTO player_keys (username, crate_name, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + ?";
            Connection conn = DatabaseManager.get().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, targetPlayer); stmt.setString(2, crateName.toLowerCase());
                stmt.setInt(3, amount); stmt.setInt(4, amount); stmt.executeUpdate();
                source.sendSuccess(() -> Component.literal("§a[EvoHub] Gave §e" + amount + "x " + crateName + " Keys §ato §f" + targetPlayer), false);
            } catch (Exception e) {}
        }).start();
        return 1;
    }

    // LOGICA NOUĂ PENTRU EVENT-URI (TOȚI ONLINE)
    private static int giveKeyAll(CommandSourceStack source, String crateName, int amount) {
        new Thread(() -> {
            String query = "INSERT INTO player_keys (username, crate_name, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + ?";
            Connection conn = DatabaseManager.get().getConnection();
            List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
            int count = 0;

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                for (ServerPlayer player : players) {
                    String targetPlayer = player.getGameProfile().getName();
                    stmt.setString(1, targetPlayer);
                    stmt.setString(2, crateName.toLowerCase());
                    stmt.setInt(3, amount);
                    stmt.setInt(4, amount);
                    stmt.addBatch(); // Le adăugăm în "coș" ca să nu dăm crash la baza de date

                    // Notify the player about the received gift.
                    player.sendSystemMessage(Component.literal("§8[§a🎁§8] §aYou received §e" + amount + "x " + crateName + " Keys §afrom an Administrator!"));
                    count++;
                }
                stmt.executeBatch(); // Le salvăm pe toate simultan

                final int finalCount = count;
                source.sendSuccess(() -> Component.literal("§a[EvoHub] Successfully gave §e" + amount + "x " + crateName + " Keys §ato §b" + finalCount + " §aonline players!"), true);
            } catch (Exception e) {
                source.sendFailure(Component.literal("§c[EvoHub] Database error!"));
            }
        }).start();
        return 1;
    }

    private static int createCrate(CommandSourceStack source, String crateName) {
        if (CrateManager.createCrateFile(crateName)) source.sendSuccess(() -> Component.literal("§a[EvoHub] Crate §e" + crateName + " §awas created!"), false);
        else source.sendFailure(Component.literal("§c[EvoHub] Crate " + crateName + " already exists!"));
        return 1;
    }

    private static int addReward(CommandSourceStack source, String crateName, int chance) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        ItemStack itemInHand = player.getMainHandItem();
        if (itemInHand.isEmpty()) { source.sendFailure(Component.literal("§c[EvoHub] Hold an item in your hand!")); return 0; }

        if (CrateManager.addRewardToFile(crateName, itemInHand, chance)) source.sendSuccess(() -> Component.literal("§aAdded the item to crate §6" + crateName + " §awith chance §b" + chance + "%"), false);
        else source.sendFailure(Component.literal("§c[EvoHub] That crate does not exist!"));
        return 1;
    }

    private static int addMoneyReward(CommandSourceStack source, String crateName, int chance, int amount) {
        ItemStack moneyItem = new ItemStack(Items.PAPER);
        moneyItem.setHoverName(Component.literal("§e§lEVO: §a" + EvoCurrencyFormatter.formatWithCurrency(amount)));
        moneyItem.getOrCreateTag().putInt("EvoMoney", amount);

        if (CrateManager.addRewardToFile(crateName, moneyItem, chance)) {
            source.sendSuccess(() -> Component.literal("§aAdded the §e" + EvoCurrencyFormatter.formatWithCurrency(amount) + " §areward to crate §6" + crateName + " §awith chance §b" + chance + "%"), false);
        } else {
            source.sendFailure(Component.literal("§c[EvoHub] That crate does not exist!"));
        }
        return 1;
    }

    // COMANDA NOUA: ADAUGA CHEIE IN CRATE
    private static int addKeyReward(CommandSourceStack source, String crateName, int chance, String keyType, int amount) {
        ItemStack keyItem = new ItemStack(Items.TRIPWIRE_HOOK);
        keyItem.setHoverName(Component.literal("§d§lKEY: §f" + amount + "x " + keyType.toUpperCase()));
        keyItem.getOrCreateTag().putString("EvoKey", keyType.toLowerCase());
        keyItem.getOrCreateTag().putInt("EvoKeyAmount", amount);

        if (CrateManager.addRewardToFile(crateName, keyItem, chance)) {
            source.sendSuccess(() -> Component.literal("§aAdded the §d" + amount + "x " + keyType + " Keys §areward to crate §6" + crateName + " §awith chance §b" + chance + "%"), false);
        } else {
            source.sendFailure(Component.literal("§c[EvoHub] That crate does not exist!"));
        }
        return 1;
    }
}
