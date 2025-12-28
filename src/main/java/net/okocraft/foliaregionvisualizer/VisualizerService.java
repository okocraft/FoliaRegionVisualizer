package net.okocraft.foliaregionvisualizer;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.okocraft.foliaregionvisualizer.visualizer.CachingVisualizer;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class VisualizerService {

    private final BlueMapAPI api;
    private final String name;
    private final boolean defaultHidden;
    private final Set<String> disabledMapNames;
    private final String detailFormat;
    private final Color spawnColor;
    private final CachingVisualizer.RenderType renderType;

    private final Object2ObjectMap<UUID, CachingVisualizer> visualizerMap = new Object2ObjectOpenHashMap<>();

    public VisualizerService(@NotNull BlueMapAPI api, @NotNull String name, boolean defaultHidden,
                             @NotNull Set<String> disabledMapNames, @NotNull String detailFormat, @NotNull Color spawnColor,
                             @NotNull CachingVisualizer.RenderType renderType) {
        this.api = api;
        this.name = name;
        this.defaultHidden = defaultHidden;
        this.disabledMapNames = disabledMapNames;
        this.detailFormat = detailFormat;
        this.spawnColor = spawnColor;
        this.renderType = renderType;
    }

    public void update(@NotNull World world) {
        ServerLevel level = ((CraftWorld) world).getHandle();

        UUID uid = world.getUID();

        CachingVisualizer visualizer = this.visualizerMap.get(uid);

        if (visualizer == null) {
            MarkerSet markerSet = createMarkerSet(uid);

            if (markerSet == null) {
                return;
            }

            visualizer = new CachingVisualizer(uid, markerSet, this.detailFormat, this.spawnColor, this.renderType);
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

    private @Nullable MarkerSet createMarkerSet(@NotNull UUID worldUid) {
        Optional<BlueMapWorld> world = this.api.getWorld(worldUid);

        if (world.isEmpty()) {
            return null;
        }

        MarkerSet markerSet = null;
        String id = "FoliaRegionVisualizer-" + worldUid;

        for (BlueMapMap map : world.get().getMaps()) {
            if (this.disabledMapNames.contains(map.getId())) {
                continue;
            }

            if (markerSet == null) {
                markerSet = MarkerSet.builder().label(this.name).defaultHidden(this.defaultHidden).build();
            }

            map.getMarkerSets().put(id, markerSet);
        }

        return markerSet;
    }
}
