package appeng.api.crafting;

import net.minecraft.item.ItemStack;

/**
 * A custom itemstack provider for a {@link net.minecraft.tileentity.TileEntity} to show what machine an interface is
 * attached to.
 */
public interface ICraftingIconProvider {

    /**
     * @return The item to show in a crafting tree and the interface terminal when an interface is attached to this tile
     *         entity.
     */
    ItemStack getMachineCraftingIcon();
}
