package net.okocraft.foliaregionvisualizer;

import de.bluecolored.bluemap.api.BlueMapAPI;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FoliaRegionVisualizerPlugin extends JavaPlugin {

    private final Consumer<BlueMapAPI> enableListener = this::onBlueMapEnabled;
    private final Consumer<BlueMapAPI> disableListener = this::onBlueMapDisabled;

    private VisualizerService service;
    private ScheduledTask task;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        reloadConfig();
    }

    @Override
    public void onEnable() {
        BlueMapAPI.onEnable(this.enableListener);
        BlueMapAPI.onDisable(this.disableListener);
    }

    @Override
    public void onDisable() {
        BlueMapAPI.unregisterListener(this.enableListener);
        BlueMapAPI.unregisterListener(this.disableListener);
        stop();

        if (this.service != null) {
            this.service.clear();
        }

        getServer().getAsyncScheduler().cancelTasks(this);
    }

    private synchronized void onBlueMapEnabled(@NotNull BlueMapAPI api) {
        // BlueMap calls this on every reload, not always after onBlueMapDisabled.
        stop();

        VisualizerService service = new VisualizerService(api, VisualizerSettings.load(getConfig()));
        this.service = service;
        this.task = getServer().getAsyncScheduler().runAtFixedRate(
                this,
                task -> List.copyOf(getServer().getWorlds()).forEach(service::update),
                5, 5, TimeUnit.SECONDS
        );
    }

    private void onBlueMapDisabled(@NotNull BlueMapAPI api) {
        stop();
    }

    private synchronized void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }
}
