/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import appeng.api.AEApi;
import appeng.api.config.CraftingStatus;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.config.YesNo;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IDisplayRepo;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.ActionKey;
import appeng.client.gui.AEBaseMEGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.InternalSlotME;
import appeng.client.me.ItemRepo;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.CommonHelper;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.parts.reporting.AbstractPartTerminal;
import appeng.tile.misc.TileSecurity;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import codechicken.nei.TextField;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class GuiMEMonitorable extends AEBaseMEGui implements ISortSource, IConfigManagerHost, IDropToFillTextField {

    public static int craftingGridOffsetX;
    public static int craftingGridOffsetY;

    private static String memoryText = "";
    private final IDisplayRepo repo;
    private final int offsetX = 9;
    private final int MAGIC_HEIGHT_NUMBER = 114 + 1;
    private final int lowerTextureOffset = 0;
    private final IConfigManager configSrc;
    private final boolean viewCell;
    private final ItemStack[] myCurrentViewCells = new ItemStack[5];
    private final ContainerMEMonitorable monitorableContainer;
    private GuiTabButton craftingStatusBtn;
    private GuiImgButton craftingStatusImgBtn;
    private final MEGuiTextField searchField;
    private TextField NEISearchField;
    private GuiText myName;
    private int perRow = 9;
    private int reservedSpace = 0;
    private boolean customSortOrder = true;
    private int rows = 0;
    private int standardSize;
    private GuiImgButton ViewBox;
    private GuiImgButton SortByBox;
    private GuiImgButton SortDirBox;
    private GuiImgButton searchBoxSettings;
    private GuiImgButton terminalStyleBox;
    private GuiImgButton searchStringSave;
    private GuiImgButton typeFilter;
    private boolean isAutoFocus = false;
    private int currentMouseX = 0;
    private int currentMouseY = 0;
    protected boolean hasShiftKeyDown = false;

    public GuiMEMonitorable(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        this(inventoryPlayer, te, new ContainerMEMonitorable(inventoryPlayer, te));
    }

    public GuiMEMonitorable(final InventoryPlayer inventoryPlayer, final ITerminalHost te,
            final ContainerMEMonitorable c) {

        super(c);

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
        this.repo = new ItemRepo(scrollbar, this);

        this.xSize = 195;
        this.ySize = 204;

        this.standardSize = this.xSize;

        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();
        (this.monitorableContainer = (ContainerMEMonitorable) this.inventorySlots).setGui(this);

        this.viewCell = te instanceof IViewCellStorage;

        if (te instanceof TileSecurity) {
            this.myName = GuiText.Security;
        } else if (te instanceof WirelessTerminalGuiObject) {
            this.myName = GuiText.WirelessTerminal;
        } else if (te instanceof IPortableCell) {
            this.myName = GuiText.PortableCell;
        } else if (te instanceof IMEChest) {
            this.myName = GuiText.Chest;
        } else if (te instanceof AbstractPartTerminal) {
            this.myName = GuiText.Terminal;
        }

        this.searchField = new MEGuiTextField(90, 12, ButtonToolTips.SearchStringTooltip.getLocal()) {

            @Override
            public void onTextChange(final String oldText) {
                final String text = getText();
                repo.setSearchString(text);
                repo.updateView();
                setScrollBar();
            }
        };

        if (Loader.isModLoaded("NotEnoughItems")) {

            try {
                final Class<? super Object> clazz = ReflectionHelper
                        .getClass(this.getClass().getClassLoader(), "codechicken.nei.LayoutManager");
                final Field fldSearchField = clazz.getField("searchField");
                this.NEISearchField = (TextField) fldSearchField.get(clazz);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public void postUpdate(final List<IAEItemStack> list) {
        for (final IAEItemStack is : list) {
            this.repo.postUpdate(is);
        }

        this.repo.updateView();
        this.setScrollBar();
    }

    private void setScrollBar() {
        this.getScrollBar().setTop(18).setLeft(175).setHeight(this.rows * 18 - 2);
        this.getScrollBar().setRange(
                0,
                (this.repo.size() + this.perRow - 1) / this.perRow - this.rows,
                Math.max(1, this.rows / 6));
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {

        if (btn == this.craftingStatusBtn || btn == this.craftingStatusImgBtn) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_STATUS));
        }

        if (btn instanceof GuiImgButton iBtn) {
            if (iBtn.getSetting() != Settings.ACTIONS) {
                final Enum cv = iBtn.getCurrentValue();
                final boolean backwards = Mouse.isButtonDown(1);
                final Enum next = Platform.rotateEnum(cv, backwards, iBtn.getSetting().getPossibleValues());

                if (btn == this.terminalStyleBox) {
                    AEConfig.instance.settings.putSetting(iBtn.getSetting(), next);
                } else if (btn == this.searchBoxSettings) {
                    AEConfig.instance.settings.putSetting(iBtn.getSetting(), next);
                } else if (btn == this.searchStringSave) {
                    AEConfig.instance.preserveSearchBar = next == YesNo.YES;
                } else {
                    try {
                        NetworkHandler.instance
                                .sendToServer(new PacketValueConfig(iBtn.getSetting().name(), next.name()));
                    } catch (final IOException e) {
                        AELog.debug(e);
                    }
                }

                iBtn.set(next);

                if (next.getClass() == SearchBoxMode.class || next.getClass() == TerminalStyle.class) {
                    this.reinitalize();
                }
            }
        }
    }

    private void reinitalize() {
        this.buttonList.clear();
        this.initGui();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        this.perRow = AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) != TerminalStyle.FULL ? 9
                : 9 + ((this.width - this.standardSize) / 18);
        this.rows = calculateRowsCount();

        this.getMeSlots().clear();
        for (int y = 0; y < this.rows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                this.getMeSlots()
                        .add(new InternalSlotME(this.repo, x + y * this.perRow, this.offsetX + x * 18, 18 + y * 18));
            }
        }

        if (AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) != TerminalStyle.FULL) {
            this.xSize = this.standardSize + ((this.perRow - 9) * 18);
        } else {
            this.xSize = this.standardSize;
        }

        super.initGui();
        // full size : 204
        // extra slots : 72
        // slot 18

        this.ySize = MAGIC_HEIGHT_NUMBER + this.rows * 18 + this.reservedSpace;
        final int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));

        int offset = this.guiTop + 8;

        if (this.customSortOrder) {
            this.buttonList.add(
                    this.SortByBox = new GuiImgButton(
                            this.guiLeft - 18,
                            offset,
                            Settings.SORT_BY,
                            this.configSrc.getSetting(Settings.SORT_BY)));
            offset += 20;
        }

        if (this.viewCell || this instanceof GuiWirelessTerm) {
            this.buttonList.add(
                    this.ViewBox = new GuiImgButton(
                            this.guiLeft - 18,
                            offset,
                            Settings.VIEW_MODE,
                            this.configSrc.getSetting(Settings.VIEW_MODE)));
            offset += 20;
        }
        if (!AEApi.instance().registries().itemDisplay().getItemFilters().isEmpty()) {
            this.buttonList.add(
                    this.typeFilter = new GuiImgButton(
                            this.guiLeft - 18,
                            offset,
                            Settings.TYPE_FILTER,
                            this.configSrc.getSetting(Settings.TYPE_FILTER)));
            offset += 20;
        }

        this.buttonList.add(
                this.SortDirBox = new GuiImgButton(
                        this.guiLeft - 18,
                        offset,
                        Settings.SORT_DIRECTION,
                        this.configSrc.getSetting(Settings.SORT_DIRECTION)));
        offset += 20;

        this.buttonList.add(
                this.searchBoxSettings = new GuiImgButton(
                        this.guiLeft - 18,
                        offset,
                        Settings.SEARCH_MODE,
                        AEConfig.instance.settings.getSetting(Settings.SEARCH_MODE)));
        offset += 20;

        this.buttonList.add(
                this.searchStringSave = new GuiImgButton(
                        this.guiLeft - 18,
                        offset,
                        Settings.SAVE_SEARCH,
                        AEConfig.instance.preserveSearchBar ? YesNo.YES : YesNo.NO));
        offset += 20;

        if (!(this instanceof GuiMEPortableCell) || this instanceof GuiWirelessTerm) {
            this.buttonList.add(
                    this.terminalStyleBox = new GuiImgButton(
                            this.guiLeft - 18,
                            offset,
                            Settings.TERMINAL_STYLE,
                            AEConfig.instance.settings.getSetting(Settings.TERMINAL_STYLE)));
            offset += 20;
        }

        if (this.viewCell || this instanceof GuiWirelessTerm) {
            if (AEConfig.instance.getConfigManager().getSetting(Settings.CRAFTING_STATUS)
                    .equals(CraftingStatus.BUTTON)) {
                this.buttonList.add(
                        this.craftingStatusImgBtn = new GuiImgButton(
                                this.guiLeft - 18,
                                offset,
                                Settings.CRAFTING_STATUS,
                                AEConfig.instance.settings.getSetting(Settings.CRAFTING_STATUS)));
            } else {
                this.buttonList.add(
                        this.craftingStatusBtn = new GuiTabButton(
                                this.guiLeft + 170,
                                this.guiTop - 4,
                                2 + 11 * 16,
                                GuiText.CraftingStatus.getLocal(),
                                itemRender));
                this.craftingStatusBtn.setHideEdge(13); // GuiTabButton implementation //
            }
        }

        // Enum setting = AEConfig.INSTANCE.getSetting( "Terminal", SearchBoxMode.class, SearchBoxMode.AUTOSEARCH );
        final Enum searchMode = AEConfig.instance.settings.getSetting(Settings.SEARCH_MODE);
        this.isAutoFocus = SearchBoxMode.AUTOSEARCH == searchMode || SearchBoxMode.NEI_AUTOSEARCH == searchMode;

        this.searchField.x = this.guiLeft + Math.max(80, this.offsetX);
        this.searchField.y = this.guiTop + 4;
        this.searchField.setFocused(this.isAutoFocus);

        if (this.isSubGui()) {
            this.searchField.setText(memoryText);
        } else if (AEConfig.instance.preserveSearchBar) {
            this.searchField.setText(memoryText, true);
            repo.setSearchString(memoryText);
        }
        this.searchField.setCursorPositionEnd();

        this.setScrollBar();

        craftingGridOffsetX = Integer.MAX_VALUE;
        craftingGridOffsetY = Integer.MAX_VALUE;

        for (final Object s : this.inventorySlots.inventorySlots) {
            if (s instanceof AppEngSlot) {
                if (((Slot) s).xDisplayPosition < 197) {
                    this.repositionSlot((AppEngSlot) s);
                }
            }

            if (s instanceof SlotCraftingMatrix || s instanceof SlotFakeCraftingMatrix) {
                final Slot g = (Slot) s;
                if (g.xDisplayPosition > 0 && g.yDisplayPosition > 0) {
                    craftingGridOffsetX = Math.min(craftingGridOffsetX, g.xDisplayPosition);
                    craftingGridOffsetY = Math.min(craftingGridOffsetY, g.yDisplayPosition);
                }
            }
        }

        craftingGridOffsetX -= 25;
        craftingGridOffsetY -= 6;
    }

    protected int calculateRowsCount() {
        final boolean hasNEI = IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.NEI);
        final int NEIPadding = hasNEI ? 22
                /** input */
                + 20
                /** top panel */
                : 0;
        final int extraSpace = this.height - MAGIC_HEIGHT_NUMBER - NEIPadding - this.reservedSpace;

        return Math.max(3, Math.min(this.getMaxRows(), (int) Math.floor(extraSpace / 18)));
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(
                this.getGuiDisplayName(this.myName.getLocal()),
                8,
                6,
                GuiColors.MEMonitorableTitle.getColor());
        this.fontRendererObj.drawString(
                GuiText.inventory.getLocal(),
                8,
                this.ySize - 96 + 3,
                GuiColors.MEMonitorableInventory.getColor());

        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        searchField.mouseClicked(xCoord, yCoord, btn);
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        memoryText = this.searchField.getText();
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {

        this.bindTexture(this.getBackground());
        final int x_width = 195;
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, x_width, 18);

        if (this.viewCell || (this instanceof GuiSecurity)) {
            this.drawTexturedModalRect(offsetX + x_width, offsetY, x_width, 0, 46, 128);
        }

        for (int x = 0; x < this.rows; x++) {
            this.drawTexturedModalRect(offsetX, offsetY + 18 + x * 18, 0, 18, x_width, 18);
        }

        this.drawTexturedModalRect(
                offsetX,
                offsetY + 16 + this.rows * 18 + this.lowerTextureOffset,
                0,
                106 - 18 - 18,
                x_width,
                99 + this.reservedSpace - this.lowerTextureOffset);

        if (this.viewCell) {
            boolean update = false;

            for (int i = 0; i < 5; i++) {
                if (this.myCurrentViewCells[i] != this.monitorableContainer.getCellViewSlot(i).getStack()) {
                    update = true;
                    this.myCurrentViewCells[i] = this.monitorableContainer.getCellViewSlot(i).getStack();
                }
            }

            if (update) {
                this.repo.setViewCell(this.myCurrentViewCells);
            }
        }

        searchField.drawTextBox();
    }

    protected String getBackground() {
        return "guis/terminal.png";
    }

    @Override
    protected boolean isPowered() {
        return this.repo.hasPower();
    }

    int getMaxRows() {
        return AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.SMALL
                ? AEConfig.instance.MEMonitorableSmallSize
                : Integer.MAX_VALUE;
    }

    protected void repositionSlot(final AppEngSlot s) {
        s.yDisplayPosition = s.getY() + this.ySize - 78 - 5;
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (!this.checkHotbarKeys(key)) {

            if (NEISearchField != null && (NEISearchField.focused() || searchField.isFocused())
                    && CommonHelper.proxy.isActionKey(ActionKey.TOGGLE_FOCUS, key)) {
                final boolean focused = searchField.isFocused();
                searchField.setFocused(!focused);
                NEISearchField.setFocus(focused);
                return;
            }

            if (NEISearchField != null && NEISearchField.focused()) {
                return;
            }

            if (searchField.isFocused() && key == Keyboard.KEY_RETURN) {
                searchField.setFocused(false);
                return;
            }

            if (character == ' ' && searchField.getText().isEmpty()) {
                return;
            }

            final boolean mouseInGui = this
                    .isPointInRegion(0, 0, this.xSize, this.ySize, this.currentMouseX, this.currentMouseY);

            if (this.isAutoFocus && !searchField.isFocused() && mouseInGui) {
                searchField.setFocused(true);
            }

            if (!searchField.textboxKeyTyped(character, key)) {
                super.keyTyped(character, key);
            }
        }
    }

    @Override
    public void updateScreen() {
        this.repo.setPowered(this.monitorableContainer.isPowered());
        super.updateScreen();
    }

    @Override
    public Enum getSortBy() {
        return this.configSrc.getSetting(Settings.SORT_BY);
    }

    @Override
    public Enum getSortDir() {
        return this.configSrc.getSetting(Settings.SORT_DIRECTION);
    }

    @Override
    public Enum getTypeFilter() {
        return this.configSrc.getSetting(Settings.TYPE_FILTER);
    }

    @Override
    public Enum getSortDisplay() {
        return this.configSrc.getSetting(Settings.VIEW_MODE);
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.SortByBox != null) {
            this.SortByBox.set(this.configSrc.getSetting(Settings.SORT_BY));
        }

        if (this.SortDirBox != null) {
            this.SortDirBox.set(this.configSrc.getSetting(Settings.SORT_DIRECTION));
        }

        if (this.ViewBox != null) {
            this.ViewBox.set(this.configSrc.getSetting(Settings.VIEW_MODE));
        }
        if (this.typeFilter != null) {
            this.typeFilter.set(this.configSrc.getSetting(Settings.TYPE_FILTER));
        }

        this.repo.updateView();
    }

    @Override
    public void handleKeyboardInput() {
        super.handleKeyboardInput();
        hasShiftKeyDown |= isShiftKeyDown();
        if (hasShiftKeyDown && !Keyboard.getEventKeyState()) { // keyup
            this.repo.updateView();
            hasShiftKeyDown = false;
        }
    }

    @SuppressWarnings("SameParameterValue")
    protected boolean isPointInRegion(int rectX, int rectY, int rectWidth, int rectHeight, int pointX, int pointY) {
        pointX -= this.guiLeft;
        pointY -= this.guiTop;
        return pointX >= rectX - 1 && pointX < rectX + rectWidth + 1
                && pointY >= rectY - 1
                && pointY < rectY + rectHeight + 1;
    }

    int getReservedSpace() {
        return this.reservedSpace;
    }

    void setReservedSpace(final int reservedSpace) {
        this.reservedSpace = reservedSpace;
    }

    public boolean isCustomSortOrder() {
        return this.customSortOrder;
    }

    void setCustomSortOrder(final boolean customSortOrder) {
        this.customSortOrder = customSortOrder;
    }

    public int getStandardSize() {
        return this.standardSize;
    }

    void setStandardSize(final int standardSize) {
        this.standardSize = standardSize;
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        super.drawScreen(mouseX, mouseY, btn);

        handleTooltip(mouseX, mouseY, searchField);
    }

    public boolean isOverTextField(final int mousex, final int mousey) {
        return searchField.isMouseIn(mousex, mousey);
    }

    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack) {
        searchField.setText(displayName);
    }

    @Override
    protected void handleMouseClick(Slot p_146984_1_, int p_146984_2_, int p_146984_3_, int p_146984_4_) {

        // Hack for view cells, because they are outside the container
        if (p_146984_1_ != null && p_146984_4_ == 4 && p_146984_1_.xDisplayPosition > this.xSize) {
            p_146984_4_ = 0;
        }

        super.handleMouseClick(p_146984_1_, p_146984_2_, p_146984_3_, p_146984_4_);
    }

    public boolean hideItemPanelSlot(int tx, int ty, int tw, int th) {

        if (this.viewCell) {
            int rw = 33;
            int rh = 14 + myCurrentViewCells.length * 18;
            if (monitorableContainer.isAPatternTerminal()) rh += 21;

            if (rw <= 0 || rh <= 0 || tw <= 0 || th <= 0) {
                return false;
            }

            int rx = this.guiLeft + this.xSize;
            int ry = this.guiTop + 0;

            rw += rx;
            rh += ry;
            tw += tx;
            th += ty;

            // overflow || intersect
            return (rw < rx || rw > tx) && (rh < ry || rh > ty) && (tw < tx || tw > rx) && (th < ty || th > ry);
        }

        return false;
    }
}
