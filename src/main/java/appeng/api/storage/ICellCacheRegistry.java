package appeng.api.storage;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public interface ICellCacheRegistry {

    @SideOnly(Side.CLIENT)
    long getTotalBytes();

    @SideOnly(Side.CLIENT)
    long getFreeBytes();

    @SideOnly(Side.CLIENT)
    long getUsedBytes();

    @SideOnly(Side.CLIENT)
    long getTotalTypes();

    @SideOnly(Side.CLIENT)
    long getFreeTypes();

    @SideOnly(Side.CLIENT)
    long getUsedTypes();

    @SideOnly(Side.CLIENT)
    StorageChannel getCellType();

}
