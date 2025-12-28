package net.okocraft.foliaregionvisualizer.data;

import ca.spottedleaf.moonrise.common.time.TickData;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegions;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class RegionInfo {

    private static final VarHandle SECTION_BY_KEY_HANDLE;

    static {
        try {
            Class<ThreadedRegionizer.ThreadedRegion> threadedRegionClass = ThreadedRegionizer.ThreadedRegion.class;
            Field sectionByKeyField = threadedRegionClass.getDeclaredField("sectionByKey");
            SECTION_BY_KEY_HANDLE = MethodHandles.privateLookupIn(threadedRegionClass, MethodHandles.lookup()).unreflectVarHandle(sectionByKeyField);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static @NotNull Long2ObjectMap<RegionInfo> collectFrom(@NotNull ServerLevel level) {
        Long2ObjectOpenHashMap<RegionInfo> map = new Long2ObjectOpenHashMap<RegionInfo>();
        ThreadedRegionizer<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> regionizer = level.regioniser;

        BlockPos spawnPos = level.levelData.getRespawnData().pos();
        ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> spawnRegion = regionizer.getRegionAtSynchronised(spawnPos.getX() >> 4, spawnPos.getZ() >> 4);
        long spawnRegionId;
        int shift = 4 + level.regioniser.sectionChunkShift;

        if (spawnRegion != null) {
            spawnRegionId = spawnRegion.id;
            map.put(spawnRegionId, createRegionInfo(true, shift, spawnRegion));
        } else {
            spawnRegionId = -1;
        }

        regionizer.computeForAllRegions(region -> {
            if (region.id != spawnRegionId) {
                map.put(region.id, createRegionInfo(false, shift, region));
            }
        });

        return map;
    }

    private static @NotNull RegionInfo createRegionInfo(boolean isSpawn, int shift, @NotNull ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region) {
        Long2ReferenceOpenHashMap<?> sections = (Long2ReferenceOpenHashMap<?>) SECTION_BY_KEY_HANDLE.get(region);

        double mspt;
        int players;

        TickRegions.TickRegionData data = region.getData();
        if (data != null) {
            TickData.TickReportData report = data.getRegionSchedulingHandle().getTickReport1m(System.nanoTime());
            mspt = report != null ? report.timePerTickData().segmentAll().average() / 1.0E6 : 0.0;

            TickRegions.RegionStats stats = data.getRegionStats();
            players = stats != null ? stats.getPlayerCount() : 0;
        } else {
            mspt = 0.0;
            players = 0;
        }

        return new RegionInfo(isSpawn, shift, new LongOpenHashSet(sections.keySet()), mspt, players);
    }

    private final boolean isSpawn;
    private final int shift;
    private final LongSet regionSectionKeys;
    private final double mspt;
    private final int players;

    private RegionInfo(boolean isSpawn, int shift, @NotNull LongSet regionSectionKeys,
                       double mspt, int players) {
        this.isSpawn = isSpawn;
        this.shift = shift;
        this.regionSectionKeys = regionSectionKeys;
        this.mspt = mspt;
        this.players = players;
    }

    public boolean isSpawn() {
        return isSpawn;
    }

    public int getShift() {
        return shift;
    }

    public LongSet getRegionSectionKeys() {
        return regionSectionKeys;
    }

    public double getMspt() {
        return this.mspt;
    }

    public int getPlayers() {
        return this.players;
    }
}
