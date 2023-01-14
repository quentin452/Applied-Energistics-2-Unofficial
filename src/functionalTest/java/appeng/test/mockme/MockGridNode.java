package appeng.test.mockme;

import appeng.me.GridNode;

public class MockGridNode extends GridNode {
    public final MockGridBlock myBlock;

    public MockGridNode() {
        super(new MockGridBlock());
        myBlock = (MockGridBlock) this.getGridBlock();
    }
}
