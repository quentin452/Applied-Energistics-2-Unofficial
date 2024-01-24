/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.storage;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.OreFilteredList;
import appeng.util.prioitylist.PrecisePriorityList;

public class CellInventoryHandler extends MEInventoryHandler<IAEItemStack>
        implements ICellInventoryHandler, ICellCacheRegistry {

    CellInventoryHandler(final IMEInventory<IAEItemStack> c) {
        super(c, StorageChannel.ITEMS);

        final ICellInventory ci = this.getCellInv();

        if (ci != null) {
            final IInventory upgrades = ci.getUpgradesInventory();
            final IInventory config = ci.getConfigInventory();
            final FuzzyMode fzMode = ci.getFuzzyMode();
            final String filter = ci.getOreFilter();

            boolean hasInverter = false;
            boolean hasFuzzy = false;
            boolean hasOreFilter = false;
            boolean hasSticky = false;

            for (int x = 0; x < upgrades.getSizeInventory(); x++) {
                final ItemStack is = upgrades.getStackInSlot(x);
                if (is != null && is.getItem() instanceof IUpgradeModule) {
                    final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                    if (u != null) {
                        switch (u) {
                            case FUZZY -> hasFuzzy = true;
                            case INVERTER -> hasInverter = true;
                            case ORE_FILTER -> hasOreFilter = true;
                            case STICKY -> hasSticky = true;
                            default -> {}
                        }
                    }
                }
            }
            this.setWhitelist(hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);

            if (hasSticky) {
                setSticky(true);
            }

            if (hasOreFilter && !filter.isEmpty()) {
                this.setPartitionList(new OreFilteredList(filter));
            } else {
                final IItemList<IAEItemStack> priorityList = AEApi.instance().storage().createItemList();
                for (int x = 0; x < config.getSizeInventory(); x++) {
                    final ItemStack is = config.getStackInSlot(x);
                    if (is != null) {
                        priorityList.add(AEItemStack.create(is));
                    }
                }
                if (!priorityList.isEmpty()) {
                    if (hasFuzzy) {
                        this.setPartitionList(new FuzzyPriorityList<>(priorityList, fzMode));
                    } else {
                        this.setPartitionList(new PrecisePriorityList<>(priorityList));
                    }
                }
            }
        }
    }

    @Override
    public ICellInventory getCellInv() {
        Object o = this.getInternal();
        if (o instanceof MEPassThrough) {
            o = ((MEPassThrough<?>) o).getInternal();
        }
        return (ICellInventory) (o instanceof ICellInventory ? o : null);
    }

    @Override
    public boolean isPreformatted() {
        return !this.getPartitionList().isEmpty();
    }

    @Override
    public boolean isFuzzy() {
        return this.getPartitionList() instanceof FuzzyPriorityList;
    }

    @Override
    public IncludeExclude getIncludeExcludeMode() {
        return this.getWhitelist();
    }

    public int getStatusForCell() {
        int val = this.getCellInv().getStatusForCell();

        if (val == 1 && this.isPreformatted()) {
            val = 2;
        }

        return val;
    }

    @Override
    public boolean canGetInv() {
        return this.getCellInv() != null;
    }

    @Override
    public long getTotalBytes() {
        return this.getCellInv().getTotalBytes();
    }

    @Override
    public long getFreeBytes() {
        return this.getCellInv().getFreeBytes();
    }

    @Override
    public long getUsedBytes() {
        return this.getCellInv().getUsedBytes();
    }

    @Override
    public long getTotalTypes() {
        return this.getCellInv().getTotalItemTypes();
    }

    @Override
    public long getFreeTypes() {
        return this.getCellInv().getRemainingItemTypes();
    }

    @Override
    public long getUsedTypes() {
        return this.getCellInv().getStoredItemTypes();
    }

    @Override
    public int getCellStatus() {
        return this.getStatusForCell();
    }

    @Override
    public TYPE getCellType() {
        return TYPE.ITEM;
    }
}
