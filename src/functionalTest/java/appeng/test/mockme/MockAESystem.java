package appeng.test.mockme;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.crafting.v2.CraftingJobV2;
import appeng.me.cache.CraftingGridCache;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class MockAESystem {
    public final World world;
    public final MockGrid grid = new MockGrid();
    public final BaseActionSource dummyActionSource = new BaseActionSource();
    public final CraftingGridCache cgCache;

    public MockAESystem(World world) {
        this.world = world;
        this.cgCache = grid.getCache(ICraftingGrid.class);
    }

    public CraftingJobV2 makeCraftingJob(ItemStack request) {
        return new CraftingJobV2(world, grid, dummyActionSource, AEItemStack.create(request), null);
    }
}
