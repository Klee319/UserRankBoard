package com.codezhangborui.pixelRank.handler;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class EconomyHandler {

    private static Economy economy = null;
    private static boolean vaultAvailable = false;
    private static HashMap<String, Long> cachedBalances = new HashMap<>();

    public static boolean setup(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        vaultAvailable = true;
        refreshBalances();
        return true;
    }

    public static boolean isAvailable() {
        return vaultAvailable;
    }

    public static HashMap<String, Long> getCachedBalances() {
        return cachedBalances;
    }

    public static void refreshBalances() {
        if (!vaultAvailable) {
            return;
        }
        HashMap<String, Long> balances = new HashMap<>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() == null) {
                continue;
            }
            double balance = economy.getBalance(player);
            if (balance > 0) {
                balances.put(player.getName(), Math.round(balance));
            }
        }
        cachedBalances = balances;
    }

    public static HashMap<String, Long> getTopBalances(int limit, Pattern ignorePattern) {
        LinkedHashMap<String, Long> top = new LinkedHashMap<>();
        cachedBalances.entrySet().stream()
                .filter(entry -> !ignorePattern.matcher(entry.getKey()).matches())
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(limit)
                .forEach(entry -> top.put(entry.getKey(), entry.getValue()));
        return top;
    }
}
