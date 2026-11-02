package net.okocraft.foliaregionvisualizer;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import net.okocraft.foliaregionvisualizer.util.BlueMapWorldId;
import net.okocraft.foliaregionvisualizer.visualizer.CachingVisualizer;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps one {@link CachingVisualizer} for each world that BlueMap renders. Used from the update task only, except for
 * {@link #clear()} after the task has been cancelled.
 */
public class VisualizerService {

    private final BlueMapAPI api;
    private final VisualizerSettings settings;
    private final Map<UUID, CachingVisualizer> visualizers = new HashMap<>();

    public VisualizerService(@NotNull BlueMapAPI api, @NotNull VisualizerSettings settings) {
        this.api = api;
        this.settings = settings;
    }

    public void update(@NotNull World world) {
        UUID uid = world.getUID();
        CachingVisualizer visualizer = this.visualizers.get(uid);

        if (visualizer == null) {
            MarkerSet markerSet = findBlueMapWorld(world).map(blueMapWorld -> createMarkerSet(blueMapWorld, uid)).orElse(null);

            if (markerSet == null) {
                return;
            }

            visualizer = new CachingVisualizer(uid, markerSet, this.settings);
            this.visualizers.put(uid, visualizer);
        }

        visualizer.update(((CraftWorld) world).getHandle());
    }

    /**
     * Removes the marker sets of this plugin from every BlueMap map.
     */
    public void clear() {
        for (UUID uid : this.visualizers.keySet()) {
            this.api.getWorld(uid).ifPresent(world -> {
                for (BlueMapMap map : world.getMaps()) {
                    map.getMarkerSets().remove(markerSetId(uid));
                }
            });
        }
    }

    private @NotNull Optional<BlueMapWorld> findBlueMapWorld(@NotNull World world) {
        return this.api.getWorld(world.getUID())
                .or(() -> this.api.getWorld(BlueMapWorldId.create(world.getWorldPath(), world.getEnvironment())));
    }

    /**
     * Adds one marker set, shared by every map of the world that is not disabled.
     *
     * @return the marker set, or {@code null} if every map of the world is disabled
     */
    private @Nullable MarkerSet createMarkerSet(@NotNull BlueMapWorld world, @NotNull UUID worldUid) {
        MarkerSet markerSet = null;

        for (BlueMapMap map : world.getMaps()) {
            if (this.settings.disabledMaps().contains(map.getId())) {
                continue;
            }

            if (markerSet == null) {
                markerSet = MarkerSet.builder().label(this.settings.markerSetName()).defaultHidden(this.settings.defaultHidden()).build();
            }

            map.getMarkerSets().put(markerSetId(worldUid), markerSet);
        }

        return markerSet;
    }

    private static @NotNull String markerSetId(@NotNull UUID worldUid) {
        return "FoliaRegionVisualizer-" + worldUid;
    }
}
