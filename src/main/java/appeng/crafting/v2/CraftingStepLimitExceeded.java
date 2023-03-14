package appeng.crafting.v2;

import appeng.core.localization.GuiText;

public class CraftingStepLimitExceeded extends RuntimeException {

    public CraftingStepLimitExceeded() {}

    public CraftingStepLimitExceeded(Throwable cause) {
        super(cause);
    }

    @Override
    public String toString() {
        return GuiText.CraftingStepLimitExceeded.getUnlocalized();
    }
}
