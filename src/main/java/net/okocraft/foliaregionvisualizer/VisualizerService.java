package net.okocraft.foliaregionvisualizer;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.okocraft.foliaregionvisualizer.util.CraftBukkitUtils;
import net.okocraft.foliaregionvisualizer.visualizer.CachingVisualizer;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public class VisualizerService {

    private static final boolean ALWAYS_PRINT_REASON = false;
    private static boolean reasonPrinted = false;

    private final BlueMapAPI api;
    private final String name;
    private final boolean defaultHidden;
    private final Set<String> disabledMapNames;
    private final Color spawnColor;
    private final CachingVisualizer.RenderType renderType;

    private final Object2ObjectMap<UUID, CachingVisualizer> visualizerMap = new Object2ObjectOpenHashMap<>();

    public VisualizerService(@NotNull BlueMapAPI api, @NotNull String name, boolean defaultHidden,
                             @NotNull Set<String> disabledMapNames, @NotNull Color spawnColor,
                             @NotNull CachingVisualizer.RenderType renderType) {
        this.api = api;
        this.name = name;
        this.defaultHidden = defaultHidden;
        this.disabledMapNames = disabledMapNames;
        this.spawnColor = spawnColor;
        this.renderType = renderType;
    }

    public void update(@NotNull World world) {
        var level = CraftBukkitUtils.getServerLevel(world);

        if (level == null) {
            if (ALWAYS_PRINT_REASON || !reasonPrinted) {
                reasonPrinted = true;

                var reason = CraftBukkitUtils.getReasonForNotObtaining(world);
                JavaPlugin.getPlugin(FoliaRegionVisualizerPlugin.class).getLogger().warning(reason);
            }

            return;
        }

        var uid = world.getUID();

        var visualizer = this.visualizerMap.get(uid);

        if (visualizer == null) {
            var markerSet = createMarkerSet(uid);

            if (markerSet == null) {
                return;
            }

            visualizer = new CachingVisualizer(uid, markerSet, this.spawnColor, this.renderType);
            this.visualizerMap.put(uid, visualizer);
        }

        visualizer.update(level);
    }

    public void clear() {
        for (var entry : visualizerMap.object2ObjectEntrySet()) {
            var world = this.api.getWorld(entry.getKey());

            if (world.isEmpty()) {
                continue;
            }

            var id = "FoliaRegionVisualizer-" + entry.getKey();

            for (var map : world.get().getMaps()) {
                map.getMarkerSets().remove(id);
            }
        }
    }

    private @Nullable MarkerSet createMarkerSet(@NotNull UUID worldUid) {
        var world = this.api.getWorld(worldUid);

        if (world.isEmpty()) {
            return null;
        }

        MarkerSet markerSet = null;
        var id = "FoliaRegionVisualizer-" + worldUid;

        for (var map : world.get().getMaps()) {
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
