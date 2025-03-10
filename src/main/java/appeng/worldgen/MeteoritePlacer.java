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

import appeng.api.AEApi;
import appeng.api.definitions.IBlockDefinition;
import appeng.api.definitions.IBlocks;
import appeng.api.definitions.IMaterials;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.core.worlddata.WorldData;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.worldgen.meteorite.Fallout;
import appeng.worldgen.meteorite.FalloutCopy;
import appeng.worldgen.meteorite.FalloutSand;
import appeng.worldgen.meteorite.FalloutSnow;
import appeng.worldgen.meteorite.IMeteoriteWorld;
import appeng.worldgen.meteorite.MeteoriteBlockPutter;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public final class MeteoritePlacer {
    private static final double PRESSES_SPAWN_CHANCE = 0.7;
    private static final int SKYSTONE_SPAWN_LIMIT = 12;
    private final Collection<Block> validSpawn = new HashSet<>();
    private final Collection<Block> invalidSpawn = new HashSet<>();
    private final IBlockDefinition skyChestDefinition;
    private final IBlockDefinition skyStoneDefinition;
    private final MeteoriteBlockPutter putter = new MeteoriteBlockPutter();
    private double meteoriteSize = (Math.random() * 6.0) + 2;
    private double realCrater = this.meteoriteSize * 2 + 5;
    private double squaredMeteoriteSize = this.meteoriteSize * this.meteoriteSize;
    private double crater = this.realCrater * this.realCrater;
    private NBTTagCompound settings;
    private Fallout type;

    public MeteoritePlacer() {
        final IBlocks blocks = AEApi.instance().definitions().blocks();

        this.skyChestDefinition = blocks.skyChest();
        this.skyStoneDefinition = blocks.skyStone();

        this.validSpawn.add(Blocks.stone);
        this.validSpawn.add(Blocks.cobblestone);
        this.validSpawn.add(Blocks.grass);
        this.validSpawn.add(Blocks.sand);
        this.validSpawn.add(Blocks.dirt);
        this.validSpawn.add(Blocks.gravel);
        this.validSpawn.add(Blocks.netherrack);
        this.validSpawn.add(Blocks.iron_ore);
        this.validSpawn.add(Blocks.gold_ore);
        this.validSpawn.add(Blocks.diamond_ore);
        this.validSpawn.add(Blocks.redstone_ore);
        this.validSpawn.add(Blocks.hardened_clay);
        this.validSpawn.add(Blocks.ice);
        this.validSpawn.add(Blocks.snow);
        this.validSpawn.add(Blocks.stained_hardened_clay);

        for (final Block skyStoneBlock : this.skyStoneDefinition.maybeBlock().asSet()) {
            this.invalidSpawn.add(skyStoneBlock);
        }
        this.invalidSpawn.add(Blocks.planks);
        this.invalidSpawn.add(Blocks.iron_door);
        this.invalidSpawn.add(Blocks.iron_bars);
        this.invalidSpawn.add(Blocks.wooden_door);
        this.invalidSpawn.add(Blocks.brick_block);
        this.invalidSpawn.add(Blocks.clay);
        this.invalidSpawn.add(Blocks.water);
        this.invalidSpawn.add(Blocks.log);
        this.invalidSpawn.add(Blocks.log2);

        this.type = new Fallout(this.putter, this.skyStoneDefinition);
    }

    boolean spawnMeteorite(final IMeteoriteWorld w, final NBTTagCompound meteoriteBlob) {
        this.settings = meteoriteBlob;

        final int x = this.settings.getInteger("x");
        final int y = this.settings.getInteger("y");
        final int z = this.settings.getInteger("z");

        this.meteoriteSize = this.settings.getDouble("real_sizeOfMeteorite");
        this.realCrater = this.settings.getDouble("realCrater");
        this.squaredMeteoriteSize = this.settings.getDouble("sizeOfMeteorite");
        this.crater = this.settings.getDouble("crater");

        final Block blk = Block.getBlockById(this.settings.getInteger("blk"));

        if (blk == Blocks.sand) {
            this.type = new FalloutSand(w, x, y, z, this.putter, this.skyStoneDefinition);
        } else if (blk == Blocks.hardened_clay) {
            this.type = new FalloutCopy(w, x, y, z, this.putter, this.skyStoneDefinition);
        } else if (blk == Blocks.ice || blk == Blocks.snow) {
            this.type = new FalloutSnow(w, x, y, z, this.putter, this.skyStoneDefinition);
        }

        final int skyMode = this.settings.getInteger("skyMode");

        // creator
        if (skyMode > 10) {
            this.placeCrater(w, x, y, z);
        }

        this.placeMeteorite(w, x, y, z);

        // collapse blocks...
        if (skyMode > 3) {
            this.decay(w, x, y, z);
        }

        w.done();
        return true;
    }

    private void placeCrater(final IMeteoriteWorld w, final int x, final int y, final int z) {
        final boolean lava = this.settings.getBoolean("lava");

        final int maxY = 255;
        final int minX = w.minX(x - 200);
        final int maxX = w.maxX(x + 200);
        final int minZ = w.minZ(z - 200);
        final int maxZ = w.maxZ(z + 200);

        for (int j = y - 5; j < maxY; j++) {
            boolean changed = false;

            for (int i = minX; i < maxX; i++) {
                for (int k = minZ; k < maxZ; k++) {
                    final double dx = i - x;
                    final double dz = k - z;
                    final double h = y - this.meteoriteSize + 1 + this.type.adjustCrater();

                    final double distanceFrom = dx * dx + dz * dz;

                    if (j > h + distanceFrom * 0.02) {
                        if (lava && j < y && w.getBlock(x, y - 1, z).isBlockSolid(w.getWorld(), i, j, k, 0)) {
                            if (j > h + distanceFrom * 0.02) {
                                this.putter.put(w, i, j, k, Blocks.lava);
                            }
                        } else {
                            changed = this.putter.put(w, i, j, k, Platform.AIR_BLOCK) || changed;
                        }
                    }
                }
            }
        }

        for (final Entity o : w.getWorld()
                .getEntitiesWithinAABB(
                        EntityItem.class,
                        AxisAlignedBB.getBoundingBox(
                                w.minX(x - 30), y - 5, w.minZ(z - 30), w.maxX(x + 30), y + 30, w.maxZ(z + 30)))) {
            o.setDead();
        }
    }

    private void placeMeteorite(final IMeteoriteWorld w, final int x, final int y, final int z) {
        final int meteorXLength = w.minX(x - 8);
        final int meteorXHeight = w.maxX(x + 8);
        final int meteorZLength = w.minZ(z - 8);
        final int meteorZHeight = w.maxZ(z + 8);

        // spawn meteor
        for (int i = meteorXLength; i < meteorXHeight; i++) {
            for (int j = y - 8; j < y + 8; j++) {
                for (int k = meteorZLength; k < meteorZHeight; k++) {
                    final double dx = i - x;
                    final double dy = j - y;
                    final double dz = k - z;

                    if (dx * dx * 0.7 + dy * dy * (j > y ? 1.4 : 0.8) + dz * dz * 0.7 < this.squaredMeteoriteSize) {
                        for (final Block skyStoneBlock :
                                this.skyStoneDefinition.maybeBlock().asSet()) {
                            this.putter.put(w, i, j, k, skyStoneBlock);
                        }
                    }
                }
            }
        }

        if (AEConfig.instance.isFeatureEnabled(AEFeature.SpawnPressesInMeteorites)) {
            for (final Block skyChestBlock :
                    this.skyChestDefinition.maybeBlock().asSet()) {
                this.putter.put(w, x, y, z, skyChestBlock);
            }

            final TileEntity te = w.getTileEntity(x, y, z);
            if (te instanceof IInventory) {
                final InventoryAdaptor ap = InventoryAdaptor.getAdaptor(te, ForgeDirection.UP);

                int primary = Math.max(1, (int) (Math.random() * 4));

                if (primary > 3) // in case math breaks...
                {
                    primary = 3;
                }

                for (int zz = 0; zz < primary; zz++) {
                    int r = 0;
                    boolean duplicate = false;

                    do {
                        duplicate = false;

                        if (Math.random() > PRESSES_SPAWN_CHANCE) {
                            r = WorldData.instance().storageData().getNextOrderedValue("presses");
                        } else {
                            r = (int) (Math.random() * 1000);
                        }

                        ItemStack toAdd = null;
                        final IMaterials materials =
                                AEApi.instance().definitions().materials();

                        switch (r % 4) {
                            case 0:
                                for (final ItemStack calc : materials
                                        .calcProcessorPress()
                                        .maybeStack(1)
                                        .asSet()) {
                                    toAdd = calc;
                                }
                                break;
                            case 1:
                                for (final ItemStack calc : materials
                                        .engProcessorPress()
                                        .maybeStack(1)
                                        .asSet()) {
                                    toAdd = calc;
                                }
                                break;
                            case 2:
                                for (final ItemStack calc : materials
                                        .logicProcessorPress()
                                        .maybeStack(1)
                                        .asSet()) {
                                    toAdd = calc;
                                }
                                break;
                            case 3:
                                for (final ItemStack calc :
                                        materials.siliconPress().maybeStack(1).asSet()) {
                                    toAdd = calc;
                                }
                                break;
                            default:
                        }

                        if (toAdd != null) {
                            if (ap.simulateRemove(1, toAdd, null) == null) {
                                ap.addItems(toAdd);
                            } else {
                                duplicate = true;
                            }
                        }
                    } while (duplicate);
                }

                final int secondary = Math.max(1, (int) (Math.random() * 3));
                for (int zz = 0; zz < secondary; zz++) {
                    switch ((int) (Math.random() * 1000) % 3) {
                        case 0:
                            final int amount = (int) ((Math.random() * SKYSTONE_SPAWN_LIMIT) + 1);
                            for (final ItemStack skyStoneStack :
                                    this.skyStoneDefinition.maybeStack(amount).asSet()) {
                                ap.addItems(skyStoneStack);
                            }
                            break;
                        case 1:
                            final List<ItemStack> possibles = new LinkedList<ItemStack>();
                            possibles.addAll(OreDictionary.getOres("nuggetIron"));
                            possibles.addAll(OreDictionary.getOres("nuggetCopper"));
                            possibles.addAll(OreDictionary.getOres("nuggetTin"));
                            possibles.addAll(OreDictionary.getOres("nuggetSilver"));
                            possibles.addAll(OreDictionary.getOres("nuggetLead"));
                            possibles.addAll(OreDictionary.getOres("nuggetPlatinum"));
                            possibles.addAll(OreDictionary.getOres("nuggetNickel"));
                            possibles.addAll(OreDictionary.getOres("nuggetAluminium"));
                            possibles.addAll(OreDictionary.getOres("nuggetElectrum"));
                            possibles.add(new ItemStack(net.minecraft.init.Items.gold_nugget));

                            ItemStack nugget = Platform.pickRandom(possibles);
                            if (nugget != null) {
                                nugget = nugget.copy();
                                nugget.stackSize = (int) (Math.random() * 12) + 1;
                                ap.addItems(nugget);
                            }
                            break;
                    }
                }
            }
        }
    }

    private void decay(final IMeteoriteWorld w, final int x, final int y, final int z) {
        double randomShit = 0;

        final int meteorXLength = w.minX(x - 30);
        final int meteorXHeight = w.maxX(x + 30);
        final int meteorZLength = w.minZ(z - 30);
        final int meteorZHeight = w.maxZ(z + 30);

        for (int i = meteorXLength; i < meteorXHeight; i++) {
            for (int k = meteorZLength; k < meteorZHeight; k++) {
                for (int j = y - 9; j < y + 30; j++) {
                    Block blk = w.getBlock(i, j, k);
                    if (blk == Blocks.lava) {
                        continue;
                    }

                    if (blk.isReplaceable(w.getWorld(), i, j, k)) {
                        blk = Platform.AIR_BLOCK;
                        final Block blk_b = w.getBlock(i, j + 1, k);

                        if (blk_b != blk) {
                            final int meta_b = w.getBlockMetadata(i, j + 1, k);

                            w.setBlock(i, j, k, blk_b, meta_b, 3);
                            w.setBlock(i, j + 1, k, blk);
                        } else if (randomShit < 100 * this.crater) {
                            final double dx = i - x;
                            final double dy = j - y;
                            final double dz = k - z;
                            final double dist = dx * dx + dy * dy + dz * dz;

                            final Block xf = w.getBlock(i, j - 1, k);
                            if (!xf.isReplaceable(w.getWorld(), i, j - 1, k)) {
                                final double extraRange = Math.random() * 0.6;
                                final double height =
                                        this.crater * (extraRange + 0.2) - Math.abs(dist - this.crater * 1.7);

                                if (xf != blk && height > 0 && Math.random() > 0.6) {
                                    randomShit++;
                                    this.type.getRandomFall(w, i, j, k);
                                }
                            }
                        }
                    } else {
                        // decay.
                        final Block blk_b = w.getBlock(i, j + 1, k);
                        if (blk_b == Platform.AIR_BLOCK) {
                            if (Math.random() > 0.4) {
                                final double dx = i - x;
                                final double dy = j - y;
                                final double dz = k - z;

                                if (dx * dx + dy * dy + dz * dz < this.crater * 1.6) {
                                    this.type.getRandomInset(w, i, j, k);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    double getSqDistance(final int x, final int z) {
        final int chunkX = this.settings.getInteger("x") - x;
        final int chunkZ = this.settings.getInteger("z") - z;

        return chunkX * chunkX + chunkZ * chunkZ;
    }

    public boolean spawnMeteorite(final IMeteoriteWorld w, final int x, final int y, final int z) {

        if (!w.hasNoSky()) {
            return false;
        }

        Block blk = w.getBlock(x, y, z);
        if (!this.validSpawn.contains(blk)) {
            return false; // must spawn on a valid block..
        }

        this.settings = new NBTTagCompound();
        this.settings.setInteger("x", x);
        this.settings.setInteger("y", y);
        this.settings.setInteger("z", z);
        this.settings.setInteger("blk", Block.getIdFromBlock(blk));

        this.settings.setDouble("real_sizeOfMeteorite", this.meteoriteSize);
        this.settings.setDouble("realCrater", this.realCrater);
        this.settings.setDouble("sizeOfMeteorite", this.squaredMeteoriteSize);
        this.settings.setDouble("crater", this.crater);

        this.settings.setBoolean("lava", Math.random() > 0.9);

        if (blk == Blocks.sand) {
            this.type = new FalloutSand(w, x, y, z, this.putter, this.skyStoneDefinition);
        } else if (blk == Blocks.hardened_clay) {
            this.type = new FalloutCopy(w, x, y, z, this.putter, this.skyStoneDefinition);
        } else if (blk == Blocks.ice || blk == Blocks.snow) {
            this.type = new FalloutSnow(w, x, y, z, this.putter, this.skyStoneDefinition);
        }

        int realValidBlocks = 0;

        for (int i = x - 6; i < x + 6; i++) {
            for (int j = y - 6; j < y + 6; j++) {
                for (int k = z - 6; k < z + 6; k++) {
                    blk = w.getBlock(i, j, k);
                    if (this.validSpawn.contains(blk)) {
                        realValidBlocks++;
                    }
                }
            }
        }

        int validBlocks = 0;
        for (int i = x - 15; i < x + 15; i++) {
            for (int j = y - 15; j < y + 15; j++) {
                for (int k = z - 15; k < z + 15; k++) {
                    blk = w.getBlock(i, j, k);
                    if (this.invalidSpawn.contains(blk)) {
                        return false;
                    }
                    if (this.validSpawn.contains(blk)) {
                        validBlocks++;
                    }
                }
            }
        }

        final int minBLocks = 200;
        if (validBlocks > minBLocks && realValidBlocks > 80) {
            // we can spawn here!

            int skyMode = 0;

            for (int i = x - 15; i < x + 15; i++) {
                for (int j = y - 15; j < y + 11; j++) {
                    for (int k = z - 15; k < z + 15; k++) {
                        if (w.canBlockSeeTheSky(i, j, k)) {
                            skyMode++;
                        }
                    }
                }
            }

            boolean solid = true;
            for (int j = y - 15; j < y - 1; j++) {
                if (w.getBlock(x, j, z) == Platform.AIR_BLOCK) {
                    solid = false;
                }
            }

            if (!solid) {
                skyMode = 0;
            }

            // creator
            if (skyMode > 10) {
                this.placeCrater(w, x, y, z);
            }

            this.placeMeteorite(w, x, y, z);

            // collapse blocks...
            if (skyMode > 3) {
                this.decay(w, x, y, z);
            }

            this.settings.setInteger("skyMode", skyMode);
            w.done();

            WorldData.instance()
                    .spawnData()
                    .addNearByMeteorites(w.getWorld().provider.dimensionId, x >> 4, z >> 4, this.settings);
            return true;
        }
        return false;
    }

    NBTTagCompound getSettings() {
        return this.settings;
    }
}
