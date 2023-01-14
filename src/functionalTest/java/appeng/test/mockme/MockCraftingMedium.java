package appeng.test.mockme;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import net.minecraft.inventory.InventoryCrafting;

public class MockCraftingMedium implements ICraftingMedium {
    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        return true;
    }

    @Override
    public boolean isBusy() {
        return false;
    }
}
