package appeng.test.mockme;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import net.minecraftforge.common.util.ForgeDirection;

public class MockGridMachine implements IGridHost {
    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return null;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.GLASS;
    }

    @Override
    public void securityBreak() {
        // no-op
    }
}
