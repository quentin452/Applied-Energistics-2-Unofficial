/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.worldgen;

import appeng.api.features.IWorldGen.WorldGenType;
import appeng.core.AEConfig;
import appeng.core.features.registries.WorldGenRegistry;
import appeng.core.worlddata.WorldData;
import appeng.hooks.TickHandler;
import appeng.util.IWorldCallable;
import appeng.util.Platform;
import appeng.worldgen.meteorite.ChunkOnly;
import cpw.mods.fml.common.IWorldGenerator;
import java.util.Random;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

public final class MeteoriteWorldGen implements IWorldGenerator {
    @Override
    public void generate(
            final Random r,
            final int chunkX,
            final int chunkZ,
            final World w,
            final IChunkProvider chunkGenerator,
            final IChunkProvider chunkProvider) {
        if (WorldGenRegistry.INSTANCE.isWorldGenEnabled(WorldGenType.Meteorites, w)) {
            // Find the meteorite grid cell corresponding to this chunk
            final int gridCellSize = Math.max(8, AEConfig.instance.minMeteoriteDistance);
            final int gridCellMargin = Math.max(1, gridCellSize / 10);
            final int gridX = Math.floorDiv(chunkX << 4, gridCellSize);
            final int gridZ = Math.floorDiv(chunkZ << 4, gridCellSize);
            // Override chunk-based seed with grid-based seed, constructed in the same way as the FML-provided seed
            final long worldSeed = w.getSeed();
            r.setSeed(worldSeed);
            final long xSeed = r.nextLong() >> 2 + 1L;
            final long zSeed = r.nextLong() >> 2 + 1L;
            final long gridSeed = (xSeed * gridX + zSeed * gridZ) ^ worldSeed;
            r.setSeed(gridSeed);
            // Calculate a deterministic position of the meteorite in the grid cell
            final boolean spawnMeteor = r.nextDouble() < AEConfig.instance.meteoriteSpawnChance;
            final int meteorX = (gridX * gridCellSize) + r.nextInt(gridCellSize - 2 * gridCellMargin) + gridCellMargin;
            final int meteorZ = (gridZ * gridCellSize) + r.nextInt(gridCellSize - 2 * gridCellMargin) + gridCellMargin;
            final int meteorDepth = 180 + r.nextInt(20);
            final int meteorChunkX = meteorX >> 4;
            final int meteorChunkZ = meteorZ >> 4;
            // add new meteorites?
            if (spawnMeteor && (meteorChunkX == chunkX) && (meteorChunkZ == chunkZ)) {
                TickHandler.INSTANCE.addCallable(w, new MeteoriteSpawn(meteorX, meteorDepth, meteorZ));
            } else {
                TickHandler.INSTANCE.addCallable(w, new MeteoriteSpawn(chunkX << 4, 128, chunkZ << 4));
            }
        } else {
            WorldData.instance().compassData().service().updateArea(w, chunkX, chunkZ);
        }
    }

    private static class MeteoriteSpawn implements IWorldCallable<Object> {

        private final int x;
        private final int z;
        private final int depth;

        public MeteoriteSpawn(final int x, final int depth, final int z) {
            this.x = x;
            this.z = z;
            this.depth = depth;
        }

        private Iterable<NBTTagCompound> getNearByMeteorites(final World w, final int chunkX, final int chunkZ) {
            return WorldData.instance().spawnData().getNearByMeteorites(w.provider.dimensionId, chunkX, chunkZ);
        }

        private boolean tryMeteorite(final World w) {
            int depth = this.depth;
            for (int tries = 0; tries < 20; tries++) {
                final MeteoritePlacer mp = new MeteoritePlacer();

                if (mp.spawnMeteorite(new ChunkOnly(w, x >> 4, z >> 4), x, depth, z)) {
                    final int px = x >> 4;
                    final int pz = z >> 4;

                    for (int cx = px - 6; cx < px + 6; cx++) {
                        for (int cz = pz - 6; cz < pz + 6; cz++) {
                            if (w.getChunkProvider().chunkExists(cx, cz)) {
                                if (px == cx && pz == cz) {
                                    continue;
                                }

                                if (WorldData.instance().spawnData().hasGenerated(w.provider.dimensionId, cx, cz)) {
                                    final MeteoritePlacer mp2 = new MeteoritePlacer();
                                    mp2.spawnMeteorite(new ChunkOnly(w, cx, cz), mp.getSettings());
                                }
                            }
                        }
                    }

                    System.err.printf("Meteorite spawned at %d, %d, %d\n", x, depth, z);
                    return true;
                }

                depth -= 15;
                if (depth < 40) {
                    return false;
                }
            }

            return false;
        }

        @Override
        public Object call(final World world) throws Exception {
            final int chunkX = this.x >> 4;
            final int chunkZ = this.z >> 4;

            // Generate blocks for nearby meteorites
            for (final NBTTagCompound data : this.getNearByMeteorites(world, chunkX, chunkZ)) {
                final MeteoritePlacer mp = new MeteoritePlacer();
                mp.spawnMeteorite(new ChunkOnly(world, chunkX, chunkZ), data);
            }

            this.tryMeteorite(world);

            WorldData.instance().spawnData().setGenerated(world.provider.dimensionId, chunkX, chunkZ);
            WorldData.instance().compassData().service().updateArea(world, chunkX, chunkZ);

            return null;
        }
    }
}
