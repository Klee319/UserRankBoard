package com.codezhangborui.pixelRank;

import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.handler.LeaderboardHandler;
import com.codezhangborui.pixelRank.ranking.RankingRegistry;
import com.codezhangborui.pixelRank.ranking.RankingSource;
import com.codezhangborui.pixelRank.ranking.TrinityForgeStat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PixelRankCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    /** 内蔵ランキングの日本語表示名。外部供給の項目は config のタイトルをそのまま使う。 */
    private static final Map<String, String> JAPANESE_LABELS = new LinkedHashMap<>();
    private static final Map<String, NamedTextColor> LABEL_COLORS = new LinkedHashMap<>();

    static {
        JAPANESE_LABELS.put("mine", "採掘");
        JAPANESE_LABELS.put("place", "設置");
        JAPANESE_LABELS.put("time", "プレイ時間");
        JAPANESE_LABELS.put("death", "死亡");
        JAPANESE_LABELS.put("move", "移動距離");
        JAPANESE_LABELS.put("mobkill", "モブ討伐");
        JAPANESE_LABELS.put("jump", "ジャンプ");
        JAPANESE_LABELS.put("money", "所持金");

        LABEL_COLORS.put("mine", NamedTextColor.GOLD);
        LABEL_COLORS.put("place", NamedTextColor.AQUA);
        LABEL_COLORS.put("time", NamedTextColor.GREEN);
        LABEL_COLORS.put("death", NamedTextColor.RED);
        LABEL_COLORS.put("move", NamedTextColor.LIGHT_PURPLE);
        LABEL_COLORS.put("mobkill", NamedTextColor.DARK_RED);
        LABEL_COLORS.put("jump", NamedTextColor.BLUE);
        LABEL_COLORS.put("money", NamedTextColor.YELLOW);

        // TrinityForge 連携項目。TF 不在なら登録自体されないので、ここに名前が残っていても表示されない。
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            JAPANESE_LABELS.put(stat.alias(), stat.japaneseLabel());
            LABEL_COLORS.put(stat.alias(), NamedTextColor.AQUA);
        }
    }

    public PixelRankCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private static String label(RankingSource source) {
        String japanese = JAPANESE_LABELS.get(source.alias());
        return japanese != null ? japanese : source.title();
    }

    private static NamedTextColor color(RankingSource source) {
        return LABEL_COLORS.getOrDefault(source.alias(), NamedTextColor.WHITE);
    }

    private void sendRank(CommandSender sender, RankingSource source) {
        Map<String, Long> top = source.top(50, LeaderboardHandler.ignorePredicate());
        int i = 0;
        for (Map.Entry<String, Long> entry : top.entrySet()) {
            i++;
            String player = entry.getKey();
            String message;
            if (i == 1) {
                message = "§6";
            } else if (i == 2) {
                message = "§e";
            } else if (i == 3) {
                message = "§a";
            } else {
                message = "§7";
            }
            if (sender instanceof Player senderPlayer) {
                message += senderPlayer.getName().equals(player) ? ">§d" : " ";
            }
            message += i + " | " + player + " - " + entry.getValue();
            sender.sendMessage(message);
        }
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
            message.append(Component.text("/pixelrank rank <" + String.join("|", aliases()) + ">", NamedTextColor.WHITE).clickEvent(ClickEvent.runCommand("/pixelrank rank"))
                    .append(Component.text(" - 指定したランキングを表示します。", NamedTextColor.GRAY))).append(Component.newline());
            if (sender instanceof Player) {
                message.append(Component.text("/pixelrank toggle", NamedTextColor.WHITE).clickEvent(ClickEvent.runCommand("/pixelrank toggle"))
                        .append(Component.text(" - スコアボードの表示を切り替えます。", NamedTextColor.GRAY))).append(Component.newline());
            }
            if (isAdmin(sender)) {
                message.append(Component.text("/pixelrank reload", NamedTextColor.WHITE).clickEvent(ClickEvent.runCommand("/pixelrank reload"))
                        .append(Component.text(" - 設定ファイルを再読み込みします。", NamedTextColor.GRAY)).append(Component.newline()));
            }
            sender.sendMessage(message.build());
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (isAdmin(sender)) {
                Configuration.reload();
                if (!Database.save()) {
                    plugin.getLogger().severe("データベースへの保存に失敗しました。");
                }
                sender.sendMessage(Component.text("設定を再読み込みしました。", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("このコマンドを実行する権限がありません。", NamedTextColor.RED));
            }
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
                return true;
            }
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
                    for (RankingSource source : RankingRegistry.enabled()) {
                        sender.sendMessage("\033[96m/pixelrank rank " + source.alias() + "\033[0m - "
                                + label(source) + "ランキングを表示します。");
                    }
                } else {
                    ComponentBuilder<TextComponent, TextComponent.Builder> message = Component.text();
                    for (RankingSource source : RankingRegistry.enabled()) {
                        message.append(Component.text("[" + label(source) + "] ", color(source))
                                .clickEvent(ClickEvent.runCommand("/pixelrank rank " + source.alias())));
                    }
                    sender.sendMessage(message.build());
                }
                return true;
            }
            Optional<RankingSource> source = RankingRegistry.byAlias(args[1]);
            if (source.isPresent() && source.get().isEnabled()) {
                sender.sendMessage(Component.text(label(source.get()) + "ランキング:").color(NamedTextColor.AQUA));
                sendRank(sender, source.get());
                return true;
            }
            sender.sendMessage(Component.text("不明または無効なランキングの種類です。", NamedTextColor.RED)
                    .append(Component.text(" 詳細は ", NamedTextColor.WHITE))
                    .append(Component.text("/pixelrank help", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/pixelrank help")))
                    .append(Component.text(" をご覧ください。", NamedTextColor.WHITE)));
            return false;
        } else {
            TextComponent message = Component.text("不明なコマンドです。", NamedTextColor.RED)
                    .append(Component.text(" 詳細は ", NamedTextColor.WHITE))
                    .append(Component.text("/pixelrank help", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/pixelrank help")))
                    .append(Component.text(" をご覧ください。", NamedTextColor.WHITE));
            sender.sendMessage(message);
            return false;
        }
    }

    private static boolean isAdmin(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("pixelrank.admin") || sender instanceof ConsoleCommandSender;
    }

    private static List<String> aliases() {
        List<String> aliases = new ArrayList<>();
        for (RankingSource source : RankingRegistry.enabled()) {
            aliases.add(source.alias());
        }
        return aliases;
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
            if (isAdmin(sender)) {
                suggestions.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("rank")) {
            suggestions.addAll(aliases());
        }
        return suggestions;
    }
}
