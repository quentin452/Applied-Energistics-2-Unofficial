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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.ICraftingCPUSelectorContainer;
import appeng.core.AELog;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingTreeData;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.crafting.v2.CraftingJobV2;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.parts.reporting.PartTerminal;
import appeng.util.Platform;

public class ContainerCraftConfirm extends AEBaseContainer implements ICraftingCPUSelectorContainer {

    private Future<ICraftingJob> job;
    private ICraftingJob result;

    @GuiSync(0)
    public long bytesUsed;

    @GuiSync(1)
    public long cpuBytesAvail;

    @GuiSync(2)
    public int cpuCoProcessors;

    @GuiSync(3)
    public boolean autoStart = false;

    @GuiSync(4)
    public boolean simulation = true;

    @GuiSync(6)
    public boolean noCPU = true;

    @GuiSync(7)
    public String myName = "";

    @GuiSync.Recurse(8)
    public final ContainerCPUTable cpuTable;

    public ContainerCraftConfirm(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
        this.cpuTable = new ContainerCPUTable(this, this::onCPUUpdate, false, this::cpuMatches);
    }

    @Override
    public void selectCPU(int cpu) {
        this.cpuTable.selectCPU(cpu);
    }

    public void onCPUUpdate(ICraftingCPU cpu) {
        if (cpu == null) {
            this.setCpuAvailableBytes(0);
            this.setCpuCoProcessors(0);
            this.setName("");
        } else {
            this.setName(cpu.getName());
            this.setCpuAvailableBytes(cpu.getAvailableStorage());
            this.setCpuCoProcessors(cpu.getCoProcessors());
        }
    }

    @Override
    public void detectAndSendChanges() {
        // Wait with CPU selection until job bytes are retrieved
        if (this.bytesUsed != 0) {
            cpuTable.detectAndSendChanges(getGrid(), crafters);
        }
        if (Platform.isClient()) {
            return;
        }

        this.setNoCPU(this.cpuTable.getCPUs().isEmpty());

        super.detectAndSendChanges();

        if (this.getJob() != null && this.getJob().isDone()) {
            try {
                this.result = this.getJob().get();

                if (!this.result.isSimulation()) {
                    this.setSimulation(false);
                    if (this.isAutoStart()) {
                        this.startJob();
                        return;
                    }
                } else {
                    this.setSimulation(true);
                }

                try {
                    final PacketMEInventoryUpdate storageUpdate = new PacketMEInventoryUpdate((byte) 0);
                    final PacketMEInventoryUpdate pendingUpdate = new PacketMEInventoryUpdate((byte) 1);
                    final PacketMEInventoryUpdate missingUpdate = this.result.isSimulation()
                            ? new PacketMEInventoryUpdate((byte) 2)
                            : null;

                    final IItemList<IAEItemStack> plan = AEApi.instance().storage().createItemList();
                    this.result.populatePlan(plan);

                    this.setUsedBytes(this.result.getByteTotal());

                    for (final IAEItemStack plannedItem : plan) {

                        IAEItemStack toExtract = plannedItem.copy();
                        toExtract.reset();
                        toExtract.setStackSize(plannedItem.getStackSize());

                        final IAEItemStack toCraft = plannedItem.copy();
                        toCraft.reset();
                        toCraft.setStackSize(plannedItem.getCountRequestable());
                        toCraft.setCountRequestableCrafts(plannedItem.getCountRequestableCrafts());

                        final IStorageGrid sg = this.getGrid().getCache(IStorageGrid.class);
                        final IMEInventory<IAEItemStack> items = sg.getItemInventory();

                        IAEItemStack missing = null;
                        if (missingUpdate != null && this.result.isSimulation()) {
                            missing = toExtract.copy();
                            toExtract = items.extractItems(toExtract, Actionable.SIMULATE, this.getActionSource());

                            if (toExtract == null) {
                                toExtract = missing.copy();
                                toExtract.setStackSize(0);
                            }

                            missing.setStackSize(missing.getStackSize() - toExtract.getStackSize());
                        }

                        if (toExtract.getStackSize() > 0) {
                            storageUpdate.appendItem(toExtract);
                        }

                        if (toCraft.getStackSize() > 0) {
                            pendingUpdate.appendItem(toCraft);
                        }

                        if (missingUpdate != null && missing != null && missing.getStackSize() > 0) {
                            missingUpdate.appendItem(missing);
                        }
                    }

                    final List<PacketCraftingTreeData> treeUpdates;
                    if (this.result instanceof CraftingJobV2) {
                        treeUpdates = PacketCraftingTreeData.createChunks((CraftingJobV2) this.result);
                    } else {
                        treeUpdates = null;
                    }
                    for (final Object player : this.crafters) {
                        if (player instanceof EntityPlayerMP playerMP) {
                            NetworkHandler.instance.sendTo(storageUpdate, playerMP);
                            NetworkHandler.instance.sendTo(pendingUpdate, playerMP);
                            if (missingUpdate != null) {
                                NetworkHandler.instance.sendTo(missingUpdate, playerMP);
                            }
                            if (treeUpdates != null) {
                                for (PacketCraftingTreeData pkt : treeUpdates) {
                                    NetworkHandler.instance.sendTo(pkt, playerMP);
                                }
                            }
                        }
                    }
                } catch (final IOException e) {
                    // :P
                }
            } catch (final Throwable e) {
                this.getPlayerInv().player.addChatMessage(new ChatComponentText("Error: " + e.toString()));
                AELog.debug(e);
                this.setValidContainer(false);
                this.result = null;
            }

            this.setJob(null);
        }
        this.verifyPermissions(SecurityPermissions.CRAFT, false);
    }

    private IGrid getGrid() {
        final IActionHost h = ((IActionHost) this.getTarget());
        if (h == null || h.getActionableNode() == null) return null;
        return h.getActionableNode().getGrid();
    }

    private boolean cpuMatches(final CraftingCPUStatus c) {
        return c.getStorage() >= this.getUsedBytes() && !c.isBusy();
    }

    public void startJob() {
        if (this.result != null && !this.isSimulation() && getGrid() != null) {
            final ICraftingGrid cc = this.getGrid().getCache(ICraftingGrid.class);
            CraftingCPUStatus selected = this.cpuTable.getSelectedCPU();
            final ICraftingLink g = cc.submitJob(
                    this.result,
                    null,
                    (selected == null) ? null : selected.getServerCluster(),
                    true,
                    this.getActionSrc());
            this.setAutoStart(false);
            if (g != null) {
                this.switchToOriginalGUI();
            }
        }
    }

    public void optimizePatterns() {
        // only V2 supported
        if (this.result instanceof CraftingJobV2 && !this.isSimulation() && getGrid() != null) {
            Platform.openGUI(
                    this.getPlayerInv().player,
                    this.getOpenContext().getTile(),
                    this.getOpenContext().getSide(),
                    GuiBridge.GUI_OPTIMIZE_PATTERNS);
            if (this.getPlayerInv().player.openContainer instanceof ContainerOptimizePatterns cop) {
                cop.setResult(this.result);
            }
        }
    }

    public void switchToOriginalGUI() {
        GuiBridge originalGui = null;

        final IActionHost ah = this.getActionHost();
        if (ah instanceof WirelessTerminalGuiObject) {
            originalGui = GuiBridge.GUI_WIRELESS_TERM;
        }

        if (ah instanceof PartTerminal) {
            originalGui = GuiBridge.GUI_ME;
        }

        if (ah instanceof PartCraftingTerminal) {
            originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (ah instanceof PartPatternTerminal) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (ah instanceof PartPatternTerminalEx) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL_EX;
        }

        if (originalGui != null && this.getOpenContext() != null) {
            NetworkHandler.instance
                    .sendTo(new PacketSwitchGuis(originalGui), (EntityPlayerMP) this.getInventoryPlayer().player);

            final TileEntity te = this.getOpenContext().getTile();
            Platform.openGUI(this.getInventoryPlayer().player, te, this.getOpenContext().getSide(), originalGui);
        }
    }

    private BaseActionSource getActionSrc() {
        return new PlayerSource(this.getPlayerInv().player, (IActionHost) this.getTarget());
    }

    @Override
    public void removeCraftingFromCrafters(final ICrafting c) {
        super.removeCraftingFromCrafters(c);
        if (this.getJob() != null) {
            this.getJob().cancel(true);
            this.setJob(null);
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer par1EntityPlayer) {
        super.onContainerClosed(par1EntityPlayer);
        if (this.getJob() != null) {
            this.getJob().cancel(true);
            this.setJob(null);
        }
    }

    public World getWorld() {
        return this.getPlayerInv().player.worldObj;
    }

    public boolean isAutoStart() {
        return this.autoStart;
    }

    public void setAutoStart(final boolean autoStart) {
        this.autoStart = autoStart;
    }

    public long getUsedBytes() {
        return this.bytesUsed;
    }

    private void setUsedBytes(final long bytesUsed) {
        this.bytesUsed = bytesUsed;
    }

    public long getCpuAvailableBytes() {
        return this.cpuBytesAvail;
    }

    private void setCpuAvailableBytes(final long cpuBytesAvail) {
        this.cpuBytesAvail = cpuBytesAvail;
    }

    public int getCpuCoProcessors() {
        return this.cpuCoProcessors;
    }

    private void setCpuCoProcessors(final int cpuCoProcessors) {
        this.cpuCoProcessors = cpuCoProcessors;
    }

    public int getSelectedCpu() {
        return this.cpuTable.selectedCpuSerial;
    }

    public String getName() {
        return this.myName;
    }

    private void setName(@Nonnull final String myName) {
        this.myName = myName;
    }

    public boolean hasNoCPU() {
        return this.noCPU;
    }

    private void setNoCPU(final boolean noCPU) {
        this.noCPU = noCPU;
    }

    public boolean isSimulation() {
        return this.simulation;
    }

    private void setSimulation(final boolean simulation) {
        this.simulation = simulation;
    }

    private Future<ICraftingJob> getJob() {
        return this.job;
    }

    public void setJob(final Future<ICraftingJob> job) {
        this.job = job;
    }
}
