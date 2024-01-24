/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.lwjgl.opengl.GL11;

import com.google.common.base.Joiner;

import appeng.api.AEApi;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.WorldCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.IGuiTooltipHandler;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.render.BlockPosHighlighter;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingItemInterface;
import appeng.core.sync.packets.PacketCraftingRemainingOperations;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;

public class GuiCraftingCPU extends AEBaseGui implements ISortSource, IGuiTooltipHandler {

    protected static final int GUI_HEIGHT = 184;
    protected static final int GUI_WIDTH = 238;

    protected static final int TEXTURE_BELOW_TOP_ROW_Y = 41;
    protected static final int TEXTURE_ABOVE_BOTTOM_ROW_Y = 51;
    protected static final int DISPLAYED_ROWS = 6;

    protected static final int SECTION_LENGTH = 67;
    protected static final int SECTION_HEIGHT = 23;

    protected static final int SCROLLBAR_TOP = 19;
    protected static final int SCROLLBAR_LEFT = 218;
    protected static final int SCROLLBAR_HEIGHT = 137;

    private static final int CANCEL_LEFT_OFFSET = 163;
    private static final int CANCEL_TOP_OFFSET = 25;
    private static final int CANCEL_HEIGHT = 20;
    private static final int CANCEL_WIDTH = 50;

    private static final int TITLE_TOP_OFFSET = 7;
    private static final int TITLE_LEFT_OFFSET = 8;

    private static final int ITEMSTACK_LEFT_OFFSET = 9;
    private static final int ITEMSTACK_TOP_OFFSET = 22;
    private static final int ITEMS_PER_ROW = 3;

    private final ContainerCraftingCPU craftingCpu;

    protected IItemList<IAEItemStack> storage = AEApi.instance().storage().createItemList();
    protected IItemList<IAEItemStack> active = AEApi.instance().storage().createItemList();
    protected IItemList<IAEItemStack> pending = AEApi.instance().storage().createItemList();

    protected int rows = DISPLAYED_ROWS;

    private class RemainingOperations implements ITooltip {

        private long refreshTick = System.currentTimeMillis();
        private long lastWorkingTick = 0;

        private int remainingOperations = 0;

        public long getLastWorkingTick() {
            return lastWorkingTick;
        }

        public long getRefreshTick() {
            return refreshTick;
        }

        public void setLastWorkingTick(long lastWorkingTick) {
            this.lastWorkingTick = lastWorkingTick;
        }

        public void setRefreshTick(long refreshTick) {
            this.refreshTick = refreshTick;
        }

        @Override
        public String getMessage() {
            return GuiText.RemainingOperations.getLocal();
        }

        @Override
        public int xPos() {
            return guiLeft + TITLE_LEFT_OFFSET + 200 - this.getStringWidth();
        }

        @Override
        public int yPos() {
            return guiTop + TITLE_TOP_OFFSET;
        }

        @Override
        public int getWidth() {
            return this.getStringWidth();
        }

        @Override
        public int getHeight() {
            return fontRendererObj.FONT_HEIGHT;
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        public void setRemainingOperations(int remainingOperations) {
            this.remainingOperations = remainingOperations;
        }

        public int getRemainingOperations() {
            return this.remainingOperations;
        }

        public int getStringWidth() {
            return fontRendererObj.getStringWidth(String.valueOf(this.remainingOperations));
        }
    }

    protected List<IAEItemStack> visual = new ArrayList<>();
    private GuiButton cancel;
    private int tooltip = -1;
    private final RemainingOperations remainingOperations = new RemainingOperations();
    private ItemStack hoveredStack;
    private ItemStack hoveredNbtStack;

    public GuiCraftingCPU(final InventoryPlayer inventoryPlayer, final Object te) {
        this(new ContainerCraftingCPU(inventoryPlayer, te));
    }

    protected GuiCraftingCPU(final ContainerCraftingCPU container) {
        super(container);
        this.craftingCpu = container;
        this.ySize = GUI_HEIGHT;
        this.xSize = GUI_WIDTH;

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
    }

    public void clearItems() {
        this.storage = AEApi.instance().storage().createItemList();
        this.active = AEApi.instance().storage().createItemList();
        this.pending = AEApi.instance().storage().createItemList();
        this.visual = new ArrayList<>();
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (this.cancel == btn) {
            try {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("TileCrafting.Cancel", "Cancel"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        if (this.hoveredNbtStack != null && isShiftKeyDown()) {
            NBTTagCompound data = Platform.openNbtData(this.hoveredNbtStack);
            WorldCoord blockPos2 = new WorldCoord(
                    (int) mc.thePlayer.posX,
                    (int) mc.thePlayer.posY,
                    (int) mc.thePlayer.posZ);
            BlockPosHighlighter.clear();
            for (DimensionalCoord blockPos : DimensionalCoord.readAsListFromNBT(data)) {
                BlockPosHighlighter.highlightBlock(
                        blockPos,
                        System.currentTimeMillis() + 500 * WorldCoord.getTaxicabDistance(blockPos, blockPos2),
                        false);
                mc.thePlayer.addChatMessage(
                        new ChatComponentTranslation(
                                PlayerMessages.InterfaceHighlighted.getName(),
                                blockPos.x,
                                blockPos.y,
                                blockPos.z));
            }
            mc.thePlayer.closeScreen();
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.setScrollBar();
        this.cancel = new GuiButton(
                0,
                this.guiLeft + CANCEL_LEFT_OFFSET,
                this.guiTop + this.ySize - CANCEL_TOP_OFFSET,
                CANCEL_WIDTH,
                CANCEL_HEIGHT,
                GuiText.Cancel.getLocal());
        this.buttonList.add(this.cancel);
    }

    private void setScrollBar() {
        final int size = this.visual.size();

        this.getScrollBar().setTop(SCROLLBAR_TOP).setLeft(SCROLLBAR_LEFT).setHeight(SCROLLBAR_HEIGHT);
        this.getScrollBar().setRange(0, (size + 2) / ITEMS_PER_ROW - rows, 1);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.cancel.enabled = !this.visual.isEmpty();

        final int gx = (this.width - this.xSize) / 2;
        final int gy = (this.height - this.ySize) / 2;

        this.tooltip = -1;

        final int offY = SECTION_HEIGHT;
        int y = 0;
        int x = 0;
        for (int z = 0; z <= ITEMS_PER_ROW * rows; z++) {
            final int minX = gx + ITEMSTACK_LEFT_OFFSET + x * SECTION_LENGTH;
            final int minY = gy + ITEMSTACK_TOP_OFFSET + y * offY;

            if (minX < mouseX && minX + SECTION_LENGTH > mouseX) {
                if (minY < mouseY && minY + offY > mouseY) {
                    this.tooltip = z;
                    break;
                }
            }

            x++;

            if (x == ITEMS_PER_ROW) {
                y++;
                x = 0;
            }
        }

        super.drawScreen(mouseX, mouseY, btn);
        this.handleTooltip(mouseX, mouseY, remainingOperations);
    }

    private void updateRemainingOperations() {
        int interval = 1000;
        if (this.remainingOperations.getRefreshTick() >= this.remainingOperations.getLastWorkingTick() + interval) {
            try {
                NetworkHandler.instance.sendToServer(new PacketCraftingRemainingOperations());
            } catch (IOException ignored) {}
            this.remainingOperations.setLastWorkingTick(this.remainingOperations.refreshTick);
        } else {
            this.remainingOperations.setRefreshTick(System.currentTimeMillis());
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        String title = this.getGuiDisplayName(GuiText.CraftingStatus.getLocal());

        if (this.craftingCpu.getEstimatedTime() > 0 && !this.visual.isEmpty()) {
            final long etaInMilliseconds = TimeUnit.MILLISECONDS
                    .convert(this.craftingCpu.getEstimatedTime(), TimeUnit.NANOSECONDS);
            final String etaTimeText = DurationFormatUtils
                    .formatDuration(etaInMilliseconds, GuiText.ETAFormat.getLocal());
            title += " - " + etaTimeText;
        }
        updateRemainingOperations();
        this.fontRendererObj.drawString(
                String.valueOf(remainingOperations.getRemainingOperations()),
                TITLE_LEFT_OFFSET + 200 - this.remainingOperations.getStringWidth(),
                TITLE_TOP_OFFSET,
                GuiColors.CraftingCPUTitle.getColor());

        this.fontRendererObj
                .drawString(title, TITLE_LEFT_OFFSET, TITLE_TOP_OFFSET, GuiColors.CraftingCPUTitle.getColor());

        int x = 0;
        int y = 0;
        final int viewStart = this.getScrollBar().getCurrentScroll() * ITEMS_PER_ROW;
        final int viewEnd = viewStart + ITEMS_PER_ROW * rows;

        String dspToolTip = "";
        final List<String> lineList = new LinkedList<>();
        int toolPosX = 0;
        int toolPosY = 0;

        hoveredStack = null;

        final int offY = 23;

        final ReadableNumberConverter converter = ReadableNumberConverter.INSTANCE;
        for (int z = viewStart; z < Math.min(viewEnd, this.visual.size()); z++) {
            final IAEItemStack refStack = this.visual.get(z); // repo.getReferenceItem( z );
            if (refStack != null) {
                GL11.glPushMatrix();
                GL11.glScaled(0.5, 0.5, 0.5);

                final IAEItemStack stored = this.storage.findPrecise(refStack);
                final IAEItemStack activeStack = this.active.findPrecise(refStack);
                final IAEItemStack pendingStack = this.pending.findPrecise(refStack);

                int lines = 0;

                if (stored != null && stored.getStackSize() > 0) {
                    lines++;
                }
                boolean active = false;
                if (activeStack != null && activeStack.getStackSize() > 0) {
                    lines++;
                    active = true;
                }
                boolean scheduled = false;
                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    lines++;
                    scheduled = true;
                }

                if (AEConfig.instance.useColoredCraftingStatus && (active || scheduled)) {
                    final int bgColor = active ? GuiColors.CraftingCPUActive.getColor()
                            : GuiColors.CraftingCPUInactive.getColor();
                    final int startX = (x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET) * 2;
                    final int startY = ((y * offY + ITEMSTACK_TOP_OFFSET) - 3) * 2;
                    drawRect(startX, startY, startX + (SECTION_LENGTH * 2), startY + (offY * 2) - 2, bgColor);
                }

                final int negY = ((lines - 1) * 5) / 2;
                int downY = 0;

                if (stored != null && stored.getStackSize() > 0) {
                    final String str = GuiText.Stored.getLocal() + ": "
                            + converter.toWideReadableForm(stored.getStackSize());
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);
                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2,
                            GuiColors.CraftingCPUStored.getColor());

                    if (this.tooltip == z - viewStart) {
                        lineList.add(
                                GuiText.Stored.getLocal() + ": "
                                        + NumberFormat.getInstance().format(stored.getStackSize()));
                    }

                    downY += 5;
                }

                if (activeStack != null && activeStack.getStackSize() > 0) {
                    final String str = GuiText.Crafting.getLocal() + ": "
                            + converter.toWideReadableForm(activeStack.getStackSize());
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);

                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2,
                            GuiColors.CraftingCPUAmount.getColor());

                    if (this.tooltip == z - viewStart) {
                        lineList.add(
                                GuiText.Crafting.getLocal() + ": "
                                        + NumberFormat.getInstance().format(activeStack.getStackSize()));
                    }

                    downY += 5;
                }

                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    final String str = GuiText.Scheduled.getLocal() + ": "
                            + converter.toWideReadableForm(pendingStack.getStackSize());
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);

                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2,
                            GuiColors.CraftingCPUScheduled.getColor());

                    if (this.tooltip == z - viewStart) {
                        lineList.add(
                                GuiText.Scheduled.getLocal() + ": "
                                        + NumberFormat.getInstance().format(pendingStack.getStackSize()));
                    }
                }

                GL11.glPopMatrix();
                final int posX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19;
                final int posY = y * offY + ITEMSTACK_TOP_OFFSET;

                final ItemStack is = refStack.copy().getItemStack();

                if (this.tooltip == z - viewStart) {
                    dspToolTip = Platform.getItemDisplayName(is);

                    if (lineList.size() > 0) {
                        addItemTooltip(is, lineList);
                        dspToolTip = dspToolTip + '\n' + Joiner.on("\n").join(lineList);
                    }

                    toolPosX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 8;
                    toolPosY = y * offY + ITEMSTACK_TOP_OFFSET;

                    hoveredStack = is;
                }

                this.drawItem(posX, posY, is);

                x++;

                if (x > 2) {
                    y++;
                    x = 0;
                }
            }
        }

        if (this.tooltip >= 0 && dspToolTip.length() > 0) {
            this.drawTooltip(toolPosX, toolPosY + 10, 0, dspToolTip);
        }
    }

    @SuppressWarnings("unchecked")
    protected void addItemTooltip(ItemStack is, List<String> lineList) {
        if (isShiftKeyDown()) {
            List l = is.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);
            if (!l.isEmpty()) l.remove(0);
            lineList.addAll(l);
            if (this.hoveredNbtStack == null || this.hoveredNbtStack.getItem() != is.getItem()) {
                this.hoveredNbtStack = is;
                try {
                    NetworkHandler.instance.sendToServer(
                            new PacketCraftingItemInterface(AEApi.instance().storage().createItemStack(is)));
                } catch (Exception ignored) {}
            } else {
                NBTTagCompound data = Platform.openNbtData(this.hoveredNbtStack);
                List<DimensionalCoord> blocks = DimensionalCoord.readAsListFromNBT(data);
                if (blocks.isEmpty()) return;
                for (DimensionalCoord blockPos : blocks) {
                    lineList.add(
                            String.format(
                                    "Dim:%s X:%s Y:%s Z:%s",
                                    blockPos.getDimension(),
                                    blockPos.x,
                                    blockPos.y,
                                    blockPos.z));
                }
                lineList.add(GuiText.HoldShiftClick_HIGHLIGHT_INTERFACE.getLocal());
            }
        } else {
            this.hoveredNbtStack = null;
            lineList.add(GuiText.HoldShiftForTooltip.getLocal());
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/craftingcpu.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    public void postUpdate(IAEItemStack is) {
        this.hoveredNbtStack = is.getItemStack();
    }

    public void postUpdate(int remainingOperations) {
        this.remainingOperations.setRemainingOperations(remainingOperations);
    }

    public void postUpdate(final List<IAEItemStack> list, final byte ref) {
        switch (ref) {
            case 0 -> {
                for (final IAEItemStack l : list) {
                    this.handleInput(this.storage, l);
                }
            }
            case 1 -> {
                for (final IAEItemStack l : list) {
                    this.handleInput(this.active, l);
                }
            }
            case 2 -> {
                for (final IAEItemStack l : list) {
                    this.handleInput(this.pending, l);
                }
            }
        }

        for (final IAEItemStack l : list) {
            final long amt = this.getTotal(l);

            if (amt <= 0) {
                this.deleteVisualStack(l);
            } else {
                final IAEItemStack is = this.findVisualStack(l);
                is.setStackSize(amt);
            }
        }

        this.setScrollBar();
    }

    private void handleInput(final IItemList<IAEItemStack> s, final IAEItemStack l) {
        IAEItemStack a = s.findPrecise(l);

        if (l.getStackSize() <= 0) {
            if (a != null) {
                a.reset();
            }
        } else {
            if (a == null) {
                s.add(l.copy());
                a = s.findPrecise(l);
            }

            if (a != null) {
                a.setStackSize(l.getStackSize());
            }
        }
    }

    private long getTotal(final IAEItemStack is) {
        final IAEItemStack a = this.storage.findPrecise(is);
        final IAEItemStack b = this.active.findPrecise(is);
        final IAEItemStack c = this.pending.findPrecise(is);

        long total = 0;

        if (a != null) {
            total += a.getStackSize();
        }

        if (b != null) {
            total += b.getStackSize();
        }

        if (c != null) {
            total += c.getStackSize();
        }

        return total;
    }

    private void deleteVisualStack(final IAEItemStack l) {
        final Iterator<IAEItemStack> i = this.visual.iterator();

        while (i.hasNext()) {
            final IAEItemStack o = i.next();
            if (o.equals(l)) {
                i.remove();
                return;
            }
        }
    }

    private IAEItemStack findVisualStack(final IAEItemStack l) {
        for (final IAEItemStack o : this.visual) {
            if (o.equals(l)) {
                return o;
            }
        }

        final IAEItemStack stack = l.copy();
        this.visual.add(stack);

        return stack;
    }

    @Override
    public Enum getSortBy() {
        return SortOrder.NAME;
    }

    @Override
    public Enum getSortDir() {
        return SortDir.ASCENDING;
    }

    @Override
    public Enum getSortDisplay() {
        return ViewItems.ALL;
    }

    @Override
    public ItemStack getHoveredStack() {
        return hoveredStack;
    }
}
