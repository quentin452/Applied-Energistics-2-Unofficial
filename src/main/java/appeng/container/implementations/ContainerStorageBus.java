/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import java.util.Iterator;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.OptionalSlotFakeTypeOnly;
import appeng.container.slot.SlotRestrictedInput;
import appeng.parts.misc.PartStorageBus;
import appeng.util.Platform;
import appeng.util.iterators.NullIterator;

public class ContainerStorageBus extends ContainerUpgradeable {

    private final PartStorageBus storageBus;

    @GuiSync(3)
    public AccessRestriction rwMode = AccessRestriction.READ_WRITE;

    @GuiSync(4)
    public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

    @GuiSync(7)
    public YesNo stickyMode = YesNo.NO;

    public ContainerStorageBus(final InventoryPlayer ip, final PartStorageBus te) {
        super(ip, te);
        this.storageBus = te;
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        final int xo = 8;
        final int yo = 23 + 6;

        final IInventory config = this.getUpgradeable().getInventoryByName("config");
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlotToContainer(new OptionalSlotFakeTypeOnly(config, this, y * 9 + x, xo, yo, x, y, y));
            }
        }

        final IInventory upgrades = this.getUpgradeable().getInventoryByName("upgrades");
        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        upgrades,
                        0,
                        187,
                        8,
                        this.getInventoryPlayer())).setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        upgrades,
                        1,
                        187,
                        8 + 18,
                        this.getInventoryPlayer())).setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        upgrades,
                        2,
                        187,
                        8 + 18 * 2,
                        this.getInventoryPlayer())).setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        upgrades,
                        3,
                        187,
                        8 + 18 * 3,
                        this.getInventoryPlayer())).setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        upgrades,
                        4,
                        187,
                        8 + 18 * 4,
                        this.getInventoryPlayer())).setNotDraggable());
    }

    @Override
    protected boolean supportCapacity() {
        return true;
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            this.setFuzzyMode((FuzzyMode) this.getUpgradeable().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setReadWriteMode(
                    (AccessRestriction) this.getUpgradeable().getConfigManager().getSetting(Settings.ACCESS));
            this.setStorageFilter(
                    (StorageFilter) this.getUpgradeable().getConfigManager().getSetting(Settings.STORAGE_FILTER));
            this.setStickyMode((YesNo) this.getUpgradeable().getConfigManager().getSetting(Settings.STICKY_MODE));
        }

        this.standardDetectAndSendChanges();
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        if (this.getUpgradeable().getInstalledUpgrades(Upgrades.ORE_FILTER) > 0) return false;

        final int upgrades = this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);

        return upgrades > (idx - 2);
    }

    public void clear() {
        final IInventory inv = this.getUpgradeable().getInventoryByName("config");
        for (int x = 0; x < inv.getSizeInventory(); x++) {
            inv.setInventorySlotContents(x, null);
        }
        this.detectAndSendChanges();
    }

    public void partition() {
        final IInventory inv = this.getUpgradeable().getInventoryByName("config");

        final IMEInventory<IAEItemStack> cellInv = this.storageBus.getInternalHandler();

        Iterator<IAEItemStack> i = new NullIterator<>();
        if (cellInv != null) {
            final IItemList<IAEItemStack> list = cellInv.getAvailableItems(AEApi.instance().storage().createItemList());
            i = list.iterator();
        }

        for (int x = 0; x < inv.getSizeInventory(); x++) {
            if (i.hasNext() && this.isSlotEnabled((x / 9) - 2)) {
                final ItemStack g = i.next().getItemStack();
                g.stackSize = 1;
                inv.setInventorySlotContents(x, g);
            } else {
                inv.setInventorySlotContents(x, null);
            }
        }

        this.detectAndSendChanges();
    }

    public AccessRestriction getReadWriteMode() {
        return this.rwMode;
    }

    public StorageFilter getStorageFilter() {
        return this.storageFilter;
    }

    private void setStorageFilter(final StorageFilter storageFilter) {
        this.storageFilter = storageFilter;
    }

    public YesNo getStickyMode() {
        return this.stickyMode;
    }

    private void setStickyMode(final YesNo stickyMode) {
        this.stickyMode = stickyMode;
    }

    private void setReadWriteMode(final AccessRestriction rwMode) {
        this.rwMode = rwMode;
    }

}
