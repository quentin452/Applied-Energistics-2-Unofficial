package appeng.util.inv;

import appeng.parts.p2p.PartP2PItems;

public class AdaptorP2PItem extends AdaptorIInventory {

    public AdaptorP2PItem(PartP2PItems p2p) {
        super(p2p, p2p.getInventoryStackLimit());
    }
}
