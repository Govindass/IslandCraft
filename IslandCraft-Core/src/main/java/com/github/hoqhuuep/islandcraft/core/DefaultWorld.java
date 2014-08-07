package com.github.hoqhuuep.islandcraft.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.minecraft.util.org.apache.commons.lang3.StringUtils;

import org.bukkit.configuration.ConfigurationSection;

import com.github.hoqhuuep.islandcraft.api.BiomeDistribution;
import com.github.hoqhuuep.islandcraft.api.ICBiome;
import com.github.hoqhuuep.islandcraft.api.ICIsland;
import com.github.hoqhuuep.islandcraft.api.ICLocation;
import com.github.hoqhuuep.islandcraft.api.ICRegion;
import com.github.hoqhuuep.islandcraft.api.ICWorld;
import com.github.hoqhuuep.islandcraft.api.IslandDistribution;
import com.github.hoqhuuep.islandcraft.core.IslandDatabase;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;

public class DefaultWorld implements ICWorld {
    private final String name;
    private final long seed;
    private final IslandDatabase database;
    private final BiomeDistribution ocean;
    private final IslandDistribution islandDistribution;
    private final List<String> islandGenerators;
    private final IslandCache cache;
    private final ICClassLoader classLoader;
    private final Cache<ICLocation, ICIsland> databaseCache;

    public DefaultWorld(final String name, final long seed, final IslandDatabase database, final ConfigurationSection config, final IslandCache cache, final ICClassLoader classLoader) {
        this.name = name;
        this.seed = seed;
        this.database = database;
        this.cache = cache;
        this.classLoader = classLoader;

        if (!config.isString("ocean")) {
            ICLogger.logger.warning("No string-value for 'worlds.' + name + '.ocean' found in config.yml");
            ICLogger.logger.warning("Default value 'com.github.hoqhuuep.islandcraft.core.ConstantBiomeDistribution DEEP_OCEAN' will be used");
        }
        ocean = classLoader.getBiomeDistribution(config.getString("ocean", "com.github.hoqhuuep.islandcraft.core.ConstantBiomeDistribution DEEP_OCEAN"));

        if (!config.isString("island-distribution")) {
            ICLogger.logger.warning("No string-value for 'worlds.' + name + '.island-distribution' found in config.yml");
            ICLogger.logger.warning("Default value 'com.github.hoqhuuep.islandcraft.core.EmptyIslandDistribution' will be used");
        }
        islandDistribution = classLoader.getIslandDistribution(config.getString("island-distribution", "com.github.hoqhuuep.islandcraft.core.EmptyIslandDistribution"));

        if (!config.isList("island-generators")) {
            ICLogger.logger.warning("No list-value for 'worlds.' + name + '.island-generators' found in config.yml");
            ICLogger.logger.warning("Default value '[com.github.hoqhuuep.islandcraft.core.EmptyIslandGenerator]' will be used");
        }
        islandGenerators = config.getStringList("island-generators");
        if (islandGenerators.isEmpty()) {
            islandGenerators.add("com.github.hoqhuuep.islandcraft.core.EmptyIslandGenerator");
        }
        // Load islandGenerators just to make sure there are no errors
        for (final String islandGenerator : islandGenerators) {
            classLoader.getIslandGenerator(islandGenerator);
        }
        databaseCache = CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.SECONDS).build(new DatabaseCacheLoader());
    }

    @Override
    public long getSeed() {
        return seed;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ICBiome getBiomeAt(final ICLocation location) {
        return getBiomeAt(location.getX(), location.getZ());
    }

    @Override
    public ICBiome getBiomeAt(final int x, final int z) {
        final ICIsland island = getIslandAt(x, z);
        if (island == null) {
            return ocean.biomeAt(x, z, seed);
        }
        final ICLocation origin = island.getInnerRegion().getMin();
        final ICBiome biome = island.getBiomeAt(x - origin.getX(), z - origin.getZ());
        if (biome == null) {
            return ocean.biomeAt(x, z, seed);
        }
        return biome;
    }

    @Override
    public ICBiome[] getBiomeChunk(ICLocation location) {
        return getBiomeChunk(location.getX(), location.getZ());
    }

    @Override
    public ICBiome[] getBiomeChunk(int x, int z) {
        final ICIsland island = getIslandAt(x, z);
        if (island == null) {
            final ICBiome[] chunk = new ICBiome[256];
            for (int i = 0; i < 256; ++i) {
                chunk[i] = ocean.biomeAt(x + i % 16, z + i / 16, seed);
            }
            return chunk;
        }
        final ICLocation origin = island.getInnerRegion().getMin();
        final ICBiome[] biomes = island.getBiomeChunk(x - origin.getX(), z - origin.getZ());
        if (biomes == null) {
            final ICBiome[] chunk = new ICBiome[256];
            for (int i = 0; i < 256; ++i) {
                chunk[i] = ocean.biomeAt(x + i % 16, z + i / 16, seed);
            }
            return chunk;
        }
        for (int i = 0; i < 256; ++i) {
            if (biomes[i] == null) {
                biomes[i] = ocean.biomeAt(x + i % 16, z + i / 16, seed);
            }
        }
        return biomes;
    }

    @Override
    public ICIsland getIslandAt(final ICLocation location) {
        return getIslandAt(location.getX(), location.getZ());
    }

    @Override
    public ICIsland getIslandAt(final int x, final int z) {
        final ICLocation center = islandDistribution.getCenterAt(x, z, seed);
        if (center == null) {
            return null;
        }
        return databaseCache.apply(center);
    }

    @Override
    public Set<ICIsland> getIslandsAt(final ICLocation location) {
        return getIslandsAt(location.getX(), location.getZ());
    }

    @Override
    public Set<ICIsland> getIslandsAt(final int x, final int z) {
        final Set<ICLocation> centers = islandDistribution.getCentersAt(x, z, seed);
        final Set<ICIsland> islands = new HashSet<ICIsland>(centers.size());
        for (final ICLocation center : centers) {
            islands.add(databaseCache.apply(center));
        }
        return islands;
    }

    private class DatabaseCacheLoader extends CacheLoader<ICLocation, ICIsland> {
        @Override
        public ICIsland load(final ICLocation center) {
            final ICRegion innerRegion = islandDistribution.getInnerRegion(center);
            final ICRegion outerRegion = islandDistribution.getOuterRegion(center);
            IslandDatabase.Result fromDatabase = database.load(name, center.getX(), center.getZ());
            if (fromDatabase == null) {
                final long islandSeed = pickIslandSeed(center.getX(), center.getZ());
                final String generator = pickIslandGenerator(islandSeed);
                database.save(name, center.getX(), center.getZ(), islandSeed, generator);
                return new DefaultIsland(innerRegion, outerRegion, islandSeed, classLoader.getIslandGenerator(generator), cache);
            }
            return new DefaultIsland(innerRegion, outerRegion, fromDatabase.getIslandSeed(), classLoader.getIslandGenerator(fromDatabase.getGenerator()), cache);
        }

        private long pickIslandSeed(final int centerX, final int centerZ) {
            return new Random(seed ^ ((long) centerX << 24 | centerZ & 0x00FFFFFFL)).nextLong();
        }

        private String pickIslandGenerator(final long islandSeed) {
            return islandGenerators.get(new Random(islandSeed).nextInt(islandGenerators.size()));
        }
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final DefaultWorld other = (DefaultWorld) obj;
        return StringUtils.equals(name, other.name);
    }
}
