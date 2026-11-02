package net.okocraft.foliaregionvisualizer;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.okocraft.foliaregionvisualizer.util.BlueMapWorldId;
import net.okocraft.foliaregionvisualizer.visualizer.CachingVisualizer;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class VisualizerService {

    private final BlueMapAPI api;
    private final VisualizerSettings settings;

    private final Object2ObjectMap<UUID, CachingVisualizer> visualizerMap = new Object2ObjectOpenHashMap<>();

    public VisualizerService(@NotNull BlueMapAPI api, @NotNull VisualizerSettings settings) {
        this.api = api;
        this.settings = settings;
    }

    public void update(@NotNull World world) {
        ServerLevel level = ((CraftWorld) world).getHandle();

        UUID uid = world.getUID();

        CachingVisualizer visualizer = this.visualizerMap.get(uid);

        if (visualizer == null) {
            Optional<BlueMapWorld> blueMapWorld = this.api.getWorld(uid).or(() -> this.api.getWorld(BlueMapWorldId.create(world.getWorldPath(), world.getEnvironment())));

            if (blueMapWorld.isEmpty()) {
                return;
            }

            MarkerSet markerSet = createMarkerSet(blueMapWorld.get(), uid);

            if (markerSet == null) {
                return;
            }

            visualizer = new CachingVisualizer(uid, markerSet, this.settings.detailFormat(), this.settings.spawnColor(), this.settings.renderType());
            this.visualizerMap.put(uid, visualizer);
        }

        visualizer.update(level);
    }

    public void clear() {
        for (Object2ObjectMap.Entry<UUID, CachingVisualizer> entry : visualizerMap.object2ObjectEntrySet()) {
            Optional<BlueMapWorld> world = this.api.getWorld(entry.getKey());

            if (world.isEmpty()) {
                continue;
            }

            String id = "FoliaRegionVisualizer-" + entry.getKey();

            for (BlueMapMap map : world.get().getMaps()) {
                map.getMarkerSets().remove(id);
            }
        }
    }

    private @Nullable MarkerSet createMarkerSet(@NotNull BlueMapWorld world, @NotNull UUID worldUid) {
        MarkerSet markerSet = null;
        String id = "FoliaRegionVisualizer-" + worldUid;

        for (BlueMapMap map : world.getMaps()) {
            if (this.settings.disabledMaps().contains(map.getId())) {
                continue;
            }

            if (markerSet == null) {
                markerSet = MarkerSet.builder().label(this.settings.markerSetName()).defaultHidden(this.settings.defaultHidden()).build();
            }

            map.getMarkerSets().put(id, markerSet);
        }

        return markerSet;
    }
}
