package appeng.block.crafting;

import appeng.client.texture.ExtraBlockTextures;
import appeng.tile.crafting.TileCraftingStorageTile;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

public class BlockAdvancedCraftingStorage extends BlockCraftingStorage {

    public BlockAdvancedCraftingStorage() {
        this.setTileEntity(TileCraftingStorageTile.class);
    }

    @Override
    public IIcon getIcon(final int direction, final int metadata) {
        switch (metadata & (~4)) {
            default:
                return super.getIcon(0, -1);
            case 0:
                return ExtraBlockTextures.BlockCraftingStorage256k.getIcon();
            case 1:
                return ExtraBlockTextures.BlockCraftingStorage1024k.getIcon();
            case 2:
                return ExtraBlockTextures.BlockCraftingStorage4096k.getIcon();
            case 3:
                return ExtraBlockTextures.BlockCraftingStorage16384k.getIcon();

            case FLAG_FORMED:
                return ExtraBlockTextures.BlockCraftingStorage256kFit.getIcon();
            case 1 | FLAG_FORMED:
                return ExtraBlockTextures.BlockCraftingStorage1024kFit.getIcon();
            case 2 | FLAG_FORMED:
                return ExtraBlockTextures.BlockCraftingStorage4096kFit.getIcon();
            case 3 | FLAG_FORMED:
                return ExtraBlockTextures.BlockCraftingStorage16384kFit.getIcon();
        }
    }

    @Override
    public String getUnlocalizedName(final ItemStack is) {
        switch (is.getItemDamage()) {
            case 0:
                return "tile.appliedenergistics2.BlockCraftingStorage256k";
            case 1:
                return "tile.appliedenergistics2.BlockCraftingStorage1024k";
            case 2:
                return "tile.appliedenergistics2.BlockCraftingStorage4096k";
            case 3:
                return "tile.appliedenergistics2.BlockCraftingStorage16384k";
            default:
                return this.getItemUnlocalizedName(is);
        }
    }
}
