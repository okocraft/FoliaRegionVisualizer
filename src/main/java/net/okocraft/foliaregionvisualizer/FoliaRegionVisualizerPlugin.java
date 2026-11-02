package net.okocraft.foliaregionvisualizer;

import de.bluecolored.bluemap.api.BlueMapAPI;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FoliaRegionVisualizerPlugin extends JavaPlugin {

    // BlueMap calls these on every (re)load of BlueMap, possibly off the server thread.
    private final Consumer<BlueMapAPI> onBlueMapEnable = this::start;
    private final Consumer<BlueMapAPI> onBlueMapDisable = api -> stop(false);

    // Guarded by this.
    private @Nullable VisualizerService service;
    private @Nullable ScheduledTask updateTask;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        reloadConfig();
    }

    @Override
    public void onEnable() {
        BlueMapAPI.onEnable(this.onBlueMapEnable);
        BlueMapAPI.onDisable(this.onBlueMapDisable);
    }

    @Override
    public void onDisable() {
        BlueMapAPI.unregisterListener(this.onBlueMapEnable);
        BlueMapAPI.unregisterListener(this.onBlueMapDisable);
        stop(true);
    }

    private synchronized void start(@NotNull BlueMapAPI api) {
        // BlueMap may be enabled again without a disable call in between; never leave a second task running.
        stop(false);

        VisualizerService service = new VisualizerService(api, VisualizerSettings.load(getConfig()));
        this.service = service;
        this.updateTask = getServer().getAsyncScheduler().runAtFixedRate(
                this,
                task -> List.copyOf(getServer().getWorlds()).forEach(service::update),
                5, 5, TimeUnit.SECONDS
        );
    }

    /**
     * Stops updating the markers. Idempotent.
     *
     * @param removeMarkers whether to remove the marker sets from BlueMap (not needed when BlueMap itself is stopping)
     */
    private synchronized void stop(boolean removeMarkers) {
        if (this.updateTask != null) {
            this.updateTask.cancel();
            this.updateTask = null;
        }

        if (this.service != null) {
            if (removeMarkers) {
                this.service.clear();
            }
            this.service = null;
        }
    }
}
