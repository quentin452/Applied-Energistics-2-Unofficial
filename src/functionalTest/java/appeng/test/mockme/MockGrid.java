package appeng.test.mockme;

import appeng.me.Grid;

public class MockGrid extends Grid {
    public final MockGridNode rootNode;

    public MockGrid() {
        super(new MockGridNode());
        rootNode = (MockGridNode) this.getPivot();
    }
}
