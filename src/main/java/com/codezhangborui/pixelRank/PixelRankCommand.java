package com.codezhangborui.pixelRank;

import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.handler.EconomyHandler;
import com.codezhangborui.pixelRank.handler.LeaderboardHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class PixelRankCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    public PixelRankCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private void sendRank(CommandSender sender, HashMap<String, Long> rankMap) {
        AtomicInteger rankCounter = new AtomicInteger(1);
        Pattern ignorePattern = Pattern.compile(Configuration.getString("ranks.ignore_username_regex"));
        rankMap.entrySet().stream().filter(entry -> !ignorePattern.matcher(entry.getKey()).matches()).sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue())).limit(50).forEach(entry -> {
            int i = rankCounter.getAndIncrement();
            String player = entry.getKey();
            String message = "";
            if (i == 1) {
                message += "§6";
            } else if (i == 2) {
                message += "§e";
            } else if (i == 3) {
                message += "§a";
            } else {
                message += "§7";
            }
            if (sender instanceof Player senderPlayer) {
                if (senderPlayer.getName().equals(player)) {
                    message += ">§d";
                } else {
                    message += " ";
                }
            }
            message += i + " | " + player + " - " + entry.getValue();
            sender.sendMessage(message);
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("サーバーは PixelRank バージョン " + plugin.getDescription().getVersion() + " を実行しています", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("詳細は ", NamedTextColor.WHITE)
                    .append(Component.text("/pixelrank help", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/pixelrank help")))
                    .append(Component.text(" をご覧ください。", NamedTextColor.WHITE)));
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
            ComponentBuilder<TextComponent, TextComponent.Builder> message = Component.text();
            message.append(Component.text("PixelRank ヘルプ:", NamedTextColor.AQUA)).append(Component.newline());
            message.append(Component.text("/pixelrank help", NamedTextColor.WHITE).clickEvent(ClickEvent.runCommand("/pixelrank help"))
                    .append(Component.text(" - このヘルプを表示します。", NamedTextColor.GRAY))).append(Component.newline());
            message.append(Component.text("/pixelrank rank <mine|place|time|death|move|mobkill|money>", NamedTextColor.WHITE).clickEvent(ClickEvent.runCommand("/pixelrank rank"))
                    .append(Component.text(" - 指定したランキングを表示します。", NamedTextColor.GRAY))).append(Component.newline());
            if (sender instanceof Player) {
                message.append(Component.text("/pixelrank toggle", NamedTextColor.WHITE).clickEvent(ClickEvent.runCommand("/pixelrank toggle"))
                        .append(Component.text(" - スコアボードの表示を切り替えます。", NamedTextColor.GRAY))).append(Component.newline());
            }
            if (sender.isOp() || sender.hasPermission("pixelrank.admin") || sender instanceof ConsoleCommandSender) {
                message.append(Component.text("/pixelrank reload", NamedTextColor.WHITE).clickEvent(ClickEvent.runCommand("/pixelrank reload"))
                        .append(Component.text(" - 設定ファイルを再読み込みします。", NamedTextColor.GRAY)).append(Component.newline()));
            }
            sender.sendMessage(message.build());
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (sender.isOp() || sender.hasPermission("pixelrank.admin") || sender instanceof ConsoleCommandSender) {
                Configuration.reload();
                if (!Database.save()) {
                    plugin.getLogger().severe("Failed to save data to the database!");
                }
                sender.sendMessage(Component.text("設定を再読み込みしました。", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("このコマンドを実行する権限がありません。", NamedTextColor.RED));
            }
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
                return true;
            }
            Player player = (Player) sender;
            boolean enabled = LeaderboardHandler.toggleScoreboard(player);
            if (enabled) {
                sender.sendMessage(Component.text("スコアボードの表示を有効にしました。", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("スコアボードの表示を無効にしました。", NamedTextColor.YELLOW));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("rank")) {
            if (args.length == 1) {
                sender.sendMessage(Component.text("ランキングの種類を指定してください:", NamedTextColor.AQUA));
                if (sender instanceof ConsoleCommandSender) {
                    if(Configuration.getBoolean("ranks.mining_rank")) {
                        sender.sendMessage("\033[96m/pixelrank rank mine\033[0m - 採掘ランキングを表示します。");
                    }
                    if(Configuration.getBoolean("ranks.placing_rank")) {
                        sender.sendMessage("\033[96m/pixelrank rank place\033[0m - 設置ランキングを表示します。");
                    }
                    if(Configuration.getBoolean("ranks.online_time_rank")) {
                        sender.sendMessage("\033[96m/pixelrank rank time\033[0m - プレイ時間ランキングを表示します。");
                    }
                    if(Configuration.getBoolean("ranks.death_rank")) {
                        sender.sendMessage("\033[96m/pixelrank rank death\033[0m - 死亡ランキングを表示します。");
                    }
                    if(Configuration.getBoolean("ranks.movement_rank")) {
                        sender.sendMessage("\033[96m/pixelrank rank move\033[0m - 移動距離ランキングを表示します。");
                    }
                    if(Configuration.getBoolean("ranks.mob_kill_rank")) {
                        sender.sendMessage("\033[96m/pixelrank rank mobkill\033[0m - モブ討伐ランキングを表示します。");
                    }
                    if(Configuration.getBoolean("ranks.money_rank") && EconomyHandler.isAvailable()) {
                        sender.sendMessage("\033[96m/pixelrank rank money\033[0m - 所持金ランキングを表示します。");
                    }
                } else {
                    ComponentBuilder<TextComponent, TextComponent.Builder> message = Component.text();
                    if(Configuration.getBoolean("ranks.mining_rank")) {
                        message.append(Component.text("[採掘] ", NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand("/pixelrank rank mine")));
                    }
                    if(Configuration.getBoolean("ranks.placing_rank")) {
                        message.append(Component.text("[設置] ", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/pixelrank rank place")));
                    }
                    if(Configuration.getBoolean("ranks.online_time_rank")) {
                        message.append(Component.text("[プレイ時間] ", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/pixelrank rank time")));
                    }
                    if(Configuration.getBoolean("ranks.death_rank")) {
                        message.append(Component.text("[死亡] ", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/pixelrank rank death")));
                    }
                    if(Configuration.getBoolean("ranks.movement_rank")) {
                        message.append(Component.text("[移動距離] ", NamedTextColor.LIGHT_PURPLE).clickEvent(ClickEvent.runCommand("/pixelrank rank move")));
                    }
                    if(Configuration.getBoolean("ranks.mob_kill_rank")) {
                        message.append(Component.text("[モブ討伐] ", NamedTextColor.DARK_RED).clickEvent(ClickEvent.runCommand("/pixelrank rank mobkill")));
                    }
                    if(Configuration.getBoolean("ranks.money_rank") && EconomyHandler.isAvailable()) {
                        message.append(Component.text("[所持金] ", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/pixelrank rank money")));
                    }
                    sender.sendMessage(message.build());
                }
                return true;
            } else {
                if (args[1].equalsIgnoreCase("mine") && Configuration.getBoolean("ranks.mining_rank")) {
                    sender.sendMessage(Component.text("採掘ランキング:").color(NamedTextColor.AQUA));
                    sendRank(sender, Database.mining_rank);
                    return true;
                } else if (args[1].equalsIgnoreCase("place") && Configuration.getBoolean("ranks.placing_rank")) {
                    sender.sendMessage(Component.text("設置ランキング:").color(NamedTextColor.AQUA));
                    sendRank(sender, Database.placing_rank);
                    return true;
                } else if (args[1].equalsIgnoreCase("time") && Configuration.getBoolean("ranks.online_time_rank")) {
                    sender.sendMessage(Component.text("プレイ時間ランキング:").color(NamedTextColor.AQUA));
                    sendRank(sender, Database.online_time_rank);
                    return true;
                } else if (args[1].equalsIgnoreCase("death") && Configuration.getBoolean("ranks.death_rank")) {
                    sender.sendMessage(Component.text("死亡ランキング:").color(NamedTextColor.AQUA));
                    sendRank(sender, Database.death_rank);
                    return true;
                } else if (args[1].equalsIgnoreCase("move") && Configuration.getBoolean("ranks.movement_rank")) {
                    sender.sendMessage(Component.text("移動距離ランキング:").color(NamedTextColor.AQUA));
                    sendRank(sender, Database.movement_rank);
                    return true;
                } else if (args[1].equalsIgnoreCase("mobkill") && Configuration.getBoolean("ranks.mob_kill_rank")) {
                    sender.sendMessage(Component.text("モブ討伐ランキング:").color(NamedTextColor.AQUA));
                    sendRank(sender, Database.mob_kill_rank);
                    return true;
                } else if (args[1].equalsIgnoreCase("money") && Configuration.getBoolean("ranks.money_rank") && EconomyHandler.isAvailable()) {
                    sender.sendMessage(Component.text("所持金ランキング:").color(NamedTextColor.AQUA));
                    EconomyHandler.refreshBalances();
                    sendRank(sender, EconomyHandler.getCachedBalances());
                    return true;
                } else {
                    TextComponent message = Component.text("不明または無効なランキングの種類です。", NamedTextColor.RED)
                            .append(Component.text(" 詳細は ", NamedTextColor.WHITE))
                            .append(Component.text("/pixelrank help", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/pixelrank help")))
                            .append(Component.text(" をご覧ください。", NamedTextColor.WHITE));
                    sender.sendMessage(message);
                    return false;
                }
            }
        } else {
            TextComponent message = Component.text("不明なコマンドです。", NamedTextColor.RED)
                            .append(Component.text(" 詳細は ", NamedTextColor.WHITE))
                            .append(Component.text("/pixelrank help", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/pixelrank help")))
                            .append(Component.text(" をご覧ください。", NamedTextColor.WHITE));
            sender.sendMessage(message);
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("help");
            suggestions.add("rank");
            if (sender instanceof Player) {
                suggestions.add("toggle");
            }
            if (sender.isOp() || sender.hasPermission("pixelrank.admin") || sender instanceof ConsoleCommandSender) {
                suggestions.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("rank")) {
            if(Configuration.getBoolean("ranks.mining_rank")) {
                suggestions.add("mine");
            }
            if(Configuration.getBoolean("ranks.placing_rank")) {
                suggestions.add("place");
            }
            if(Configuration.getBoolean("ranks.online_time_rank")) {
                suggestions.add("time");
            }
            if(Configuration.getBoolean("ranks.death_rank")) {
                suggestions.add("death");
            }
            if(Configuration.getBoolean("ranks.movement_rank")) {
                suggestions.add("move");
            }
            if(Configuration.getBoolean("ranks.mob_kill_rank")) {
                suggestions.add("mobkill");
            }
            if(Configuration.getBoolean("ranks.money_rank") && EconomyHandler.isAvailable()) {
                suggestions.add("money");
            }
        }
        return suggestions;
    }
}