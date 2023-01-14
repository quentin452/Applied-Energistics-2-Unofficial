package appeng.test.mockme;

import appeng.api.networking.*;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import java.util.EnumSet;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

public class MockGridBlock implements IGridBlock {
    @Override
    public double getIdlePowerUsage() {
        return 0;
    }

    @Override
    public EnumSet<GridFlags> getFlags() {
        return EnumSet.noneOf(GridFlags.class);
    }

    @Override
    public boolean isWorldAccessible() {
        return false;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(0, 0, 0, 256);
    }

    @Override
    public AEColor getGridColor() {
        return AEColor.Transparent;
    }

    @Override
    public void onGridNotification(GridNotification notification) {
        // no-op
    }

    @Override
    public void setNetworkStatus(IGrid grid, int channelsInUse) {
        // no-op
    }

    @Override
    public EnumSet<ForgeDirection> getConnectableSides() {
        return EnumSet.allOf(ForgeDirection.class);
    }

    @Override
    public IGridHost getMachine() {
        return new MockGridMachine();
    }

    @Override
    public void gridChanged() {
        // no-op
    }

    @Override
    public ItemStack getMachineRepresentation() {
        return new ItemStack(Items.record_13);
    }
}
