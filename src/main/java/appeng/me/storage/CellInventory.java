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

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class CellInventory implements ICellInventory {

    private static final String ITEM_TYPE_TAG = "it";
    private static final String ITEM_COUNT_TAG = "ic";
    private static final String ITEM_SLOT = "#";
    private static final String ITEM_SLOT_COUNT = "@";
    private static final Set<Integer> BLACK_LIST = new HashSet<>();
    private static String[] itemSlots;
    private static String[] itemSlotCount;
    private final NBTTagCompound tagCompound;
    private final ISaveProvider container;
    private int maxItemTypes = 63;
    private short storedItemTypes = 0;
    private long storedItemCount = 0;
    private IItemList<IAEItemStack> cellItems;
    private final ItemStack cellItem;
    private IStorageCell cellType;

    private CellInventory(final ItemStack o, final ISaveProvider container) throws AppEngException {
        if (itemSlots == null) {
            itemSlots = new String[this.maxItemTypes];
            itemSlotCount = new String[this.maxItemTypes];

            for (int x = 0; x < this.maxItemTypes; x++) {
                itemSlots[x] = ITEM_SLOT + x;
                itemSlotCount[x] = ITEM_SLOT_COUNT + x;
            }
        }

        if (o == null) {
            throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
        }

        this.cellType = null;
        this.cellItem = o;

        final Item type = this.cellItem.getItem();

        if (type instanceof IStorageCell) {
            this.cellType = (IStorageCell) this.cellItem.getItem();
            this.maxItemTypes = this.cellType.getTotalTypes(this.cellItem);
        }

        if (this.cellType == null) {
            throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
        }

        if (!this.cellType.isStorageCell(this.cellItem)) {
            throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
        }

        if (this.maxItemTypes > 63) {
            this.maxItemTypes = 63;
        }
        if (this.maxItemTypes < 1) {
            this.maxItemTypes = 1;
        }

        this.container = container;
        this.tagCompound = Platform.openNbtData(o);
        this.storedItemTypes = this.tagCompound.getShort(ITEM_TYPE_TAG);
        this.storedItemCount = this.tagCompound.getLong(ITEM_COUNT_TAG);
        this.cellItems = null;
    }

    public static IMEInventoryHandler<IAEItemStack> getCell(final ItemStack o, final ISaveProvider container2) {
        try {
            return new CellInventoryHandler(new CellInventory(o, container2));
        } catch (final AppEngException e) {
            return null;
        }
    }

    private static boolean isStorageCell(final ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }

        try {
            final Item type = itemStack.getItem();

            if (type instanceof IStorageCell) {
                return !((IStorageCell) type).storableInStorageCell();
            }
        } catch (final Throwable err) {
            return true;
        }

        return false;
    }

    public static boolean isCell(final ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }

        final Item type = itemStack.getItem();

        if (type instanceof IStorageCell) {
            return ((IStorageCell) type).isStorageCell(itemStack);
        }

        return false;
    }

    public static void addBasicBlackList(final int itemID, final int meta) {
        BLACK_LIST.add((meta << Platform.DEF_OFFSET) | itemID);
    }

    private static boolean isBlackListed(final IAEItemStack input) {
        if (BLACK_LIST.contains(
                (OreDictionary.WILDCARD_VALUE << Platform.DEF_OFFSET) | Item.getIdFromItem(input.getItem()))) {
            return true;
        }

        return BLACK_LIST
                .contains((input.getItemDamage() << Platform.DEF_OFFSET) | Item.getIdFromItem(input.getItem()));
    }

    private boolean isEmpty(final IMEInventory<IAEItemStack> meInventory) {
        return meInventory.getAvailableItems(AEApi.instance().storage().createItemList()).isEmpty();
    }

    @Override
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode, final BaseActionSource src) {
        if (input == null) {
            return null;
        }

        if (input.getStackSize() == 0) {
            return null;
        }

        if (isBlackListed(input) || this.cellType.isBlackListed(this.cellItem, input)) {
            return input;
        }

        final ItemStack sharedItemStack = input.getItemStack();

        if (CellInventory.isStorageCell(sharedItemStack)) {
            final IMEInventory<IAEItemStack> meInventory = getCell(sharedItemStack, null);

            if (meInventory != null && !this.isEmpty(meInventory)) {
                return input;
            }
        }

        final IAEItemStack l = this.getCellItems().findPrecise(input);

        if (l != null) {
            final long remainingItemSlots = this.getRemainingItemCount();

            if (remainingItemSlots < 0) {
                return input;
            }

            if (input.getStackSize() > remainingItemSlots) {
                final IAEItemStack r = input.copy();
                r.setStackSize(r.getStackSize() - remainingItemSlots);

                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() + remainingItemSlots);
                    this.updateItemCount(remainingItemSlots);
                    this.saveChanges();
                }

                return r;
            } else {
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() + input.getStackSize());
                    this.updateItemCount(input.getStackSize());
                    this.saveChanges();
                }

                return null;
            }
        }

        if (this.canHoldNewItem()) // room for new type, and for at least one item!
        {
            final long remainingItemCount = this.getRemainingItemCount() - this.getBytesPerType() * 8L;

            if (remainingItemCount > 0) {
                if (input.getStackSize() > remainingItemCount) {
                    final IAEItemStack toReturn = AEItemStack.create(sharedItemStack);
                    toReturn.decStackSize(remainingItemCount);

                    if (mode == Actionable.MODULATE) {
                        final IAEItemStack toWrite = AEItemStack.create(sharedItemStack);
                        toWrite.setStackSize(remainingItemCount);

                        this.cellItems.add(toWrite);
                        this.updateItemCount(toWrite.getStackSize());
                        this.saveChanges();
                    }
                    return toReturn;
                }

                if (mode == Actionable.MODULATE) {
                    this.updateItemCount(input.getStackSize());
                    this.cellItems.add(input);
                    this.saveChanges();
                }

                return null;
            }
        }

        return input;
    }

    @Override
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final BaseActionSource src) {
        if (request == null) {
            return null;
        }

        final long size = request.getStackSize();

        IAEItemStack results = null;

        final IAEItemStack l = this.getCellItems().findPrecise(request);

        if (l != null) {
            results = l.copy();

            if (l.getStackSize() <= size) {
                results.setStackSize(l.getStackSize());

                if (mode == Actionable.MODULATE) {
                    this.updateItemCount(-l.getStackSize());
                    l.setStackSize(0);
                    this.saveChanges();
                }
            } else {
                results.setStackSize(size);

                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() - size);
                    this.updateItemCount(-size);
                    this.saveChanges();
                }
            }
        }

        return results;
    }

    private IItemList<IAEItemStack> getCellItems() {
        if (this.cellItems == null) {
            this.loadCellItems();
        }

        return this.cellItems;
    }

    private void updateItemCount(final long delta) {
        this.storedItemCount += delta;
        this.tagCompound.setLong(ITEM_COUNT_TAG, this.storedItemCount);
    }

    private void saveChanges() {
        // cellItems.clean();
        long itemCount = 0;

        // add new pretty stuff...
        int x = 0;

        for (final IAEItemStack v : this.cellItems) {
            itemCount += v.getStackSize();

            final NBTBase c = this.tagCompound.getTag(itemSlots[x]);

            if (c instanceof NBTTagCompound) {
                v.writeToNBT((NBTTagCompound) c);
            } else {
                final NBTTagCompound g = new NBTTagCompound();
                v.writeToNBT(g);
                this.tagCompound.setTag(itemSlots[x], g);
            }

            /*
             * NBTBase tagSlotCount = tagCompound.getTag( itemSlotCount[x] ); if ( tagSlotCount instanceof NBTTagInt )
             * ((NBTTagInt) tagSlotCount).data = (int) v.getStackSize(); else
             */
            this.tagCompound.setLong(itemSlotCount[x], v.getStackSize());

            x++;
        }

        // NBTBase tagType = tagCompound.getTag( ITEM_TYPE_TAG );
        // NBTBase tagCount = tagCompound.getTag( ITEM_COUNT_TAG );
        final short oldStoredItems = this.storedItemTypes;

        /*
         * if ( tagType instanceof NBTTagShort ) ((NBTTagShort) tagType).data = storedItems = (short) cellItems.size();
         * else
         */
        this.storedItemTypes = (short) this.cellItems.size();

        if (this.cellItems.isEmpty()) {
            this.tagCompound.removeTag(ITEM_TYPE_TAG);
        } else {
            this.tagCompound.setShort(ITEM_TYPE_TAG, this.storedItemTypes);
        }

        /*
         * if ( tagCount instanceof NBTTagInt ) ((NBTTagInt) tagCount).data = storedItemCount = itemCount; else
         */
        this.storedItemCount = itemCount;

        if (itemCount == 0) {
            this.tagCompound.removeTag(ITEM_COUNT_TAG);
        } else {
            this.tagCompound.setLong(ITEM_COUNT_TAG, itemCount);
        }

        // clean any old crusty stuff...
        for (; x < oldStoredItems && x < this.maxItemTypes; x++) {
            this.tagCompound.removeTag(itemSlots[x]);
            this.tagCompound.removeTag(itemSlotCount[x]);
        }

        if (this.container != null) {
            this.container.saveChanges(this);
        }
    }

    private void loadCellItems() {
        if (this.cellItems == null) {
            this.cellItems = AEApi.instance().storage().createPrimitiveItemList();
        }

        this.cellItems.resetStatus(); // clears totals and stuff.

        final int types = (int) this.getStoredItemTypes();

        for (int x = 0; x < types; x++) {
            final ItemStack t = ItemStack.loadItemStackFromNBT(this.tagCompound.getCompoundTag(itemSlots[x]));
            final IAEItemStack ias = AEItemStack.create(t);
            if (t != null) {
                ias.setStackSize(this.tagCompound.getLong(itemSlotCount[x]));
                if (ias.getStackSize() > 0) {
                    this.cellItems.add(ias);
                } else {
                    // Dirty Compact for EC2
                    ias.setStackSize(this.tagCompound.getCompoundTag(itemSlots[x]).getLong("Cnt"));
                    if (ias.getStackSize() > 0) {
                        this.cellItems.add(ias);
                    }
                }
            }
        }
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out) {
        for (final IAEItemStack i : this.getCellItems()) {
            out.add(i);
        }

        return out;
    }

    @Override
    public IAEItemStack getAvailableItem(@Nonnull IAEItemStack request) {
        long count = 0;
        for (final IAEItemStack is : this.getCellItems()) {
            if (is != null && is.getStackSize() > 0 && is.isSameType(request)) {
                count += is.getStackSize();
                if (count < 0) {
                    // overflow
                    count = Long.MAX_VALUE;
                    break;
                }
            }
        }
        return count == 0 ? null : request.copy().setStackSize(count);
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public ItemStack getItemStack() {
        return this.cellItem;
    }

    @Override
    public double getIdleDrain() {
        return this.cellType.getIdleDrain(this.cellItem);
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return this.cellType.getFuzzyMode(this.cellItem);
    }

    @Override
    public String getOreFilter() {
        return this.cellType.getOreFilter(this.cellItem);
    }

    @Override
    public IInventory getConfigInventory() {
        return this.cellType.getConfigInventory(this.cellItem);
    }

    @Override
    public IInventory getUpgradesInventory() {
        return this.cellType.getUpgradesInventory(this.cellItem);
    }

    @Override
    public int getBytesPerType() {
        return this.cellType.getBytesPerType(this.cellItem);
    }

    @Override
    public boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();

        return (bytesFree > this.getBytesPerType()
                || (bytesFree == this.getBytesPerType() && this.getUnusedItemCount() > 0))
                && this.getRemainingItemTypes() > 0;
    }

    @Override
    public long getTotalBytes() {
        return this.cellType.getBytesLong(this.cellItem);
    }

    @Override
    public long getFreeBytes() {
        return this.getTotalBytes() - this.getUsedBytes();
    }

    @Override
    public long getUsedBytes() {
        final long bytesForItemCount = (this.getStoredItemCount() + this.getUnusedItemCount()) / 8;

        return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
    }

    @Override
    public long getTotalItemTypes() {
        return this.maxItemTypes;
    }

    @Override
    public long getStoredItemCount() {
        return this.storedItemCount;
    }

    @Override
    public long getStoredItemTypes() {
        return this.storedItemTypes;
    }

    @Override
    public long getRemainingItemTypes() {
        final long basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        final long baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();

        return basedOnStorage > baseOnTotal ? baseOnTotal : basedOnStorage;
    }

    @Override
    public long getRemainingItemCount() {
        final long remaining = this.getFreeBytes() * 8 + this.getUnusedItemCount();

        return remaining > 0 ? remaining : 0;
    }

    @Override
    public int getUnusedItemCount() {
        final long div = this.getStoredItemCount() % 8;

        if (div == 0) {
            return 0;
        }

        return (int) (8 - div);
    }

    @Override
    public int getStatusForCell() {
        if (this.canHoldNewItem()) {
            return 1;
        }
        if (this.getRemainingItemCount() > 0) {
            return 2;
        }
        return 3;
    }
}
