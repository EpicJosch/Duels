package me.realized.duels;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;
import lombok.Getter;
import me.realized.duels.api.Duels;
import me.realized.duels.api.command.SubCommand;
import me.realized.duels.arena.ArenaManagerImpl;
import me.realized.duels.betting.BettingManager;
import me.realized.duels.command.commands.SpectateCommand;
import me.realized.duels.command.commands.duel.DuelCommand;
import me.realized.duels.command.commands.duels.DuelsCommand;
import me.realized.duels.command.commands.queue.QueueCommand;
import me.realized.duels.config.Config;
import me.realized.duels.config.Lang;
import me.realized.duels.data.ItemData;
import me.realized.duels.data.ItemData.ItemDataDeserializer;
import me.realized.duels.data.UserManagerImpl;
import me.realized.duels.duel.DuelManager;
import me.realized.duels.extension.ExtensionClassLoader;
import me.realized.duels.extension.ExtensionManager;
import me.realized.duels.hook.HookManager;
import me.realized.duels.inventories.InventoryManager;
import me.realized.duels.kit.KitManagerImpl;
import me.realized.duels.listeners.*;
import me.realized.duels.logging.LogManager;
import me.realized.duels.player.PlayerInfoManager;
import me.realized.duels.queue.QueueManager;
import me.realized.duels.queue.sign.QueueSignManagerImpl;
import me.realized.duels.request.RequestManager;
import me.realized.duels.setting.SettingsManager;
import me.realized.duels.shaded.bstats.Metrics;
import me.realized.duels.spectate.SpectateManagerImpl;
import me.realized.duels.teleport.Teleport;
import me.realized.duels.util.*;
import me.realized.duels.util.Log.LogSource;
import me.realized.duels.util.command.AbstractCommand;
import me.realized.duels.util.gui.GuiListener;
import me.realized.duels.util.json.JsonUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import space.arim.morepaperlib.MorePaperLib;
import space.arim.morepaperlib.scheduling.ScheduledTask;

public class DuelsPlugin extends JavaPlugin implements Duels, LogSource {

    private static final int BSTATS_ID = 20778;
    private static final int RESOURCE_ID = 114595;
    private static final String SPIGOT_INSTALLATION_URL = "https://www.spigotmc.org/wiki/spigot-installation/";

    public static DuelsPlugin plugin;

    @Getter
    private static DuelsPlugin instance;
    @Getter
    private static MorePaperLib morePaperLib;

    private final List<Loadable> loadables = new ArrayList<>();
    private final Map<String, AbstractCommand<DuelsPlugin>> commands = new HashMap<>();
    private final List<Listener> registeredListeners = new ArrayList<>();
    private int lastLoad;
    @Getter
    private LogManager logManager;
    @Getter
    private Config configuration;
    @Getter
    private Lang lang;
    @Getter
    private UserManagerImpl userManager;
    @Getter
    private GuiListener<DuelsPlugin> guiListener;
    @Getter
    private KitManagerImpl kitManager;
    @Getter
    private ArenaManagerImpl arenaManager;
    @Getter
    private SettingsManager settingManager;
    @Getter
    private PlayerInfoManager playerManager;
    @Getter
    private SpectateManagerImpl spectateManager;
    @Getter
    private BettingManager bettingManager;
    @Getter
    private InventoryManager inventoryManager;
    @Getter
    private DuelManager duelManager;
    @Getter
    private QueueManager queueManager;
    @Getter
    private QueueSignManagerImpl queueSignManager;
    @Getter
    private RequestManager requestManager;
    @Getter
    private HookManager hookManager;
    @Getter
    private Teleport teleport;
    @Getter
    private ExtensionManager extensionManager;
    @Getter
    private volatile boolean updateAvailable;
    @Getter
    private volatile String newVersion;

    @Override
    public void onEnable() {
        plugin = this;
        long start = System.currentTimeMillis();
        instance = this;
        morePaperLib = new MorePaperLib(this);
        Log.addSource(this);
        JsonUtil.registerDeserializer(ItemData.class, ItemDataDeserializer.class);

        try {
            logManager = new LogManager(this);
        } catch (IOException ex) {
            sendMessage("&c&lCould not load LogManager. Please contact the developer.");

            // Manually print the stacktrace since Log#error only prints errors to non-plugin log sources.
            ex.printStackTrace();
            getPluginLoader().disablePlugin(this);
            return;
        }

        Log.addSource(logManager);
        logManager.debug("onEnable start -> " + System.currentTimeMillis() + "\n");

        try {
            Class.forName("org.spigotmc.SpigotConfig");
        } catch (ClassNotFoundException ex) {
            sendMessage("&c&l================= *** DUELS LOAD FAILURE *** =================");
            sendMessage("&c&lDuels requires a spigot server to run, but this server was not running on spigot!");
            sendMessage("&c&lTo run your server on spigot, follow this guide: " + SPIGOT_INSTALLATION_URL);
            sendMessage("&c&lSpigot is compatible with CraftBukkit/Bukkit plugins.");
            sendMessage("&c&l================= *** DUELS LOAD FAILURE *** =================");
            getPluginLoader().disablePlugin(this);
            return;
        }

        loadables.add(configuration = new Config(this));
        loadables.add(lang = new Lang(this));
        loadables.add(userManager = new UserManagerImpl(this));
        loadables.add(guiListener = new GuiListener<>(this));
        loadables.add(kitManager = new KitManagerImpl(this));
        loadables.add(arenaManager = new ArenaManagerImpl(this));
        loadables.add(settingManager = new SettingsManager(this));
        loadables.add(playerManager = new PlayerInfoManager(this));
        loadables.add(spectateManager = new SpectateManagerImpl(this));
        loadables.add(bettingManager = new BettingManager(this));
        loadables.add(inventoryManager = new InventoryManager(this));
        loadables.add(duelManager = new DuelManager(this));
        loadables.add(queueManager = new QueueManager(this));
        loadables.add(queueSignManager = new QueueSignManagerImpl(this));
        loadables.add(requestManager = new RequestManager(this));
        hookManager = new HookManager(this);
        loadables.add(teleport = new Teleport(this));
        loadables.add(extensionManager = new ExtensionManager(this));

        if (!load()) {
            getPluginLoader().disablePlugin(this);
            return;
        }

        new KitItemListener(this);
        new DamageListener(this);
        new PotionListener(this);
        new TeleportListener(this);
        new ProjectileHitListener(this);
        new EnderpearlListener(this);
        new KitOptionsListener(this);
        new LingerPotionListener(this);

        new Metrics(this, BSTATS_ID);

        if (!configuration.isCheckForUpdates()) {
            return;
        }

        final UpdateChecker updateChecker = new UpdateChecker(this, RESOURCE_ID);
        updateChecker.check((hasUpdate, newVersion) -> {
            if (hasUpdate) {
                DuelsPlugin.this.updateAvailable = true;
                DuelsPlugin.this.newVersion = newVersion;
                sendMessage("&a===============================================");
                sendMessage("&aAn update for " + getName() + " is available!");
                sendMessage("&aDownload " + getName() + " v" + newVersion + " here:");
                Log.info(getDescription().getWebsite());
                sendMessage("&a===============================================");
            } else {
                sendMessage("&aNo updates were available. You are on the latest version!");
            }
        });
        long end = System.currentTimeMillis();
        sendMessage("&aSuccessfully enabled Duels in " + CC.getTimeDifferenceAndColor(start, end) + "&a.");
    }

    @Override
    public void onDisable() {
        final long start = System.currentTimeMillis();
        long last = start;
        logManager.debug("onDisable start -> " + start + "\n");
        unload();
        logManager.debug("unload done (took " + Math.abs(last - (last = System.currentTimeMillis())) + "ms)");
        Log.clearSources();
        logManager.debug("Log#clearSources done (took " + Math.abs(last - System.currentTimeMillis()) + "ms)");
        logManager.handleDisable();
        instance = null;
        sendMessage("&aDisable process took " + (System.currentTimeMillis() - start) + "ms.");
    }

    public static DuelsPlugin getPlugin() {
        return plugin;
    }

    /**
     * @return true if load was successful, otherwise false
     */
    private boolean load() {
        registerCommands(
                new DuelCommand(this),
                new QueueCommand(this),
                new SpectateCommand(this),
                new DuelsCommand(this)
        );

        for (final Loadable loadable : loadables) {
            final String name = loadable.getClass().getSimpleName();

            try {
                final long now = System.currentTimeMillis();
                logManager.debug("Starting load of " + name + " at " + now);
                loadable.handleLoad();
                logManager.debug(name + " has been loaded. (took " + (System.currentTimeMillis() - now) + "ms)");
                lastLoad = loadables.indexOf(loadable);
            } catch (Exception ex) {
                // Print the stacktrace to help with debugging
                ex.printStackTrace();

                // Handles the case of exceptions from LogManager not being logged in file
                if (loadable instanceof LogSource) {
                    ex.printStackTrace();
                }

                sendMessage("&c&lThere was an error while loading " + name + "! If you believe this is an issue from the plugin, please contact the developer.");
                return false;
            }
        }

        return true;
    }

    /**
     * @return true if unload was successful, otherwise false
     */
    private boolean unload() {
        registeredListeners.forEach(HandlerList::unregisterAll);
        registeredListeners.clear();
        // Unregister all extension listeners that isn't using the method Duels#registerListener
        HandlerList.getRegisteredListeners(this)
                .stream()
                .filter(listener -> listener.getListener().getClass().getClassLoader().getClass().isAssignableFrom(ExtensionClassLoader.class))
                .forEach(listener -> HandlerList.unregisterAll(listener.getListener()));
        commands.clear();

        for (final Loadable loadable : Lists.reverse(loadables)) {
            final String name = loadable.getClass().getSimpleName();

            try {
                if (loadables.indexOf(loadable) > lastLoad) {
                    continue;
                }

                final long now = System.currentTimeMillis();
                logManager.debug("Starting unload of " + name + " at " + now);
                loadable.handleUnload();
                logManager.debug(name + " has been unloaded. (took " + (System.currentTimeMillis() - now) + "ms)");
            } catch (Exception ex) {
                sendMessage("&c&lThere was an error while unloading " + name + "! If you believe this is an issue from the plugin, please contact the developer.");
                return false;
            }
        }

        return true;
    }

    @SafeVarargs
    private final void registerCommands(final AbstractCommand<DuelsPlugin>... commands) {
        sendMessage("&eRegistering commands...");
        long start = System.currentTimeMillis();
        for (final AbstractCommand<DuelsPlugin> command : commands) {
            this.commands.put(command.getName().toLowerCase(), command);
            command.register();
        }
        sendMessage("&dSuccessfully registered commands [" + CC.getTimeDifferenceAndColor(start, System.currentTimeMillis()) + ChatColor.WHITE + "]");
    }

    @Override
    public boolean registerSubCommand(@NotNull final String command, @NotNull final SubCommand subCommand) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(subCommand, "subCommand");

        final AbstractCommand<DuelsPlugin> result = commands.get(command.toLowerCase());

        if (result == null || result.isChild(subCommand.getName().toLowerCase())) {
            return false;
        }

        result.child(new AbstractCommand<DuelsPlugin>(this, subCommand) {
            @Override
            protected void execute(final CommandSender sender, final String label, final String[] args) {
                subCommand.execute(sender, label, args);
            }
        });
        return true;
    }

    @Override
    public void registerListener(@NotNull final Listener listener) {
        sendMessage("&eRegistering listeners...");
        long start = System.currentTimeMillis();

        Objects.requireNonNull(listener, "listener");
        registeredListeners.add(listener);
        Bukkit.getPluginManager().registerEvents(listener, this);

        sendMessage("&dSuccessfully registered listeners [" + CC.getTimeDifferenceAndColor(start, System.currentTimeMillis()) + ChatColor.WHITE + "]");
    }

    @Override
    public boolean reload() {
        if (!(unload() && load())) {
            getPluginLoader().disablePlugin(this);
            return false;
        }

        return true;
    }

    @Override
    public String getVersion() {
        return getDescription().getVersion();
    }

    public boolean reload(final Loadable loadable) {
        boolean unloaded = false;
        try {
            loadable.handleUnload();
            unloaded = true;
            loadable.handleLoad();
            return true;
        } catch (Exception ex) {
            sendMessage("&c&lThere was an error while " + (unloaded ? "loading " : "unloading ")
                    + loadable.getClass().getSimpleName()
                    + "! If you believe this is an issue from the plugin, please contact the developer.");
            return false;
        }
    }

    @Override
    public ScheduledTask doSync(@NotNull final Runnable task) {
        Objects.requireNonNull(task, "task");
        return DuelsPlugin.morePaperLib.scheduling().globalRegionalScheduler().run(task);
    }

    @Override
    public ScheduledTask doSyncAfter(@NotNull final Runnable task, final long delay) {
        Objects.requireNonNull(task, "task");
        return DuelsPlugin.morePaperLib.scheduling().globalRegionalScheduler().runDelayed(task, delay);
    }

    @Override
    public ScheduledTask doSyncRepeat(@NotNull final Runnable task, final long delay, final long period) {
        Objects.requireNonNull(task, "task");
        return DuelsPlugin.morePaperLib.scheduling().globalRegionalScheduler().runAtFixedRate(task, delay, period);
    }

    @Override
    public ScheduledTask doAsync(@NotNull final Runnable task) {
        Objects.requireNonNull(task, "task");
        return DuelsPlugin.morePaperLib.scheduling().asyncScheduler().run(task);
    }

    @Override
    public ScheduledTask doAsyncAfter(@NotNull final Runnable task, final long delay) {
        Objects.requireNonNull(task, "task");
        return DuelsPlugin.morePaperLib.scheduling().asyncScheduler().runDelayed(task, Duration.ofMillis(delay * 50));
    }

    @Override
    public ScheduledTask doAsyncRepeat(@NotNull final Runnable task, final long delay, final long period) {
        Objects.requireNonNull(task, "task");
        return DuelsPlugin.morePaperLib.scheduling().asyncScheduler().runAtFixedRate(task, Duration.ofMillis(delay * 50), Duration.ofMillis(period * 50));
    }

    @Override
    public void cancelTask(@NotNull final ScheduledTask task) {
        Objects.requireNonNull(task, "task");
        task.cancel();
    }

    @Override
    public void info(@NotNull final String message) {
        Objects.requireNonNull(message, "message");
        Log.info(message);
    }

    @Override
    public void warn(@NotNull final String message) {
        Objects.requireNonNull(message, "message");
        Log.warn(message);
    }

    @Override
    public void error(@NotNull final String message) {
        Objects.requireNonNull(message, "message");
        Log.error(message);
    }

    @Override
    public void error(@NotNull final String message, @NotNull final Throwable thrown) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(thrown, "thrown");
        Log.error(message, thrown);
    }

    public Loadable find(final String name) {
        return loadables.stream().filter(loadable -> loadable.getClass().getSimpleName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public List<String> getReloadables() {
        return loadables.stream()
                .filter(loadable -> loadable instanceof Reloadable)
                .map(loadable -> loadable.getClass().getSimpleName())
                .collect(Collectors.toList());
    }

    @Override
    public void log(final Level level, final String s) {
        getLogger().log(level, s);
    }

    @Override
    public void log(final Level level, final String s, final Throwable thrown) {
        getLogger().log(level, s, thrown);
    }

    public static String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&', "&7[&aDuels&bOptimised&7] &f");
    }

    public static void sendMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(getPrefix() + CC.translate(message));
    }
}