package com.codezhangborui.pixelRank;

import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.handler.EconomyHandler;
import com.codezhangborui.pixelRank.handler.EventListener;
import com.codezhangborui.pixelRank.handler.LeaderboardHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class PixelRank extends JavaPlugin {
    private static Logger logger;

    private void loadConfig() {
        Configuration.init(this);
        Configuration.setDefault("ranks.mining_rank", true, "Whether to display the mining rank");
        Configuration.setDefault("ranks.placing_rank", true, "Whether to display the placement rank");
        Configuration.setDefault("ranks.online_time_rank", true, "Whether to display the online time rank");
        Configuration.setDefault("ranks.death_rank", false, "Whether to display the death rank");
        Configuration.setDefault("ranks.movement_rank", true, "Whether to display the movement distance rank");
        Configuration.setDefault("ranks.mob_kill_rank", true, "Whether to display the mob kill rank");
        Configuration.setDefault("ranks.money_rank", true, "Whether to display the money rank (requires Vault)");
        Configuration.setDefault("ranks.switch_interval", 15, "The interval of switching ranks on the scoreboard");
        Configuration.setDefault("ranks.ignore_username_regex", "Input_a_regex_here_to_ignore_specific_usernames", "The regex to ignore specific usernames");
        Configuration.setDefault("storage.database", "database.db", "The filename of local SQLite database file");
        Configuration.setDefault("storage.save_interval", 60, "The interval of saving data to the database");
        Configuration.setDefault("leaderboards.mining_rank", "Mining", "The title of the mining rank leaderboard");
        Configuration.setDefault("leaderboards.placing_rank", "Placement", "The title of the placement rank leaderboard");
        Configuration.setDefault("leaderboards.online_time_rank", "Online Time", "The title of the online time rank leaderboard");
        Configuration.setDefault("leaderboards.death_rank", "Death", "The title of the death rank leaderboard");
        Configuration.setDefault("leaderboards.movement_rank", "Movement", "The title of the movement distance rank leaderboard");
        Configuration.setDefault("leaderboards.mob_kill_rank", "Mob Kills", "The title of the mob kill rank leaderboard");
        Configuration.setDefault("leaderboards.money_rank", "Money", "The title of the money rank leaderboard");
        Configuration.setDefault("leaderboards.max_leaderboard_size", 10, "The maximum size of the leaderboard");
    }

    @Override
    public void onEnable() {
        logger = getLogger();
        logger.info("\033[96mPixelRank Version " + getDescription().getVersion() + ". Hello!\033[0m");
        logger.info("\033[32mLoading configuration...\033[0m");
        loadConfig();
        logger.info("\033[32mRecovering data from the database..\033[0m");
        Database.init(this);
        if (!Database.load()) {
            logger.severe("Failed to load data from the database. Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (EconomyHandler.setup(this)) {
            logger.info("\033[32mVault economy detected! Money rank enabled.\033[0m");
        } else {
            logger.info("\033[33mVault economy not found. Money rank disabled.\033[0m");
        }
        LeaderboardHandler.updateScoreboard();
        logger.info("\033[32mRegistrying events and commands...\033[0m");
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        PixelRankCommand commandExecutor = new PixelRankCommand(this);
        this.getCommand("pixelrank").setExecutor(commandExecutor);
        this.getCommand("pixelrank").setTabCompleter(commandExecutor);
        logger.info("\033[32mRegistrying time scheduler...\033[0m");
        Scheduler.start(this);
        logger.info("\033[92mPixelRank has been enabled!\033[0m");
    }

    @Override
    public void onDisable() {
        logger.info("\033[32mSaving all data to the database...\033[0m");
        if (!Database.save()) {
            logger.severe("Failed to save data to the database!");
        }
        logger.info("\033[91mPixelRank has been disabled! Goodbye!\033[0m");
    }
}
