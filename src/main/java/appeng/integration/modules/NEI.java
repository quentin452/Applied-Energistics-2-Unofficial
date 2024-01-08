/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.integration.modules;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.client.gui.IGuiTooltipHandler;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.gui.implementations.GuiCraftingTerm;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.implementations.GuiMEPortableCell;
import appeng.client.gui.implementations.GuiOptimizePatterns;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.implementations.GuiPatternTermEx;
import appeng.client.gui.implementations.GuiSkyChest;
import appeng.client.gui.implementations.GuiWirelessTerm;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.helpers.Reflected;
import appeng.integration.IIntegrationModule;
import appeng.integration.IntegrationHelper;
import appeng.integration.abstraction.INEI;
import appeng.integration.modules.NEIHelpers.NEIAEBookmarkContainerHandler;
import appeng.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler;
import appeng.integration.modules.NEIHelpers.NEIAEShapelessRecipeHandler;
import appeng.integration.modules.NEIHelpers.NEIAETerminalBookmarkContainerHandler;
import appeng.integration.modules.NEIHelpers.NEICraftingHandler;
import appeng.integration.modules.NEIHelpers.NEIFacadeRecipeHandler;
import appeng.integration.modules.NEIHelpers.NEIGrinderRecipeHandler;
import appeng.integration.modules.NEIHelpers.NEIGuiHandler;
import appeng.integration.modules.NEIHelpers.NEIInscriberRecipeHandler;
import appeng.integration.modules.NEIHelpers.NEIOreDictionaryFilter;
import appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler;
import appeng.integration.modules.NEIHelpers.TerminalCraftingSlotFinder;
import codechicken.nei.ItemsGrid;
import codechicken.nei.LayoutManager;
import codechicken.nei.SearchField.ISearchProvider;
import codechicken.nei.api.IBookmarkContainerHandler;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.IStackPositioner;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerObjectHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;

public class NEI implements INEI, IContainerTooltipHandler, IIntegrationModule, IContainerObjectHandler {

    @Reflected
    public static NEI instance;

    private final Class<?> apiClass;

    // recipe handler...
    private Method registerRecipeHandler;
    private Method registerUsageHandler;
    private Method registerNEIGuiHandler;
    private Method registerItemFilter;
    private Method registerBookmarkContainerHandler;

    @Reflected
    public NEI() throws ClassNotFoundException {
        IntegrationHelper.testClassExistence(this, codechicken.nei.api.API.class);
        IntegrationHelper.testClassExistence(this, IStackPositioner.class);
        IntegrationHelper.testClassExistence(this, GuiContainerManager.class);
        IntegrationHelper.testClassExistence(this, IContainerTooltipHandler.class);
        IntegrationHelper.testClassExistence(this, codechicken.nei.recipe.ICraftingHandler.class);
        IntegrationHelper.testClassExistence(this, codechicken.nei.recipe.IUsageHandler.class);
        IntegrationHelper.testClassExistence(this, codechicken.nei.api.IBookmarkContainerHandler.class);

        this.apiClass = Class.forName("codechicken.nei.api.API");
    }

    @Override
    public void init() throws Throwable {
        this.registerRecipeHandler = this.apiClass
                .getDeclaredMethod("registerRecipeHandler", codechicken.nei.recipe.ICraftingHandler.class);
        this.registerUsageHandler = this.apiClass
                .getDeclaredMethod("registerUsageHandler", codechicken.nei.recipe.IUsageHandler.class);
        this.registerNEIGuiHandler = this.apiClass.getDeclaredMethod("registerNEIGuiHandler", INEIGuiHandler.class);
        registerNEIGuiHandler.invoke(apiClass, new NEIGuiHandler());
        this.registerItemFilter = this.apiClass.getDeclaredMethod("addSearchProvider", ISearchProvider.class);
        this.registerItemFilter.invoke(apiClass, new NEIOreDictionaryFilter());
        this.registerRecipeHandler(new NEIAEShapedRecipeHandler());
        this.registerRecipeHandler(new NEIAEShapelessRecipeHandler());
        this.registerRecipeHandler(new NEIInscriberRecipeHandler());
        this.registerRecipeHandler(new NEIWorldCraftingHandler());
        if (AEConfig.instance.isFeatureEnabled(AEFeature.GrindStone)) {
            this.registerRecipeHandler(new NEIGrinderRecipeHandler());
        }
        if (AEConfig.instance.isFeatureEnabled(AEFeature.Facades)
                && AEConfig.instance.isFeatureEnabled(AEFeature.EnableFacadeCrafting)) {
            this.registerRecipeHandler(new NEIFacadeRecipeHandler());
        }
        this.registerBookmarkContainerHandler = this.apiClass
                .getDeclaredMethod("registerBookmarkContainerHandler", Class.class, IBookmarkContainerHandler.class);
        this.registerBookmarkContainerHandler.invoke(apiClass, GuiSkyChest.class, new NEIAEBookmarkContainerHandler()); // Skystone
                                                                                                                        // chests
        this.registerBookmarkContainerHandler
                .invoke(apiClass, GuiCraftingTerm.class, new NEIAETerminalBookmarkContainerHandler()); // Crafting
                                                                                                       // Terminal
        this.registerBookmarkContainerHandler
                .invoke(apiClass, GuiMEMonitorable.class, new NEIAETerminalBookmarkContainerHandler()); // Terminal
        this.registerBookmarkContainerHandler
                .invoke(apiClass, GuiWirelessTerm.class, new NEIAETerminalBookmarkContainerHandler()); // Wireless
                                                                                                       // Terminal
        this.registerBookmarkContainerHandler
                .invoke(apiClass, GuiPatternTerm.class, new NEIAETerminalBookmarkContainerHandler()); // Pattern
                                                                                                      // terminal
        this.registerBookmarkContainerHandler
                .invoke(apiClass, GuiPatternTermEx.class, new NEIAETerminalBookmarkContainerHandler()); // Big pattern
                                                                                                        // terminal
        this.registerBookmarkContainerHandler
                .invoke(apiClass, GuiMEPortableCell.class, new NEIAETerminalBookmarkContainerHandler()); // ME chest
        // large stack tooltips
        GuiContainerManager.addTooltipHandler(this);
        GuiContainerManager.addObjectHandler(this);

        // crafting terminal...
        final Method registerGuiOverlay = this.apiClass
                .getDeclaredMethod("registerGuiOverlay", Class.class, String.class, IStackPositioner.class);
        final Class<?> overlayHandler = Class.forName("codechicken.nei.api.IOverlayHandler");

        final Method registrar = this.apiClass
                .getDeclaredMethod("registerGuiOverlayHandler", Class.class, overlayHandler, String.class);
        registerGuiOverlay.invoke(this.apiClass, GuiCraftingTerm.class, "crafting", new TerminalCraftingSlotFinder());
        registerGuiOverlay.invoke(this.apiClass, GuiPatternTerm.class, "crafting", new TerminalCraftingSlotFinder());

        final Class<NEICraftingHandler> defaultHandler = NEICraftingHandler.class;
        final Constructor<NEICraftingHandler> defaultConstructor = defaultHandler.getConstructor(int.class, int.class);
        registrar.invoke(this.apiClass, GuiCraftingTerm.class, defaultConstructor.newInstance(6, 75), "crafting");
        registrar.invoke(this.apiClass, GuiPatternTerm.class, defaultConstructor.newInstance(6, 75), "crafting");
    }

    public void registerRecipeHandler(final Object o)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        this.registerRecipeHandler.invoke(this.apiClass, o);
        this.registerUsageHandler.invoke(this.apiClass, o);
    }

    @Override
    public void postInit() {}

    @Override
    public void drawSlot(final Slot s) {
        if (s == null) {
            return;
        }

        final ItemStack stack = s.getStack();

        if (stack == null) {
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        final FontRenderer fontRenderer = mc.fontRenderer;
        final int x = s.xDisplayPosition;
        final int y = s.yDisplayPosition;

        GuiContainerManager.drawItems.renderItemAndEffectIntoGUI(fontRenderer, mc.getTextureManager(), stack, x, y);
        GuiContainerManager.drawItems.renderItemOverlayIntoGUI(
                fontRenderer,
                mc.getTextureManager(),
                stack,
                x,
                y,
                String.valueOf(stack.stackSize));
    }

    @Override
    public RenderItem setItemRender(final RenderItem renderItem) {
        try {
            final RenderItem ri = GuiContainerManager.drawItems;
            GuiContainerManager.drawItems = renderItem;
            return ri;
        } catch (final Throwable t) {
            throw new IllegalStateException("Invalid version of NEI, please update", t);
        }
    }

    @Override
    public List<String> handleTooltip(final GuiContainer arg0, final int arg1, final int arg2,
            final List<String> current) {
        return current;
    }

    @Override
    public List<String> handleItemDisplayName(final GuiContainer arg0, final ItemStack arg1,
            final List<String> current) {
        return current;
    }

    @Override
    public List<String> handleItemTooltip(final GuiContainer guiScreen, final ItemStack stack, final int mouseX,
            final int mouseY, final List<String> currentToolTip) {
        if (guiScreen instanceof IGuiTooltipHandler) {
            return ((IGuiTooltipHandler) guiScreen).handleItemTooltip(stack, mouseX, mouseY, currentToolTip);
        }

        return currentToolTip;
    }

    @Override
    public void guiTick(GuiContainer gui) {}

    @Override
    public void refresh(GuiContainer gui) {}

    @Override
    public void load(GuiContainer gui) {}

    @Override
    public ItemStack getStackUnderMouse(GuiContainer gui, int mousex, int mousey) {
        if (gui instanceof IGuiTooltipHandler) {
            return ((IGuiTooltipHandler) gui).getHoveredStack();
        }
        return null;
    }

    @Override
    public boolean objectUnderMouse(GuiContainer gui, int mousex, int mousey) {
        return false;
    }

    @Override
    public boolean shouldShowTooltip(GuiContainer gui) {
        if (gui instanceof GuiCraftConfirm) return ((GuiCraftConfirm) gui).getHoveredStack() == null;
        if (gui instanceof GuiCraftingCPU) return ((GuiCraftingCPU) gui).getHoveredStack() == null;
        if (gui instanceof GuiOptimizePatterns) return ((GuiOptimizePatterns) gui).getHoveredStack() == null;
        return true;
    }

    public void addItemToBookMark(ItemStack itemStack) {
        ItemsGrid grid = LayoutManager.bookmarkPanel.getGrid();
        grid.setPage(grid.getNumPages() - 1);
        LayoutManager.bookmarkPanel.addItem(itemStack);
    }
}
