package net.okocraft.foliaregionvisualizer.data;

import io.papermc.paper.threadedregions.ThreadedRegionizer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.okocraft.foliaregionvisualizer.util.SectionUtils;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class RegionInfo {

    private static final VarHandle SECTION_BY_KEY_HANDLE;

    static {
        try {
            var threadedRegionClass = ThreadedRegionizer.ThreadedRegion.class;
            var sectionByKeyField = threadedRegionClass.getDeclaredField("sectionByKey");
            SECTION_BY_KEY_HANDLE = MethodHandles.privateLookupIn(threadedRegionClass, MethodHandles.lookup()).unreflectVarHandle(sectionByKeyField);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static @NotNull Long2ObjectMap<RegionInfo> collectFrom(@NotNull ServerLevel level) {
        var map = new Long2ObjectOpenHashMap<RegionInfo>();
        var regionizer = level.regioniser;

        var spawnPos = level.getSharedSpawnPos();
        var spawnRegion = regionizer.getRegionAtSynchronised(spawnPos.getX() >> 4, spawnPos.getZ() >> 4);
        long spawnRegionId;

        if (spawnRegion != null) {
            spawnRegionId = spawnRegion.id;
            var info = createRegionInfo(true, spawnRegion);
            info.centerLocations.add(SectionUtils.toSectionKey(spawnPos.getX(), spawnPos.getZ()));
            map.put(spawnRegionId, info);
        } else {
            spawnRegionId = -1;
        }

        for (var player : ObjectList.of(level.players().toArray(ServerPlayer[]::new))) {
            if (isHidden(player)) {
                continue;
            }

            var pos = player.blockPosition();
            var region = regionizer.getRegionAtSynchronised(pos.getX() >> 4, pos.getZ() >> 4);

            if (region == null) {
                continue;
            }

            RegionInfo info;

            if (region.id == spawnRegionId) {
                info = map.get(spawnRegionId);
            } else {
                info = map.computeIfAbsent(region.id, ignored -> createRegionInfo(false, region));
            }

            info.centerLocations.add(SectionUtils.toSectionKey(pos.getX(), pos.getZ()));
        }

        return map;
    }

    private static @NotNull RegionInfo createRegionInfo(boolean isSpawn, @NotNull ThreadedRegionizer.ThreadedRegion<?, ?> region) {
        var sections = (Long2ReferenceOpenHashMap<?>) SECTION_BY_KEY_HANDLE.get(region);
        return new RegionInfo(isSpawn, new LongOpenHashSet(sections.keySet()));
    }

    private static boolean isHidden(ServerPlayer player) {
        Player bukkitPlayer = player.getBukkitEntity();
        if (bukkitPlayer.hasPotionEffect(PotionEffectType.INVISIBILITY) || bukkitPlayer.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }

        for (MetadataValue meta : bukkitPlayer.getMetadata("vanished")) {
            if (meta.asBoolean()) {
                return true;
            }
        }

        return false;
    }

    private final boolean isSpawn;
    private final LongSet regionSectionKeys;
    private final LongList centerLocations = new LongArrayList();

    private RegionInfo(boolean isSpawn, @NotNull LongSet regionSectionKeys) {
        this.isSpawn = isSpawn;
        this.regionSectionKeys = regionSectionKeys;
    }

    public boolean isSpawn() {
        return isSpawn;
    }

    public LongSet getRegionSectionKeys() {
        return regionSectionKeys;
    }

    /**
     * Gets the list of center locations (block position)
     *
     * @return the list of center locations (block position)
     */
    public LongList getCenterLocations() {
        return centerLocations;
    }
}
